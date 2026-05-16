package com.prometheus.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

    fun computeSafeDestination(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): Pair<Double, Double> {
        val bearing = atan2(
            sin(Math.toRadians(userLon - epicenterLon)) * cos(Math.toRadians(userLat)),
            cos(Math.toRadians(epicenterLat)) * sin(Math.toRadians(userLat)) -
                    sin(Math.toRadians(epicenterLat)) * cos(Math.toRadians(userLat)) * cos(Math.toRadians(userLon - epicenterLon))
        )
        val awayBearing = bearing + PI
        val distanceKm = dangerRadiusKm * 1.5
        val angularDist = distanceKm / 6371.0
        val destLat = asin(
            sin(Math.toRadians(userLat)) * cos(angularDist) +
                    cos(Math.toRadians(userLat)) * sin(angularDist) * cos(awayBearing)
        )
        val destLon = Math.toRadians(userLon) + atan2(
            sin(awayBearing) * sin(angularDist) * cos(Math.toRadians(userLat)),
            cos(angularDist) - sin(Math.toRadians(userLat)) * sin(destLat)
        )
        return Math.toDegrees(destLat) to ((Math.toDegrees(destLon) + 540) % 360 - 180)
    }

    suspend fun fetchRoute(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ): EvacuationRoute? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$endLon,$endLat?overview=full&geometries=geojson"
            val response: String = client.get(url).body()
            val parsed = json.decodeFromString<OSRMResponse>(response)
            val route = parsed.routes.firstOrNull() ?: return null
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

    suspend fun fetchEvacuationRoute(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): EvacuationRoute? {
        val (destLat, destLon) = computeSafeDestination(userLat, userLon, epicenterLat, epicenterLon, dangerRadiusKm)
        return fetchRoute(userLat, userLon, destLat, destLon)
    }

    fun close() { client.close() }
}
