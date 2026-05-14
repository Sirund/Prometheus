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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.prometheus.android.service.BMKGPollingController
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.theme.PrometheusTheme
import com.prometheus.android.navigation.PrometheusApp

class MainActivity : ComponentActivity() {

    private var pollingController: BMKGPollingController? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startPolling()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PrometheusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PrometheusColors.darkBackground
                ) {
                    PrometheusApp()
                }
            }
        }

        requestNotificationPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingController?.stop()
        pollingController = null
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
        startPolling()
    }

    private fun startPolling() {
        if (pollingController == null) {
            pollingController = BMKGPollingController(this).apply { start() }
        }
    }
}
