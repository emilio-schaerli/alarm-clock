package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        if (alarmId != -1) {
            // Start the service for audio
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra("ALARM_ID", alarmId)
            }
            context?.startForegroundService(serviceIntent)

            // Immediately launch the full-screen activity
            val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context?.startActivity(activityIntent)
        }
    }
}
