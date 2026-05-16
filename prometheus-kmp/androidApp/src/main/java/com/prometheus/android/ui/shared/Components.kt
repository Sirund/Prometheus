package com.prometheus.android.ui.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun PrometheusCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(PrometheusColors.cardBackground, shape = shape)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f), shape = shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatusDot(
    isActive: Boolean,
    activeColor: Color = Color(0xFF4CAF50),
    inactiveColor: Color = Color(0xFFFF9800),
    size: Dp = 6.dp
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
                    .background(Color.Black.copy(alpha = 0.4f)),
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
        color = PrometheusColors.blue,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
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
