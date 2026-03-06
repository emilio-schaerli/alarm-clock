package com.example.alarmclock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmclock.ui.theme.AlarmClockTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {
    private var currentAlarmId by mutableIntStateOf(-1)
    private var currentAlarmLabel by mutableStateOf<String?>(null)

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmService.ACTION_ALARM_DONE) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        updateFromIntent(intent)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        enableEdgeToEdge()

        val filter = IntentFilter(AlarmService.ACTION_ALARM_DONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(finishReceiver, filter)
        }

        setContent {
            AlarmClockTheme {
                RingingScreen(
                    label = currentAlarmLabel,
                    onDismiss = {
                        val dismissIntent = Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_DISMISS
                            putExtra(AlarmService.EXTRA_ALARM_ID, currentAlarmId)
                        }
                        startService(dismissIntent)
                        finish()
                    },
                    onSnooze = { duration ->
                        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_SNOOZE
                            putExtra(AlarmService.EXTRA_ALARM_ID, currentAlarmId)
                            putExtra(AlarmService.EXTRA_SNOOZE_DURATION, duration)
                        }
                        startService(snoozeIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateFromIntent(intent)
    }

    private fun updateFromIntent(intent: Intent?) {
        currentAlarmId = intent?.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1) ?: -1
        currentAlarmLabel = intent?.getStringExtra(AlarmService.EXTRA_ALARM_LABEL)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(finishReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}

@Composable
fun RingingScreen(label: String?, onDismiss: () -> Unit, onSnooze: (Int) -> Unit) {
    val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))
    var selectedSnooze by remember { mutableIntStateOf(10) }
    
    // Updated to use YellowPrimary (from theme's primary color)
    val yellowPrimary = MaterialTheme.colorScheme.primary
    val grayColor = Color.Gray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = yellowPrimary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = currentTime,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
            color = yellowPrimary,
            fontWeight = FontWeight.Bold
        )
        
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Text(
            text = if (label.isNullOrBlank()) "Wake up!" else "Time to get up!",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(64.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { onSnooze(selectedSnooze) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = yellowPrimary,
                        contentColor = Color.Black
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text("Snooze", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    val snoozeOptions = listOf(5, 10, 15, 20)
                    snoozeOptions.forEach { duration ->
                        val isSelected = selectedSnooze == duration
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(yellowPrimary)
                                    else Modifier.border(1.dp, yellowPrimary, CircleShape)
                                )
                                .clickable { selectedSnooze = duration },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = duration.toString(),
                                color = Color.Black,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = grayColor,
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Dismiss", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
