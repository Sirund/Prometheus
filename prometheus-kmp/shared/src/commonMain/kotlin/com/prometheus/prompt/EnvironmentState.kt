package com.prometheus.prompt

import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert

enum class TectonicStatus { None, Watch, Medium, Danger }
enum class MeteorologicalStatus { Clear, Alert }

object EnvironmentState {

    fun evaluateTectonic(event: EarthquakeEvent?): TectonicStatus {
        if (event == null) return TectonicStatus.None
        if (event.isDangerous) return TectonicStatus.Danger
        val mag = event.magnitudeValue ?: return TectonicStatus.None
        return when {
            mag >= 5.0f -> TectonicStatus.Medium
            mag >= 4.0f -> TectonicStatus.Watch
            else -> TectonicStatus.None
        }
    }

    fun evaluateMeteorological(
        alerts: List<NowcastAlert>,
        userLat: Double?,
        userLon: Double?
    ): MeteorologicalStatus {
        if (alerts.isEmpty()) return MeteorologicalStatus.Clear
        val relevant = if (userLat != null && userLon != null) {
            alerts.filter {
                val d = it.distanceKmFrom(userLat, userLon)
                d != null && d <= 300.0
            }
        } else {
            alerts
        }
        return if (relevant.any { it.isBadWeather }) MeteorologicalStatus.Alert
        else MeteorologicalStatus.Clear
    }

    fun isCrisisActive(
        event: EarthquakeEvent?,
        alerts: List<NowcastAlert>,
        userLat: Double? = null,
        userLon: Double? = null
    ): Boolean {
        val tectonic = evaluateTectonic(event)
        val meteo = evaluateMeteorological(alerts, userLat, userLon)
        return tectonic == TectonicStatus.Danger ||
               tectonic == TectonicStatus.Medium ||
               meteo == MeteorologicalStatus.Alert
    }
}
