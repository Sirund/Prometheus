package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.shared.EntranceAnimation
import com.prometheus.android.ui.shared.PrometheusCard
import com.prometheus.android.ui.shared.SectionHeader
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.model.EarthquakeEvent

private enum class DangerLevel { None, Watch, Medium, Danger }

@Composable
fun MonitorScreen(
    onRefresh: (() -> Unit)? = null,
    event: EarthquakeEvent? = null,
    latestEvent: String? = null,
    injectionEnabled: Boolean = false,
    injectionIp: String = "",
    injectionPort: Int = 8080,
    onApplyInjection: ((Boolean, String, Int) -> Unit)? = null
) {
    val dangerLevel = when {
        event == null -> DangerLevel.None
        event.isDangerous -> DangerLevel.Danger
        else -> {
            val mag = event.magnitudeValue
            when {
                mag != null && mag >= 5.0f -> DangerLevel.Medium
                mag != null && mag >= 4.0f -> DangerLevel.Watch
                else -> DangerLevel.None
            }
        }
    }
    var lastRefresh by remember { mutableStateOf("Not yet refreshed") }
    var showInjectionDialog by remember { mutableStateOf(false) }

    if (showInjectionDialog) {
        InjectionDialog(
            currentEnabled = injectionEnabled,
            currentIp = injectionIp,
            currentPort = injectionPort,
            onDismiss = { showInjectionDialog = false },
            onApply = { enabled, ip, port ->
                onApplyInjection?.invoke(enabled, ip, port)
                showInjectionDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.darkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DangerStatusBanner(level = dangerLevel)

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 0) {
            SectionHeader(text = "LATEST BMKG EVENT")
            Spacer(Modifier.height(8.dp))
            BMKGEventCard(
                magnitude = event?._magnitude ?: "--",
                location = event?._wilayah ?: if (event != null) "Unknown location" else "Waiting for data...",
                depth = event?._kedalaman ?: "--",
                felt = event?._dirasakan ?: "--",
                potential = event?._potensi ?: "--",
                timestamp = lastRefresh
            )
        }

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 1) {
            SectionHeader(text = "ALARM & BRIEFING")
            Spacer(Modifier.height(8.dp))
            AlarmStatusCard()
        }

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 2) {
            SectionHeader(text = "RECENT EVENTS")
            Spacer(Modifier.height(4.dp))
            Text(
                text = latestEvent ?: "No data loaded. Tap refresh to poll BMKG.",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 3) {
            Button(
                onClick = {
                    lastRefresh = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    onRefresh?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrometheusColors.blue.copy(alpha = 0.15f),
                    contentColor = PrometheusColors.blue
                )
            ) {
                Text(
                    text = "REFRESH BMKG",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 4) {
            SectionHeader(text = "LOCAL INJECTION")
            Spacer(Modifier.height(8.dp))
            InjectionStatusCard(
                enabled = injectionEnabled,
                ip = injectionIp,
                port = injectionPort,
                onClick = { showInjectionDialog = true }
            )
        }
    }
}

@Composable
private fun DangerStatusBanner(level: DangerLevel) {
    val targetColor = when (level) {
        DangerLevel.None -> PrometheusColors.blue
        DangerLevel.Watch -> PrometheusColors.warning
        DangerLevel.Medium -> Color(0xFFFF8C00)
        DangerLevel.Danger -> PrometheusColors.danger
    }
    val color by animateColorAsState(targetColor, animationSpec = tween(400), label = "danger_banner")
    val label = when (level) {
        DangerLevel.None -> "NO ACTIVE ALERTS"
        DangerLevel.Watch -> "WATCH — MONITOR CLOSELY"
        DangerLevel.Medium -> "MEDIUM — NOTIFIED"
        DangerLevel.Danger -> "DANGER — TAKE ACTION NOW"
    }

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.6f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (level == DangerLevel.Danger) color.copy(alpha = pulseAlpha) else color.copy(alpha = 0.15f))
            .border(1.dp, if (level == DangerLevel.Danger) color.copy(alpha = 0.6f) else color.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (level == DangerLevel.Danger) {
            Text(text = "\u26A0\uFE0F", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BMKGEventCard(
    magnitude: String,
    location: String,
    depth: String,
    felt: String,
    potential: String,
    timestamp: String
) {
    PrometheusCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "M $magnitude",
                    color = PrometheusColors.blue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = location,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                EventField(label = "DEPTH", value = depth)
                EventField(label = "FELT", value = felt)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            EventField(label = "TSUNAMI POTENTIAL", value = potential)
            Spacer(Modifier.weight(1f))
            Text(
                text = timestamp,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EventField(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AlarmStatusCard() {
    val infinite = rememberInfiniteTransition(label = "alarm_pulse")
    val gemmaAlpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "gemma_alpha"
    )

    PrometheusCard {
        AlarmIndicatorRow(isActive = true, label = "AUDIBLE ALARM", status = "ARMED")
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))
        AlarmIndicatorRow(isActive = true, label = "TTS BRIEFING", status = "READY")
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))
        AlarmIndicatorRow(isActive = false, label = "GEMMA 4 EMERGENCY PROMPT", status = "NOT LOADED", statusAlpha = gemmaAlpha)
    }
}

@Composable
private fun AlarmIndicatorRow(
    isActive: Boolean,
    label: String,
    status: String,
    statusAlpha: Float = 1f
) {
    val statusColor = if (isActive) PrometheusColors.success else Color.Gray

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = statusAlpha))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = status,
            color = statusColor.copy(alpha = statusAlpha),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InjectionStatusCard(
    enabled: Boolean,
    ip: String,
    port: Int,
    onClick: () -> Unit
) {
    val statusColor = if (enabled && ip.isNotBlank()) Color(0xFF4CAF50) else Color.Gray
    val statusText = if (enabled && ip.isNotBlank()) "ACTIVE — $ip:$port" else "DISABLED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INJECTION",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to configure local earthquake data injection",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun InjectionDialog(
    currentEnabled: Boolean,
    currentIp: String,
    currentPort: Int,
    onDismiss: () -> Unit,
    onApply: (Boolean, String, Int) -> Unit
) {
    var enabled by remember { mutableStateOf(currentEnabled) }
    var ip by remember { mutableStateOf(currentIp) }
    var port by remember { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PrometheusColors.cardBackground,
        title = {
            Text(
                text = "LOCAL INJECTION",
                color = PrometheusColors.blue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Run 'python3 tools/local_injector.py' on your PC, then enter its IP and port below.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enable",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = PrometheusColors.blue,
                            checkedThumbColor = Color.White
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("PC IP Address", color = Color.Gray) },
                    placeholder = { Text("192.168.1.42", color = Color.Gray.copy(alpha = 0.5f)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrometheusColors.blue,
                        unfocusedBorderColor = PrometheusColors.blue.copy(alpha = 0.3f),
                        cursorColor = PrometheusColors.blue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port", color = Color.Gray) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrometheusColors.blue,
                        unfocusedBorderColor = PrometheusColors.blue.copy(alpha = 0.3f),
                        cursorColor = PrometheusColors.blue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(enabled, ip, port.toIntOrNull() ?: 8080) }) {
                Text("APPLY", color = PrometheusColors.blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        }
    )
}
