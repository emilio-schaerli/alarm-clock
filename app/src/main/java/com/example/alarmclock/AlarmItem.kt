package com.example.alarmclock

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

data class AlarmItem(
    val id: Int,
    val time: LocalTime,
    val isEnabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val snoozeUntil: LocalDateTime? = null,
    val label: String? = null
)
