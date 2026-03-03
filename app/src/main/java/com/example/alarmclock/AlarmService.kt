package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: AlarmDataStore
    private lateinit var scheduler: AlarmScheduler

    companion object {
        const val ACTION_DISMISS = "com.example.alarmclock.DISMISS"
        const val ACTION_SNOOZE = "com.example.alarmclock.SNOOZE"
        const val ACTION_ALARM_DONE = "com.example.alarmclock.ALARM_DONE"
        const val EXTRA_ALARM_ID = "ALARM_ID"
    }

    override fun onCreate() {
        super.onCreate()
        dataStore = AlarmDataStore(this)
        scheduler = AndroidAlarmScheduler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1

        when (intent?.action) {
            ACTION_DISMISS -> {
                if (alarmId != -1) {
                    dismissAlarm(alarmId)
                }
                finishAlarm()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                if (alarmId != -1) {
                    snoozeAlarm(alarmId)
                }
                finishAlarm()
                return START_NOT_STICKY
            }
        }

        val channelId = "ALARM_CHANNEL"
        val channelName = "Alarm Notifications"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            lightColor = 0xFFFBC02D.toInt()
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this, 2, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm")
            .setContentText("Your alarm is ringing!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Snooze", snoozePendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Dismiss", dismissPendingIntent)
            .setColor(0xFFFBC02D.toInt())
            .setColorized(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }

        playAlarmSound()

        return START_NOT_STICKY
    }

    private fun finishAlarm() {
        sendBroadcast(Intent(ACTION_ALARM_DONE))
        stopSelf()
    }

    private fun dismissAlarm(alarmId: Int) {
        serviceScope.launch {
            val currentAlarms = dataStore.alarmsFlow.first()
            val updatedAlarms = currentAlarms.map {
                if (it.id == alarmId) it.copy(isEnabled = false, snoozeUntil = null) else it
            }
            dataStore.saveAlarms(updatedAlarms)
            // If it's a repeating alarm, we might want to schedule the next one, 
            // but the current logic for `isEnabled = false` means we stop it.
            // If the user wants it to repeat tomorrow, they'd keep it enabled.
            // For now, following the user's previous request to toggle it off.
        }
    }

    private fun snoozeAlarm(alarmId: Int) {
        serviceScope.launch {
            val currentAlarms = dataStore.alarmsFlow.first()
            val alarmToSnooze = currentAlarms.find { it.id == alarmId }
            if (alarmToSnooze != null) {
                val snoozeTime = LocalDateTime.now().plusMinutes(10)
                val updatedAlarm = alarmToSnooze.copy(snoozeUntil = snoozeTime)
                val updatedAlarms = currentAlarms.map {
                    if (it.id == alarmId) updatedAlarm else it
                }
                dataStore.saveAlarms(updatedAlarms)
                scheduler.schedule(updatedAlarm)
            }
        }
    }

    private fun playAlarmSound() {
        if (mediaPlayer == null) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
