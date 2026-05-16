package com.prometheus.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PrometheusColors {
    val background = Color(0xFF121212)
    val surface = Color(0xFF1E1E1E)
    val surfaceElevated = Color(0xFF2C2C2C)
    val blue = Color(0xFF4FC3F7)
    val danger = Color(0xFFF44336)
    val warning = Color(0xFFFF9800)
    val success = Color(0xFF66BB6A)
    val textPrimary = Color(0xFFE0E0E0)
    val textSecondary = Color(0xFF9E9E9E)
}

val PrometheusTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrometheusColors.textPrimary),
    displayMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrometheusColors.textPrimary),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textPrimary),
    bodySmall = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.SansSerif, color = PrometheusColors.textSecondary),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrometheusColors.textSecondary),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrometheusColors.textSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrometheusColors.textSecondary),
)

val PrometheusShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrometheusColors.blue,
    secondary = PrometheusColors.blue.copy(alpha = 0.7f),
    background = PrometheusColors.background,
    surface = PrometheusColors.surface,
    surfaceVariant = PrometheusColors.surfaceElevated,
    error = PrometheusColors.danger,
    onPrimary = Color.Black,
    onBackground = PrometheusColors.textPrimary,
    onSurface = PrometheusColors.textPrimary,
    onSurfaceVariant = PrometheusColors.textSecondary,
    outline = PrometheusColors.surfaceElevated
)

@Composable
fun PrometheusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PrometheusTypography,
        shapes = PrometheusShapes,
        content = content
    )
}
