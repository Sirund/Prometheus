package com.prometheus.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors

@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.darkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SyncBanner()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "DASHBOARD",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))
        DashboardRow(title = "INPUT: AUDIO", subtitle = "ASK BY VOICE", icon = "mic")
        Spacer(Modifier.height(8.dp))
        DashboardRow(title = "INPUT: TEXT", subtitle = "TYPE QUERY", icon = "keyboard")
        Spacer(Modifier.height(8.dp))
        DashboardRow(title = "INPUT: OPTIC", subtitle = "IDENTIFY PHOTO", icon = "camera")
    }
}

@Composable
private fun SyncBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.blue.copy(alpha = 0.2f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2713",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "LOCAL DATA SYNCED",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall
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
            text = getIconEmoji(icon),
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

private fun getIconEmoji(icon: String): String = when (icon) {
    "mic" -> "\uD83C\uDF99\uFE0F"
    "keyboard" -> "\u2328\uFE0F"
    "camera" -> "\uD83D\uDCF7"
    else -> "\u25CF"
}
