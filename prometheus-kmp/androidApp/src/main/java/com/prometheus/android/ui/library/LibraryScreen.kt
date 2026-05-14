package com.prometheus.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.theme.PrometheusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val topics = listOf("Medical", "Shelter", "Navigation", "Food & Water")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LIBRARY", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.darkBackground
                )
            )
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(topics) { topic ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrometheusColors.cardBackground)
                        .clickable { }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\uD83D\uDCC1",
                        color = PrometheusColors.blue
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = topic,
                        color = PrometheusColors.blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
