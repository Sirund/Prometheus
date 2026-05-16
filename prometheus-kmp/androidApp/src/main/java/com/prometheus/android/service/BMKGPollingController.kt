package com.prometheus.android.service

import android.content.Context
import android.util.Log
import com.prometheus.android.inference.EmergencyInferenceManager
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.BMKGPollingManager
import kotlinx.coroutines.*

class BMKGPollingController(context: Context, baseUrlOverride: String? = null) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingManager = createPollingManager(baseUrlOverride)
    private val alarmManager = PrometheusAlarmManager(context)
    private val emergencyInference = EmergencyInferenceManager(context)
    private val locationProvider = LocationProvider(context)

    var onNewEvent: ((EarthquakeEvent) -> Unit)? = null
    var onPoll: ((EarthquakeEvent) -> Unit)? = null

    private fun createPollingManager(baseUrlOverride: String?): BMKGPollingManager {
        return BMKGPollingManager(baseUrlOverride = baseUrlOverride).apply {
            userLocation = locationProvider.getLastKnownLocation()
            onPoll = { events ->
                if (events.isNotEmpty()) onPoll?.invoke(events.first())
                userLocation = locationProvider.getLastKnownLocation()
            }
            onDangerousEvent = { event ->
                onNewEvent?.invoke(event)
                generateAndAnnounceBriefing(event)
            }
            onMediumEvent = { event ->
                onNewEvent?.invoke(event)
                alarmManager.triggerMediumAlert(event)
            }
            onNewEvent = { event ->
                onNewEvent?.invoke(event)
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
    }

    fun stop() {
        pollingManager.stop()
        alarmManager.showStatus(false)
        alarmManager.shutdown()
        emergencyInference.shutdown()
    }

    fun forceCheck() {
        pollingManager.forceCheck(scope)
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
