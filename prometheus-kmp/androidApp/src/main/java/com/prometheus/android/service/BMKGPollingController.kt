package com.prometheus.android.service

import android.content.Context
import android.util.Log
import com.prometheus.android.inference.EmergencyInferenceManager
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.BMKGPollingManager
import kotlinx.coroutines.*

class BMKGPollingController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pollingManager = BMKGPollingManager()
    private val alarmManager = PrometheusAlarmManager(context)
    private val emergencyInference = EmergencyInferenceManager(context)

    var onNewEvent: ((EarthquakeEvent) -> Unit)? = null
    var onPoll: ((EarthquakeEvent) -> Unit)? = null

    fun start() {
        pollingManager.onPoll = { events ->
            if (events.isNotEmpty()) onPoll?.invoke(events.first())
        }
        pollingManager.onDangerousEvent = { event ->
            onNewEvent?.invoke(event)
            generateAndAnnounceBriefing(event)
        }
        pollingManager.onNewEvent = { event ->
            onNewEvent?.invoke(event)
        }
        pollingManager.onError = { error ->
            Log.e("BMKGPolling", "Poll error", error)
        }
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
