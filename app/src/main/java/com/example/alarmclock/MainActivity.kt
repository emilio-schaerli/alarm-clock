package com.example.alarmclock

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.alarmclock.ui.theme.AlarmClockTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var scheduler: AlarmScheduler
    private lateinit var dataStore: AlarmDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduler = AndroidAlarmScheduler(this)
        dataStore = AlarmDataStore(this)
        
        setContent {
            AlarmClockTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                AlarmScreen(scheduler, dataStore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(scheduler: AlarmScheduler, dataStore: AlarmDataStore) {
    val alarms by dataStore.alarmsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showTimePicker by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Alarms", 
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingAlarm = null
                    showTimePicker = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (alarms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No alarms set", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { isEnabled ->
                                scope.launch {
                                    val updatedAlarms = alarms.map {
                                        if (it.id == alarm.id) it.copy(isEnabled = isEnabled) else it
                                    }
                                    dataStore.saveAlarms(updatedAlarms)
                                    val updated = updatedAlarms.find { it.id == alarm.id }
                                    if (updated != null) {
                                        if (isEnabled) scheduler.schedule(updated) else scheduler.cancel(updated)
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    val updatedAlarms = alarms.filter { it.id != alarm.id }
                                    dataStore.saveAlarms(updatedAlarms)
                                    scheduler.cancel(alarm)
                                }
                            },
                            onEdit = {
                                editingAlarm = alarm
                                showTimePicker = true
                            }
                        )
                    }
                }
            }
        }

        if (showTimePicker) {
            val initialTime = editingAlarm?.time ?: LocalTime.now()
            val timePickerState = rememberTimePickerState(
                initialHour = initialTime.hour,
                initialMinute = initialTime.minute,
                is24Hour = false
            )
            
            var selectedDays by remember { mutableStateOf(editingAlarm?.daysOfWeek ?: emptySet()) }
            var startDate by remember { mutableStateOf(editingAlarm?.startDate) }
            var endDate by remember { mutableStateOf(editingAlarm?.endDate) }
            
            var showDateRangePicker by remember { mutableStateOf(false) }

            val view = LocalView.current

            LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Automatically select all days if a date range is set but no days are selected
            LaunchedEffect(startDate, endDate) {
                if ((startDate != null || endDate != null) && selectedDays.isEmpty()) {
                    selectedDays = (1..7).toSet()
                }
            }

            Dialog(onDismissRequest = { showTimePicker = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (editingAlarm == null) "Set alarm time" else "Edit alarm time",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 20.dp)
                        )
                        TimePicker(state = timePickerState)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Days of week selector
                        Text("Repeat", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("M", "T", "W", "T", "F", "S", "S")
                            days.forEachIndexed { index, day ->
                                val dayNum = index + 1
                                val isSelected = selectedDays.contains(dayNum)
                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            selectedDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                                        },
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            day,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Date range selector
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Date Range", style = MaterialTheme.typography.labelSmall)
                            TextButton(
                                onClick = { showDateRangePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val dateText = if (startDate != null && endDate != null) {
                                    "$startDate to $endDate"
                                } else if (startDate != null) {
                                    "From $startDate"
                                } else {
                                    "Not set"
                                }
                                Text(dateText)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel")
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                scope.launch {
                                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                    
                                    if (editingAlarm == null) {
                                        val newAlarm = AlarmItem(
                                            id = (alarms.maxOfOrNull { it.id } ?: 0) + 1,
                                            time = newTime,
                                            daysOfWeek = selectedDays,
                                            startDate = startDate,
                                            endDate = endDate
                                        )
                                        val updatedAlarms = alarms + newAlarm
                                        dataStore.saveAlarms(updatedAlarms)
                                        scheduler.schedule(newAlarm)
                                    } else {
                                        val updatedAlarm = editingAlarm!!.copy(
                                            time = newTime,
                                            isEnabled = true,
                                            daysOfWeek = selectedDays,
                                            startDate = startDate,
                                            endDate = endDate
                                        )
                                        val updatedAlarms = alarms.map {
                                            if (it.id == updatedAlarm.id) updatedAlarm else it
                                        }
                                        dataStore.saveAlarms(updatedAlarms)
                                        scheduler.cancel(editingAlarm!!)
                                        scheduler.schedule(updatedAlarm)
                                    }
                                }
                                showTimePicker = false
                            }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            if (showDateRangePicker) {
                val dateRangePickerState = rememberDateRangePickerState(
                    initialSelectedStartDateMillis = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                    initialSelectedEndDateMillis = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showDateRangePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            startDate = dateRangePickerState.selectedStartDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            endDate = dateRangePickerState.selectedEndDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            showDateRangePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            startDate = null
                            endDate = null
                            showDateRangePicker = false 
                        }) { Text("Clear") }
                    }
                ) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier.height(400.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainer else Color(0xFFF0F0F0)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = alarm.time.format(formatter),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }
            
            if (alarm.daysOfWeek.isNotEmpty() || alarm.startDate != null || alarm.endDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alarm.daysOfWeek.isNotEmpty()) {
                        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        Text(
                            text = if (alarm.daysOfWeek.size == 7) "Every day" 
                                   else alarm.daysOfWeek.sorted().joinToString(", ") { dayNames[it - 1] },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (alarm.startDate != null || alarm.endDate != null) {
                        if (alarm.daysOfWeek.isNotEmpty()) {
                            Text(" | ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Icon(
                            Icons.Default.CalendarToday, 
                            contentDescription = null, 
                            modifier = Modifier.size(12.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (alarm.startDate != null && alarm.endDate != null) {
                                "${alarm.startDate} to ${alarm.endDate}"
                            } else if (alarm.startDate != null) {
                                "From ${alarm.startDate}"
                            } else {
                                "Until ${alarm.endDate}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
