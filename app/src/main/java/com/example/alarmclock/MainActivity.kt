package com.example.alarmclock

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import java.time.ZonedDateTime
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

                MainScreen(scheduler, dataStore)
            }
        }
    }
}

@Composable
fun MainScreen(scheduler: AlarmScheduler, dataStore: AlarmDataStore) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AccessAlarm, contentDescription = "Alarms") },
                    label = { Text("Alarms") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> AlarmScreen(scheduler, dataStore)
                1 -> SettingsScreen(dataStore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(dataStore: AlarmDataStore) {
    val sortOrder by dataStore.sortOrderFlow.collectAsState(initial = AlarmSortOrder.TIME)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Re-check permission when resuming
    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Permission Card
        if (!hasOverlayPermission) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To show the alarm overlay while using other apps (like Chrome or TikTok), you need to enable 'Display over other apps'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Permission")
                    }
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Sort Alarms By",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                val options = listOf(
                    "Time (Closest First)" to AlarmSortOrder.TIME,
                    "Alarm Label" to AlarmSortOrder.LABEL,
                    "Order Set" to AlarmSortOrder.ORDER_SET
                )
                
                Column(Modifier.selectableGroup()) {
                    options.forEach { (text, order) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (sortOrder == order),
                                    onClick = { scope.launch { dataStore.saveSortOrder(order) } },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (sortOrder == order),
                                onClick = null
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(scheduler: AlarmScheduler, dataStore: AlarmDataStore) {
    val rawAlarms by dataStore.alarmsFlow.collectAsState(initial = emptyList<AlarmItem>())
    val sortOrder by dataStore.sortOrderFlow.collectAsState(initial = AlarmSortOrder.TIME)
    
    val alarms = remember(rawAlarms, sortOrder) {
        when (sortOrder) {
            AlarmSortOrder.TIME -> {
                val now = ZonedDateTime.now()
                rawAlarms.sortedBy { alarm ->
                    alarm.nextOccurrence(now) 
                        ?: alarm.copy(isEnabled = true, snoozeUntil = null).nextOccurrence(now) 
                        ?: ZonedDateTime.now().plusYears(10)
                }
            }
            AlarmSortOrder.LABEL -> rawAlarms.sortedBy { it.label?.lowercase() ?: "" }
            AlarmSortOrder.ORDER_SET -> rawAlarms.sortedBy { it.id }
        }
    }

    val scope = rememberCoroutineScope()
    var showTimePicker by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Alarms", 
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
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
                Icon(Icons.Default.Add, contentDescription = "Add Alarm", modifier = Modifier.size(32.dp))
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
                        style = MaterialTheme.typography.headlineSmall, 
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
                                    val updatedAlarms = rawAlarms.map {
                                        if (it.id == alarm.id) it.copy(isEnabled = isEnabled, snoozeUntil = null) else it
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
                                    val updatedAlarms = rawAlarms.filter { it.id != alarm.id }
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
            var label by remember { mutableStateOf(editingAlarm?.label ?: "") }
            
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
                        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (editingAlarm == null) "Set alarm time" else "Edit alarm time",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                        )
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                selectorColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                timeSelectorUnselectedContainerColor = Color.Transparent,
                                timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Label input
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Days of week selector
                        Text("Repeat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("M", "T", "W", "T", "F", "S", "S")
                            days.forEachIndexed { index, day ->
                                val dayNum = index + 1
                                val isSelected = selectedDays.contains(dayNum)
                                Surface(
                                    modifier = Modifier
                                        .size(38.dp)
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
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Date range selector
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Date Range", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold, 
                                modifier = Modifier.align(Alignment.Start)
                            )
                            TextButton(
                                onClick = { showDateRangePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
                                val dateText = if (startDate != null && endDate != null) {
                                    "${startDate!!.format(dateFormatter)} to ${endDate!!.format(dateFormatter)}"
                                } else if (startDate != null) {
                                    "From ${startDate!!.format(dateFormatter)}"
                                } else {
                                    "Not set"
                                }
                                Text(
                                    dateText, 
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                                    
                                    if (editingAlarm == null) {
                                        val newAlarm = AlarmItem(
                                            id = (rawAlarms.maxOfOrNull { it.id } ?: 0) + 1,
                                            time = newTime,
                                            daysOfWeek = selectedDays,
                                            startDate = startDate,
                                            endDate = endDate,
                                            label = label.ifBlank { null }
                                        )
                                        val updatedAlarms = rawAlarms + newAlarm
                                        dataStore.saveAlarms(updatedAlarms)
                                        scheduler.schedule(newAlarm)
                                    } else {
                                        val updatedAlarm = editingAlarm!!.copy(
                                            time = newTime,
                                            isEnabled = true,
                                            daysOfWeek = selectedDays,
                                            startDate = startDate,
                                            endDate = endDate,
                                            snoozeUntil = null, // Reset snooze on edit
                                            label = label.ifBlank { null }
                                        )
                                        val updatedAlarms = rawAlarms.map {
                                            if (it.id == updatedAlarm.id) updatedAlarm else it
                                        }
                                        dataStore.saveAlarms(updatedAlarms)
                                        scheduler.cancel(editingAlarm!!)
                                        scheduler.schedule(updatedAlarm)
                                    }
                                }
                                showTimePicker = false
                            }) {
                                Text("OK", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
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
                        }) { Text("OK", style = MaterialTheme.typography.labelLarge) }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            startDate = null
                            endDate = null
                            showDateRangePicker = false 
                        }) { Text("Clear", style = MaterialTheme.typography.labelLarge) }
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
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainer else Color(0xFFF0F0F0)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (!alarm.label.isNullOrBlank()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = alarm.time.format(timeFormatter),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(28.dp))
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
            
            if (alarm.snoozeUntil != null && alarm.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Snooze, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Snoozed until ${alarm.snoozeUntil.format(timeFormatter)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (alarm.daysOfWeek.isNotEmpty() || alarm.startDate != null || alarm.endDate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alarm.daysOfWeek.isNotEmpty()) {
                        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        Text(
                            text = if (alarm.daysOfWeek.size == 7) "Every day" 
                                   else alarm.daysOfWeek.sorted().joinToString(", ") { dayNames[it - 1] },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (alarm.startDate != null || alarm.endDate != null) {
                        if (alarm.daysOfWeek.isNotEmpty()) {
                            Text(" | ", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        }
                        Icon(
                            Icons.Default.CalendarToday, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (alarm.startDate != null && alarm.endDate != null) {
                                "${alarm.startDate!!.format(dateFormatter)} to ${alarm.endDate!!.format(dateFormatter)}"
                            } else if (alarm.startDate != null) {
                                "From ${alarm.startDate!!.format(dateFormatter)}"
                            } else {
                                "Until ${alarm.endDate!!.format(dateFormatter)}"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
