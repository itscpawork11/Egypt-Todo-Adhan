package com.offline.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Prayer
import com.offline.app.core.prayer.EgyptGovernorate
import com.offline.app.core.prayer.PrayerCalculationEngine
import com.offline.app.core.prayer.PrayerOffsets
import com.offline.app.core.prayer.PrayerTimings
import com.offline.app.data.local.AppDatabase
import com.offline.app.data.local.entities.PrayerConfigEntity
import com.offline.app.data.local.entities.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/alarm/BootReceiver.kt
 *
 * Re-arms every alarm after a reboot or an app update, because AlarmManager
 * alarms do not survive process death or reboot.
 *
 * It reschedules:
 *  - The next 24 hours of prayer times (the nearest future occurrence of each of
 *    the five prayers), plus their pre-Adhan reminders when enabled.
 *  - Every active task reminder stored in the database.
 *
 * The DB read and the scheduling work happen off the main thread; `goAsync()` is
 * used so the receiver is not destroyed before the work completes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACCEPTED_ACTIONS) return

        // On locked-boot (direct boot) the credential-encrypted database is not
        // available yet. Skip until the user has unlocked the device; the normal
        // BOOT_COMPLETED / package-replaced broadcast will then run the reschedule.
        if (!isUserUnlocked(context)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                rescheduleEverything(appContext)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun rescheduleEverything(context: Context) {
        val database = AppDatabase.getInstance(context)
        val config: PrayerConfigEntity = database.prayerConfigDao().getConfig()
            ?: PrayerConfigEntity()

        // Clear any stale alarms before re-arming to avoid duplicates.
        AlarmScheduler.cancelAllPrayerAlarms(context)

        schedulePrayers(context, config)
        scheduleTasks(context, database.taskDao().getTasksWithReminders())
    }

    // =====================================================================
    // Prayer alarms
    // =====================================================================

    private fun schedulePrayers(context: Context, config: PrayerConfigEntity) {
        val governorate = EgyptGovernorate.fromNameOrDefault(config.selectedGovernorate)
        val coordinates = Coordinates(governorate.latitude, governorate.longitude)
        val offsets = PrayerOffsets(
            fajr = config.fajrOffset,
            dhuhr = config.dhuhrOffset,
            asr = config.asrOffset,
            maghrib = config.maghribOffset,
            isha = config.ishaOffset
        )

        val zoneId = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()

        val todayTimings: PrayerTimings =
            PrayerCalculationEngine.calculateForLocalDate(coordinates, today, offsets)
        val tomorrowTimings: PrayerTimings =
            PrayerCalculationEngine.calculateForLocalDate(coordinates, today.plusDays(1), offsets)

        for (prayer in OBLIGATORY_PRAYERS) {
            val trigger = nextOccurrenceMillis(prayer, todayTimings, tomorrowTimings, now) ?: continue

            AlarmScheduler.schedulePrayerAdhan(
                context = context,
                prayer = prayer,
                triggerMillis = trigger,
                governorateName = governorate.englishName
            )

            if (config.preAdhanReminderMinutes > 0) {
                AlarmScheduler.schedulePreAdhanReminder(
                    context = context,
                    prayer = prayer,
                    prayerMillis = trigger,
                    minutesBefore = config.preAdhanReminderMinutes
                )
            }
        }
    }

    /**
     * Returns the nearest future epoch millis for [prayer]: today's time when it
     * is still ahead, otherwise tomorrow's time.
     */
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

    // =====================================================================
    // Task reminders
    // =====================================================================

    private fun scheduleTasks(context: Context, tasks: List<TaskEntity>) {
        val now = System.currentTimeMillis()
        for (task in tasks) {
            val reminderTime = task.reminderTime ?: continue
            if (reminderTime <= now) continue
            AlarmScheduler.scheduleTaskReminder(
                context = context,
                taskId = task.id,
                taskTitle = task.title,
                triggerMillis = reminderTime
            )
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private fun isUserUnlocked(context: Context): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            ?: return true
        return userManager.isUserUnlocked
    }

    companion object {
        private val ACCEPTED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        /** The five obligatory prayers that receive alarms. */
        private val OBLIGATORY_PRAYERS: List<Prayer> = listOf(
            Prayer.FAJR,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA
        )
    }
}
