package com.prometheus.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.prometheus.android.ui.assistant.AssistantScreen
import com.prometheus.android.ui.map.MapScreen
import com.prometheus.android.ui.monitor.MonitorScreen
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.vision.VisionScreen
import com.prometheus.model.EarthquakeEvent

enum class Screen(val label: String, val icon: ImageVector) {
    Monitor("MONITOR", Icons.Filled.Radio),
    Evacuate("EVACUATE", Icons.Filled.Map),
    Survival("SURVIVAL", Icons.Filled.ChatBubble),
    Vision("VISION", Icons.Filled.CameraAlt)
}

@Composable
fun PrometheusApp(
    onRefreshBmkg: (() -> Unit)? = null,
    latestEvent: String? = null,
    currentEvent: EarthquakeEvent? = null
) {
    var selectedScreen by remember { mutableStateOf(Screen.Monitor) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = PrometheusColors.cardBackground) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrometheusColors.blue,
                            selectedTextColor = PrometheusColors.blue,
                            unselectedIconColor = PrometheusColors.blue.copy(alpha = 0.5f),
                            unselectedTextColor = PrometheusColors.blue.copy(alpha = 0.5f),
                            indicatorColor = PrometheusColors.blue.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedScreen) {
                Screen.Monitor -> MonitorScreen(
                    onRefresh = onRefreshBmkg,
                    event = currentEvent,
                    latestEvent = latestEvent
                )
                Screen.Evacuate -> MapScreen()
                Screen.Survival -> AssistantScreen()
                Screen.Vision -> VisionScreen()
            }
        }
    }
}
