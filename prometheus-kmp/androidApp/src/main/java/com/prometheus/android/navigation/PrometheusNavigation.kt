package com.prometheus.android.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LightMode
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
import com.prometheus.android.inference.ConversationManager
import com.prometheus.android.ui.ask.AskScreen
import com.prometheus.android.ui.assistant.ConversationData
import com.prometheus.android.ui.map.MapScreen
import com.prometheus.android.ui.monitor.MonitorScreen
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.UserLocation
import com.prometheus.model.WeatherInfo

enum class Screen(val label: String, val icon: ImageVector) {
    Monitor("MONITOR", Icons.Filled.Radio),
    Evacuate("EVACUATE", Icons.Filled.Map),
    Ask("ASK", Icons.Filled.Forum)
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
    onApplyInjection: ((Boolean, String, Int) -> Unit)? = null,
    conversations: List<ConversationData> = emptyList(),
    activeIndex: Int = 0,
    conversationManager: ConversationManager? = null,
    onConversationsChange: (List<ConversationData>) -> Unit = {},
    onActiveIndexChange: (Int) -> Unit = {}
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
                        Screen.Ask -> AskScreen(
                            conversations = conversations,
                            activeIndex = activeIndex,
                            conversationManager = conversationManager,
                            onConversationsChange = onConversationsChange,
                            onActiveIndexChange = onActiveIndexChange,
                            currentEvent = currentEvent,
                            nowcastAlerts = nowcastAlerts,
                            userLocation = currentLocation
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var showHelp by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(p.surfaceElevated)
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        color = p.blue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
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

                if (showHelp) {
                    AlertDialog(
                        onDismissRequest = { showHelp = false },
                        title = { Text("Prometheus Help", color = p.blue, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                HelpSection(
                                    tab = "MONITOR",
                                    steps = listOf(
                                        "Shows real-time BMKG earthquake data and danger status banner",
                                        "Colour-coded threat level: blue=clear, orange=elevated, red=danger",
                                        "Refresh BMKG to poll for latest seismic events",
                                        "Local injection card lets you simulate events for testing"
                                    )
                                )
                                HelpSection(
                                    tab = "EVACUATE",
                                    steps = listOf(
                                        "Live map showing epicentre (red) and danger radius circle",
                                        "Blue route line shows fastest exit via Google Directions",
                                        "Green shield marks safe zone outside danger radius",
                                        "Expand ROUTING DETAILS for travel time estimates"
                                    )
                                )
                                HelpSection(
                                    tab = "ASK",
                                    steps = listOf(
                                        "Powered by Gemma 4 AI running fully on-device (offline)",
                                        "CHAT mode: text/image conversation, survival tips, first aid, shelter",
                                        "TALK mode: hold mic to speak, camera captures image, voice+image analysis",
                                        "Response spoken aloud via TTS — tap any bubble to replay"
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showHelp = false }) {
                                Text("GOT IT", color = p.blue, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = p.surface
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSection(tab: String, steps: List<String>) {
    val p = LocalPrometheusColors.current
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = tab,
            color = p.blue,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        steps.forEach { step ->
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2022",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = step,
                    color = p.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
