package com.prometheus.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PrometheusColors {
    val blue = Color(0xFF00BFFF)
    val darkBackground = Color(0xFF000000)
    val cardBackground = Color(0xFF1A1A1A)
}

private val DarkColorScheme = darkColorScheme(
    primary = PrometheusColors.blue,
    onPrimary = Color.Black,
    secondary = PrometheusColors.blue.copy(alpha = 0.7f),
    background = PrometheusColors.darkBackground,
    surface = PrometheusColors.cardBackground,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun PrometheusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
