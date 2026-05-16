package com.prometheus.android.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prometheus.android.ui.assistant.AssistantScreen
import com.prometheus.android.ui.map.MapScreen
import com.prometheus.android.ui.monitor.MonitorScreen
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.vision.VisionScreen
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.UserLocation

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
    currentEvent: EarthquakeEvent? = null,
    currentLocation: UserLocation? = null,
    injectionEnabled: Boolean = false,
    injectionIp: String = "",
    injectionPort: Int = 8080,
    onApplyInjection: ((Boolean, String, Int) -> Unit)? = null
) {
    var selectedScreen by remember { mutableStateOf(Screen.Monitor) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = PrometheusColors.background,
                tonalElevation = 0.dp
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = selectedScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedScreen = screen },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(if (isSelected) Modifier.background(PrometheusColors.surfaceElevated) else Modifier)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(if (isSelected) 22.dp else 20.dp)
                                )
                            }
                        },
                        label = {
                            if (isSelected) {
                                Text(
                                    screen.label,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrometheusColors.blue,
                            unselectedIconColor = PrometheusColors.textSecondary.copy(alpha = 0.5f),
                            indicatorColor = Color.Transparent
                        ),
                        alwaysShowLabel = false
                    )
                }
            }
        },
        containerColor = PrometheusColors.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    Screen.Monitor -> MonitorScreen(
                        onRefresh = onRefreshBmkg,
                        event = currentEvent,
                        latestEvent = latestEvent,
                        injectionEnabled = injectionEnabled,
                        injectionIp = injectionIp,
                        injectionPort = injectionPort,
                        onApplyInjection = onApplyInjection
                    )
                    Screen.Evacuate -> MapScreen(
                        event = currentEvent,
                        userLocation = currentLocation
                    )
                    Screen.Survival -> AssistantScreen()
                    Screen.Vision -> VisionScreen()
                }
            }
        }
    }
}
