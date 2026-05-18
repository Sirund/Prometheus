package com.prometheus.monitor

import com.prometheus.model.EarthquakeEvent
import com.prometheus.prompt.SystemPrompts

object EmergencyBriefingFormatter {

    fun formatEventSummary(event: EarthquakeEvent): String {
        val sb = StringBuilder()
        sb.appendLine("[BMKG EARTHQUAKE REPORT]")
        if (event.DateTime != null) sb.appendLine("Time (UTC): ${event.DateTime} (local: ${event.Tanggal ?: ""} ${event.Jam ?: ""})")
        event.magnitudeValue?.let { sb.appendLine("Magnitude: M $it") }
        event.depthKm?.let { sb.appendLine("Depth: $it km") }
        if (event.Wilayah != null) sb.appendLine("Location: ${event.Wilayah}")
        event._potensi?.let { sb.appendLine("Tsunami potential: $it") }
        event._dirasakan?.let { sb.appendLine("Felt reports (MMI): $it") }
        event.coordinatePair?.let { (lat, lon) ->
            sb.appendLine("Coordinates: $lat, $lon")
        }
        val rules = event.matchedDangerRules
        if (rules.isNotEmpty()) {
            sb.appendLine("Danger classifications: ${rules.joinToString(", ") { "[${it.severity.name}] ${it.id}" }}")
        }
        return sb.toString()
    }

    fun buildEmergencyPrompt(event: EarthquakeEvent): String {
        val summary = formatEventSummary(event)
        return "${SystemPrompts.EMERGENCY_COORDINATOR}\n\n$summary"
    }

    fun buildBriefingText(event: EarthquakeEvent): String {
        val sb = StringBuilder()
        sb.appendLine("Earthquake detected.")
        event.magnitudeValue?.let { sb.appendLine("Magnitude $it.") }
        event._wilayah?.let { sb.appendLine("Near ${it.take(60)}.") }
        if (event.hasTsunamiPotential) {
            sb.appendLine("Tsunami possible. Move to high ground immediately.")
        } else {
            sb.appendLine("No tsunami threat.")
        }
        sb.appendLine("Drop, cover, and hold on. Stay away from windows. Follow BMKG instructions.")
        return sb.toString()
    }
}
