package com.prometheus.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.*

@Serializable
data class OSRMResponse(
    val code: String,
    val routes: List<OSRMRoute> = emptyList()
)

@Serializable
data class OSRMRoute(
    val geometry: OSRMGeometry,
    val distance: Double = 0.0,
    val duration: Double = 0.0
)

@Serializable
data class OSRMGeometry(
    val coordinates: List<List<Double>> = emptyList()
)

data class EvacuationRoute(
    val coordinates: List<Pair<Double, Double>>,
    val distanceKm: Double,
    val durationMin: Double
)

class EvacuationRouter {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient()

    data class Candidate(
        val lat: Double, val lon: Double,
        val angleDeg: Double, val label: String
    )

    fun generateCandidates(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): List<Candidate> {
        val bearing = atan2(
            sin(Math.toRadians(userLon - epicenterLon)) * cos(Math.toRadians(userLat)),
            cos(Math.toRadians(epicenterLat)) * sin(Math.toRadians(userLat)) -
                    sin(Math.toRadians(epicenterLat)) * cos(Math.toRadians(userLat)) * cos(Math.toRadians(userLon - epicenterLon))
        )
        val away = Math.toDegrees(bearing + PI)
        val exitDistanceKm = dangerRadiusKm * 1.5
        val angularDist = exitDistanceKm / 6371.0

        fun destAtAngle(angleDeg: Double): Pair<Double, Double> {
            val rad = Math.toRadians(angleDeg)
            val lat = asin(
                sin(Math.toRadians(userLat)) * cos(angularDist) +
                        cos(Math.toRadians(userLat)) * sin(angularDist) * cos(rad)
            )
            val lon = Math.toRadians(userLon) + atan2(
                sin(rad) * sin(angularDist) * cos(Math.toRadians(userLat)),
                cos(angularDist) - sin(Math.toRadians(userLat)) * sin(lat)
            )
            return Math.toDegrees(lat) to ((Math.toDegrees(lon) + 540) % 360 - 180)
        }

        val offsets = listOf(
            0.0 to "primary",
            -30.0 to "left30", 30.0 to "right30",
            -60.0 to "left60", 60.0 to "right60",
            -90.0 to "left90", 90.0 to "right90"
        )
        val seen = mutableSetOf<Pair<Double, Double>>()
        return offsets.mapNotNull { (offset, label) ->
            val (dlat, dlon) = destAtAngle(away + offset)
            if (seen.add(dlat to dlon)) Candidate(dlat, dlon, away + offset, label) else null
        }
    }

    private suspend fun fetchRoute(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ): EvacuationRoute? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$endLon,$endLat?overview=full&geometries=geojson"
            val response: String = client.get(url).body()
            val parsed = json.decodeFromString<OSRMResponse>(response)
            val route = parsed.routes.firstOrNull() ?: return null
            if (route.geometry.coordinates.isEmpty()) return null
            val coords = route.geometry.coordinates.map { (it[1] to it[0]) }
            EvacuationRoute(
                coordinates = coords,
                distanceKm = route.distance / 1000.0,
                durationMin = route.duration / 60.0
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val a = sin(dlat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dlon / 2).pow(2)
        return 2 * atan2(sqrt(a), sqrt(1 - a)) * 6371.0
    }

    private fun scoreRoute(
        route: EvacuationRoute,
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double
    ): Double {
        val last = route.coordinates.last()
        val distUserToEpicenter = haversineKm(userLat, userLon, epicenterLat, epicenterLon)
        val distDestToEpicenter = haversineKm(last.first, last.second, epicenterLat, epicenterLon)
        val netAwayKm = distDestToEpicenter - distUserToEpicenter
        if (netAwayKm <= 0) return Double.MAX_VALUE
        return route.distanceKm / max(netAwayKm, 0.1)
    }

    suspend fun fetchEvacuationRoute(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): EvacuationRoute? {
        val candidates = generateCandidates(userLat, userLon, epicenterLat, epicenterLon, dangerRadiusKm)

        val results = coroutineScope {
            candidates.map { c ->
                async { fetchRoute(userLat, userLon, c.lat, c.lon) }
            }.map { it.await() }
        }

        return results
            .filterNotNull()
            .filter { it.coordinates.size >= 2 }
            .minByOrNull { scoreRoute(it, userLat, userLon, epicenterLat, epicenterLon) }
    }

    fun close() { client.close() }
}
