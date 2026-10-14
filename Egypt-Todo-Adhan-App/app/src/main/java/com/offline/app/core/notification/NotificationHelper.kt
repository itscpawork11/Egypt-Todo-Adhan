package com.offline.app.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.offline.app.core.service.AdhanAudioService

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/notification/NotificationHelper.kt
 *
 * Central notification factory shared by [com.offline.app.core.alarm.AlarmReceiver]
 * and [AdhanAudioService].
 *
 * Responsibilities:
 *  - Create the notification channels (high importance for reminders, a dedicated
 *    channel for Adhan playback).
 *  - Build and post pre-Adhan and task reminder notifications with sound and
 *    vibration.
 *  - Build the Adhan foreground-service notification with a full-screen intent and
 *    a "stop" action.
 *
 * The content intent targets the launcher activity by class name (no compile-time
 * dependency on the activity class), so this module stays decoupled from the UI
 * layer.
 */
object NotificationHelper {

    /** Channel for pre-Adhan reminder notifications. */
    const val CHANNEL_PRE_ADHAN: String = "pre_adhan_reminders"

    /** Channel for task reminder notifications. */
    const val CHANNEL_TASK_REMINDER: String = "task_reminders"

    /** Channel for the Adhan playback foreground-service notification. */
    const val CHANNEL_ADHAN_PLAYBACK: String = "adhan_playback"

    /** Fixed notification id for the Adhan foreground service. */
    const val ADHAN_SERVICE_NOTIFICATION_ID: Int = 4100

    private const val PRE_ADHAN_NOTIFICATION_BASE: Int = 4200

    private const val TASK_NOTIFICATION_BASE: Int = 5000

    private const val TASK_NOTIFICATION_RANGE: Long = 500_000L

    private const val MAIN_ACTIVITY_REQUEST_CODE: Int = 7000

    private const val STOP_ACTION_REQUEST_CODE: Int = 7001

    /** Fully-qualified name of the launcher activity (opened from notifications). */
    private const val MAIN_ACTIVITY_CLASS_NAME: String = "com.offline.app.MainActivity"

    // =====================================================================
    // Channels
    // =====================================================================

    /**
     * Creates all notification channels. Safe to call repeatedly: channels are
     * created idempotently and existing user settings are preserved.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val preAdhanChannel = NotificationChannel(
            CHANNEL_PRE_ADHAN,
            "Pre-Adhan reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies shortly before each prayer time."
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 300L, 200L, 300L)
            enableLights(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val taskChannel = NotificationChannel(
            CHANNEL_TASK_REMINDER,
            "Task reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminds you about scheduled tasks."
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 400L, 150L, 400L)
            enableLights(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val adhanChannel = NotificationChannel(
            CHANNEL_ADHAN_PLAYBACK,
            "Adhan playback",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shown while the Adhan audio is playing."
            // The Adhan audio itself is played by MediaPlayer, so the channel is
            // silent to avoid double playback.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(preAdhanChannel)
        manager.createNotificationChannel(taskChannel)
        manager.createNotificationChannel(adhanChannel)
    }

    /** Whether the user currently allows notifications for this app. */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // =====================================================================
    // Reminder notifications
    // =====================================================================

    /**
     * Posts a high-importance pre-Adhan reminder.
     *
     * @param prayerOrdinal [com.batoulapps.adhan.Prayer] ordinal (request/id key).
     * @param prayerName the prayer enum name (e.g. "FAJR").
     * @param minutesBefore how many minutes remain until the prayer.
     * @param triggerMillis the prayer time the reminder refers to.
     */
    fun showPreAdhanReminder(
        context: Context,
        prayerOrdinal: Int,
        prayerName: String,
        minutesBefore: Int,
        triggerMillis: Long
    ) {
        ensureChannels(context)
        if (!areNotificationsEnabled(context)) return

        val arabicName = arabicPrayerName(prayerName)
        val notification = NotificationCompat.Builder(context, CHANNEL_PRE_ADHAN)
            .setSmallIcon(reminderSmallIcon())
            .setContentTitle("اقترب موعد الصلاة")
            .setContentText("صلاة $arabicName بعد $minutesBefore دقيقة")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("صلاة $arabicName بعد $minutesBefore دقيقة")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(triggerMillis)
            .setShowWhen(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0L, 300L, 200L, 300L))
            .build()

        notifyIfAllowed(context, preAdhanNotificationId(prayerOrdinal), notification)
    }

    /**
     * Posts a high-importance task reminder.
     *
     * @param taskId the Room task id (used to derive the notification id).
     * @param taskTitle the task title.
     */
    fun showTaskReminder(
        context: Context,
        taskId: Long,
        taskTitle: String
    ) {
        ensureChannels(context)
        if (!areNotificationsEnabled(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_REMINDER)
            .setSmallIcon(reminderSmallIcon())
            .setContentTitle("تذكير بمهمة")
            .setContentText(taskTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskTitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0L, 400L, 150L, 400L))
            .build()

        notifyIfAllowed(context, taskNotificationId(taskId), notification)
    }

    // =====================================================================
    // Adhan foreground-service notification
    // =====================================================================

    /**
     * Builds the ongoing Adhan notification with a full-screen intent and a stop
     * action.
     *
     * @param prayerName the prayer enum name (e.g. "FAJR").
     * @param isPlaying true once the audio has started, false while preparing.
     */
    fun buildAdhanNotification(
        context: Context,
        prayerName: String,
        isPlaying: Boolean
    ): Notification {
        ensureChannels(context)
        val arabicName = arabicPrayerName(prayerName)
        val text = if (isPlaying) {
            "الأذان قيد التشغيل الآن - اضغط لإيقافه"
        } else {
            "جارٍ تحضير الأذان..."
        }
        return NotificationCompat.Builder(context, CHANNEL_ADHAN_PLAYBACK)
            .setSmallIcon(adhanSmallIcon())
            .setContentTitle("حان وقت صلاة $arabicName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .setFullScreenIntent(mainActivityPendingIntent(context), true)
            .addAction(
                NotificationCompat.Action(
                    stopActionIcon(),
                    "إيقاف",
                    stopAdhanPendingIntent(context)
                )
            )
            .build()
    }

    /** Posts or updates the Adhan service notification. */
    fun showAdhanServiceNotification(context: Context, notification: Notification) {
        notifyIfAllowed(context, ADHAN_SERVICE_NOTIFICATION_ID, notification)
    }

    /** Cancels a notification by id. */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    // =====================================================================
    // Notification ids
    // =====================================================================

    fun preAdhanNotificationId(prayerOrdinal: Int): Int =
        PRE_ADHAN_NOTIFICATION_BASE + prayerOrdinal

    fun taskNotificationId(taskId: Long): Int {
        val folded = (((taskId % TASK_NOTIFICATION_RANGE) + TASK_NOTIFICATION_RANGE) % TASK_NOTIFICATION_RANGE)
        return TASK_NOTIFICATION_BASE + folded.toInt()
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    private fun notifyIfAllowed(context: Context, id: Int, notification: Notification) {
        if (!areNotificationsEnabled(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /**
     * Content intent that opens the launcher activity. Uses the class name so the
     * data layer does not need a compile-time reference to the UI module.
     */
    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.packageName, MAIN_ACTIVITY_CLASS_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            MAIN_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Intent that tells the service to stop playback. */
    private fun stopAdhanPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AdhanAudioService::class.java).apply {
            action = AdhanAudioService.ACTION_STOP_ADHAN
        }
        return PendingIntent.getService(
            context,
            STOP_ACTION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Framework drawable used as the reminder small icon. */
    private fun reminderSmallIcon(): Int = android.R.drawable.ic_popup_reminder

    /** Framework drawable used as the Adhan small icon. */
    private fun adhanSmallIcon(): Int = android.R.drawable.ic_lock_idle_alarm

    /** Framework drawable used as the stop-action icon. */
    private fun stopActionIcon(): Int = android.R.drawable.ic_menu_close_clear_cancel

    /** Maps an Adhan prayer enum name to its Arabic label. */
    private fun arabicPrayerName(rawName: String?): String = when (rawName) {
        "FAJR" -> "الفجر"
        "SUNRISE" -> "الشروق"
        "DHUHR" -> "الظهر"
        "ASR" -> "العصر"
        "MAGHRIB" -> "المغرب"
        "ISHA" -> "العشاء"
        else -> "الصلاة"
    }
}
