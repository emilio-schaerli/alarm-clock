package com.example.alarmclock

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId

data class AlarmItem(
    val id: Int,
    val time: LocalTime,
    val isEnabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val snoozeUntil: LocalDateTime? = null,
    val label: String? = null
) {
    fun nextOccurrence(from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (snoozeUntil != null && isEnabled) {
            val snoozeZdt = snoozeUntil.atZone(ZoneId.systemDefault())
            if (snoozeZdt.isAfter(from)) return snoozeZdt
        }

        var alarmTime = from
            .withHour(time.hour)
            .withMinute(time.minute)
            .withSecond(0)
            .withNano(0)

        // Find the next valid occurrence within the next year
        for (i in 0..366) {
            val date = alarmTime.toLocalDate()
            
            val isWithinDateRange = (startDate == null || !date.isBefore(startDate)) &&
                                    (endDate == null || !date.isAfter(endDate))
            
            val isCorrectDay = daysOfWeek.isEmpty() || daysOfWeek.contains(date.dayOfWeek.value)

            if (alarmTime.isAfter(from) && isWithinDateRange && isCorrectDay) {
                return alarmTime
            }
            alarmTime = alarmTime.plusDays(1)
            
            if (endDate != null && alarmTime.toLocalDate().isAfter(endDate)) {
                break
            }
        }
        
        // If no valid occurrence found (e.g. date range passed), but we still want a sortable value
        // we can return a very far future date or just null.
        return null
    }
}
