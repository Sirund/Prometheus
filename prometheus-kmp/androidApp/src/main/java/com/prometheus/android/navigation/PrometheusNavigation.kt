package com.prometheus.android.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.android.ui.vision.VisionScreen
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.UserLocation
import com.prometheus.model.WeatherInfo

enum class Screen(val label: String, val icon: ImageVector) {
    Monitor("MONITOR", Icons.Filled.Radio),
    Evacuate("EVACUATE", Icons.Filled.Map),
    Chat("CHAT", Icons.Filled.ChatBubble),
    Vision("TALK", Icons.Filled.RecordVoiceOver)
}

@Composable
fun PrometheusApp(
    isDarkMode: Boolean = true,
    onToggleDarkMode: () -> Unit = {},
    onRefreshBmkg: (() -> Unit)? = null,
    latestEvent: String? = null,
    currentEvent: EarthquakeEvent? = null,
    currentLocation: UserLocation? = null,
    weatherInfo: WeatherInfo = WeatherInfo.EMPTY,
    nowcastAlerts: List<NowcastAlert> = emptyList(),
    injectionEnabled: Boolean = false,
    injectionIp: String = "",
    injectionPort: Int = 8080,
    onApplyInjection: ((Boolean, String, Int) -> Unit)? = null
) {
    var selectedScreen by remember { mutableStateOf(Screen.Monitor) }
    val p = LocalPrometheusColors.current

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = p.background,
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
                                    .then(if (isSelected) Modifier.background(p.surfaceElevated) else Modifier)
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
                            selectedIconColor = p.blue,
                            unselectedIconColor = p.textSecondary.copy(alpha = 0.5f),
                            indicatorColor = Color.Transparent
                        ),
                        alwaysShowLabel = false
                    )
                }
            }
        },
        containerColor = p.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                            weatherInfo = weatherInfo,
                            nowcastAlerts = nowcastAlerts,
                            injectionEnabled = injectionEnabled,
                            injectionIp = injectionIp,
                            injectionPort = injectionPort,
                            onApplyInjection = onApplyInjection
                        )
                        Screen.Evacuate -> MapScreen(
                            event = currentEvent,
                            userLocation = currentLocation
                        )
                        Screen.Chat -> AssistantScreen()
                        Screen.Vision -> VisionScreen()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(p.surfaceElevated)
                    .clickable(onClick = onToggleDarkMode),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkMode) "Switch to light mode" else "Switch to dark mode",
                    tint = p.blue,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
