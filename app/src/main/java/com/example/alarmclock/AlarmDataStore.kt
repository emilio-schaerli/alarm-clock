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

enum class AlarmSortOrder {
    TIME, LABEL, ORDER_SET
}

@Serializable
data class AlarmData(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val daysOfWeek: Set<Int> = emptySet(),
    val startDate: String? = null,
    val endDate: String? = null,
    val snoozeUntil: String? = null,
    val label: String? = null
)

class AlarmDataStore(private val context: Context) {
    private val alarmsKey = stringPreferencesKey("alarms_list")
    private val sortOrderKey = stringPreferencesKey("sort_order")
    private val ringtoneUriKey = stringPreferencesKey("ringtone_uri")
    private val ringtoneNameKey = stringPreferencesKey("ringtone_name")

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
                        it.snoozeUntil?.let { dateTime -> LocalDateTime.parse(dateTime) },
                        it.label
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val sortOrderFlow: Flow<AlarmSortOrder> = context.dataStore.data
        .map { preferences ->
            val sortOrderName = preferences[sortOrderKey] ?: AlarmSortOrder.TIME.name
            try {
                AlarmSortOrder.valueOf(sortOrderName)
            } catch (e: Exception) {
                AlarmSortOrder.TIME
            }
        }

    val ringtoneUriFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ringtoneUriKey] }

    val ringtoneNameFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[ringtoneNameKey] ?: "Default" }

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
                it.snoozeUntil?.toString(),
                it.label
            ) 
        }
        context.dataStore.edit { preferences ->
            preferences[alarmsKey] = Json.encodeToString(alarmsData)
        }
    }

    suspend fun saveSortOrder(sortOrder: AlarmSortOrder) {
        context.dataStore.edit { preferences ->
            preferences[sortOrderKey] = sortOrder.name
        }
    }

    suspend fun saveRingtone(uri: String?, name: String) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(ringtoneUriKey)
            } else {
                preferences[ringtoneUriKey] = uri
            }
            preferences[ringtoneNameKey] = name
        }
    }
}
