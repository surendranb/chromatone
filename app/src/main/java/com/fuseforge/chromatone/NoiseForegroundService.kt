package com.fuseforge.chromatone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fuseforge.chromatone.audio.NoiseGenerator
import com.fuseforge.chromatone.audio.NoisePlayer

class NoiseForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "chromatone_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.fuseforge.chromatone.PLAY"
        const val ACTION_PAUSE = "com.fuseforge.chromatone.PAUSE"
        const val ACTION_STOP = "com.fuseforge.chromatone.STOP"
        const val EXTRA_NOISE_TYPE = "noise_type"
        const val EXTRA_VOLUME = "volume"
    }

    private var noisePlayer: NoisePlayer? = null
    private var isPlaying = false
    private var currentNoiseType: NoiseType = NoiseType.White
    private var currentVolume: Float = 0.7f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Get extras for noise type and volume if present
        intent?.getStringExtra(EXTRA_NOISE_TYPE)?.let {
            currentNoiseType = NoiseType.valueOf(it)
        }
        currentVolume = intent?.getFloatExtra(EXTRA_VOLUME, 0.7f) ?: currentVolume

        when (intent?.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            else -> if (!isPlaying) handlePlay() // Default: start playback if not already
        }
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handlePlay() {
        noisePlayer?.stop()
        noisePlayer = NoisePlayer(
            bufferProvider = { bufferSize -> NoiseGenerator.getNoiseBuffer(currentNoiseType, bufferSize) },
            volume = currentVolume
        )
        noisePlayer?.start()
        isPlaying = true
        updateNotification()
    }

    private fun handlePause() {
        if (!isPlaying) return
        noisePlayer?.stop()
        noisePlayer = null
        isPlaying = false
        updateNotification()
    }

    private fun handleStop() {
        noisePlayer?.stop()
        noisePlayer = null
        isPlaying = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChromaTone")
            .setContentText("Playing ${currentNoiseType.displayName}")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .addAction(playPauseAction)
            .addAction(stopAction)
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
        super.onDestroy()
    }
} 