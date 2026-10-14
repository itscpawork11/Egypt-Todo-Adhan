package com.offline.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.batoulapps.adhan.Prayer
import com.offline.app.core.notification.NotificationHelper
import com.offline.app.core.service.AdhanAudioService

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/alarm/AlarmReceiver.kt
 *
 * Receives the alarms scheduled by
 * [com.offline.app.core.alarm.AlarmScheduler] and dispatches them:
 *
 *  - [AlarmScheduler.ACTION_PRAYER_ALARM] -> starts [AdhanAudioService] as a
 *    foreground service (plays the Adhan and shows the full-screen notification).
 *  - [AlarmScheduler.ACTION_PRE_ADHAN_REMINDER] -> posts a high-importance
 *    reminder notification with sound and vibration.
 *  - [AlarmScheduler.ACTION_TASK_REMINDER] -> posts a high-importance task
 *    reminder notification with sound and vibration.
 *
 * The receiver is `exported="false"` and is only ever triggered by the app's own
 * PendingIntents.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_PRAYER_ALARM -> handlePrayerAlarm(context, intent)
            AlarmScheduler.ACTION_PRE_ADHAN_REMINDER -> handlePreAdhanReminder(context, intent)
            AlarmScheduler.ACTION_TASK_REMINDER -> handleTaskReminder(context, intent)
            else -> Unit
        }
    }

    private fun handlePrayerAlarm(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)

        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME)
            ?: resolvePrayerName(intent.getIntExtra(AlarmScheduler.EXTRA_PRAYER_ORDINAL, -1))

        val serviceIntent = Intent(context, AdhanAudioService::class.java).apply {
            action = AdhanAudioService.ACTION_START_ADHAN
            putExtra(AdhanAudioService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(
                AdhanAudioService.EXTRA_PRAYER_ORDINAL,
                intent.getIntExtra(AlarmScheduler.EXTRA_PRAYER_ORDINAL, -1)
            )
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun handlePreAdhanReminder(context: Context, intent: Intent) {
        val ordinal = intent.getIntExtra(AlarmScheduler.EXTRA_PRAYER_ORDINAL, -1)
        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME)
            ?: resolvePrayerName(ordinal)
        val minutesBefore = intent.getIntExtra(AlarmScheduler.EXTRA_PRE_ADHAN_MINUTES, 0)
        val triggerMillis = intent.getLongExtra(
            AlarmScheduler.EXTRA_TRIGGER_MILLIS,
            System.currentTimeMillis()
        )

        NotificationHelper.showPreAdhanReminder(
            context = context,
            prayerOrdinal = ordinal,
            prayerName = prayerName,
            minutesBefore = minutesBefore,
            triggerMillis = triggerMillis
        )
    }

    private fun handleTaskReminder(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE)
            ?: "مهمة"

        NotificationHelper.showTaskReminder(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle
        )
    }

    private fun resolvePrayerName(ordinal: Int): String {
        val values = Prayer.values()
        return if (ordinal in values.indices) values[ordinal].name else "FAJR"
    }
}
