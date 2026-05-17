package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.prometheus.model.NowcastAlert
import com.prometheus.model.WeatherInfo

private enum class DangerLevel { None, Watch, Medium, Danger }

@Composable
fun MonitorScreen(
    onRefresh: (() -> Unit)? = null,
    event: EarthquakeEvent? = null,
    latestEvent: String? = null,
    weatherInfo: WeatherInfo = WeatherInfo.EMPTY,
    nowcastAlerts: List<NowcastAlert> = emptyList(),
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
            Column {
                SectionHeader(text = "EARTHQUAKE INFO")
                Spacer(Modifier.height(8.dp))
                val latLon = if (event != null) "${event.Lintang ?: "--"}, ${event.Bujur ?: "--"}" else "--"
                val eventTime = if (event != null) "${event.tanggal_ ?: ""} ${event.jam_ ?: ""}".trim() else ""
                HeroEventCard(
                    magnitude = event?._magnitude ?: "--",
                    location = event?._wilayah ?: if (event != null) "Unknown location" else "Waiting for data...",
                    depth = event?._kedalaman ?: "--",
                    felt = event?._dirasakan ?: "--",
                    latLon = latLon,
                    potential = event?._potensi ?: "--",
                    level = dangerLevel,
                    timestamp = eventTime
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 1) {
            Column {
                SectionHeader(text = "WEATHER")
                Spacer(Modifier.height(8.dp))
                WeatherInfoCard(weather = weatherInfo)
            }
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 2) {
            val latestAlert = nowcastAlerts.maxOrNull()
            Column {
                SectionHeader(text = "WEATHER WARNING")
                Spacer(Modifier.height(8.dp))
                if (latestAlert != null) {
                    NowcastAlertCard(alert = latestAlert)
                } else {
                    NowcastClearCard()
                }
            }
        }
        Spacer(Modifier.height(16.dp))

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
                onClick = { onRefresh?.invoke() },
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
        DangerLevel.None -> Triple(p.success, "\u2705", "ALL CLEAR \u2014 No active alerts")
        DangerLevel.Watch -> Triple(p.warning, "\u26A0\uFE0F", "WATCH \u2014 Monitor closely")
        DangerLevel.Medium -> Triple(Color(0xFFFF8C00), "\u26A0\uFE0F", "MEDIUM ALERT \u2014 Notified")
        DangerLevel.Danger -> Triple(p.danger, "\u26A0\uFE0F", "DANGER \u2014 Take action now")
    }
    ThreatLevelBanner(color = color, label = label, icon = icon)
}

@Composable
private fun HeroEventCard(
    magnitude: String,
    location: String,
    depth: String,
    felt: String,
    latLon: String,
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
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                modifier = Modifier.weight(1f),
                icon = "\uD83D\uDCCA",
                value = magnitude.let { if (it.startsWith("M ")) it else "M $it" },
                label = "MAGNITUDE",
                valueColor = accentColor
            )
            StatColumn(
                modifier = Modifier.weight(1f),
                icon = "\u2B07\uFE0F",
                value = depth,
                label = "DEPTH",
                valueColor = p.textPrimary
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83D\uDCCD", fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = felt,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                if (latLon != "--") {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = latLon,
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = p.surfaceElevated)
        Spacer(Modifier.height(6.dp))
        Text(text = potential, style = MaterialTheme.typography.labelLarge, color = p.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = p.surfaceElevated)
        Spacer(Modifier.height(6.dp))
        Text(text = location, style = MaterialTheme.typography.labelLarge, color = p.textPrimary, fontWeight = FontWeight.Bold)
        if (timestamp.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun RowScope.StatColumn(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
    valueColor: Color
) {
    val p = LocalPrometheusColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = p.textSecondary
        )
    }
}

@Composable
private fun WeatherInfoCard(weather: WeatherInfo) {
    val p = LocalPrometheusColors.current
    PrometheusCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            WeatherStatColumn(
                modifier = Modifier.weight(1f),
                icon = "\uD83C\uDF21\uFE0F",
                value = "${weather.temperature}\u00B0",
                label = "TEMP"
            )
            WeatherStatColumn(
                modifier = Modifier.weight(1f),
                icon = "\uD83D\uDCA7",
                value = "${weather.humidity}%",
                label = "HUMIDITY"
            )
            WeatherStatColumn(
                modifier = Modifier.weight(1f),
                icon = "\uD83D\uDCA8",
                value = "${weather.windSpeed} km/j",
                label = "WIND"
            )
        }
        if (weather.weatherDesc.isNotBlank() && weather.weatherDesc != "--") {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = p.surfaceElevated)
            Spacer(Modifier.height(6.dp))
            Text(
                text = weather.weatherDesc,
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun RowScope.WeatherStatColumn(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    val p = LocalPrometheusColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = p.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = p.textSecondary
        )
    }
}

@Composable
private fun NowcastAlertCard(alert: NowcastAlert) {
    val p = LocalPrometheusColors.current
    val alertColor = if (alert.isBadWeather) p.danger else p.warning
    var expanded by remember { mutableStateOf(false) }
    PrometheusCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.Top) {
            Text(text = if (alert.isBadWeather) "\u26A0\uFE0F" else "\uD83D\uDEE1\uFE0F", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.eventType,
                    style = MaterialTheme.typography.labelLarge,
                    color = alertColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = alert.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (expanded) "\u25B2 Tap to collapse" else "\u25BC Tap to expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun NowcastClearCard() {
    val p = LocalPrometheusColors.current
    PrometheusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "\u2600\uFE0F", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Cuaca baik \u2014 Tidak ada peringatan",
                style = MaterialTheme.typography.bodySmall,
                color = p.success
            )
        }
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
    val statusText = if (enabled && ip.isNotBlank()) "ACTIVE \u2014 $ip:$port" else "DISABLED"

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
            Spacer(Modifier.height(8.dp))
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
