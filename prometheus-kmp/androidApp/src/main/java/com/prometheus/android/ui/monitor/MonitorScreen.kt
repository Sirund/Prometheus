package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prometheus.android.ui.shared.EntranceAnimation
import com.prometheus.android.ui.shared.PrometheusCard
import com.prometheus.android.ui.shared.SectionHeader
import com.prometheus.android.ui.shared.StatusDot
import com.prometheus.android.ui.shared.ThreatLevelBanner
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
            .background(PrometheusColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DangerBanner(level = dangerLevel)

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 0) {
            HeroEventCard(
                magnitude = event?._magnitude ?: "--",
                location = event?._wilayah ?: if (event != null) "Unknown location" else "Waiting for data...",
                depth = event?._kedalaman ?: "--",
                felt = event?._dirasakan ?: "--",
                potential = event?._potensi ?: "--",
                level = dangerLevel,
                timestamp = lastRefresh
            )
        }

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 1) {
            SectionHeader(text = "SYSTEM STATUS")
            Spacer(Modifier.height(8.dp))
            SystemStatusCard()
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 2) {
            GemmaStatusCard()
        }

        Spacer(Modifier.height(28.dp))

        EntranceAnimation(visible = true, index = 3) {
            SectionHeader(text = "RECENT EVENTS")
            Spacer(Modifier.height(10.dp))
            Text(
                text = latestEvent ?: "No data loaded. Tap refresh to poll BMKG.",
                color = PrometheusColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 4) {
            Button(
                onClick = {
                    lastRefresh = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    onRefresh?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C),
                    contentColor = PrometheusColors.blue
                )
            ) {
                Text(
                    text = "REFRESH BMKG",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        EntranceAnimation(visible = true, index = 5) {
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
private fun DangerBanner(level: DangerLevel) {
    val (color, icon, label) = when (level) {
        DangerLevel.None -> Triple(PrometheusColors.success, "\u2705", "ALL CLEAR — No active alerts")
        DangerLevel.Watch -> Triple(PrometheusColors.warning, "\u26A0\uFE0F", "WATCH — Monitor closely")
        DangerLevel.Medium -> Triple(Color(0xFFFF8C00), "\u26A0\uFE0F", "MEDIUM ALERT — Notified")
        DangerLevel.Danger -> Triple(PrometheusColors.danger, "\u26A0\uFE0F", "DANGER — Take action now")
    }
    ThreatLevelBanner(color = color, label = label, icon = icon)
}

@Composable
private fun HeroEventCard(
    magnitude: String,
    location: String,
    depth: String,
    felt: String,
    potential: String,
    level: DangerLevel,
    timestamp: String
) {
    val accentColor = when (level) {
        DangerLevel.None -> PrometheusColors.blue
        DangerLevel.Watch -> PrometheusColors.warning
        DangerLevel.Medium -> Color(0xFFFF8C00)
        DangerLevel.Danger -> PrometheusColors.danger
    }

    PrometheusCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = magnitude.let { if (it.startsWith("M ")) it else "M $it" },
                style = MaterialTheme.typography.displayLarge,
                color = accentColor,
                fontSize = 52.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "DEPTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrometheusColors.textSecondary
                )
                Text(
                    text = depth,
                    style = MaterialTheme.typography.labelLarge,
                    color = PrometheusColors.textPrimary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = location,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = "FELT", style = MaterialTheme.typography.labelSmall, color = PrometheusColors.textSecondary)
                Text(text = felt, style = MaterialTheme.typography.labelLarge, color = PrometheusColors.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "TSUNAMI", style = MaterialTheme.typography.labelSmall, color = PrometheusColors.textSecondary)
                Text(text = potential, style = MaterialTheme.typography.labelLarge, color = when {
                    potential.contains("berpotensi", ignoreCase = true) || potential.contains("warning", ignoreCase = true) || potential.contains("ya", ignoreCase = true) -> PrometheusColors.danger
                    else -> PrometheusColors.textPrimary
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Updated $timestamp",
            style = MaterialTheme.typography.labelSmall,
            color = PrometheusColors.textSecondary
        )
    }
}

@Composable
private fun SystemStatusCard() {
    PrometheusCard {
        AlarmIndicatorRow(isActive = true, label = "AUDIBLE ALARM", status = "ARMED")
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = PrometheusColors.surfaceElevated)
        Spacer(Modifier.height(12.dp))
        AlarmIndicatorRow(isActive = true, label = "TTS BRIEFING", status = "READY")
    }
}

@Composable
private fun GemmaStatusCard() {
    val infinite = rememberInfiniteTransition(label = "gemma_pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "gemma_alpha"
    )

    PrometheusCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PrometheusColors.success.copy(alpha = alpha))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GEMMA 4",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "On-device AI emergency assistant ready when networks go down",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrometheusColors.textSecondary,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "STANDBY",
                style = MaterialTheme.typography.labelSmall,
                color = PrometheusColors.success.copy(alpha = alpha),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlarmIndicatorRow(isActive: Boolean, label: String, status: String) {
    val statusColor = if (isActive) PrometheusColors.success else PrometheusColors.textSecondary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(isActive = isActive)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PrometheusColors.textPrimary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
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
    val statusColor = if (enabled && ip.isNotBlank()) PrometheusColors.success else PrometheusColors.textSecondary
    val statusText = if (enabled && ip.isNotBlank()) "ACTIVE — $ip:$port" else "DISABLED"

    PrometheusCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(isActive = enabled && ip.isNotBlank())
            Spacer(Modifier.width(10.dp))
            Text(
                text = "LOCAL INJECTION",
                style = MaterialTheme.typography.bodySmall,
                color = PrometheusColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to configure local earthquake data injection",
            style = MaterialTheme.typography.bodySmall,
            color = PrometheusColors.textSecondary
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
        containerColor = PrometheusColors.surface,
        title = {
            Text(
                text = "LOCAL INJECTION",
                style = MaterialTheme.typography.labelLarge,
                color = PrometheusColors.blue
            )
        },
        text = {
            Column {
                Text(
                    text = "Run 'python3 tools/local_injector.py' on your PC, then enter its IP and port below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrometheusColors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrometheusColors.textPrimary
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
                    label = { Text("PC IP Address") },
                    placeholder = { Text("192.168.1.42") },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrometheusColors.textPrimary,
                        unfocusedTextColor = PrometheusColors.textPrimary,
                        focusedBorderColor = PrometheusColors.blue,
                        unfocusedBorderColor = PrometheusColors.surfaceElevated,
                        cursorColor = PrometheusColors.blue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrometheusColors.textPrimary,
                        unfocusedTextColor = PrometheusColors.textPrimary,
                        focusedBorderColor = PrometheusColors.blue,
                        unfocusedBorderColor = PrometheusColors.surfaceElevated,
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
                Text("CANCEL", color = PrometheusColors.textSecondary)
            }
        }
    )
}
