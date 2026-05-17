package com.prometheus.network

import com.prometheus.model.NowcastAlert
import com.prometheus.model.WeatherInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class BMKGWeatherClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetchWeatherForecast(adm4: String = WeatherInfo.DEFAULT_ADM4): WeatherInfo {
        return try {
            val response = client.get("https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=$adm4")
            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonArray?.firstOrNull()?.jsonObject
            val cuacaArray = data?.get("cuaca")?.jsonArray
            val todaySlots = cuacaArray?.firstOrNull()?.jsonArray
            val current = todaySlots?.firstOrNull()?.jsonObject ?: return WeatherInfo.EMPTY

            WeatherInfo(
                temperature = "${current["t"]?.jsonPrimitive?.content ?: "--"}",
                humidity = "${current["hu"]?.jsonPrimitive?.content ?: "--"}",
                windSpeed = current["ws"]?.jsonPrimitive?.content ?: "--",
                windDirection = current["wd"]?.jsonPrimitive?.content ?: "--",
                weatherDesc = current["weather_desc"]?.jsonPrimitive?.content ?: "--",
                visibility = current["vs_text"]?.jsonPrimitive?.content ?: "--"
            )
        } catch (e: Exception) {
            WeatherInfo.EMPTY
        }
    }

    suspend fun fetchNowcastAlerts(): List<NowcastAlert> {
        return try {
            val xml = client.get("https://www.bmkg.go.id/alerts/nowcast/id").body<String>()
            parseNowcastRss(xml)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseNowcastRss(xml: String): List<NowcastAlert> {
        val alerts = mutableListOf<NowcastAlert>()
        val items = xml.split("<item>", "</item>")
        for (i in 1 until items.size - 1 step 2) {
            val item = items[i]
            val title = extractXmlTag(item, "title")
            val description = extractXmlTag(item, "description")
            val link = extractXmlTag(item, "link")
            val pubDate = extractXmlTag(item, "pubDate")
            val guid = extractXmlTag(item, "guid")
            if (title != null && description != null) {
                alerts.add(NowcastAlert(
                    title = title,
                    description = description,
                    link = link ?: "",
                    pubDate = pubDate ?: "",
                    guid = guid ?: ""
                ))
            }
        }
        return alerts.sortedDescending()
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = xml.indexOf(startTag)
        if (start == -1) return null
        val valueStart = start + startTag.length
        val end = xml.indexOf(endTag, valueStart)
        if (end == -1) return null
        val raw = xml.substring(valueStart, end)
        return raw.trim().replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .ifBlank { null }
    }

    fun close() {
        client.close()
    }
}
