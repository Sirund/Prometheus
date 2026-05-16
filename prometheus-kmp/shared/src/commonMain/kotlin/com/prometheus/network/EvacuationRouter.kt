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
data class DirectionsResponse(
    val status: String,
    val routes: List<DirectionsRoute> = emptyList()
)

@Serializable
data class DirectionsRoute(
    val legs: List<DirectionsLeg> = emptyList(),
    val overview_polyline: PolylinePoint? = null
)

@Serializable
data class PolylinePoint(val points: String = "")

@Serializable
data class DirectionsLeg(
    val distance: DistanceValue = DistanceValue(),
    val duration: DurationValue = DurationValue()
)

@Serializable
data class DistanceValue(val value: Int = 0)
@Serializable
data class DurationValue(val value: Int = 0)

data class EvacuationRoute(
    val coordinates: List<Pair<Double, Double>>,
    val distanceKm: Double,
    val durationMin: Double,
    val walkMin: Double,
    val runMin: Double,
    val cycleMin: Double,
    val motorMin: Double
)

object PolylineDecoder {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        val result = mutableListOf<Pair<Double, Double>>()

        while (index < len) {
            var b: Int
            var shift = 0
            var value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (value and 1 == 1) -(value shr 1) else value shr 1
            lat += dlat

            shift = 0
            value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (value and 1 == 1) -(value shr 1) else value shr 1
            lng += dlng

            result.add((lat / 1e5) to (lng / 1e5))
        }
        return result
    }
}

class EvacuationRouter(private val googleApiKey: String = "") {
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
            val dlat = asin(
                sin(Math.toRadians(userLat)) * cos(angularDist) +
                        cos(Math.toRadians(userLat)) * sin(angularDist) * cos(rad)
            )
            val dlon = Math.toRadians(userLon) + atan2(
                sin(rad) * sin(angularDist) * cos(Math.toRadians(userLat)),
                cos(angularDist) - sin(Math.toRadians(userLat)) * sin(dlat)
            )
            return Math.toDegrees(dlat) to ((Math.toDegrees(dlon) + 540) % 360 - 180)
        }

        val seen = mutableSetOf<Pair<Double, Double>>()
        return (0 until 12).mapNotNull { i ->
            val angle = i * 30.0
            val (dlat, dlon) = destAtAngle(away + angle)
            if (seen.add(dlat to dlon)) Candidate(dlat, dlon, away + angle, "${i * 30}deg") else null
        }
    }

    private data class ModeResult(
        val mode: String,
        val polyline: List<Pair<Double, Double>>,
        val distanceKm: Double,
        val durationMin: Double
    )

    private suspend fun fetchDirections(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        mode: String
    ): ModeResult? {
        if (googleApiKey.isBlank()) return null
        return try {
            val url = buildString {
                append("https://maps.googleapis.com/maps/api/directions/json")
                append("?origin=$startLat,$startLon")
                append("&destination=$endLat,$endLon")
                append("&mode=$mode")
                append("&key=$googleApiKey")
            }
            val raw: String = client.get(url).body()
        val response = json.decodeFromString<DirectionsResponse>(raw)
            if (response.status != "OK") return null
            val route = response.routes.firstOrNull() ?: return null
            val leg = route.legs.firstOrNull() ?: return null
            val poly = route.overview_polyline ?: return null
            val coords = PolylineDecoder.decode(poly.points)
            if (coords.isEmpty()) return null
            ModeResult(
                mode = mode,
                polyline = coords,
                distanceKm = leg.distance.value / 1000.0,
                durationMin = leg.duration.value / 60.0
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
        polyline: List<Pair<Double, Double>>,
        distanceKm: Double,
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double
    ): Double {
        val last = polyline.last()
        val distUserToEpicenter = haversineKm(userLat, userLon, epicenterLat, epicenterLon)
        val distDestToEpicenter = haversineKm(last.first, last.second, epicenterLat, epicenterLon)
        val netAwayKm = distDestToEpicenter - distUserToEpicenter
        if (netAwayKm <= 0) return Double.MAX_VALUE
        val distUserToDest = haversineKm(userLat, userLon, last.first, last.second)
        return distanceKm / max(netAwayKm, 0.1)
    }

    suspend fun fetchEvacuationRoute(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): EvacuationRoute? {
        val candidates = generateCandidates(userLat, userLon, epicenterLat, epicenterLon, dangerRadiusKm)
        if (candidates.isEmpty()) return null

        class CandidateResult(val candidate: Candidate, val modeResult: ModeResult)

        val results = coroutineScope {
            candidates.map { c ->
                async { fetchDirections(userLat, userLon, c.lat, c.lon, "driving")?.let { CandidateResult(c, it) } }
            }.mapNotNull { it.await() }
        }

        val best = results
            .filter { it.modeResult.polyline.size >= 2 }
            .minByOrNull { scoreRoute(it.modeResult.polyline, it.modeResult.distanceKm, userLat, userLon, epicenterLat, epicenterLon) }
            ?: return null

        val walkingDriving = fetchDirections(userLat, userLon, best.candidate.lat, best.candidate.lon, "walking")
        val cyclingResult = fetchDirections(userLat, userLon, best.candidate.lat, best.candidate.lon, "bicycling")

        val dr = best.modeResult
        val walkDist = walkingDriving?.distanceKm ?: dr.distanceKm
        val walkDur = walkingDriving?.durationMin ?: (dr.distanceKm / 5.0 * 60.0)
        val cycleDur = cyclingResult?.durationMin ?: (dr.distanceKm / 15.0 * 60.0)

        return EvacuationRoute(
            coordinates = dr.polyline,
            distanceKm = dr.distanceKm,
            durationMin = dr.durationMin,
            walkMin = walkDur,
            runMin = walkDist / 10.0 * 60.0,
            cycleMin = cycleDur,
            motorMin = dr.distanceKm / 40.0 * 60.0
        )
    }

    fun close() { client.close() }
}
