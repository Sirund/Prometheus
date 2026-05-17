package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.prometheus.android.ui.theme.LocalPrometheusColors
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
    var showInjection by remember { mutableStateOf(false) }

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

    val p = LocalPrometheusColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
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
            Column {
                SectionHeader(text = "SYSTEM STATUS")
                Spacer(Modifier.height(8.dp))
                SystemStatusCard()
            }
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 2) {
            GemmaStatusCard()
        }

        Spacer(Modifier.height(28.dp))

        EntranceAnimation(visible = true, index = 3) {
            Column {
                SectionHeader(text = "RECENT EVENTS")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = latestEvent ?: "No data loaded. Tap refresh to poll BMKG.",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
                    containerColor = p.surfaceElevated,
                    contentColor = p.blue
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

        if (showInjection) {
            EntranceAnimation(visible = true, index = 5) {
                Column {
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
    }
}

@Composable
private fun DangerBanner(level: DangerLevel) {
    val p = LocalPrometheusColors.current
    val (color, icon, label) = when (level) {
        DangerLevel.None -> Triple(p.success, "\u2705", "ALL CLEAR — No active alerts")
        DangerLevel.Watch -> Triple(p.warning, "\u26A0\uFE0F", "WATCH — Monitor closely")
        DangerLevel.Medium -> Triple(Color(0xFFFF8C00), "\u26A0\uFE0F", "MEDIUM ALERT — Notified")
        DangerLevel.Danger -> Triple(p.danger, "\u26A0\uFE0F", "DANGER — Take action now")
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
    val p = LocalPrometheusColors.current
    val accentColor = when (level) {
        DangerLevel.None -> p.blue
        DangerLevel.Watch -> p.warning
        DangerLevel.Medium -> Color(0xFFFF8C00)
        DangerLevel.Danger -> p.danger
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
                    color = p.textSecondary
                )
                Text(
                    text = depth,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = location,
            style = MaterialTheme.typography.headlineMedium,
            color = p.textPrimary,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "FELT", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(text = felt, style = MaterialTheme.typography.labelLarge, color = p.textPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(text = "TSUNAMI POTENTIAL", style = MaterialTheme.typography.labelSmall, color = p.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(text = potential, style = MaterialTheme.typography.labelLarge, color = when {
                    potential.contains("berpotensi", ignoreCase = true) || potential.contains("warning", ignoreCase = true) || potential.contains("ya", ignoreCase = true) -> p.danger
                    else -> p.textPrimary
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Updated $timestamp",
            style = MaterialTheme.typography.labelSmall,
            color = p.textSecondary
        )
    }
}

@Composable
private fun SystemStatusCard() {
    val p = LocalPrometheusColors.current
    PrometheusCard {
        AlarmIndicatorRow(isActive = true, label = "AUDIBLE ALARM", status = "ARMED")
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = p.surfaceElevated)
        Spacer(Modifier.height(12.dp))
        AlarmIndicatorRow(isActive = true, label = "TTS BRIEFING", status = "READY")
    }
}

@Composable
private fun GemmaStatusCard() {
    val p = LocalPrometheusColors.current
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
                    .background(p.success.copy(alpha = alpha))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GEMMA 4",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = p.textPrimary
                )
                Text(
                    text = "On-device AI emergency assistant ready when networks go down",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "STANDBY",
                style = MaterialTheme.typography.labelSmall,
                color = p.success.copy(alpha = alpha),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlarmIndicatorRow(isActive: Boolean, label: String, status: String) {
    val p = LocalPrometheusColors.current
    val statusColor = if (isActive) p.success else p.textSecondary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(isActive = isActive)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = p.textPrimary
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
    val p = LocalPrometheusColors.current
    val statusColor = if (enabled && ip.isNotBlank()) p.success else p.textSecondary
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
                color = p.textPrimary,
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
            color = p.textSecondary
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
    val p = LocalPrometheusColors.current
    var enabled by remember { mutableStateOf(currentEnabled) }
    var ip by remember { mutableStateOf(currentIp) }
    var port by remember { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = {
            Text(
                text = "LOCAL INJECTION",
                style = MaterialTheme.typography.labelLarge,
                color = p.blue
            )
        },
        text = {
            Column {
                Text(
                    text = "Run 'python3 tools/local_injector.py' on your PC, then enter its IP and port below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = p.blue,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.textPrimary,
                        unfocusedTextColor = p.textPrimary,
                        focusedBorderColor = p.blue,
                        unfocusedBorderColor = p.surfaceElevated,
                        cursorColor = p.blue
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = p.textPrimary,
                        unfocusedTextColor = p.textPrimary,
                        focusedBorderColor = p.blue,
                        unfocusedBorderColor = p.surfaceElevated,
                        cursorColor = p.blue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(enabled, ip, port.toIntOrNull() ?: 8080) }) {
                Text("APPLY", color = p.blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = p.textSecondary)
            }
        }
    )
}
