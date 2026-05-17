package com.prometheus.monitor

import com.prometheus.model.NowcastAlert
import com.prometheus.network.BMKGWeatherClient
import kotlinx.coroutines.*

class NowcastPollingManager(
    private val intervalMs: Long = 300_000L
) {
    private val client = BMKGWeatherClient()
    private var job: Job? = null
    private var knownAlertKeys = mutableSetOf<String>()

    var onNewAlert: ((NowcastAlert) -> Unit)? = null
    var onBadWeather: ((List<NowcastAlert>) -> Unit)? = null
    var onPoll: ((List<NowcastAlert>) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            while (isActive) {
                try {
                    val alerts = client.fetchNowcastAlerts()
                    onPoll?.invoke(alerts)

                    val badWeatherAlerts = alerts.filter { it.isBadWeather }
                    if (badWeatherAlerts.isNotEmpty()) {
                        val newBad = badWeatherAlerts.filter { it.link !in knownAlertKeys }
                        if (newBad.isNotEmpty()) {
                            knownAlertKeys.addAll(newBad.map { it.link })
                            onBadWeather?.invoke(newBad)
                            newBad.forEach { onNewAlert?.invoke(it) }
                        }
                    }

                    knownAlertKeys = knownAlertKeys
                        .filter { key -> alerts.any { it.link == key } }
                        .toMutableSet()
                } catch (e: Exception) {
                    onError?.invoke(e)
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        client.close()
    }

    fun forceCheck(scope: CoroutineScope) {
        scope.launch {
            try {
                val alerts = client.fetchNowcastAlerts()
                onPoll?.invoke(alerts)
                val badWeatherAlerts = alerts.filter { it.isBadWeather }
                if (badWeatherAlerts.isNotEmpty()) {
                    val newBad = badWeatherAlerts.filter { it.link !in knownAlertKeys }
                    if (newBad.isNotEmpty()) {
                        knownAlertKeys.addAll(newBad.map { it.link })
                        onBadWeather?.invoke(newBad)
                        newBad.forEach { onNewAlert?.invoke(it) }
                    }
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }
}
