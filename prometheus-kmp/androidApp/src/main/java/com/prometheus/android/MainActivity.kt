package com.prometheus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.prometheus.android.navigation.PrometheusApp
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.android.ui.theme.PrometheusTheme

class MainActivity : ComponentActivity() {
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
    }
}
