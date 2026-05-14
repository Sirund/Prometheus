package com.prometheus.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.prometheus.android.ui.assistant.AssistantScreen
import com.prometheus.android.ui.dashboard.DashboardScreen
import com.prometheus.android.ui.library.LibraryScreen
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.vision.VisionScreen

enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("DASHBOARD", Icons.Filled.Home),
    Assistant("ASSISTANT", Icons.AutoMirrored.Filled.Send),
    Vision("VISION", Icons.Filled.PhotoCamera),
    Library("LIBRARY", Icons.AutoMirrored.Filled.List)
}

@Composable
fun PrometheusApp() {
    var selectedScreen by remember { mutableStateOf(Screen.Dashboard) }

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
        when (selectedScreen) {
            Screen.Dashboard -> DashboardScreen()
            Screen.Assistant -> AssistantScreen()
            Screen.Vision -> VisionScreen()
            Screen.Library -> LibraryScreen()
        }
    }
}
