package com.prometheus.android.inference

import android.content.Context
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.EmergencyBriefingFormatter

class EmergencyInferenceManager(private val context: Context) {

    suspend fun ensureLoaded(): Boolean = true

    suspend fun generateBriefing(event: EarthquakeEvent): String {
        return EmergencyBriefingFormatter.buildBriefingText(event)
    }

    fun shutdown() { }
}
