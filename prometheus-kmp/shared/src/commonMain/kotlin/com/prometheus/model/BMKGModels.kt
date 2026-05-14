package com.prometheus.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

private val bmkgJson = Json { ignoreUnknownKeys = true }

@Serializable
data class BMKGResponse(
    val Infogempa: InfoGempaWrapper? = null,
    val infogempa_: InfoGempaWrapper? = null
) {
    val info: InfoGempaWrapper? get() = Infogempa ?: infogempa_

    val events: List<EarthquakeEvent>
        get() = info?.eventList ?: emptyList()
}

@Serializable
data class InfoGempaWrapper(
    val gempa: JsonElement
) {
    val eventList: List<EarthquakeEvent>
        get() = when (gempa) {
            is JsonArray -> bmkgJson.decodeFromJsonElement<List<EarthquakeEvent>>(gempa)
            is JsonObject -> listOf(bmkgJson.decodeFromJsonElement<EarthquakeEvent>(gempa))
            else -> emptyList()
        }
}

data class BMKGEndpoint(
    val id: String,
    val description: String,
    val url: String
)

object BMKGEndpoints {
    val AUTO_GEMPA = BMKGEndpoint(
        id = "autogempa",
        description = "Single latest earthquake report",
        url = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json"
    )
    val GEMPA_TERKINI = BMKGEndpoint(
        id = "gempaterkini",
        description = "List of recent significant earthquakes",
        url = "https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json"
    )
    val GEMPA_DIRASAKAN = BMKGEndpoint(
        id = "gempadirasakan",
        description = "List of recent felt earthquakes",
        url = "https://data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json"
    )
}
