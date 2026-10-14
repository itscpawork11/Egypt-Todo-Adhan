package com.offline.app.core.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/prayer/PrayerCalculationEngine.kt
 *
 * Pure, fully-offline prayer-time engine built on Adhan-Java (1.2.1).
 *
 * Fixed calculation standard (per Egyptian General Authority of Survey):
 *  - Calculation method: [CalculationMethod.EGYPTIAN] — Fajr angle 19.5°,
 *    Isha angle 17.5°.
 *  - Madhab: [Madhab.SHAFI] — the earlier Asr shadow length.
 *
 * The engine has no Android dependencies, no I/O and no network. All results are
 * absolute UTC instants expressed as epoch milliseconds, which makes them safe
 * to persist in Room and to schedule with AlarmManager.
 */

/**
 * Per-prayer minute adjustments applied on top of the astronomical times.
 * Positive values move a prayer later, negative values earlier.
 * A zeroed instance applies no manual adjustment.
 */
data class PrayerOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
) {
    companion object {
        /** No manual adjustment at all. */
        val NONE: PrayerOffsets = PrayerOffsets()
    }
}

/**
 * The six calculated times for a single local day, as absolute epoch millis (UTC).
 */
data class PrayerTimings(
    val fajrMillis: Long,
    val sunriseMillis: Long,
    val dhuhrMillis: Long,
    val asrMillis: Long,
    val maghribMillis: Long,
    val ishaMillis: Long
) {

    val fajr: Instant get() = Instant.ofEpochMilli(fajrMillis)
    val sunrise: Instant get() = Instant.ofEpochMilli(sunriseMillis)
    val dhuhr: Instant get() = Instant.ofEpochMilli(dhuhrMillis)
    val asr: Instant get() = Instant.ofEpochMilli(asrMillis)
    val maghrib: Instant get() = Instant.ofEpochMilli(maghribMillis)
    val isha: Instant get() = Instant.ofEpochMilli(ishaMillis)

    /** Epoch millis for the requested [prayer], or null for [Prayer.NONE]. */
    fun millisOf(prayer: Prayer): Long? = when (prayer) {
        Prayer.FAJR -> fajrMillis
        Prayer.SUNRISE -> sunriseMillis
        Prayer.DHUHR -> dhuhrMillis
        Prayer.ASR -> asrMillis
        Prayer.MAGHRIB -> maghribMillis
        Prayer.ISHA -> ishaMillis
        else -> null
    }

    /** The five prayers plus sunrise, in chronological order. */
    fun inChronologicalOrder(): List<Pair<Prayer, Long>> = listOf(
        Prayer.FAJR to fajrMillis,
        Prayer.SUNRISE to sunriseMillis,
        Prayer.DHUHR to dhuhrMillis,
        Prayer.ASR to asrMillis,
        Prayer.MAGHRIB to maghribMillis,
        Prayer.ISHA to ishaMillis
    )

    /** The five obligatory prayers only, in chronological order. */
    fun obligatoryInOrder(): List<Pair<Prayer, Long>> = listOf(
        Prayer.FAJR to fajrMillis,
        Prayer.DHUHR to dhuhrMillis,
        Prayer.ASR to asrMillis,
        Prayer.MAGHRIB to maghribMillis,
        Prayer.ISHA to ishaMillis
    )
}

/**
 * Describes the next upcoming prayer relative to a reference instant.
 *
 * @param prayer the upcoming prayer.
 * @param triggerMillis absolute epoch millis when that prayer begins.
 * @param remainingMillis milliseconds left from the reference instant; never
 *        negative.
 */
data class NextPrayerInfo(
    val prayer: Prayer,
    val triggerMillis: Long,
    val remainingMillis: Long
)

/**
 * Stateless prayer-calculation engine.
 *
 * Usage:
 * ```
 * val coordinates = Coordinates(30.0444, 31.2357)
 * val timings = PrayerCalculationEngine.calculate(
 *     coordinates = coordinates,
 *     date = Date(),
 *     offsets = PrayerOffsets(fajr = 2, isha = -1)
 * )
 * val current = PrayerCalculationEngine.currentPrayer(timings, System.currentTimeMillis())
 * val next = PrayerCalculationEngine.nextPrayerInfo(coordinates, Date(), PrayerOffsets.NONE)
 * ```
 */
object PrayerCalculationEngine {

    /** Calculation standard: Egyptian General Authority of Survey. */
    val method: CalculationMethod = CalculationMethod.EGYPTIAN

    /** Asr madhab: Shafi (earlier shadow length). */
    val madhab: Madhab = Madhab.SHAFI

    private const val MILLIS_PER_DAY: Long = 86_400_000L

    /**
     * Builds the Adhan [CalculationParameters] for the fixed Egyptian / Shafi
     * standard with the supplied minute [offsets].
     */
    fun buildParameters(offsets: PrayerOffsets = PrayerOffsets.NONE): CalculationParameters {
        val parameters: CalculationParameters = CalculationMethod.EGYPTIAN.getParameters()
        parameters.madhab = Madhab.SHAFI
        parameters.adjustments.fajr = offsets.fajr
        parameters.adjustments.sunrise = offsets.sunrise
        parameters.adjustments.dhuhr = offsets.dhuhr
        parameters.adjustments.asr = offsets.asr
        parameters.adjustments.maghrib = offsets.maghrib
        parameters.adjustments.isha = offsets.isha
        return parameters
    }

    /**
     * Calculates the six times for the local date that contains [date] in
     * [zoneId] (defaults to the device time zone).
     *
     * @param coordinates latitude/longitude of the selected governorate.
     * @param date any instant within the target local day.
     * @param offsets per-prayer minute adjustments.
     * @param zoneId time zone used to derive the target local date.
     */
    fun calculate(
        coordinates: Coordinates,
        date: Date,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PrayerTimings {
        val localDate: LocalDate = Instant.ofEpochMilli(date.time)
            .atZone(zoneId)
            .toLocalDate()
        return calculateForLocalDate(coordinates, localDate, offsets)
    }

    /**
     * Calculates the six times for an explicit [localDate].
     */
    fun calculateForLocalDate(
        coordinates: Coordinates,
        localDate: LocalDate,
        offsets: PrayerOffsets = PrayerOffsets.NONE
    ): PrayerTimings {
        val dateComponents = DateComponents(
            localDate.year,
            localDate.monthValue,
            localDate.dayOfMonth
        )
        return buildTimings(coordinates, dateComponents, offsets)
    }

    /**
     * Calculates the six times for an explicit calendar date.
     */
    fun calculateForDateParts(
        coordinates: Coordinates,
        year: Int,
        month: Int,
        day: Int,
        offsets: PrayerOffsets = PrayerOffsets.NONE
    ): PrayerTimings {
        return buildTimings(coordinates, DateComponents(year, month, day), offsets)
    }

    private fun buildTimings(
        coordinates: Coordinates,
        dateComponents: DateComponents,
        offsets: PrayerOffsets
    ): PrayerTimings {
        val prayerTimes = PrayerTimes(
            coordinates,
            dateComponents,
            buildParameters(offsets)
        )
        return PrayerTimings(
            fajrMillis = prayerTimes.fajr.requireEpochMillis("Fajr"),
            sunriseMillis = prayerTimes.sunrise.requireEpochMillis("Sunrise"),
            dhuhrMillis = prayerTimes.dhuhr.requireEpochMillis("Dhuhr"),
            asrMillis = prayerTimes.asr.requireEpochMillis("Asr"),
            maghribMillis = prayerTimes.maghrib.requireEpochMillis("Maghrib"),
            ishaMillis = prayerTimes.isha.requireEpochMillis("Isha")
        )
    }

    // =====================================================================
    // Current / next prayer resolution
    // =====================================================================

    /**
     * The prayer window that contains [nowMillis]:
     * FAJR until sunrise, SUNRISE until dhuhr, and so on; from Isha onwards the
     * current window is ISHA. Returns [Prayer.NONE] only before the day's Fajr.
     */
    fun currentPrayer(timings: PrayerTimings, nowMillis: Long): Prayer = when {
        nowMillis >= timings.ishaMillis -> Prayer.ISHA
        nowMillis >= timings.maghribMillis -> Prayer.MAGHRIB
        nowMillis >= timings.asrMillis -> Prayer.ASR
        nowMillis >= timings.dhuhrMillis -> Prayer.DHUHR
        nowMillis >= timings.sunriseMillis -> Prayer.SUNRISE
        nowMillis >= timings.fajrMillis -> Prayer.FAJR
        else -> Prayer.NONE
    }

    /**
     * The next prayer strictly after [nowMillis] within the same day.
     * Returns [Prayer.NONE] once Isha has started (use [nextPrayerInfo] for the
     * tomorrow-Fajr rollover).
     */
    fun nextPrayer(timings: PrayerTimings, nowMillis: Long): Prayer = when {
        nowMillis < timings.fajrMillis -> Prayer.FAJR
        nowMillis < timings.sunriseMillis -> Prayer.SUNRISE
        nowMillis < timings.dhuhrMillis -> Prayer.DHUHR
        nowMillis < timings.asrMillis -> Prayer.ASR
        nowMillis < timings.maghribMillis -> Prayer.MAGHRIB
        nowMillis < timings.ishaMillis -> Prayer.ISHA
        else -> Prayer.NONE
    }

    /**
     * Milliseconds remaining until the next prayer within the same day, or null
     * when there is none left (after Isha).
     */
    fun millisUntilNextPrayer(timings: PrayerTimings, nowMillis: Long): Long? {
        val next = nextPrayer(timings, nowMillis)
        val trigger = timings.millisOf(next) ?: return null
        return (trigger - nowMillis).coerceAtLeast(0L)
    }

    /**
     * Full countdown to the next prayer, with tomorrow-Fajr rollover after Isha.
     *
     * @param coordinates location used to compute the following day when needed.
     * @param date reference instant (its local day is used).
     * @param offsets same adjustments used for today's timings.
     * @param nowMillis the instant "now" is evaluated at.
     * @param zoneId time zone used to derive the local day.
     */
    fun nextPrayerInfo(
        coordinates: Coordinates,
        date: Date,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): NextPrayerInfo {
        val todayTimings = calculate(coordinates, date, offsets, zoneId)
        val todayNext = nextPrayer(todayTimings, nowMillis)
        if (todayNext != Prayer.NONE) {
            val trigger = todayTimings.millisOf(todayNext) ?: 0L
            return NextPrayerInfo(
                prayer = todayNext,
                triggerMillis = trigger,
                remainingMillis = (trigger - nowMillis).coerceAtLeast(0L)
            )
        }
        // After Isha: the next prayer is tomorrow's Fajr.
        val tomorrowLocalDate = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
        val tomorrowTimings = calculateForLocalDate(coordinates, tomorrowLocalDate, offsets)
        val trigger = tomorrowTimings.fajrMillis
        return NextPrayerInfo(
            prayer = Prayer.FAJR,
            triggerMillis = trigger,
            remainingMillis = (trigger - nowMillis).coerceAtLeast(0L)
        )
    }

    /**
     * Convenience overload of [nextPrayerInfo] that uses the already-computed
     * [timings] for today and only falls back to [coordinates] when the day's
     * prayers have finished.
     */
    fun nextPrayerInfo(
        timings: PrayerTimings,
        coordinates: Coordinates,
        date: Date,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): NextPrayerInfo {
        val todayNext = nextPrayer(timings, nowMillis)
        if (todayNext != Prayer.NONE) {
            val trigger = timings.millisOf(todayNext) ?: 0L
            return NextPrayerInfo(
                prayer = todayNext,
                triggerMillis = trigger,
                remainingMillis = (trigger - nowMillis).coerceAtLeast(0L)
            )
        }
        return nextPrayerInfo(coordinates, date, offsets, nowMillis, zoneId)
    }

    /**
     * The next prayer beginning exactly at [nowMillis] (or the following one).
     */
    fun nextPrayerCountdownMillis(
        coordinates: Coordinates,
        date: Date,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = nextPrayerInfo(coordinates, date, offsets, nowMillis, zoneId).remainingMillis

    /** Epoch millis of a specific [prayer] computed via Adhan's own lookup. */
    fun timestampFor(
        coordinates: Coordinates,
        date: Date,
        prayer: Prayer,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val localDate = Instant.ofEpochMilli(date.time).atZone(zoneId).toLocalDate()
        val dateComponents = DateComponents(
            localDate.year,
            localDate.monthValue,
            localDate.dayOfMonth
        )
        val prayerTimes = PrayerTimes(coordinates, dateComponents, buildParameters(offsets))
        val result: Date? = prayerTimes.timeForPrayer(prayer)
        return result?.toInstant()?.toEpochMilli()
    }

    /**
     * Adhan's own "current prayer" resolution for the given instant, delegating
     * to [PrayerTimes.currentPrayer] for parity with the library.
     */
    fun currentPrayerFromAdhan(
        coordinates: Coordinates,
        date: Date,
        offsets: PrayerOffsets = PrayerOffsets.NONE,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Prayer {
        val localDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val dateComponents = DateComponents(
            localDate.year,
            localDate.monthValue,
            localDate.dayOfMonth
        )
        val prayerTimes = PrayerTimes(coordinates, dateComponents, buildParameters(offsets))
        return prayerTimes.currentPrayer(Date(nowMillis))
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Adhan-Java returns `null` for a time when the sun never reaches the
     * required angle (a polar-region edge case that cannot occur at Egyptian
     * latitudes). Converting to epoch millis therefore fails loudly instead of
     * silently producing a bogus value.
     */
    private fun Date?.requireEpochMillis(prayerLabel: String): Long {
        val value = this
            ?: throw IllegalStateException(
                "Adhan did not produce a valid time for $prayerLabel; " +
                    "the coordinate/date combination is out of range."
            )
        return value.toInstant().toEpochMilli()
    }

    /** Milliseconds in one day; exposed for countdown arithmetic. */
    fun millisPerDay(): Long = MILLIS_PER_DAY
}
