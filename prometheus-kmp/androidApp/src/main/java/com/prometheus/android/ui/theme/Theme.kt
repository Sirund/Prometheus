package com.prometheus.android.ui.theme

import android.os.Build
import com.prometheus.android.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class PrometheusColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val blue: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val darkPrometheusColors = PrometheusColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceElevated = Color(0xFF2C2C2C),
    blue = Color(0xFF4FC3F7),
    danger = Color(0xFFF44336),
    warning = Color(0xFFFF9800),
    success = Color(0xFF66BB6A),
    textPrimary = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF9E9E9E)
)

val lightPrometheusColors = PrometheusColors(
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE8E8E8),
    blue = Color(0xFF0288D1),
    danger = Color(0xFFD32F2F),
    warning = Color(0xFFF57C00),
    success = Color(0xFF388E3C),
    textPrimary = Color(0xFF1E1E1E),
    textSecondary = Color(0xFF6B6B6B)
)

val LocalPrometheusColors = staticCompositionLocalOf { darkPrometheusColors }

private val InterFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val InterFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = InterFontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = InterFontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = InterFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = InterFontProvider, weight = FontWeight.Medium),
)

val PrometheusTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    displayMedium = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFontFamily, color = Color.Unspecified),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFontFamily, color = Color.Unspecified),
    bodyLarge = TextStyle(fontSize = 18.sp, fontFamily = InterFontFamily, color = Color.Unspecified),
    bodyMedium = TextStyle(fontSize = 16.sp, fontFamily = InterFontFamily, color = Color.Unspecified),
    bodySmall = TextStyle(fontSize = 15.sp, fontFamily = InterFontFamily, color = Color.Unspecified),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = Color.Unspecified),
)

val PrometheusShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = darkPrometheusColors.blue,
    secondary = darkPrometheusColors.blue.copy(alpha = 0.7f),
    background = darkPrometheusColors.background,
    surface = darkPrometheusColors.surface,
    surfaceVariant = darkPrometheusColors.surfaceElevated,
    error = darkPrometheusColors.danger,
    onPrimary = Color.Black,
    onBackground = darkPrometheusColors.textPrimary,
    onSurface = darkPrometheusColors.textPrimary,
    onSurfaceVariant = darkPrometheusColors.textSecondary,
    outline = darkPrometheusColors.surfaceElevated
)

private val LightColorScheme = lightColorScheme(
    primary = lightPrometheusColors.blue,
    secondary = lightPrometheusColors.blue.copy(alpha = 0.7f),
    background = lightPrometheusColors.background,
    surface = lightPrometheusColors.surface,
    surfaceVariant = lightPrometheusColors.surfaceElevated,
    error = lightPrometheusColors.danger,
    onPrimary = Color.White,
    onBackground = lightPrometheusColors.textPrimary,
    onSurface = lightPrometheusColors.textPrimary,
    onSurfaceVariant = lightPrometheusColors.textSecondary,
    outline = lightPrometheusColors.surfaceElevated
)

@Composable
fun PrometheusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkPrometheusColors else lightPrometheusColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    CompositionLocalProvider(LocalPrometheusColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PrometheusTypography,
            shapes = PrometheusShapes,
            content = content
        )
    }
}
