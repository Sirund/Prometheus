package com.prometheus.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.WeatherInfo

class PollingService : Service() {

    companion object {
        const val ACTION_START = "com.prometheus.action.START_POLLING"
        const val ACTION_UPDATE_INJECTION = "com.prometheus.action.UPDATE_INJECTION"
        const val EXTRA_INJECTION_URL = "injection_url"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "prometheus_polling_service"

        var onPoll: ((List<EarthquakeEvent>) -> Unit)? = null
        var onNewEvent: ((EarthquakeEvent) -> Unit)? = null
        var onWeatherUpdate: ((WeatherInfo) -> Unit)? = null
        var onNowcastUpdate: ((List<NowcastAlert>) -> Unit)? = null
        var onBadWeather: ((List<NowcastAlert>) -> Unit)? = null
        var onForceCheckRequest: (() -> Unit)? = null

        private var controller: BMKGPollingController? = null

        fun forceCheck() {
            controller?.forceCheck()
            onForceCheckRequest?.invoke()
        }

        fun updateInjectionUrl(url: String?) {
            controller?.updateInjectionUrl(url)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startController()
            ACTION_UPDATE_INJECTION -> {
                val url = intent.getStringExtra(EXTRA_INJECTION_URL)
                if (url != null) updateInjectionUrl(url)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startController() {
        if (controller != null) return
        controller = BMKGPollingController(this, baseUrlOverride = InjectionSettings.baseUrl).apply {
            onPoll = { events -> PollingService.onPoll?.invoke(events) }
            onNewEvent = { event -> PollingService.onNewEvent?.invoke(event) }
            onWeatherUpdate = { weather -> PollingService.onWeatherUpdate?.invoke(weather) }
            onNowcastUpdate = { alerts -> PollingService.onNowcastUpdate?.invoke(alerts) }
            onBadWeather = { alerts -> PollingService.onBadWeather?.invoke(alerts) }
            start()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Polling Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "BMKG monitoring background service" }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prometheus")
            .setContentText("Monitoring active")
            .setPriority(NotificationManager.IMPORTANCE_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }
}
