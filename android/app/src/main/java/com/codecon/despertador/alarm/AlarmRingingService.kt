package com.codecon.despertador.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.codecon.despertador.MainActivity
import com.codecon.despertador.domain.VolumeRamp

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startTimeMillis = 0L
    private val volumeRamp = VolumeRamp(start = 0.05f, end = 1.0f, durationSeconds = 30.0)

    private val rampRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000.0
                    val currentVolume = volumeRamp.level(elapsedSeconds)
                    try {
                        player.setVolume(currentVolume, currentVolume)
                    } catch (_: Exception) {}
                    if (elapsedSeconds < volumeRamp.durationSeconds) {
                        handler.postDelayed(this, 1000L)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val target = intent?.getLongExtra(EXTRA_TARGET, 0L) ?: 0L
        val ringTime = intent?.getLongExtra(EXTRA_RING_TIME, 0L) ?: System.currentTimeMillis()

        val notification = buildAlarmNotification(target, ringTime)
        startForeground(NOTIFICATION_ID, notification)

        startRinging()

        return START_STICKY
    }

    private fun startRinging() {
        if (mediaPlayer == null) {
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    val initialVolume = volumeRamp.level(0.0)
                    setVolume(initialVolume, initialVolume)
                    prepare()
                    start()
                }

                startTimeMillis = System.currentTimeMillis()
                handler.post(rampRunnable)
            } catch (_: Exception) {
                // Fallback gracioso se dispositivo não tiver áudio disponível
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarme Despertador",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações do alarme em reprodução"
                setBypassDnd(true)
                setSound(null, audioAttributes)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(target: Long, ringTime: Long): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_RING_TIME, ringTime)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarme Despertador!")
            .setContentText("Sacuda o celular para desligar o alarme.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(rampRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "alarm_ringing_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RING_TIME = "extra_ring_time"
        const val EXTRA_TARGET = "extra_target"
        const val ACTION_STOP_ALARM = "com.codecon.despertador.ACTION_STOP_ALARM"
    }
}
