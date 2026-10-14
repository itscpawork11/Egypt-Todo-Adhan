package com.offline.app.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.offline.app.core.notification.NotificationHelper
import com.offline.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/service/AdhanAudioService.kt
 *
 * Foreground service that plays the Adhan.
 *
 * Behaviour:
 *  - Always runs as a foreground service of type `mediaPlayback`, so playback is
 *    not killed in the background and is allowed to start from an exact alarm.
 *  - Reads the selected Adhan sound URI and the "sound enabled" flag from the Room
 *    configuration; falls back to the system alarm sound when no custom file is set.
 *  - Manages audio focus properly: pauses on transient loss, resumes on regain and
 *    stops on permanent loss.
 *  - Shows an ongoing notification with a full-screen intent and a stop action.
 *  - Releases all resources and stops itself when playback completes or on error.
 */
class AdhanAudioService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null

    private var audioManager: AudioManager? = null

    private var audioFocusRequest: AudioFocusRequest? = null

    private var isForegroundStarted: Boolean = false

    private var prayerName: String = DEFAULT_PRAYER_NAME

    private val audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            onAudioFocusChange(focusChange)
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ADHAN -> {
                stopPlaybackAndSelf()
                return START_NOT_STICKY
            }

            else -> handleStart(intent)
        }
        return START_NOT_STICKY
    }

    // =====================================================================
    // Startup
    // =====================================================================

    private fun handleStart(intent: Intent?) {
        prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: DEFAULT_PRAYER_NAME

        // Become foreground immediately with a "preparing" notification, then load
        // configuration and begin playback asynchronously.
        startForegroundWithNotification(isPlaying = false)

        serviceScope.launch {
            val config = withContext(Dispatchers.IO) {
                runCatching {
                    AppDatabase.getInstance(applicationContext).prayerConfigDao().getConfig()
                }.getOrNull()
            }

            val soundEnabled = config?.isAdhanSoundEnabled ?: true
            if (!soundEnabled) {
                // No audio requested: keep the notification briefly, then stop.
                delay(PLAYER_IDLE_STOP_DELAY_MILLIS)
                stopPlaybackAndSelf()
                return@launch
            }

            startPlayback(config?.selectedAdhanSoundUri)
        }
    }

    private fun startForegroundWithNotification(isPlaying: Boolean) {
        val notification = NotificationHelper.buildAdhanNotification(this, prayerName, isPlaying)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ADHAN_SERVICE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        isForegroundStarted = true
    }

    private fun updateNotification(isPlaying: Boolean) {
        if (!isForegroundStarted) return
        val notification = NotificationHelper.buildAdhanNotification(this, prayerName, isPlaying)
        NotificationHelper.showAdhanServiceNotification(this, notification)
    }

    // =====================================================================
    // Playback
    // =====================================================================

    private fun startPlayback(uriString: String?) {
        if (mediaPlayer != null) return
        requestAudioFocus()

        val uri = resolveAdhanUri(uriString)
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            player.setDataSource(applicationContext, uri)
            player.isLooping = false
            player.setOnPreparedListener { prepared ->
                prepared.start()
                updateNotification(isPlaying = true)
            }
            player.setOnCompletionListener { stopPlaybackAndSelf() }
            player.setOnErrorListener { _, _, _ ->
                stopPlaybackAndSelf()
                true
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (throwable: Throwable) {
            stopPlaybackAndSelf()
        }
    }

    private fun resolveAdhanUri(uriString: String?): Uri {
        if (!uriString.isNullOrBlank()) {
            val parsed = runCatching { Uri.parse(uriString) }.getOrNull()
            if (parsed != null) return parsed
        }
        // Fallback: the system default alarm alert sound.
        return Settings.System.DEFAULT_ALARM_ALERT_URI
    }

    // =====================================================================
    // Audio focus
    // =====================================================================

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        val legacyResult = manager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_ALARM,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        return legacyResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest
            if (request != null) {
                manager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        audioFocusRequest = null
    }

    private fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stopPlaybackAndSelf()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val player = mediaPlayer ?: return
                if (player.isPlaying) player.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val player = mediaPlayer ?: return
                if (player.isPlaying) player.setVolume(DUCK_VOLUME, DUCK_VOLUME)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                val player = mediaPlayer ?: return
                player.setVolume(1f, 1f)
                if (!player.isPlaying) player.start()
            }
        }
    }

    // =====================================================================
    // Teardown
    // =====================================================================

    private fun stopPlaybackAndSelf() {
        releasePlayer()
        abandonAudioFocus()
        if (isForegroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        NotificationHelper.cancelNotification(this, NotificationHelper.ADHAN_SERVICE_NOTIFICATION_ID)
        stopSelf()
    }

    private fun releasePlayer() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.reset() }
        runCatching { player.release() }
        mediaPlayer = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releasePlayer()
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        /** Starts Adhan playback. */
        const val ACTION_START_ADHAN: String = "com.offline.app.action.START_ADHAN"

        /** Stops Adhan playback (used by the notification stop action). */
        const val ACTION_STOP_ADHAN: String = "com.offline.app.action.STOP_ADHAN"

        /** Extra: prayer enum name (e.g. "FAJR"). */
        const val EXTRA_PRAYER_NAME: String = "extra_service_prayer_name"

        /** Extra: prayer ordinal (Int). */
        const val EXTRA_PRAYER_ORDINAL: String = "extra_service_prayer_ordinal"

        private const val DEFAULT_PRAYER_NAME: String = "FAJR"

        private const val PLAYER_IDLE_STOP_DELAY_MILLIS: Long = 4_000L

        private const val DUCK_VOLUME: Float = 0.2f
    }
}
