package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prometheus.android.ui.shared.EntranceAnimation
import com.prometheus.android.ui.shared.PrometheusCard
import com.prometheus.android.ui.shared.SectionHeader
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.WeatherInfo

private enum class DangerLevel { None, Watch, Medium, Danger }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onRefresh: (() -> Unit)? = null,
    event: EarthquakeEvent? = null,
    latestEvent: String? = null,
    weatherInfo: WeatherInfo = WeatherInfo.EMPTY,
    nowcastAlerts: List<NowcastAlert> = emptyList()
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


    val p = LocalPrometheusColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitor", color = p.blue) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface)
            )
        },
        containerColor = p.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            val classification = when (dangerLevel) {
            DangerLevel.None -> "ALL CLEAR"
            DangerLevel.Watch -> "WATCH"
            DangerLevel.Medium -> "MEDIUM ALERT"
            DangerLevel.Danger -> "DANGER"
        }

        EntranceAnimation(visible = true, index = 0) {
            Column {
                SectionHeader(text = "EARTHQUAKE INFO — $classification")
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

        val weatherLabel = if (weatherInfo.weatherDesc.isNotBlank() && weatherInfo.weatherDesc != "--") {
            "WEATHER — ${weatherInfo.weatherDesc}"
        } else {
            "WEATHER"
        }
        EntranceAnimation(visible = true, index = 1) {
            Column {
                SectionHeader(text = weatherLabel)
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


    }
}
 
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
        Column {
            Text(
                text = potential,
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = p.surfaceElevated)
        Spacer(Modifier.height(6.dp))
        Column {
            Text(
                text = location,
                style = MaterialTheme.typography.labelSmall,
                color = p.textSecondary
            )
        }
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
    PrometheusCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
    PrometheusCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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


