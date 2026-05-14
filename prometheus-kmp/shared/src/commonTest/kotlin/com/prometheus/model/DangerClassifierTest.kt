package com.prometheus.model

import com.prometheus.loadFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun gempaterkini_m60depth10_highMagnitudeAndShallow() {
        val events = loadEvents("gempaterkini.json")
        val event = events[0]

        assertEquals(6.0f, event.magnitudeValue)
        assertEquals(10, event.depthKm)
        assertEquals(false, event.hasTsunamiPotential)
        assertEquals(true, event.isDangerous)

        val ruleIds = event.matchedDangerRules.map { it.id }.sorted()
        assertEquals(listOf("high_magnitude", "moderate_magnitude_shallow"), ruleIds)
    }

    @Test
    fun gempaterkini_m53depth188_deepNotDangerous() {
        val events = loadEvents("gempaterkini.json")
        val event = events[1]

        assertEquals(5.3f, event.magnitudeValue)
        assertEquals(188, event.depthKm)
        assertEquals(false, event.isDangerous)

        val ruleIds = event.matchedDangerRules.map { it.id }
        assertEquals(listOf("moderate_magnitude_deep"), ruleIds)
    }

    @Test
    fun gempaterkini_m51depth12_shallowDangerous() {
        val events = loadEvents("gempaterkini.json")
        val event = events[4]

        assertEquals(5.1f, event.magnitudeValue)
        assertEquals(12, event.depthKm)
        assertEquals(true, event.isDangerous)

        val ruleIds = event.matchedDangerRules.map { it.id }
        assertEquals(listOf("moderate_magnitude_shallow"), ruleIds)
    }

    @Test
    fun gempaterkini_m50depth12_shallowAtThreshold() {
        val events = loadEvents("gempaterkini.json")
        val event = events[6]

        assertEquals(5.0f, event.magnitudeValue)
        assertEquals(12, event.depthKm)
        assertEquals(true, event.isDangerous)

        val ruleIds = event.matchedDangerRules.map { it.id }
        assertEquals(listOf("moderate_magnitude_shallow"), ruleIds)
    }

    @Test
    fun gempaterkini_m60depth31_dangerous() {
        val events = loadEvents("gempaterkini.json")
        val event = events[8]

        assertEquals(6.0f, event.magnitudeValue)
        assertEquals(31, event.depthKm)
        assertEquals(true, event.isDangerous)

        val ruleIds = event.matchedDangerRules.map { it.id }.sorted()
        assertEquals(listOf("high_magnitude", "moderate_magnitude_shallow"), ruleIds)
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
