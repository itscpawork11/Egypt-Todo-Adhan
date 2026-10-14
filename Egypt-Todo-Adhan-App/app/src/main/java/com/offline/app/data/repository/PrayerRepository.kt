package com.offline.app.data.repository

import android.content.Context
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Prayer
import com.offline.app.core.alarm.AlarmScheduler
import com.offline.app.core.prayer.EgyptGovernorate
import com.offline.app.core.prayer.NextPrayerInfo
import com.offline.app.core.prayer.PrayerCalculationEngine
import com.offline.app.core.prayer.PrayerOffsets
import com.offline.app.core.prayer.PrayerTimings
import com.offline.app.data.local.AppDatabase
import com.offline.app.data.local.dao.PrayerConfigDao
import com.offline.app.data.local.entities.PrayerConfigEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/repository/PrayerRepository.kt
 *
 * Repository that owns the prayer configuration, computes daily prayer times with
 * [PrayerCalculationEngine], and keeps the exact alarms synchronized with
 * [AlarmScheduler].
 *
 * The configuration row is always exposed as a non-null entity: when the database
 * has no row yet, the default Egypt (Cairo) configuration is used.
 */
interface PrayerRepository {

    // ----- Configuration reads -----

    /** The configuration, always non-null (falls back to defaults). */
    fun observePrayerConfig(): Flow<PrayerConfigEntity>

    /** The selected governorate derived from the stored name. */
    fun observeGovernorate(): Flow<EgyptGovernorate>

    /** One-shot read of the configuration. */
    suspend fun getPrayerConfig(): PrayerConfigEntity

    // ----- Configuration writes -----

    /** Persists the full configuration and re-syncs the alarms. */
    suspend fun savePrayerConfig(config: PrayerConfigEntity)

    /** Updates the selected governorate and re-syncs the alarms. */
    suspend fun updateGovernorate(governorateName: String)

    /** Updates all five manual minute offsets and re-syncs the alarms. */
    suspend fun updateOffsets(
        fajr: Int,
        dhuhr: Int,
        asr: Int,
        maghrib: Int,
        isha: Int
    )

    /** Updates the pre-Adhan reminder lead time (0 disables) and re-syncs alarms. */
    suspend fun updatePreAdhanReminderMinutes(minutes: Int)

    /** Enables or disables the Adhan sound and re-syncs the alarms. */
    suspend fun setAdhanSoundEnabled(enabled: Boolean)

    /** Sets the Adhan sound URI (null clears it, reverting to the default sound). */
    suspend fun setAdhanSoundUri(uri: String?)

    /** Restores factory defaults and re-syncs the alarms. */
    suspend fun resetToDefaults()

    // ----- Prayer time computation -----

    /** Daily timings derived from the current configuration for today. */
    fun observeDailyTimings(): Flow<PrayerTimings>

    /** Calculates timings for the local day of [date] using the current config. */
    suspend fun calculateDailyTimings(date: Date = Date()): PrayerTimings

    /** Calculates timings for a [localDate] and an explicit [config]. */
    fun calculateTimings(localDate: LocalDate, config: PrayerConfigEntity): PrayerTimings

    /** The prayer window that contains the current instant. */
    suspend fun currentPrayer(): Prayer

    /** The next upcoming prayer with its countdown. */
    suspend fun nextPrayer(): NextPrayerInfo

    // ----- Alarm coordination -----

    /** Re-arms the next 24 hours of prayer alarms from the current configuration. */
    suspend fun syncAlarms()

    /** Cancels all prayer alarms. */
    suspend fun cancelAlarms()
}

/**
 * Default [PrayerRepository] implementation.
 *
 * @param prayerConfigDao the Room data source for the single configuration row.
 * @param appContext an application context used only for alarm scheduling.
 */
class DefaultPrayerRepository(
    private val prayerConfigDao: PrayerConfigDao,
    private val appContext: Context
) : PrayerRepository {

    private val applicationContext: Context = appContext.applicationContext

    // =====================================================================
    // Configuration reads
    // =====================================================================

    override fun observePrayerConfig(): Flow<PrayerConfigEntity> =
        prayerConfigDao.observeConfig().map { entity -> entity ?: defaultConfig() }

    override fun observeGovernorate(): Flow<EgyptGovernorate> =
        observePrayerConfig().map { config ->
            EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        }

    override suspend fun getPrayerConfig(): PrayerConfigEntity {
        val existing = prayerConfigDao.getConfig()
        if (existing != null) return existing
        val fallback = defaultConfig()
        prayerConfigDao.upsertConfig(fallback)
        return fallback
    }

    // =====================================================================
    // Configuration writes
    // =====================================================================

    override suspend fun savePrayerConfig(config: PrayerConfigEntity) {
        prayerConfigDao.upsertConfig(config.copy(id = PrayerConfigEntity.SINGLETON_ID))
        syncAlarms()
    }

    override suspend fun updateGovernorate(governorateName: String) {
        prayerConfigDao.updateGovernorate(governorateName)
        syncAlarms()
    }

    override suspend fun updateOffsets(
        fajr: Int,
        dhuhr: Int,
        asr: Int,
        maghrib: Int,
        isha: Int
    ) {
        prayerConfigDao.updateFajrOffset(fajr)
        prayerConfigDao.updateDhuhrOffset(dhuhr)
        prayerConfigDao.updateAsrOffset(asr)
        prayerConfigDao.updateMaghribOffset(maghrib)
        prayerConfigDao.updateIshaOffset(isha)
        syncAlarms()
    }

    override suspend fun updatePreAdhanReminderMinutes(minutes: Int) {
        prayerConfigDao.updatePreAdhanReminderMinutes(minutes)
        syncAlarms()
    }

    override suspend fun setAdhanSoundEnabled(enabled: Boolean) {
        prayerConfigDao.updateAdhanSoundEnabled(enabled)
        syncAlarms()
    }

    override suspend fun setAdhanSoundUri(uri: String?) {
        prayerConfigDao.updateAdhanSoundUri(uri)
    }

    override suspend fun resetToDefaults() {
        prayerConfigDao.resetToDefaults()
        syncAlarms()
    }

    // =====================================================================
    // Prayer time computation
    // =====================================================================

    override fun observeDailyTimings(): Flow<PrayerTimings> =
        observePrayerConfig().map { config ->
            val localDate = LocalDate.now()
            calculateTimings(localDate, config)
        }

    override suspend fun calculateDailyTimings(date: Date): PrayerTimings {
        val config = getPrayerConfig()
        val zoneId = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(date.time).atZone(zoneId).toLocalDate()
        return calculateTimings(localDate, config)
    }

    override fun calculateTimings(
        localDate: LocalDate,
        config: PrayerConfigEntity
    ): PrayerTimings {
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = toOffsets(config)
        return PrayerCalculationEngine.calculateForLocalDate(coordinates, localDate, offsets)
    }

    override suspend fun currentPrayer(): Prayer {
        val config = getPrayerConfig()
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = toOffsets(config)
        val timings = PrayerCalculationEngine.calculate(coordinates, Date(), offsets)
        return PrayerCalculationEngine.currentPrayer(timings, System.currentTimeMillis())
    }

    override suspend fun nextPrayer(): NextPrayerInfo {
        val config = getPrayerConfig()
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = toOffsets(config)
        return PrayerCalculationEngine.nextPrayerInfo(
            coordinates = coordinates,
            date = Date(),
            offsets = offsets
        )
    }

    // =====================================================================
    // Alarm coordination
    // =====================================================================

    override suspend fun syncAlarms() {
        val config = getPrayerConfig()
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = toOffsets(config)

        // Remove stale alarms first so changes never leave an orphan behind.
        AlarmScheduler.cancelAllPrayerAlarms(applicationContext)

        val zoneId = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()

        val todayTimings =
            PrayerCalculationEngine.calculateForLocalDate(coordinates, today, offsets)
        val tomorrowTimings =
            PrayerCalculationEngine.calculateForLocalDate(coordinates, today.plusDays(1), offsets)

        for (prayer in OBLIGATORY_PRAYERS) {
            val trigger = nextOccurrenceMillis(prayer, todayTimings, tomorrowTimings, now) ?: continue

            AlarmScheduler.schedulePrayerAdhan(
                context = applicationContext,
                prayer = prayer,
                triggerMillis = trigger,
                governorateName = governorate.englishName
            )

            if (config.preAdhanReminderMinutes > 0) {
                AlarmScheduler.schedulePreAdhanReminder(
                    context = applicationContext,
                    prayer = prayer,
                    prayerMillis = trigger,
                    minutesBefore = config.preAdhanReminderMinutes
                )
            }
        }
    }

    override suspend fun cancelAlarms() {
        AlarmScheduler.cancelAllPrayerAlarms(applicationContext)
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Fallback used only when the database row is missing. The governorate is
     * pinned to [EgyptGovernorate.DEFAULT] (Cairo) so it matches the row seeded by
     * [AppDatabase], rather than the entity's generic "Riyadh" placeholder.
     */
    private fun defaultConfig(): PrayerConfigEntity = PrayerConfigEntity(
        id = PrayerConfigEntity.SINGLETON_ID,
        selectedGovernorate = EgyptGovernorate.DEFAULT.englishName
    )

    private fun toOffsets(config: PrayerConfigEntity): PrayerOffsets = PrayerOffsets(
        fajr = config.fajrOffset,
        dhuhr = config.dhuhrOffset,
        asr = config.asrOffset,
        maghrib = config.maghribOffset,
        isha = config.ishaOffset
    )

    private fun nextOccurrenceMillis(
        prayer: Prayer,
        todayTimings: PrayerTimings,
        tomorrowTimings: PrayerTimings,
        nowMillis: Long
    ): Long? {
        val todayTime = todayTimings.millisOf(prayer)
        if (todayTime != null && todayTime > nowMillis) return todayTime
        return tomorrowTimings.millisOf(prayer)
    }

    companion object {
        private val OBLIGATORY_PRAYERS: List<Prayer> = listOf(
            Prayer.FAJR,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA
        )

        /**
         * Builds a [DefaultPrayerRepository] from the process-wide database.
         */
        fun create(context: Context): PrayerRepository {
            val appContext = context.applicationContext
            return DefaultPrayerRepository(
                prayerConfigDao = AppDatabase.getInstance(appContext).prayerConfigDao(),
                appContext = appContext
            )
        }
    }
}
