package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        if (alarmId != -1) {
            // Start the service for audio and to launch the overlay activity
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            }
            context?.startForegroundService(serviceIntent)
        }
    }
}
