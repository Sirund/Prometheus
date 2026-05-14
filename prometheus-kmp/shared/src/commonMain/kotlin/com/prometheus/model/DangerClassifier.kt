package com.prometheus.model

enum class DangerSeverity {
    CRITICAL, HIGH, LOW, INFO
}

data class DangerRuleMatch(
    val id: String,
    val severity: DangerSeverity
)

object DangerClassifier {
    data class Rule(
        val id: String,
        val severity: DangerSeverity,
        val check: (EarthquakeEvent) -> Boolean
    )

    val rules: List<Rule> = listOf(
        Rule("tsunami_potential", DangerSeverity.CRITICAL) { it.hasTsunamiPotential },
        Rule("high_magnitude", DangerSeverity.HIGH) { (it.magnitudeValue ?: 0f) >= 6.0f },
        Rule("moderate_magnitude_shallow", DangerSeverity.HIGH) {
            val mag = it.magnitudeValue ?: return@Rule false
            val depth = it.depthKm ?: return@Rule false
            mag >= 5.0f && depth < 70
        },
        Rule("felt_intensity_damage", DangerSeverity.HIGH) { it.maxMMI >= 5 },
        Rule("moderate_magnitude_deep", DangerSeverity.LOW) {
            val mag = it.magnitudeValue ?: return@Rule false
            val depth = it.depthKm ?: return@Rule false
            mag >= 5.0f && depth >= 70
        }
    )

    fun classify(event: EarthquakeEvent): List<DangerRuleMatch> =
        rules.filter { it.check(event) }.map { DangerRuleMatch(it.id, it.severity) }
}
