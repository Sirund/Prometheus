package com.prometheus.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PrometheusColors {
    val blue = Color(0xFF00BFFF)
    val darkBackground = Color(0xFF000000)
    val cardBackground = Color(0xFF1A1A1A)
    val danger = Color(0xFFEF4444)
    val warning = Color(0xFFF59E0B)
    val success = Color(0xFF4CAF50)
    val surfaceBorder = blue.copy(alpha = 0.3f)
}

private val DarkColorScheme = darkColorScheme(
    primary = PrometheusColors.blue,
    onPrimary = Color.Black,
    secondary = PrometheusColors.blue.copy(alpha = 0.7f),
    background = PrometheusColors.darkBackground,
    surface = PrometheusColors.cardBackground,
    onBackground = Color.White,
    onSurface = Color.White,
    error = PrometheusColors.danger
)

val PrometheusShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun PrometheusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        shapes = PrometheusShapes,
        content = content
    )
}
