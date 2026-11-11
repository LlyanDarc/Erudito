package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EruditoBlueDark,
    onPrimary = Color(0xFF0A2540),
    primaryContainer = EruditoBlueContainerDark,
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = EruditoGoldAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3E2B00),
    onSecondaryContainer = Color(0xFFFFDEA2),
    tertiary = EruditoTeal,
    background = EruditoSurfaceDark,
    surface = EruditoSurfaceDark,
    surfaceVariant = EruditoCardDark,
    onSurface = EruditoTextPrimaryDark,
    onSurfaceVariant = EruditoTextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = EruditoBluePrimary,
    onPrimary = Color.White,
    primaryContainer = EruditoBlueContainer,
    onPrimaryContainer = EruditoOnBlueContainer,
    secondary = EruditoGoldAccent,
    onSecondary = Color.White,
    secondaryContainer = EruditoGoldContainer,
    onSecondaryContainer = Color(0xFF5A3800),
    tertiary = EruditoTeal,
    background = EruditoSurfaceLight,
    surface = EruditoSurfaceLight,
    surfaceVariant = EruditoCardLight,
    onSurface = EruditoTextPrimaryLight,
    onSurfaceVariant = EruditoTextSecondaryLight
)

@Composable
fun EruditoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
