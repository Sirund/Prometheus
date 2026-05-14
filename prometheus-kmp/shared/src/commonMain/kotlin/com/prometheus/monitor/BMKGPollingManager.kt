package com.prometheus.monitor

import com.prometheus.model.EarthquakeEvent
import com.prometheus.network.BMKGClient
import kotlinx.coroutines.*

class BMKGPollingManager(
    private val intervalMs: Long = 60_000L
) {
    private val client = BMKGClient()
    private var job: Job? = null
    private var lastEventId: String? = null

    var onDangerousEvent: ((EarthquakeEvent) -> Unit)? = null
    var onNewEvent: ((EarthquakeEvent) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    var onPoll: ((List<EarthquakeEvent>) -> Unit)? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            while (isActive) {
                try {
                    val events = client.fetchAutogempa()
                    onPoll?.invoke(events)
                    if (events.isNotEmpty()) {
                        val latest = events.first()
                        if (latest.DateTime != null && latest.DateTime != lastEventId) {
                            lastEventId = latest.DateTime
                            onNewEvent?.invoke(latest)
                            if (latest.isDangerous) {
                                onDangerousEvent?.invoke(latest)
                            }
                        }
                    }
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
                val events = client.fetchAutogempa()
                onPoll?.invoke(events)
                if (events.isNotEmpty()) {
                    val latest = events.first()
                    if (latest.DateTime != null && latest.DateTime != lastEventId) {
                        lastEventId = latest.DateTime
                        onNewEvent?.invoke(latest)
                        if (latest.isDangerous) {
                            onDangerousEvent?.invoke(latest)
                        }
                    }
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }
}
