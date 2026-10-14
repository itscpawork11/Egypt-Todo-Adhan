package com.offline.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.batoulapps.adhan.Prayer
import com.offline.app.core.alarm.AlarmReceiver

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/alarm/AlarmScheduler.kt
 *
 * Exact-alarm scheduler for the 100% offline prayer-times app.
 *
 * Responsibilities:
 *  1. Schedule the main Adhan alarm at the exact prayer time.
 *  2. Schedule the pre-Adhan notification (e.g. 5/10/15 minutes before).
 *  3. Schedule user task reminders.
 *  4. Cancel any alarm reliably by its unique PendingIntent request code.
 *  5. Guard every exact-alarm call behind the Android 12+ (API 31) / 14+ (API 34)
 *     `canScheduleExactAlarms()` check, degrading gracefully when the capability
 *     is unavailable.
 *
 * All scheduling uses [AlarmManager.RTC_WAKEUP] so that alarms fire against wall
 * clock time and wake the device. When the exact-alarm capability is granted the
 * scheduler uses [AlarmManager.setExactAndAllowWhileIdle] to fire precisely even
 * in Doze; otherwise it falls back to [AlarmManager.setAndAllowWhileIdle] (still
 * Doze-safe, but inexact).
 *
 * This class is stateless; [AlarmScheduler] is a singleton object.
 */
object AlarmScheduler {

    // =====================================================================
    // Intent actions and extras
    // =====================================================================

    /** Fired at the exact prayer time to start the Adhan (audio + notification). */
    const val ACTION_PRAYER_ALARM: String = "com.offline.app.action.PRAYER_ALARM"

    /** Fired shortly before the prayer time to show a reminder notification. */
    const val ACTION_PRE_ADHAN_REMINDER: String = "com.offline.app.action.PRE_ADHAN_REMINDER"

    /** Fired at a task's reminder time. */
    const val ACTION_TASK_REMINDER: String = "com.offline.app.action.TASK_REMINDER"

    /** Extra: the [Prayer] ordinal (Int) associated with the alarm. */
    const val EXTRA_PRAYER_ORDINAL: String = "extra_prayer_ordinal"

    /** Extra: the prayer display key, for the receiver to resolve independently. */
    const val EXTRA_PRAYER_NAME: String = "extra_prayer_name"

    /** Extra: the task id (Long) associated with a task reminder. */
    const val EXTRA_TASK_ID: String = "extra_task_id"

    /** Extra: the task title, so the notification needs no database read. */
    const val EXTRA_TASK_TITLE: String = "extra_task_title"

    /** Extra: minutes before the prayer for a pre-Adhan reminder. */
    const val EXTRA_PRE_ADHAN_MINUTES: String = "extra_pre_adhan_minutes"

    /** Extra: the absolute trigger time in epoch millis. */
    const val EXTRA_TRIGGER_MILLIS: String = "extra_trigger_millis"

    /** Extra: which alarm kind produced this intent. */
    const val EXTRA_ALARM_KIND: String = "extra_alarm_kind"

    // =====================================================================
    // Request-code namespaces (kept far apart so they never collide)
    // =====================================================================

    private const val PRAYER_ADHAN_REQUEST_BASE: Int = 1_000

    private const val PRE_ADHAN_REQUEST_BASE: Int = 2_000

    private const val TASK_REMINDER_REQUEST_BASE: Int = 3_000

    /** Modulus that folds a (possibly large) task id into the request-code space. */
    private const val TASK_REQUEST_RANGE: Long = 500_000L

    private const val MILLIS_PER_MINUTE: Long = 60_000L

    /** Identifier of an alarm family, used for grouping and unique ids. */
    enum class AlarmKind {
        PRAYER_ADHAN,
        PRE_ADHAN_REMINDER,
        TASK_REMINDER
    }

    /** Outcome of a scheduling attempt. */
    enum class AlarmScheduleResult {
        /** Scheduled with [AlarmManager.setExactAndAllowWhileIdle]. */
        SCHEDULED_EXACT,

        /** Scheduled with [AlarmManager.setAndAllowWhileIdle] (no exact permission). */
        SCHEDULED_INEXACT,

        /** The trigger time was in the past; nothing was scheduled. */
        SKIPPED_IN_PAST,

        /** The feature is disabled (e.g. pre-Adhan lead time of 0); nothing scheduled. */
        DISABLED,

        /** No [AlarmManager] was available; nothing was scheduled. */
        FAILED
    }

    // =====================================================================
    // Exact-alarm capability (Android 12+ / 14+)
    // =====================================================================

    /**
     * Whether the app may currently schedule exact alarms.
     *
     * On Android 12 (API 31) and above this reflects
     * [AlarmManager.canScheduleExactAlarms]. On older releases exact alarms are
     * always allowed, so it returns true.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = alarmManager(context) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Builds the Settings intent that lets the user grant the exact-alarm
     * permission. Returns null on Android versions below 12, where the
     * permission does not exist.
     */
    fun exactAlarmPermissionSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // =====================================================================
    // 1. Main Adhan alarm
    // =====================================================================

    /**
     * Schedules the main Adhan alarm for [prayer] at the exact [triggerMillis].
     *
     * @param prayer the prayer whose time has arrived.
     * @param triggerMillis absolute epoch millis of the prayer time.
     * @param governorateName optional governorate label carried in the intent.
     */
    fun schedulePrayerAdhan(
        context: Context,
        prayer: Prayer,
        triggerMillis: Long,
        governorateName: String? = null
    ): AlarmScheduleResult {
        val intent = buildIntent(
            context = context,
            action = ACTION_PRAYER_ALARM,
            kind = AlarmKind.PRAYER_ADHAN,
            requestCode = prayerAdhanRequestCode(prayer)
        ).apply {
            putExtra(EXTRA_PRAYER_ORDINAL, prayer.ordinal)
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerMillis)
            if (governorateName != null) {
                putExtra("extra_governorate_name", governorateName)
            }
        }
        val requestCode = prayerAdhanRequestCode(prayer)
        return schedule(context, requestCode, intent, triggerMillis)
    }

    /**
     * Cancels the main Adhan alarm for [prayer].
     * Returns true if a matching pending alarm existed and was cancelled.
     */
    fun cancelPrayerAdhan(context: Context, prayer: Prayer): Boolean {
        val requestCode = prayerAdhanRequestCode(prayer)
        val intent = buildIntent(
            context = context,
            action = ACTION_PRAYER_ALARM,
            kind = AlarmKind.PRAYER_ADHAN,
            requestCode = requestCode
        )
        return cancel(context, requestCode, intent)
    }

    // =====================================================================
    // 2. Pre-Adhan notification
    // =====================================================================

    /**
     * Schedules a pre-Adhan notification [minutesBefore] before [prayer].
     *
     * A [minutesBefore] of 0 disables the reminder and cancels any existing one.
     *
     * @param prayerMillis absolute epoch millis of the prayer time.
     * @param minutesBefore lead time in minutes (expected: 5, 10 or 15).
     */
    fun schedulePreAdhanReminder(
        context: Context,
        prayer: Prayer,
        prayerMillis: Long,
        minutesBefore: Int
    ): AlarmScheduleResult {
        val requestCode = preAdhanRequestCode(prayer)
        if (minutesBefore <= 0) {
            cancelPreAdhanReminder(context, prayer)
            return AlarmScheduleResult.DISABLED
        }
        val triggerMillis = preAdhanTriggerMillis(prayerMillis, minutesBefore)
        val intent = buildIntent(
            context = context,
            action = ACTION_PRE_ADHAN_REMINDER,
            kind = AlarmKind.PRE_ADHAN_REMINDER,
            requestCode = requestCode
        ).apply {
            putExtra(EXTRA_PRAYER_ORDINAL, prayer.ordinal)
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
            putExtra(EXTRA_PRE_ADHAN_MINUTES, minutesBefore)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        return schedule(context, requestCode, intent, triggerMillis)
    }

    /**
     * Cancels the pre-Adhan reminder for [prayer].
     * Returns true if a matching pending alarm existed and was cancelled.
     */
    fun cancelPreAdhanReminder(context: Context, prayer: Prayer): Boolean {
        val requestCode = preAdhanRequestCode(prayer)
        val intent = buildIntent(
            context = context,
            action = ACTION_PRE_ADHAN_REMINDER,
            kind = AlarmKind.PRE_ADHAN_REMINDER,
            requestCode = requestCode
        )
        return cancel(context, requestCode, intent)
    }

    /** Epoch millis of a pre-Adhan trigger, given the prayer time and lead time. */
    fun preAdhanTriggerMillis(prayerMillis: Long, minutesBefore: Int): Long {
        return prayerMillis - (minutesBefore.coerceAtLeast(0).toLong() * MILLIS_PER_MINUTE)
    }

    // =====================================================================
    // 3. Task reminders
    // =====================================================================

    /**
     * Schedules a reminder for a task.
     *
     * @param taskId the Room task primary key (used to derive the unique id).
     * @param taskTitle title carried in the intent for a database-free notification.
     * @param triggerMillis absolute epoch millis when the reminder should fire.
     */
    fun scheduleTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        triggerMillis: Long
    ): AlarmScheduleResult {
        val requestCode = taskReminderRequestCode(taskId)
        val intent = buildIntent(
            context = context,
            action = ACTION_TASK_REMINDER,
            kind = AlarmKind.TASK_REMINDER,
            requestCode = requestCode
        ).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        return schedule(context, requestCode, intent, triggerMillis)
    }

    /**
     * Cancels a task reminder.
     * Returns true if a matching pending alarm existed and was cancelled.
     */
    fun cancelTaskReminder(context: Context, taskId: Long): Boolean {
        val requestCode = taskReminderRequestCode(taskId)
        val intent = buildIntent(
            context = context,
            action = ACTION_TASK_REMINDER,
            kind = AlarmKind.TASK_REMINDER,
            requestCode = requestCode
        )
        return cancel(context, requestCode, intent)
    }

    /**
     * Reschedules a task reminder for a new time: cancels the previous one and
     * schedules a fresh alarm at [newTriggerMillis].
     */
    fun rescheduleTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String,
        newTriggerMillis: Long
    ): AlarmScheduleResult {
        cancelTaskReminder(context, taskId)
        return scheduleTaskReminder(context, taskId, taskTitle, newTriggerMillis)
    }

    // =====================================================================
    // Bulk cancellation
    // =====================================================================

    /** Cancels the Adhan alarm for every prayer. Returns the number cancelled. */
    fun cancelAllPrayerAdhan(context: Context): Int {
        var cancelled = 0
        for (prayer in Prayer.values()) {
            if (cancelPrayerAdhan(context, prayer)) cancelled++
        }
        return cancelled
    }

    /** Cancels the pre-Adhan reminder for every prayer. Returns the number cancelled. */
    fun cancelAllPreAdhanReminders(context: Context): Int {
        var cancelled = 0
        for (prayer in Prayer.values()) {
            if (cancelPreAdhanReminder(context, prayer)) cancelled++
        }
        return cancelled
    }

    /** Cancels every prayer-related alarm (main + pre-Adhan). Returns cancelled count. */
    fun cancelAllPrayerAlarms(context: Context): Int {
        return cancelAllPrayerAdhan(context) + cancelAllPreAdhanReminders(context)
    }

    /**
     * Cancels the given task reminders. Returns the number cancelled.
     */
    fun cancelTaskReminders(context: Context, taskIds: List<Long>): Int {
        var cancelled = 0
        for (id in taskIds) {
            if (cancelTaskReminder(context, id)) cancelled++
        }
        return cancelled
    }

    // =====================================================================
    // Unique request-code derivation
    // =====================================================================

    /** Unique request code for the main Adhan alarm of [prayer]. */
    fun prayerAdhanRequestCode(prayer: Prayer): Int {
        return PRAYER_ADHAN_REQUEST_BASE + prayer.ordinal
    }

    /** Unique request code for the pre-Adhan reminder of [prayer]. */
    fun preAdhanRequestCode(prayer: Prayer): Int {
        return PRE_ADHAN_REQUEST_BASE + prayer.ordinal
    }

    /** Unique request code for the reminder of [taskId]. */
    fun taskReminderRequestCode(taskId: Long): Int {
        val folded = (((taskId % TASK_REQUEST_RANGE) + TASK_REQUEST_RANGE) % TASK_REQUEST_RANGE)
        return TASK_REMINDER_REQUEST_BASE + folded.toInt()
    }

    // =====================================================================
    // Core scheduling / cancellation
    // =====================================================================

    /**
     * Schedules [intent] (wrapped in a broadcast PendingIntent at [requestCode])
     * for [triggerMillis], choosing the strongest available exact mode.
     */
    private fun schedule(
        context: Context,
        requestCode: Int,
        intent: Intent,
        triggerMillis: Long
    ): AlarmScheduleResult {
        val alarmManager = alarmManager(context) ?: return AlarmScheduleResult.FAILED
        if (triggerMillis <= System.currentTimeMillis()) {
            return AlarmScheduleResult.SKIPPED_IN_PAST
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
            AlarmScheduleResult.SCHEDULED_EXACT
        } else {
            // Doze-safe fallback: fires as soon as the system permits, without an
            // exact-time guarantee. This keeps the app functional when the user
            // has revoked the exact-alarm capability.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
            AlarmScheduleResult.SCHEDULED_INEXACT
        }
    }

    /**
     * Cancels the pending alarm identified by [requestCode] and the identity of
     * [intent]. Uses [PendingIntent.FLAG_NO_CREATE] so nothing is created merely
     * to be cancelled.
     *
     * @return true when a matching PendingIntent existed and was cancelled.
     */
    private fun cancel(context: Context, requestCode: Int, intent: Intent): Boolean {
        val alarmManager = alarmManager(context) ?: return false
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return false

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        return true
    }

    /**
     * Builds the broadcast intent that targets [AlarmReceiver].
     *
     * The [kind] and [requestCode] are placed in the intent so that two different
     * alarms sharing an action still resolve to distinct PendingIntents.
     */
    private fun buildIntent(
        context: Context,
        action: String,
        kind: AlarmKind,
        requestCode: Int
    ): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_KIND, kind.name)
            putExtra("$EXTRA_ALARM_KIND.request_code", requestCode)
        }
    }

    private fun alarmManager(context: Context): AlarmManager? {
        return context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
}
