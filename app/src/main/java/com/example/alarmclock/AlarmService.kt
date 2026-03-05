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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dataStore: AlarmDataStore
    private lateinit var scheduler: AlarmScheduler

    companion object {
        const val ACTION_DISMISS = "com.example.alarmclock.DISMISS"
        const val ACTION_SNOOZE = "com.example.alarmclock.SNOOZE"
        const val ACTION_ALARM_DONE = "com.example.alarmclock.ALARM_DONE"
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_LABEL = "ALARM_LABEL"
        const val EXTRA_SNOOZE_DURATION = "SNOOZE_DURATION"
        const val DEFAULT_SNOOZE_MINUTES = 10
    }

    override fun onCreate() {
        super.onCreate()
        dataStore = AlarmDataStore(this)
        scheduler = AndroidAlarmScheduler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val snoozeDuration = intent?.getIntExtra(EXTRA_SNOOZE_DURATION, DEFAULT_SNOOZE_MINUTES) ?: DEFAULT_SNOOZE_MINUTES

        when (intent?.action) {
            ACTION_DISMISS -> {
                serviceScope.launch {
                    if (alarmId != -1) {
                        dismissAlarm(alarmId)
                    }
                    finishAlarm()
                }
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                serviceScope.launch {
                    if (alarmId != -1) {
                        snoozeAlarm(alarmId, snoozeDuration)
                    }
                    finishAlarm()
                }
                return START_NOT_STICKY
            }
        }

        // When the alarm fires, we should clear the snoozeUntil field.
        if (alarmId != -1) {
            clearSnooze(alarmId)
        }

        // We still need to call startForeground to keep the service (and music) alive,
        // but we'll use a silent, low-priority channel so it doesn't "pop up" or distract.
        startForegroundServiceWithSilentNotification()

        serviceScope.launch {
            val label = if (alarmId != -1) {
                dataStore.alarmsFlow.first().find { it.id == alarmId }?.label
            } else null

            // Directly launch the full-screen overlay activity.
            val activityIntent = Intent(this@AlarmService, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                         Intent.FLAG_ACTIVITY_SINGLE_TOP or
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
            }
            
            try {
                startActivity(activityIntent)
            } catch (e: Exception) {
                // If the activity fails to start for some reason, we're still safe as the service is running.
            }
        }

        playAlarmSound()

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithSilentNotification() {
        val channelId = "ALARM_SERVICE_SILENT"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create a low-priority channel that doesn't make sound or pop up (heads-up)
        val channel = NotificationChannel(channelId, "Active Alarm", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm is ringing")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    private fun finishAlarm() {
        sendBroadcast(Intent(ACTION_ALARM_DONE))
        stopSelf()
    }

    private suspend fun dismissAlarm(alarmId: Int) {
        withContext(Dispatchers.IO) {
            val currentAlarms = dataStore.alarmsFlow.first()
            val alarmToDismiss = currentAlarms.find { it.id == alarmId }
            if (alarmToDismiss != null) {
                val isRepeating = alarmToDismiss.daysOfWeek.isNotEmpty() || (alarmToDismiss.endDate != null)
                val updatedAlarm = alarmToDismiss.copy(isEnabled = isRepeating, snoozeUntil = null)
                
                val updatedAlarms = currentAlarms.map {
                    if (it.id == alarmId) updatedAlarm else it
                }
                dataStore.saveAlarms(updatedAlarms)
                
                if (isRepeating) {
                    scheduler.schedule(updatedAlarm)
                }
            }
        }
    }

    private suspend fun snoozeAlarm(alarmId: Int, durationMinutes: Int) {
        withContext(Dispatchers.IO) {
            val currentAlarms = dataStore.alarmsFlow.first()
            val alarmToSnooze = currentAlarms.find { it.id == alarmId }
            if (alarmToSnooze != null) {
                val snoozeTime = LocalDateTime.now().plusMinutes(durationMinutes.toLong())
                val updatedAlarm = alarmToSnooze.copy(snoozeUntil = snoozeTime)
                val updatedAlarms = currentAlarms.map {
                    if (it.id == alarmId) updatedAlarm else it
                }
                dataStore.saveAlarms(updatedAlarms)
                scheduler.schedule(updatedAlarm)
            }
        }
    }

    private fun clearSnooze(alarmId: Int) {
        serviceScope.launch(Dispatchers.IO) {
            val currentAlarms = dataStore.alarmsFlow.first()
            val updatedAlarms = currentAlarms.map {
                if (it.id == alarmId) it.copy(snoozeUntil = null) else it
            }
            dataStore.saveAlarms(updatedAlarms)
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
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
