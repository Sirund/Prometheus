package com.prometheus.monitor

import com.prometheus.model.DangerClassifier
import com.prometheus.model.DangerSeverity
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.UserLocation
import com.prometheus.network.BMKGClient
import kotlinx.coroutines.*

class BMKGPollingManager(
    private val intervalMs: Long = 30_000L,
    private val baseUrlOverride: String? = null
) {
    private val client = BMKGClient(baseUrlOverride)
    private var job: Job? = null
    private var lastEventId: String? = null

    var userLocation: UserLocation? = null

    var onDangerousEvent: ((EarthquakeEvent) -> Unit)? = null
    var onMediumEvent: ((EarthquakeEvent) -> Unit)? = null
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
                            val matches = userLocation?.let {
                                DangerClassifier.classify(latest, it.latitude, it.longitude)
                            } ?: latest.matchedDangerRules
                            val highest = matches.maxByOrNull { it.severity.ordinal }
                            if (highest != null) {
                                when (highest.severity) {
                                    DangerSeverity.CRITICAL, DangerSeverity.HIGH ->
                                        onDangerousEvent?.invoke(latest)
                                    DangerSeverity.MEDIUM ->
                                        onMediumEvent?.invoke(latest)
                                    DangerSeverity.INFO -> {}
                                }
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
                        val matches = userLocation?.let {
                            DangerClassifier.classify(latest, it.latitude, it.longitude)
                        } ?: latest.matchedDangerRules
                        val highest = matches.maxByOrNull { it.severity.ordinal }
                        if (highest != null) {
                            when (highest.severity) {
                                DangerSeverity.CRITICAL, DangerSeverity.HIGH ->
                                    onDangerousEvent?.invoke(latest)
                                DangerSeverity.MEDIUM ->
                                    onMediumEvent?.invoke(latest)
                                DangerSeverity.INFO -> {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }
}
