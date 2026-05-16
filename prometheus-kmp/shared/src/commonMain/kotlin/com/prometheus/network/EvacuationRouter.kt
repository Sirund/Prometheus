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
    val duration: DurationValue = DurationValue(),
    val steps: List<DirectionsStep> = emptyList()
)

@Serializable
data class DirectionsStep(
    val maneuver: String? = null
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
        val exitDistanceKm = dangerRadiusKm * 3.0
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
            if (leg.steps.any { it.maneuver == "ferry" }) return null
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

    private fun findExitIndex(
        polyline: List<Pair<Double, Double>>,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ): Int? {
        for (i in polyline.indices) {
            val d = haversineKm(polyline[i].first, polyline[i].second, epicenterLat, epicenterLon)
            if (d > dangerRadiusKm) return i
        }
        return null
    }

    private fun cumulativeKm(polyline: List<Pair<Double, Double>>): Double {
        if (polyline.size < 2) return 0.0
        return polyline.zipWithNext().sumOf { (a, b) ->
            haversineKm(a.first, a.second, b.first, b.second)
        }
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

        data class Scored(
            val candidate: Candidate, val modeResult: ModeResult,
            val exitIndex: Int, val exitDistFromEpicenterKm: Double,
            val distToExitKm: Double
        )

        val scored = results.filter { it.modeResult.polyline.size >= 2 }.mapNotNull { r ->
            val exitIdx = findExitIndex(r.modeResult.polyline, epicenterLat, epicenterLon, dangerRadiusKm) ?: return@mapNotNull null
            val exitCoord = r.modeResult.polyline[exitIdx]
            Scored(
                candidate = r.candidate, modeResult = r.modeResult, exitIndex = exitIdx,
                exitDistFromEpicenterKm = haversineKm(exitCoord.first, exitCoord.second, epicenterLat, epicenterLon),
                distToExitKm = cumulativeKm(r.modeResult.polyline.take(exitIdx + 1))
            )
        }

        val best = scored.minByOrNull {
            it.distToExitKm / max(it.exitDistFromEpicenterKm - dangerRadiusKm, 0.1)
        } ?: return null

        val dr = best.modeResult

        return EvacuationRoute(
            coordinates = dr.polyline,
            distanceKm = dr.distanceKm,
            durationMin = dr.durationMin,
            walkMin = dr.distanceKm / 5.0 * 60.0,
            runMin = dr.distanceKm / 10.0 * 60.0,
            cycleMin = dr.distanceKm / 15.0 * 60.0,
            motorMin = dr.distanceKm / 40.0 * 60.0
        )
    }

    fun close() { client.close() }
}
