package com.prometheus.model

import com.prometheus.loadFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val testJson = Json { ignoreUnknownKeys = true }

class BMKGModelsTest {

    @Test
    fun decodeAutogempa_singleObject() {
        val raw = loadFixture("autogempa.json")
        val response = testJson.decodeFromString<BMKGResponse>(raw)
        assertNotNull(response.info)
        assertEquals(1, response.events.size)
        assertEquals("2.9", response.events[0].Magnitude)
    }

    @Test
    fun decodeGempaterkini_array() {
        val raw = loadFixture("gempaterkini.json")
        val response = testJson.decodeFromString<BMKGResponse>(raw)
        assertNotNull(response.info)
        assertEquals(15, response.events.size)
    }

    @Test
    fun magnitudeParsing() {
        assertEquals(6.0f, EarthquakeEvent(Magnitude = "6.0").magnitudeValue)
        assertEquals(2.9f, EarthquakeEvent(Magnitude = "2.9").magnitudeValue)
        assertNull(EarthquakeEvent().magnitudeValue)
    }

    @Test
    fun depthParsing() {
        assertEquals(10, EarthquakeEvent(Kedalaman = "10 km").depthKm)
        assertEquals(188, EarthquakeEvent(Kedalaman = "188 km").depthKm)
        assertEquals(5, EarthquakeEvent(Kedalaman = "5 km").depthKm)
        assertNull(EarthquakeEvent().depthKm)
    }

    @Test
    fun tsunamiDetection() {
        assertEquals(true, EarthquakeEvent.checkTsunamiPotential("Berpotensi tsunami"))
        assertEquals(true, EarthquakeEvent.checkTsunamiPotential("Berpotensi tsunami tinggi"))
        assertEquals(false, EarthquakeEvent.checkTsunamiPotential("Tidak berpotensi tsunami"))
        assertEquals(false, EarthquakeEvent.checkTsunamiPotential("Gempa ini dirasakan untuk diteruskan pada masyarakat"))
        assertEquals(false, EarthquakeEvent.checkTsunamiPotential(null))
    }

    @Test
    fun mmiParsing_single() {
        assertEquals(3, EarthquakeEvent.parseMaxMMI("III Kolaka Timur"))
        assertEquals(5, EarthquakeEvent.parseMaxMMI("V Kolaka"))
        assertEquals(6, EarthquakeEvent.parseMaxMMI("VI Makassar"))
    }

    @Test
    fun mmiParsing_multipleLocations() {
        assertEquals(3, EarthquakeEvent.parseMaxMMI("III Kolaka Timur, II-III Kolaka"))
        assertEquals(6, EarthquakeEvent.parseMaxMMI("V Kolaka, VI Makassar, IV Mamuju"))
    }

    @Test
    fun mmiParsing_range() {
        assertEquals(12, EarthquakeEvent.parseMaxMMI("XII Palu"))
        assertEquals(5, EarthquakeEvent.parseMaxMMI("II-V Kolaka"))
    }

    @Test
    fun mmiParsing_null() {
        assertEquals(0, EarthquakeEvent.parseMaxMMI(null))
    }

    @Test
    fun mmiParsing_empty() {
        assertEquals(0, EarthquakeEvent.parseMaxMMI(""))
    }

    @Test
    fun coordinateParsing() {
        val (lat, lon) = EarthquakeEvent(Coordinates = "-4.08,121.79").coordinatePair!!
        assertEquals(-4.08, lat, 0.001)
        assertEquals(121.79, lon, 0.001)
    }

    @Test
    fun coordinateParsing_fallback() {
        val (lat, lon) = EarthquakeEvent(coordinates_ = "-6.41,130.23").coordinatePair!!
        assertEquals(-6.41, lat, 0.001)
        assertEquals(130.23, lon, 0.001)
    }

    @Test
    fun coordinateParsing_null() {
        assertNull(EarthquakeEvent().coordinatePair)
    }

    @Test
    fun dangerSeverity_criticalTriggersAlarm() {
        val match = DangerRuleMatch("test", DangerSeverity.CRITICAL)
        assertEquals(true, listOf(match).any { it.severity == DangerSeverity.CRITICAL || it.severity == DangerSeverity.HIGH })
    }

    @Test
    fun dangerSeverity_infoNoAlarm() {
        val match = DangerRuleMatch("test", DangerSeverity.INFO)
        assertEquals(false, listOf(match).any { it.severity == DangerSeverity.CRITICAL || it.severity == DangerSeverity.HIGH })
    }
}
