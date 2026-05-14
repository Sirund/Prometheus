package com.prometheus.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors

@Composable
fun DashboardScreen() {
    var monitoringActive by remember { mutableStateOf(true) }
    var lastCheck by remember { mutableStateOf("Polling every 60s...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.darkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        StatusBanner(active = monitoringActive, lastCheck = lastCheck)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "DASHBOARD",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "BMKG LIVE MONITOR",
            color = PrometheusColors.blue.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "autogempa — every 60s",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        DashboardRow(title = "INPUT: AUDIO", subtitle = "ASK BY VOICE", icon = "\uD83C\uDF99\uFE0F")
        Spacer(Modifier.height(8.dp))
        DashboardRow(title = "INPUT: TEXT", subtitle = "TYPE QUERY", icon = "\u2328\uFE0F")
        Spacer(Modifier.height(8.dp))
        DashboardRow(title = "INPUT: OPTIC", subtitle = "IDENTIFY PHOTO", icon = "\uD83D\uDCF7")
    }
}

@Composable
private fun StatusBanner(active: Boolean, lastCheck: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.blue.copy(alpha = 0.2f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (active) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (active) "BMKG MONITORING ACTIVE" else "MONITORING STOPPED",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "V 2.4.0",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DashboardRow(title: String, subtitle: String, icon: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                color = PrometheusColors.blue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = PrometheusColors.blue,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = icon,
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
