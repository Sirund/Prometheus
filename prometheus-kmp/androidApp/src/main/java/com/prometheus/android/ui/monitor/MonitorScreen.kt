package com.prometheus.android.ui.monitor

import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prometheus.android.R
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
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
        val classificationColor = when (dangerLevel) {
            DangerLevel.None -> p.blue
            DangerLevel.Watch -> p.warning
            DangerLevel.Medium -> Color(0xFFFF8C00)
            DangerLevel.Danger -> p.danger
        }

        EntranceAnimation(visible = true, index = 0) {
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

        EntranceAnimation(visible = true, index = 1) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EARTHQUAKE INFO \u2014 ",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textSecondary
                    )
                    Text(
                        text = classification,
                        style = MaterialTheme.typography.labelMedium,
                        color = classificationColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                val latLon = if (event != null) "${event.Lintang ?: "--"}, ${event.Bujur ?: "--"}" else "--"
                HeroEventCard(
                    magnitude = event?._magnitude ?: "--",
                    depth = event?._kedalaman ?: "--",
                    felt = event?._dirasakan ?: "--",
                    latLon = latLon,
                    potential = event?._potensi ?: "--",
                    level = dangerLevel
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val weatherLabel = if (weatherInfo.weatherDesc.isNotBlank() && weatherInfo.weatherDesc != "--") {
            weatherInfo.weatherDesc
        } else {
            null
        }
        EntranceAnimation(visible = true, index = 2) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "WEATHER",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textSecondary
                    )
                    if (weatherLabel != null) {
                        Text(
                            text = " \u2014 ",
                            style = MaterialTheme.typography.labelMedium,
                            color = p.textSecondary
                        )
                        Text(
                            text = weatherLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = p.blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                WeatherInfoCard(weather = weatherInfo)
            }
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 3) {
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

        EntranceAnimation(visible = true, index = 4) {
            val context = LocalContext.current
            Column {
                SectionHeader(text = "EMERGENCY")
                Spacer(Modifier.height(8.dp))
                PrometheusCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:112")
                            }
                            context.startActivity(intent)
                        },
                    elevated = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Emergency",
                            tint = p.blue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Layanan Darurat Terpadu",
                                style = MaterialTheme.typography.titleMedium,
                                color = p.blue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "112",
                                style = MaterialTheme.typography.displayMedium,
                                color = p.blue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        EntranceAnimation(visible = true, index = 5) {
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
    depth: String,
    felt: String,
    latLon: String,
    potential: String,
    level: DangerLevel
) {
    val p = LocalPrometheusColors.current
    val accentColor = when (level) {
        DangerLevel.None -> p.blue
        DangerLevel.Watch -> p.warning
        DangerLevel.Medium -> Color(0xFFFF8C00)
        DangerLevel.Danger -> p.danger
    }

    val potentialText = when {
        potential.contains("tidak berpotensi tsunami", ignoreCase = true) ||
        potential.contains("gempa ini dirasakan", ignoreCase = true) -> "Tidak berpotensi tsunami"
        else -> potential
    }
    val potentialColor = when {
        potential.contains("berpotensi tsunami", ignoreCase = true) ||
        potential.contains("waspada tsunami", ignoreCase = true) -> p.danger
        potentialText == "tidak berpotensi tsunami" -> p.success
        else -> p.textSecondary
    }

    PrometheusCard(elevated = true) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                modifier = Modifier.weight(1f),
                icon = {
                    Image(
                        painter = painterResource(R.drawable.magnitude),
                        contentDescription = "Magnitude",
                        modifier = Modifier.size(40.dp)
                    )
                },
                value = magnitude.let { if (it.startsWith("M ")) it else "M $it" },
                label = "MAGNITUDE",
                valueColor = accentColor
            )
            StatColumn(
                modifier = Modifier.weight(1f),
                icon = {
                    Image(
                        painter = painterResource(R.drawable.depth),
                        contentDescription = "Depth",
                        modifier = Modifier.size(40.dp)
                    )
                },
                value = depth,
                label = "DEPTH",
                valueColor = p.textPrimary
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.location),
                    contentDescription = "Location",
                    modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = felt,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
                if (latLon != "--") {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = latLon,
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        HorizontalDivider(color = p.surfaceElevated)
        Spacer(Modifier.height(4.dp))
        Text(
            text = potentialText,
            style = MaterialTheme.typography.bodyMedium,
            color = potentialColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

    }
}

@Composable
private fun RowScope.StatColumn(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    valueColor: Color
) {
    val p = LocalPrometheusColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
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
                icon = {
                    Image(
                        painter = painterResource(R.drawable.temp),
                        contentDescription = "Temperature",
                        modifier = Modifier.size(40.dp)
                    )
                },
                value = "${weather.temperature}\u00B0",
                label = "TEMP"
            )
            WeatherStatColumn(
                modifier = Modifier.weight(1f),
                icon = {
                    Image(
                        painter = painterResource(R.drawable.humidity),
                        contentDescription = "Humidity",
                        modifier = Modifier.size(40.dp)
                    )
                },
                value = "${weather.humidity}%",
                label = "HUMIDITY"
            )
            WeatherStatColumn(
                modifier = Modifier.weight(1f),
                icon = {
                    Image(
                        painter = painterResource(R.drawable.wind),
                        contentDescription = "Wind",
                        modifier = Modifier.size(40.dp)
                    )
                },
                value = "${weather.windSpeed} km/j",
                label = "WIND"
            )
        }
    }
}

@Composable
private fun RowScope.WeatherStatColumn(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    val p = LocalPrometheusColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
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
    val darkRed = Color(0xFFB71C1C)
    PrometheusCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (alert.isBadWeather) Icons.Filled.Warning else Icons.Filled.Shield,
                    contentDescription = if (alert.isBadWeather) "Warning" else "Clear",
                    tint = if (alert.isBadWeather) darkRed else alertColor,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = alert.intensity.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (alert.isBadWeather) darkRed else alertColor,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Beresiko ${alert.potential.lowercase().trimEnd('.')}.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDD52", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${alert.alertDate} \u2022 ${alert.alertTime} \u2014 ${alert.estimatedEnd.removePrefix("~ ")} WIB",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                color = p.surfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AFFECTED AREAS - ${alert.provinceName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textSecondary,
                    )
                    if (alert.specificLocation.isNotBlank() && alert.specificLocation != "--") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = alert.specificLocation,
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowcastClearCard() {
    val p = LocalPrometheusColors.current
    PrometheusCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.clear_weather),
                contentDescription = "Clear weather",
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "No warning",
                style = MaterialTheme.typography.bodySmall,
                color = p.success
            )
        }
    }
}


