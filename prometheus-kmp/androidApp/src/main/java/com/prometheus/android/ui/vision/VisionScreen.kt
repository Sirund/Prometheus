package com.prometheus.android.ui.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
fun VisionScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vision Assist", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.cardBackground
                ),
                actions = {
                    Text(
                        text = "\uD83E\uDD1D",
                        color = PrometheusColors.blue,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 8.dp)
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .background(PrometheusColors.cardBackground)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\uD83D\uDCF7",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "CAMERA FEED",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "AVCaptureSession  →  Gemma 4 Vision  →  Speech",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .background(PrometheusColors.cardBackground)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.2f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDD0A",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PrometheusColors.blue.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Spoken response will play here — no screen reading required",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.cardBackground.copy(alpha = 0.5f))
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "VISION ACCESSIBILITY MODE",
                    color = PrometheusColors.blue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Point the camera at surroundings, signage, or injuries. Gemma 4 describes what it sees in calm spoken language — no typing or screen reading needed. Designed for visually impaired users and high-stress situations.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { /* TODO: trigger camera + Gemma vision */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrometheusColors.blue.copy(alpha = 0.12f),
                    contentColor = PrometheusColors.blue
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\uD83D\uDCF7",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        text = "TAP TO DESCRIBE SURROUNDINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
