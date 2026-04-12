package com.fuseforge.chromatone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.*
import com.fuseforge.chromatone.audio.NoiseGenerator
import com.fuseforge.chromatone.audio.NoisePlayer

class NoiseForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "chromatone_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.fuseforge.chromatone.PLAY"
        const val ACTION_PAUSE = "com.fuseforge.chromatone.PAUSE"
        const val ACTION_STOP = "com.fuseforge.chromatone.STOP"
        const val ACTION_SET_TIMER = "com.fuseforge.chromatone.SET_TIMER"
        const val EXTRA_NOISE_TYPE = "noise_type"
        const val EXTRA_TIMER_SECONDS = "timer_seconds"
    }

    private var noisePlayer: NoisePlayer? = null
    private var isPlaying = false
    private var currentNoiseType: NoiseType = NoiseType.White
    private var mediaSession: MediaSessionCompat? = null
    private var remainingSeconds: Int? = null
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Binding
    inner class LocalBinder : Binder() {
        fun getService(): NoiseForegroundService = this@NoiseForegroundService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    fun isPlaying(): Boolean = isPlaying
    fun getSelectedNoise(): NoiseType = currentNoiseType
    fun getRemainingSeconds(): Int? = remainingSeconds

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ChromaToneSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handlePlay() }
                override fun onPause() { handlePause() }
                override fun onStop() { handleStop() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extract all extras first
        intent?.getStringExtra(EXTRA_NOISE_TYPE)?.let {
            currentNoiseType = NoiseType.valueOf(it)
        }
        val timerSeconds = intent?.getIntExtra(EXTRA_TIMER_SECONDS, -1) ?: -1
        if (timerSeconds >= 0) {
            remainingSeconds = timerSeconds
        }

        when (intent?.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            ACTION_SET_TIMER -> {
                // If ACTION_SET_TIMER update handled by extraction above, 
                // but handleSetTimer specifically handles isPlaying check
                handleSetTimer(if (timerSeconds >= 0) timerSeconds else null)
            }
            else -> if (!isPlaying) handlePlay() // Default: start playback if not already
        }
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying))
        return START_STICKY
    }


    private fun handlePlay() {
        noisePlayer?.stop()
        noisePlayer = NoisePlayer(
            bufferProvider = { bufferSize -> NoiseGenerator.getNoiseBuffer(currentNoiseType, bufferSize) }
        )
        noisePlayer?.start()
        isPlaying = true
        
        // Start/Resume timer if scheduled
        if (remainingSeconds != null && remainingSeconds!! > 0) {
            startTimer()
        }
        
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
    }

    private fun handlePause() {
        if (!isPlaying) return
        noisePlayer?.stop()
        noisePlayer = null
        isPlaying = false
        
        // Pause timer
        timerJob?.cancel()
        timerJob = null
        
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }

    private fun handleStop() {
        noisePlayer?.stop()
        noisePlayer = null
        isPlaying = false
        timerJob?.cancel()
        timerJob = null
        remainingSeconds = null
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSetTimer(seconds: Int?) {
        remainingSeconds = seconds
        timerJob?.cancel()
        if (seconds != null && seconds > 0 && isPlaying) {
            startTimer()
        }
        updateNotification()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (remainingSeconds != null && remainingSeconds!! > 0) {
                delay(1000)
                remainingSeconds = remainingSeconds!! - 1
                updateNotification()
            }
            if (remainingSeconds == 0) {
                handleStop()
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        val position = if (state == PlaybackStateCompat.STATE_PLAYING) 1L else 0L // Approximation as it's continuous
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, position, 1.0f)
                .build()
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                getPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                getPendingIntent(ACTION_PLAY)
            )
        }
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            getPendingIntent(ACTION_STOP)
        )
        val contentText = if (remainingSeconds != null && remainingSeconds!! > 0) {
            val hrs = remainingSeconds!! / 3600
            val mins = (remainingSeconds!! % 3600) / 60
            val secs = remainingSeconds!! % 60
            String.format("Playing %s - %02d:%02d:%02d", currentNoiseType.displayName, hrs, mins, secs)
        } else {
            "Playing ${currentNoiseType.displayName}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChromaTone")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1) // index of actions to show in compact (collapsed) view
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NoiseForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        noisePlayer?.stop()
        noisePlayer = null
        timerJob?.cancel()
        serviceScope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }
}