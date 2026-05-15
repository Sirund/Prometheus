package com.prometheus.model

import kotlin.math.*

data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0
    private const val DEG_TO_RAD = kotlin.math.PI / 180.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD
        val rlat1 = lat1 * DEG_TO_RAD
        val rlat2 = lat2 * DEG_TO_RAD
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rlat1) * cos(rlat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun distanceKm(from: UserLocation, toLat: Double, toLon: Double): Double =
        distanceKm(from.latitude, from.longitude, toLat, toLon)
}