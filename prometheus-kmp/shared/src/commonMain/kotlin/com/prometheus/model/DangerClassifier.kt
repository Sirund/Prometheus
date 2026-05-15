package com.prometheus.model

enum class DangerSeverity {
    CRITICAL, HIGH, MEDIUM, INFO
}

data class DangerRuleMatch(
    val id: String,
    val severity: DangerSeverity,
    val ruleName: String = "",
    val tindakanAlarm: String = ""
)

object DangerClassifier {
    data class Rule(
        val id: String,
        val ruleName: String,
        val severity: DangerSeverity,
        val tindakanAlarm: String,
        val check: (EarthquakeEvent, Double?) -> Boolean
    )

    val rules: List<Rule> = listOf(
        Rule(
            id = "local_danger",
            ruleName = "Local Danger",
            severity = DangerSeverity.CRITICAL,
            tindakanAlarm = "Sirine Utama Berbunyi, Evakuasi Mandiri."
        ) { event, distance ->
            val mag = event.magnitudeValue ?: return@Rule false
            val depth = event.depthKm ?: return@Rule false
            mag >= 5.0f && depth < 70 && distance != null && distance <= 50.0
        },
        Rule(
            id = "regional_major",
            ruleName = "Regional Major",
            severity = DangerSeverity.HIGH,
            tindakanAlarm = "Alarm Keras, Bersiap Siaga."
        ) { event, distance ->
            val mag = event.magnitudeValue ?: return@Rule false
            val depth = event.depthKm ?: return@Rule false
            mag >= 6.0f && depth < 70 && distance != null && distance <= 150.0
        },
        Rule(
            id = "mega_earthquake",
            ruleName = "Mega Earthquakes",
            severity = DangerSeverity.HIGH,
            tindakanAlarm = "Alarm Keras, potensi ayunan kuat."
        ) { event, distance ->
            val mag = event.magnitudeValue ?: return@Rule false
            mag >= 7.0f && distance != null && distance <= 400.0
        },
        Rule(
            id = "distant_felt",
            ruleName = "Distant Felt",
            severity = DangerSeverity.MEDIUM,
            tindakanAlarm = "Notifikasi HP / Buzzer Pendek."
        ) { event, distance ->
            val mag = event.magnitudeValue ?: return@Rule false
            val depth = event.depthKm ?: return@Rule false
            mag >= 5.5f && depth < 70 && distance != null && distance in 150.0..300.0
        },
        Rule(
            id = "deep_close",
            ruleName = "Deep & Close",
            severity = DangerSeverity.MEDIUM,
            tindakanAlarm = "Notifikasi / Alarm Lemah."
        ) { event, distance ->
            val mag = event.magnitudeValue ?: return@Rule false
            val depth = event.depthKm ?: return@Rule false
            mag >= 6.0f && depth >= 70 && distance != null && distance <= 100.0
        },
        Rule(
            id = "tsunami_potential",
            ruleName = "Tsunami Potential",
            severity = DangerSeverity.CRITICAL,
            tindakanAlarm = "Sirine Tsunami, Evakuasi ke Tempat Tinggi."
        ) { event, _ ->
            event.hasTsunamiPotential
        }
    )

    fun classify(event: EarthquakeEvent, userLat: Double? = null, userLon: Double? = null): List<DangerRuleMatch> {
        val distance = if (userLat != null && userLon != null) {
            val epicenter = event.coordinatePair
            if (epicenter != null) GeoUtils.distanceKm(userLat, userLon, epicenter.first, epicenter.second)
            else null
        } else null
        return classify(event, distance)
    }

    fun classify(event: EarthquakeEvent, distanceKm: Double?): List<DangerRuleMatch> =
        rules.filter { it.check(event, distanceKm) }.map {
            DangerRuleMatch(it.id, it.severity, it.ruleName, it.tindakanAlarm)
        }
}
