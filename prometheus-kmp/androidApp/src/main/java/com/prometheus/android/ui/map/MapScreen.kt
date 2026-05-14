package com.prometheus.android.ui.map

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evacuation", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.cardBackground
                ),
                actions = {
                    Text(
                        text = "\uD83D\uDDFA\uFE0F",
                        color = PrometheusColors.blue,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            EvacuationStatusBanner()

            Spacer(Modifier.height(20.dp))

            SectionHeader(title = "EVACUATION MAP")
            Spacer(Modifier.height(8.dp))
            MapPlaceholder()

            Spacer(Modifier.height(20.dp))

            SectionHeader(title = "ROUTING DETAILS")
            Spacer(Modifier.height(8.dp))
            RoutingDetailsCard()

            Spacer(Modifier.height(16.dp))

            EvacuationInfoNote()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EvacuationStatusBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.blue.copy(alpha = 0.1f))
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83D\uDEE1\uFE0F",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "EVACUATION ROUTING",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "STANDBY",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MapPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\uD83D\uDDFA\uFE0F",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Google Maps SDK",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Epicentre pin  ·  safe-radius overlay",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "Route appears when a dangerous event is classified",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoutingDetailsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        RouteInfoRow(label = "EPICENTRE", value = "Waiting for event...")
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "USER LOCATION", value = "Not acquired")
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "SAFE RADIUS", value = "TBD per magnitude band")
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "EXIT ROUTE", value = "No active event")
        HorizontalDivider(color = PrometheusColors.blue.copy(alpha = 0.15f))
        RouteInfoRow(label = "DIRECTION", value = "--")
    }
}

@Composable
private fun RouteInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(128.dp)
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EvacuationInfoNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground.copy(alpha = 0.5f))
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.15f))
            .padding(16.dp)
    ) {
        Text(
            text = "HOW IT WORKS",
            color = PrometheusColors.blue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "On a dangerous classification, the BMKG epicentre is pinned on the map. The shortest driving or walking route from your location to outside the hazard radius is computed via Google Maps Directions API. Gemma 4 delivers a spoken briefing with direction and what to avoid.",
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall
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
