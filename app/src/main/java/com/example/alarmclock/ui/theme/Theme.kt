package com.example.alarmclock.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = YellowPrimary,
    secondary = YellowSecondary,
    tertiary = YellowTertiary,
    background = White,
    surface = White,
    onPrimary = Black,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = Black,
    onSurface = Black,
    primaryContainer = LightYellowContainer,
    onPrimaryContainer = OnLightYellowContainer,
    secondaryContainer = LightYellowContainer,
    onSecondaryContainer = OnLightYellowContainer,
    surfaceContainer = LightYellowContainer,
    onSurfaceVariant = OnLightYellowContainer
)

@Composable
fun AlarmClockTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The status bar color is handled by enableEdgeToEdge() in the Activity.
            // We only need to ensure the icon appearance (light/dark) is correct.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
