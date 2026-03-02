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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import java.time.LocalTime
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(scheduler: AlarmScheduler, dataStore: AlarmDataStore) {
    val alarms by dataStore.alarmsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showTimePicker by remember { mutableStateOf(false) }

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
                onClick = { showTimePicker = true },
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
                            }
                        )
                    }
                }
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = LocalTime.now().hour,
                initialMinute = LocalTime.now().minute,
                is24Hour = false
            )
            val view = LocalView.current

            // Trigger stronger haptic feedback when hour or minute changes
            LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                // Using CLOCK_TICK for that precise Pixel feel, but also VIRTUAL_KEY for strength
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                            "Set alarm time",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 20.dp)
                        )
                        TimePicker(
                            state = timePickerState
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel")
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                scope.launch {
                                    val newAlarm = AlarmItem(
                                        id = (alarms.maxOfOrNull { it.id } ?: 0) + 1,
                                        time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                    )
                                    val updatedAlarms = alarms + newAlarm
                                    dataStore.saveAlarms(updatedAlarms)
                                    scheduler.schedule(newAlarm)
                                }
                                showTimePicker = false
                            }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainer else Color(0xFFF0F0F0)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
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
    }
}
