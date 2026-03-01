package com.example.alarmclock

import java.time.LocalTime

data class AlarmItem(
    val id: Int,
    val time: LocalTime,
    val isEnabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet() // 1 = Monday, ..., 7 = Sunday
)
