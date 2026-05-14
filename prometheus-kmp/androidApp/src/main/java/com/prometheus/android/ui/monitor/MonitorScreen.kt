package com.prometheus.android.ui.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors

private enum class DangerLevel { None, Watch, Danger }

@Composable
fun MonitorScreen() {
    var dangerLevel by remember { mutableStateOf(DangerLevel.None) }
    var lastRefresh by remember { mutableStateOf("Not yet refreshed") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.darkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DangerStatusBanner(level = dangerLevel)

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "LATEST BMKG EVENT")
        Spacer(Modifier.height(8.dp))
        BMKGEventCard(
            magnitude = "--",
            location = "Waiting for data...",
            depth = "--",
            felt = "--",
            potential = "--",
            timestamp = lastRefresh
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "ALARM & BRIEFING")
        Spacer(Modifier.height(8.dp))
        AlarmStatusCard()

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "RECENT EVENTS")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "No data loaded. Tap refresh to poll BMKG.",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { /* TODO: BMKGMonitor.fetch() */ },
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
}

@Composable
private fun DangerStatusBanner(level: DangerLevel) {
    val color = when (level) {
        DangerLevel.None -> PrometheusColors.blue
        DangerLevel.Watch -> Color(0xFFFFA500)
        DangerLevel.Danger -> Color.Red
    }
    val label = when (level) {
        DangerLevel.None -> "NO ACTIVE ALERTS"
        DangerLevel.Watch -> "WATCH — MONITOR CLOSELY"
        DangerLevel.Danger -> "DANGER — TAKE ACTION NOW"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = PrometheusColors.blue,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AlarmStatusCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        AlarmIndicatorRow(icon = "\uD83D\uDD14", label = "AUDIBLE ALARM", status = "ARMED", statusColor = PrometheusColors.blue)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))
        AlarmIndicatorRow(icon = "\uD83D\uDD0A", label = "TTS BRIEFING", status = "READY", statusColor = PrometheusColors.blue)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))
        AlarmIndicatorRow(icon = "\uD83E\uDDE0", label = "GEMMA 4 EMERGENCY PROMPT", status = "NOT LOADED", statusColor = Color.Gray)
    }
}

@Composable
private fun AlarmIndicatorRow(icon: String, label: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, color = statusColor.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = status,
            color = statusColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
