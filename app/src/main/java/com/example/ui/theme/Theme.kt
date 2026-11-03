package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CleanColorScheme = darkColorScheme(
    primary = CleanBlue,
    onPrimary = CleanBg,
    primaryContainer = CleanSurfaceLight,
    onPrimaryContainer = TextMain,
    secondary = CleanGreen,
    onSecondary = CleanBg,
    secondaryContainer = CleanSurface,
    onSecondaryContainer = CleanGreen,
    tertiary = CleanIndigo,
    onTertiary = TextMain,
    background = CleanBg,
    onBackground = TextMain,
    surface = CleanSurface,
    onSurface = TextMain,
    surfaceVariant = CleanSurfaceLight,
    onSurfaceVariant = TextMuted,
    outline = CleanBorder,
    outlineVariant = CleanSurfaceLight,
    error = CleanRed,
    onError = TextMain
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CleanBg.toArgb()
            window.navigationBarColor = CleanBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = CleanColorScheme,
        typography = Typography,
        content = content
    )
}
