package com.prometheus.model

import com.prometheus.loadFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testJson = Json { ignoreUnknownKeys = true }

class DangerClassifierTest {

    private fun loadEvents(fixtureName: String): List<EarthquakeEvent> {
        val raw = loadFixture(fixtureName)
        val response = testJson.decodeFromString<BMKGResponse>(raw)
        return response.events
    }

    @Test
    fun autogempaSmallEvent_notDangerous() {
        val events = loadEvents("autogempa.json")
        assertEquals(1, events.size)
        val event = events[0]

        assertEquals(2.9f, event.magnitudeValue)
        assertEquals(10, event.depthKm)
        assertEquals(3, event.maxMMI)
        assertEquals(false, event.hasTsunamiPotential)
        assertEquals(false, event.isDangerous)
        assertEquals(emptyList(), event.matchedDangerRules)
    }

    @Test
    fun localDanger_nearby() {
        val event = EarthquakeEvent(
            Magnitude = "5.5", Kedalaman = "30 km",
            Coordinates = "-7.0,110.0"
        )
        // User at Semarang (~25km from epicenter)
        val matches = DangerClassifier.classify(event, -6.99, 110.35)
        assertTrue(matches.any { it.id == "local_danger" })
        assertEquals(DangerSeverity.CRITICAL, matches.first { it.id == "local_danger" }.severity)
    }

    @Test
    fun localDanger_farAway() {
        val event = EarthquakeEvent(
            Magnitude = "5.5", Kedalaman = "30 km",
            Coordinates = "-7.0,110.0"
        )
        // User at Jakarta (~450km from epicenter) - outside 50km
        val matches = DangerClassifier.classify(event, -6.2, 106.8)
        assertTrue(matches.none { it.id == "local_danger" })
    }

    @Test
    fun regionalMajor() {
        val event = EarthquakeEvent(
            Magnitude = "6.5", Kedalaman = "30 km",
            Coordinates = "-8.0,115.0"
        )
        val matches = DangerClassifier.classify(event, -8.0, 115.0)
        assertTrue(matches.any { it.id == "regional_major" })
        assertEquals(DangerSeverity.HIGH, matches.first { it.id == "regional_major" }.severity)
    }

    @Test
    fun megaEarthquake() {
        val event = EarthquakeEvent(
            Magnitude = "7.5", Kedalaman = "50 km",
            Coordinates = "-10.0,118.0"
        )
        // User ~300km away
        val matches = DangerClassifier.classify(event, -8.0, 115.0)
        assertTrue(matches.any { it.id == "mega_earthquake" })
        assertEquals(DangerSeverity.HIGH, matches.first { it.id == "mega_earthquake" }.severity)
    }

    @Test
    fun distantFelt() {
        val event = EarthquakeEvent(
            Magnitude = "5.8", Kedalaman = "30 km",
            Coordinates = "-10.0,120.0"
        )
        // User ~200km away
        val matches = DangerClassifier.classify(event, -8.5, 118.5)
        assertTrue(matches.any { it.id == "distant_felt" })
        assertEquals(DangerSeverity.MEDIUM, matches.first { it.id == "distant_felt" }.severity)
    }

    @Test
    fun deepClose() {
        val event = EarthquakeEvent(
            Magnitude = "6.2", Kedalaman = "120 km",
            Coordinates = "-7.0,110.0"
        )
        val matches = DangerClassifier.classify(event, -7.0, 110.0)
        assertTrue(matches.any { it.id == "deep_close" })
        assertEquals(DangerSeverity.MEDIUM, matches.first { it.id == "deep_close" }.severity)
    }

    @Test
    fun tsunamiPotential_overridesDistance() {
        val event = EarthquakeEvent(
            Magnitude = "5.0", Kedalaman = "10 km",
            Potensi = "Berpotensi tsunami",
            Coordinates = "-7.0,110.0"
        )
        // Very far away but tsunami potential still triggers CRITICAL
        val matches = DangerClassifier.classify(event, 40.0, -74.0)
        assertTrue(matches.any { it.id == "tsunami_potential" })
        assertEquals(DangerSeverity.CRITICAL, matches.first { it.id == "tsunami_potential" }.severity)
    }

    @Test
    fun safeOutOfRange() {
        val event = EarthquakeEvent(
            Magnitude = "4.0", Kedalaman = "10 km",
            Coordinates = "-7.0,110.0"
        )
        val matches = DangerClassifier.classify(event, 40.0, -74.0)
        assertEquals(emptyList(), matches)
    }

    @Test
    fun noLocation_noDistanceRules() {
        val event = EarthquakeEvent(
            Magnitude = "6.0", Kedalaman = "30 km",
            Coordinates = "-7.0,110.0"
        )
        // Without location, only tsunami_potential can match
        val matches = DangerClassifier.classify(event, null as Double?)
        assertTrue(matches.none { it.id == "local_danger" || it.id == "regional_major" })
    }

    @Test
    fun coordinateParsing() {
        val events = loadEvents("autogempa.json")
        val coords = events[0].coordinatePair
        assertEquals(-4.08, coords!!.first, 0.001)
        assertEquals(121.79, coords.second, 0.001)
    }

    @Test
    fun eventId_fromDateTime() {
        val events = loadEvents("autogempa.json")
        assertEquals("2026-05-11T21:06:20+00:00", events[0].id)
    }
}
