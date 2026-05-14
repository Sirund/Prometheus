package com.prometheus.android.service

import android.content.Context
import android.util.Log
import com.prometheus.monitor.BMKGPollingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BMKGPollingController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pollingManager = BMKGPollingManager()
    private val alarmManager = PrometheusAlarmManager(context)

    var onNewEvent: ((com.prometheus.model.EarthquakeEvent) -> Unit)? = null

    fun start() {
        pollingManager.onDangerousEvent = { event ->
            alarmManager.triggerAlert(event)
            onNewEvent?.invoke(event)
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
    }

    fun forceCheck() {
        pollingManager.forceCheck(scope)
    }
}
