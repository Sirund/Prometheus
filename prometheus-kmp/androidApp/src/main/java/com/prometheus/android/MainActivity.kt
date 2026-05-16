package com.prometheus.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.prometheus.android.inference.ModelManager
import com.prometheus.android.service.BMKGPollingController
import com.prometheus.android.service.InjectionSettings
import com.prometheus.android.service.LocationProvider
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.theme.PrometheusTheme
import com.prometheus.android.navigation.PrometheusApp
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.UserLocation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pollingController: BMKGPollingController? = null
    private var latestEvent by mutableStateOf<String?>(null)
    private var currentEvent by mutableStateOf<EarthquakeEvent?>(null)
    private var currentLocation by mutableStateOf<UserLocation?>(null)
    private var injectionEnabled by mutableStateOf(false)
    private var injectionIp by mutableStateOf("")
    private var injectionPort by mutableStateOf(8080)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPolling()
        else requestStoragePermission()
    }

    private val storagePermissionLauncher = registerForActivityResult(
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

        lifecycleScope.launch {
            ModelManager.init(this@MainActivity)
        }

        setContent {
            PrometheusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PrometheusColors.background
                ) {
                    PrometheusApp(
                        onRefreshBmkg = { pollingController?.forceCheck() },
                        latestEvent = latestEvent,
                        currentEvent = currentEvent,
                        currentLocation = currentLocation,
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
                        }
                    )
                }
            }
        }

        requestNotificationPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingController?.stop()
        pollingController = null
        ModelManager.shutdown()
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
        startPolling()
    }

    private fun startPolling() {
        if (pollingController == null) {
            val locProvider = LocationProvider(this)
            currentLocation = locProvider.getLastKnownLocation()
            pollingController = BMKGPollingController(
                this,
                baseUrlOverride = InjectionSettings.baseUrl
            ).apply {
                onPoll = { events ->
                    val event = events.firstOrNull()
                    currentEvent = event
                    currentLocation = locProvider.getLastKnownLocation()
                    val mag = event?.magnitudeValue?.let { "M $it" } ?: "Unknown"
                    latestEvent = "$mag — ${event?._wilayah ?: "Unknown location"}"
                }
                onNewEvent = { event ->
                    currentEvent = event
                    currentLocation = locProvider.getLastKnownLocation()
                    val mag = event.magnitudeValue?.let { "M $it" } ?: "Unknown"
                    latestEvent = "$mag — ${event._wilayah ?: "Unknown location"}"
                }
                start()
            }
        }
    }

    private fun applyInjectionUrl() {
        pollingController?.updateInjectionUrl(InjectionSettings.baseUrl)
    }
}
