package com.offline.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Prayer
import com.offline.app.core.prayer.EgyptGovernorate
import com.offline.app.core.prayer.NextPrayerInfo
import com.offline.app.core.prayer.PrayerCalculationEngine
import com.offline.app.core.prayer.PrayerOffsets
import com.offline.app.core.prayer.PrayerTimings
import com.offline.app.data.local.entities.PrayerConfigEntity
import com.offline.app.data.repository.PrayerRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/viewmodels/PrayerViewModel.kt
 *
 * Presentation model for the prayer-times screen.
 *
 * Responsibilities:
 *  - Emit today's timetable, the current prayer and the next prayer.
 *  - Drive a 1-second countdown to the next prayer.
 *  - Apply configuration changes (governorate, minute offsets, Adhan sound and
 *    pre-Adhan lead time) through [PrayerRepository], which also keeps the exact
 *    alarms synchronized.
 *
 * The countdown is produced by merging a live configuration flow with an
 * internal one-second ticker, so the remaining time is always fresh while the
 * screen is visible and the ticker stops when it is not subscribed.
 */

/** Manual per-prayer minute offsets edited by the user. */
data class MinuteOffsets(
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
) {
    /** Converts these offsets into the engine's [PrayerOffsets]. */
    fun toPrayerOffsets(): PrayerOffsets = PrayerOffsets(
        fajr = fajr,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        isha = isha
    )

    companion object {
        /** No adjustment. */
        val ZERO: MinuteOffsets = MinuteOffsets()
    }
}

/**
 * Countdown to the next prayer, broken into its parts and a display string.
 */
data class PrayerCountdown(val totalMillis: Long = 0L) {

    val hours: Long get() = totalMillis / MILLIS_PER_HOUR

    val minutes: Long get() = (totalMillis / MILLIS_PER_MINUTE) % 60L

    val seconds: Long get() = (totalMillis / MILLIS_PER_SECOND) % 60L

    /** Countdown formatted as `HH:MM:SS`. */
    val formatted: String get() = "%02d:%02d:%02d".format(hours, minutes, seconds)

    companion object {
        private const val MILLIS_PER_SECOND: Long = 1_000L
        private const val MILLIS_PER_MINUTE: Long = 60_000L
        private const val MILLIS_PER_HOUR: Long = 3_600_000L
    }
}

/**
 * A single row of today's timetable.
 *
 * @param prayer     the prayer this row represents.
 * @param label      Arabic display label.
 * @param timeMillis absolute epoch millis of the prayer time.
 * @param isCurrent  true for the prayer window currently in effect.
 * @param isNext     true for the next upcoming prayer.
 * @param isPast     true once the prayer time has passed.
 */
data class PrayerSlot(
    val prayer: Prayer,
    val label: String,
    val timeMillis: Long,
    val isCurrent: Boolean,
    val isNext: Boolean,
    val isPast: Boolean
)

/**
 * Immutable snapshot rendered by the prayer-times screen.
 *
 * @param isLoading               True until the first configuration emission.
 * @param governorate             The resolved governorate driving the calculation.
 * @param governorateName         Bilingual governorate label.
 * @param timings                 Today's six calculated times.
 * @param slots                   Today's timetable rows (the five obligatory prayers).
 * @param currentPrayer           The prayer window currently in effect.
 * @param currentPrayerLabel      Arabic label of [currentPrayer] (empty for NONE).
 * @param nextPrayer              The next prayer with its trigger and remaining time.
 * @param nextPrayerLabel         Arabic label of the next prayer.
 * @param countdown               Countdown to the next prayer.
 * @param offsets                 The active manual minute offsets.
 * @param isAdhanSoundEnabled     Whether the Adhan sound plays at prayer time.
 * @param adhanSoundUri           The selected Adhan sound URI, or null for default.
 * @param preAdhanReminderMinutes Minutes before Adhan to notify; 0 disables it.
 */
data class PrayerUiState(
    val isLoading: Boolean = true,
    val governorate: EgyptGovernorate = EgyptGovernorate.DEFAULT,
    val governorateName: String = EgyptGovernorate.DEFAULT.displayName,
    val timings: PrayerTimings? = null,
    val slots: List<PrayerSlot> = emptyList(),
    val currentPrayer: Prayer = Prayer.NONE,
    val currentPrayerLabel: String = "",
    val nextPrayer: NextPrayerInfo? = null,
    val nextPrayerLabel: String = "",
    val countdown: PrayerCountdown = PrayerCountdown(),
    val offsets: MinuteOffsets = MinuteOffsets.ZERO,
    val isAdhanSoundEnabled: Boolean = true,
    val adhanSoundUri: String? = null,
    val preAdhanReminderMinutes: Int = 0
)

/**
 * ViewModel backing the prayer-times screen.
 *
 * @param prayerRepository the data source for the configuration and the alarm
 *        synchronization side effects.
 */
class PrayerViewModel(
    private val prayerRepository: PrayerRepository
) : ViewModel() {

    // =====================================================================
    // One-shot error surface
    // =====================================================================

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Latest error text, or null when there is nothing to show. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // =====================================================================
    // Ticker
    // =====================================================================

    /**
     * Emits the current time immediately, then once per second. The infinite
     * loop is cancelled automatically when the collection stops (structured
     * concurrency), so it never leaks a background timer.
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    // =====================================================================
    // UI state
    // =====================================================================

    /**
     * The rendered prayer state: recomputed on every configuration change and on
     * every one-second tick, so the countdown and the current/next prayer stay
     * accurate including across the Isha-to-Fajr rollover.
     */
    val uiState: StateFlow<PrayerUiState> = combine(
        prayerRepository.observePrayerConfig(),
        ticker
    ) { config, nowMillis ->
        buildState(config, nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MILLIS),
        initialValue = PrayerUiState()
    )

    // =====================================================================
    // Configuration operations
    // =====================================================================

    /** Selects a governorate and re-syncs the alarms for its coordinates. */
    fun updateGovernorate(governorate: EgyptGovernorate) {
        launchGuarded {
            prayerRepository.updateGovernorate(governorate.englishName)
        }
    }

    /** Selects a governorate by its stored name (Arabic or English). */
    fun updateGovernorateByName(governorateName: String) {
        val resolved = EgyptGovernorate.fromNameOrDefault(governorateName)
        updateGovernorate(resolved)
    }

    /** Applies all five manual minute offsets and re-syncs the alarms. */
    fun updateMinuteOffsets(offsets: MinuteOffsets) {
        launchGuarded {
            prayerRepository.updateOffsets(
                fajr = offsets.fajr,
                dhuhr = offsets.dhuhr,
                asr = offsets.asr,
                maghrib = offsets.maghrib,
                isha = offsets.isha
            )
        }
    }

    /** Applies the five manual minute offsets from individual values. */
    fun updateMinuteOffsets(
        fajr: Int,
        dhuhr: Int,
        asr: Int,
        maghrib: Int,
        isha: Int
    ) {
        updateMinuteOffsets(
            MinuteOffsets(
                fajr = fajr,
                dhuhr = dhuhr,
                asr = asr,
                maghrib = maghrib,
                isha = isha
            )
        )
    }

    /** Flips the Adhan sound switch, based on the currently stored value. */
    fun toggleAdhanSound() {
        launchGuarded {
            val config = prayerRepository.getPrayerConfig()
            prayerRepository.setAdhanSoundEnabled(!config.isAdhanSoundEnabled)
        }
    }

    /** Sets the Adhan sound switch to an explicit value. */
    fun setAdhanSoundEnabled(enabled: Boolean) {
        launchGuarded {
            prayerRepository.setAdhanSoundEnabled(enabled)
        }
    }

    /**
     * Sets the pre-Adhan lead time. Only 0, 5, 10 or 15 minutes are accepted, per
     * [PrayerConfigEntity.ALLOWED_REMINDER_MINUTES]; anything else is rejected
     * with an error message and no change is stored.
     */
    fun updatePreAdhanMinutes(minutes: Int) {
        if (minutes !in PrayerConfigEntity.ALLOWED_REMINDER_MINUTES) {
            _errorMessage.value = "مدة التنبيه يجب أن تكون 0 أو 5 أو 10 أو 15 دقيقة."
            return
        }
        launchGuarded {
            prayerRepository.updatePreAdhanReminderMinutes(minutes)
        }
    }

    /** Sets the Adhan sound URI; null reverts to the bundled default sound. */
    fun setAdhanSoundUri(uri: String?) {
        launchGuarded {
            prayerRepository.setAdhanSoundUri(uri)
        }
    }

    /** Re-arms the next 24 hours of prayer alarms from the current configuration. */
    fun syncAlarms() {
        launchGuarded {
            prayerRepository.syncAlarms()
        }
    }

    /** Cancels every prayer alarm. */
    fun cancelAlarms() {
        launchGuarded {
            prayerRepository.cancelAlarms()
        }
    }

    /** Dismisses the current error message. */
    fun clearError() {
        _errorMessage.value = null
    }

    // =====================================================================
    // State assembly
    // =====================================================================

    /**
     * Pure state builder: resolves the governorate, computes today's timings,
     * determines the current and next prayer (with tomorrow-Fajr rollover) and
     * builds the timetable rows and countdown for [nowMillis].
     */
    private fun buildState(config: PrayerConfigEntity, nowMillis: Long): PrayerUiState {
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = toPrayerOffsets(config)
        val zoneId = ZoneId.systemDefault()
        val todayLocalDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

        val todayTimings =
            PrayerCalculationEngine.calculateForLocalDate(coordinates, todayLocalDate, offsets)
        val currentPrayer = PrayerCalculationEngine.currentPrayer(todayTimings, nowMillis)

        val obligatoryToday = todayTimings.obligatoryInOrder()
        val upcomingToday = obligatoryToday.firstOrNull { it.second > nowMillis }

        val nextPrayer: Prayer
        val nextTriggerMillis: Long
        if (upcomingToday != null) {
            nextPrayer = upcomingToday.first
            nextTriggerMillis = upcomingToday.second
        } else {
            // After Isha: the next prayer is tomorrow's Fajr.
            val tomorrowTimings = PrayerCalculationEngine.calculateForLocalDate(
                coordinates,
                todayLocalDate.plusDays(1),
                offsets
            )
            nextPrayer = Prayer.FAJR
            nextTriggerMillis = tomorrowTimings.fajrMillis
        }

        val countdown = PrayerCountdown((nextTriggerMillis - nowMillis).coerceAtLeast(0L))

        val slots = obligatoryToday.map { (prayer, millis) ->
            PrayerSlot(
                prayer = prayer,
                label = labelFor(prayer),
                timeMillis = millis,
                isCurrent = prayer == currentPrayer,
                isNext = prayer == nextPrayer,
                isPast = millis <= nowMillis
            )
        }

        return PrayerUiState(
            isLoading = false,
            governorate = governorate,
            governorateName = governorate.displayName,
            timings = todayTimings,
            slots = slots,
            currentPrayer = currentPrayer,
            currentPrayerLabel = labelFor(currentPrayer),
            nextPrayer = NextPrayerInfo(
                prayer = nextPrayer,
                triggerMillis = nextTriggerMillis,
                remainingMillis = countdown.totalMillis
            ),
            nextPrayerLabel = labelFor(nextPrayer),
            countdown = countdown,
            offsets = MinuteOffsets(
                fajr = config.fajrOffset,
                dhuhr = config.dhuhrOffset,
                asr = config.asrOffset,
                maghrib = config.maghribOffset,
                isha = config.ishaOffset
            ),
            isAdhanSoundEnabled = config.isAdhanSoundEnabled,
            adhanSoundUri = config.selectedAdhanSoundUri,
            preAdhanReminderMinutes = config.preAdhanReminderMinutes
        )
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Runs [block] in [viewModelScope], translating any failure into
     * [errorMessage]. Cancellation is always rethrown so structured concurrency
     * is preserved.
     */
    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _errorMessage.value = throwable.message ?: "حدث خطأ غير متوقع."
            }
        }
    }

    private fun toPrayerOffsets(config: PrayerConfigEntity): PrayerOffsets = PrayerOffsets(
        fajr = config.fajrOffset,
        dhuhr = config.dhuhrOffset,
        asr = config.asrOffset,
        maghrib = config.maghribOffset,
        isha = config.ishaOffset
    )

    /** Arabic display label for a prayer, or "" for [Prayer.NONE]. */
    private fun labelFor(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "الفجر"
        Prayer.SUNRISE -> "الشروق"
        Prayer.DHUHR -> "الظهر"
        Prayer.ASR -> "العصر"
        Prayer.MAGHRIB -> "المغرب"
        Prayer.ISHA -> "العشاء"
        Prayer.NONE -> ""
        else -> ""
    }

    companion object {

        private const val TICK_INTERVAL_MILLIS: Long = 1_000L

        private const val SUBSCRIBE_TIMEOUT_MILLIS: Long = 5_000L

        /**
         * Builds a [PrayerViewModel] factory bound to [prayerRepository], for use
         * with `viewModel(factory = PrayerViewModel.factory(repository))`.
         */
        fun factory(prayerRepository: PrayerRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PrayerViewModel(prayerRepository) }
        }
    }
}
