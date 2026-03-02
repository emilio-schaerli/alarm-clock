package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

interface AlarmScheduler {
    fun schedule(item: AlarmItem)
    fun cancel(item: AlarmItem)
}

class AndroidAlarmScheduler(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(item: AlarmItem) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", item.id)
            putExtra("ALARM_TIME", item.time.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var alarmTime = now
            .withHour(item.time.hour)
            .withMinute(item.time.minute)
            .withSecond(0)
            .withNano(0)

        // Find the next valid occurrence
        while (true) {
            val date = alarmTime.toLocalDate()
            
            val isWithinDateRange = (item.startDate == null || !date.isBefore(item.startDate)) &&
                                    (item.endDate == null || !date.isAfter(item.endDate))
            
            val isCorrectDay = item.daysOfWeek.isEmpty() || item.daysOfWeek.contains(date.dayOfWeek.value)

            if (alarmTime.isAfter(now) && isWithinDateRange && isCorrectDay) {
                break
            }
            
            alarmTime = alarmTime.plusDays(1)
            
            // Safety break if end date is passed
            if (item.endDate != null && alarmTime.toLocalDate().isAfter(item.endDate)) {
                return // Don't schedule if no more occurrences are possible
            }
            
            // Avoid infinite loop if somehow no day is possible
            if (alarmTime.isAfter(now.plusYears(1))) return 
        }

        val info = AlarmManager.AlarmClockInfo(
            alarmTime.toInstant().toEpochMilli(),
            pendingIntent
        )

        alarmManager.setAlarmClock(info, pendingIntent)
    }

    override fun cancel(item: AlarmItem) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                item.id,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}
