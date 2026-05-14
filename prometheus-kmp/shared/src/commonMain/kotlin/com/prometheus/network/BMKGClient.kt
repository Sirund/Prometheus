package com.prometheus.network

import com.prometheus.model.BMKGResponse
import com.prometheus.model.EarthquakeEvent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class BMKGClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetchAutogempa(): List<EarthquakeEvent> {
        val response = client.get("https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json")
        return response.body<BMKGResponse>().events
    }

    suspend fun fetchGempaterkini(): List<EarthquakeEvent> {
        val response = client.get("https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json")
        return response.body<BMKGResponse>().events
    }

    suspend fun fetchGempadirasakan(): List<EarthquakeEvent> {
        val response = client.get("https://data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json")
        return response.body<BMKGResponse>().events
    }

    fun close() { client.close() }
}
