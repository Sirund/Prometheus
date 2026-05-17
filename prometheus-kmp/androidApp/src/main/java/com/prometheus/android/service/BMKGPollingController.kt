package com.prometheus.android.service

import android.content.Context
import android.util.Log
import com.prometheus.android.inference.EmergencyInferenceManager
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.UserLocation
import com.prometheus.model.WeatherInfo
import com.prometheus.monitor.BMKGPollingManager
import com.prometheus.monitor.NowcastPollingManager
import com.prometheus.network.BMKGWeatherClient
import kotlinx.coroutines.*

class BMKGPollingController(context: Context, baseUrlOverride: String? = null) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locationProvider = LocationProvider(context)
    private var pollingManager = createPollingManager(baseUrlOverride)
    private val alarmManager = PrometheusAlarmManager(context)
    private val emergencyInference = EmergencyInferenceManager()
    private val weatherClient = BMKGWeatherClient()
    private val nowcastManager = NowcastPollingManager()
    private var lastWeatherInfo: WeatherInfo = WeatherInfo.EMPTY

    var onNewEvent: ((EarthquakeEvent) -> Unit)? = null
    var onPoll: ((List<EarthquakeEvent>) -> Unit)? = null
    var onWeatherUpdate: ((WeatherInfo) -> Unit)? = null
    var onNowcastUpdate: ((List<NowcastAlert>) -> Unit)? = null
    var onBadWeather: ((List<NowcastAlert>) -> Unit)? = null

    private fun createPollingManager(baseUrlOverride: String?): BMKGPollingManager {
        return BMKGPollingManager(baseUrlOverride = baseUrlOverride).apply {
            userLocation = locationProvider.getLastKnownLocation()
            onPoll = { events ->
                this@BMKGPollingController.onPoll?.invoke(events)
                userLocation = locationProvider.getLastKnownLocation()
            }
            onDangerousEvent = { event ->
                this@BMKGPollingController.onNewEvent?.invoke(event)
                generateAndAnnounceBriefing(event)
            }
            onMediumEvent = { event ->
                this@BMKGPollingController.onNewEvent?.invoke(event)
                alarmManager.triggerMediumAlert(event)
            }
            onNewEvent = { event ->
                this@BMKGPollingController.onNewEvent?.invoke(event)
            }
            onError = { error ->
                Log.e("BMKGPolling", "Poll error", error)
            }
        }
    }

    fun updateInjectionUrl(url: String?) {
        pollingManager.stop()
        pollingManager = createPollingManager(url)
        pollingManager.start(scope)
        Log.d("BMKGPolling", "Injection URL updated: ${url ?: "none (using BMKG)"}")
    }

    fun start() {
        pollingManager.start(scope)
        alarmManager.showStatus(true)
        startWeatherPolling()
        startNowcastPolling()
    }

    fun stop() {
        pollingManager.stop()
        nowcastManager.stop()
        weatherClient.close()
        alarmManager.showStatus(false)
        alarmManager.shutdown()
        emergencyInference.shutdown()
    }

    fun forceCheck() {
        pollingManager.forceCheck(scope)
        nowcastManager.forceCheck(scope)
        scope.launch { pollWeather() }
    }

    private fun startWeatherPolling() {
        scope.launch {
            pollWeather()
            while (isActive) {
                delay(60_000L)
                pollWeather()
            }
        }
    }

    private suspend fun pollWeather() {
        try {
            val userLoc = locationProvider.getLastKnownLocation()
            val adm4 = if (userLoc != null) {
                WeatherInfo.adm4ForLocation(userLoc.latitude, userLoc.longitude)
            } else {
                WeatherInfo.DEFAULT_ADM4
            }
            val weather = weatherClient.fetchWeatherForecast(adm4)
            lastWeatherInfo = weather
            onWeatherUpdate?.invoke(weather)
        } catch (e: Exception) {
            Log.e("BMKGPolling", "Weather poll error", e)
        }
    }

    private fun startNowcastPolling() {
        nowcastManager.onPoll = { alerts ->
            val nearest = lastWeatherInfo.matchingNowcastAlert(alerts)
            onNowcastUpdate?.invoke(if (nearest != null) listOf(nearest) else emptyList())
        }
        nowcastManager.onBadWeather = { alerts ->
            val nearest = lastWeatherInfo.matchingNowcastAlert(alerts)
            if (nearest != null) {
                onBadWeather?.invoke(listOf(nearest))
                alarmManager.sendNowcastNotification(nearest)
            }
        }
        nowcastManager.onError = { error ->
            Log.e("BMKGPolling", "Nowcast poll error", error)
        }
        nowcastManager.start(scope)
    }

    private fun generateAndAnnounceBriefing(event: EarthquakeEvent) {
        scope.launch {
            try {
                alarmManager.showStatus(true)
                val briefing = emergencyInference.generateBriefing(event)
                alarmManager.triggerAlert(event, briefing)
            } catch (e: Exception) {
                Log.e("BMKGPolling", "Briefing failed", e)
                alarmManager.triggerAlert(event)
            }
        }
    }
}
