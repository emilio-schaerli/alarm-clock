package com.example.alarmclock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alarms")

@Serializable
data class AlarmData(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val daysOfWeek: Set<Int> = emptySet(),
    val startDate: String? = null,
    val endDate: String? = null,
    val snoozeUntil: String? = null
)

class AlarmDataStore(private val context: Context) {
    private val alarmsKey = stringPreferencesKey("alarms_list")

    val alarmsFlow: Flow<List<AlarmItem>> = context.dataStore.data
        .map { preferences ->
            val alarmsJson = preferences[alarmsKey] ?: "[]"
            try {
                Json.decodeFromString<List<AlarmData>>(alarmsJson).map {
                    AlarmItem(
                        it.id,
                        LocalTime.of(it.hour, it.minute),
                        it.isEnabled,
                        it.daysOfWeek,
                        it.startDate?.let { date -> LocalDate.parse(date) },
                        it.endDate?.let { date -> LocalDate.parse(date) },
                        it.snoozeUntil?.let { dateTime -> LocalDateTime.parse(dateTime) }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveAlarms(alarms: List<AlarmItem>) {
        val alarmsData = alarms.map { 
            AlarmData(
                it.id, 
                it.time.hour, 
                it.time.minute, 
                it.isEnabled, 
                it.daysOfWeek,
                it.startDate?.toString(),
                it.endDate?.toString(),
                it.snoozeUntil?.toString()
            ) 
        }
        context.dataStore.edit { preferences ->
            preferences[alarmsKey] = Json.encodeToString(alarmsData)
        }
    }
}
