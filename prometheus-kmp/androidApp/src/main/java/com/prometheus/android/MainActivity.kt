package com.prometheus.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.prometheus.android.inference.ConversationManager
import com.prometheus.android.inference.ModelManager
import com.prometheus.android.ui.assistant.ConversationData
import com.prometheus.android.ui.assistant.loadConversations
import com.prometheus.android.ui.assistant.saveConversations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.prometheus.android.service.InjectionSettings
import com.prometheus.android.service.PollingService
import com.prometheus.android.service.LocationProvider
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.android.ui.theme.PrometheusTheme
import com.prometheus.android.navigation.PrometheusApp
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.UserLocation
import com.prometheus.model.WeatherInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var latestEvent by mutableStateOf<String?>(null)
    private var currentEvent by mutableStateOf<EarthquakeEvent?>(null)
    private var currentLocation by mutableStateOf<UserLocation?>(null)
    private var weatherInfo by mutableStateOf(WeatherInfo.EMPTY)
    private var nowcastAlerts by mutableStateOf<List<NowcastAlert>>(emptyList())
    private var injectionEnabled by mutableStateOf(false)
    private var injectionIp by mutableStateOf("")
    private var injectionPort by mutableStateOf(8080)
    private var isDarkMode by mutableStateOf(true)
    private var showOverlayTutorial by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestStoragePermission()
        else requestStoragePermission()
    }

    private val fullScreenIntentLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestSystemAlertWindowPermission()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        requestStoragePermission()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestLocationPermission()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startPolling()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InjectionSettings.init(this)
        injectionEnabled = InjectionSettings.enabled
        injectionIp = InjectionSettings.ip
        injectionPort = InjectionSettings.port

        val prefs = getSharedPreferences("theme", Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("dark_mode", true)

        lifecycleScope.launch {
            ModelManager.init(this@MainActivity)
        }

        setContent {
            val saveFile = remember { File(filesDir, "conversations.json") }
            var conversations by remember { mutableStateOf(loadConversations(saveFile)) }
            var activeIndex by remember { mutableStateOf(0) }
            val conversationManager = remember { ConversationManager() }

            LaunchedEffect(conversations) {
                withContext(Dispatchers.IO) {
                    saveConversations(saveFile, conversations)
                }
            }

            DisposableEffect(Unit) {
                onDispose { conversationManager.shutdown() }
            }

            val p = LocalPrometheusColors.current
            PrometheusTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = p.background
                ) {
                    PrometheusApp(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            isDarkMode = !isDarkMode
                            getSharedPreferences("theme", Context.MODE_PRIVATE).edit()
                                .putBoolean("dark_mode", isDarkMode)
                                .apply()
                        },
                        onRefreshBmkg = { PollingService.forceCheck() },
                        latestEvent = latestEvent,
                        currentEvent = currentEvent,
                        currentLocation = currentLocation,
                        weatherInfo = weatherInfo,
                        nowcastAlerts = nowcastAlerts,
                        injectionEnabled = injectionEnabled,
                        injectionIp = injectionIp,
                        injectionPort = injectionPort,
                        onApplyInjection = { enabled, ip, port ->
                            injectionEnabled = enabled
                            injectionIp = ip
                            injectionPort = port
                            InjectionSettings.enabled = enabled
                            InjectionSettings.ip = ip
                            InjectionSettings.port = port
                            applyInjectionUrl()
                        },
                        conversations = conversations,
                        activeIndex = activeIndex,
                        conversationManager = conversationManager,
                        onConversationsChange = { conversations = it },
                        onActiveIndexChange = { activeIndex = it }
                    )

                    if (showOverlayTutorial) {
                        OverlayTutorialDialog(
                            onContinue = {
                                showOverlayTutorial = false
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                            },
                            onSkip = {
                                showOverlayTutorial = false
                                requestStoragePermission()
                            }
                        )
                    }
                }
            }
        }

        requestNotificationPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        PollingService.onPoll = null
        PollingService.onNewEvent = null
        PollingService.onWeatherUpdate = null
        PollingService.onNowcastUpdate = null
        PollingService.onBadWeather = null
        stopService(Intent(this, PollingService::class.java))
        ModelManager.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PollingService.forceCheck()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestFullScreenIntentPermission()
    }

    private fun requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.USE_FULL_SCREEN_INTENT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                fullScreenIntentLauncher.launch(Manifest.permission.USE_FULL_SCREEN_INTENT)
                return
            }
        }
        requestSystemAlertWindowPermission()
    }

    private fun requestSystemAlertWindowPermission() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayTutorial = true
            return
        }
        requestStoragePermission()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        startPolling()
    }

    private fun startPolling() {
        val intent = Intent(this, PollingService::class.java).apply {
            action = PollingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        PollingService.onPoll = { events ->
            val event = events.firstOrNull()
            currentEvent = event
            val locProvider = LocationProvider(this@MainActivity)
            currentLocation = locProvider.getLastKnownLocation()
            val mag = event?.magnitudeValue?.let { "M $it" } ?: "Unknown"
            latestEvent = "$mag — ${event?._wilayah ?: "Unknown location"}"
        }
        PollingService.onNewEvent = { event ->
            currentEvent = event
            val locProvider = LocationProvider(this@MainActivity)
            currentLocation = locProvider.getLastKnownLocation()
            val mag = event.magnitudeValue?.let { "M $it" } ?: "Unknown"
            latestEvent = "$mag — ${event._wilayah ?: "Unknown location"}"
        }
        PollingService.onWeatherUpdate = { weather ->
            weatherInfo = weather
        }
        PollingService.onNowcastUpdate = { alerts ->
            nowcastAlerts = alerts
        }

        val locProvider = LocationProvider(this)
        currentLocation = locProvider.getLastKnownLocation()
    }

    private fun applyInjectionUrl() {
        PollingService.updateInjectionUrl(InjectionSettings.baseUrl)
    }
}

@Composable
private fun OverlayTutorialDialog(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val p = LocalPrometheusColors.current
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = p.surface,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\u26A0\uFE0F", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Display Over Other Apps",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                buildString {
                    append("Prometheus needs this permission to show earthquake alerts ")
                    append("on top of your current screen when a danger is detected.\n\n")
                    append("1. Tap \u201CApps\u201D at the bottom of the next screen.\n")
                    append("2. In the list, find and tap \u201CPrometheus\u201D.\n")
                    append("3. Toggle on \u201CAllow display over other apps\u201D.\n")
                    append("4. Press back to return here.")
                },
                color = p.textSecondary
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Not now")
            }
        }
    )
}
