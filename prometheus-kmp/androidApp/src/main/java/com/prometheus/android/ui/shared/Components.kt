package com.prometheus.android.ui.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.theme.PrometheusShapes

@Composable
fun PrometheusCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = PrometheusShapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(if (elevated) PrometheusColors.surfaceElevated else PrometheusColors.surface, shape = shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun StatusDot(
    isActive: Boolean,
    activeColor: Color = PrometheusColors.success,
    inactiveColor: Color = PrometheusColors.warning,
    size: Dp = 8.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isActive) activeColor else inactiveColor)
    )
}

@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    content: @Composable () -> Unit
) {
    Box {
        content()
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PrometheusColors.blue,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = PrometheusColors.textSecondary,
        modifier = modifier
    )
}

@Composable
fun EntranceAnimation(
    visible: Boolean,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(delayMillis = index * 80)) +
                slideInVertically(
                    animationSpec = tween(delayMillis = index * 80),
                    initialOffsetY = { it / 4 }
                )
    ) {
        content()
    }
}

@Composable
fun ThreatLevelBanner(
    color: Color,
    label: String,
    icon: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon.isNotEmpty()) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            color = Color.Black,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}
