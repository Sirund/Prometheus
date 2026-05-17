package com.prometheus.model

data class NowcastAlert(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String
) {
    val isBadWeather: Boolean
        get() = checkBadWeather(title, description)

    val eventType: String
        get() = extractEventType(title, description)

    val summary: String
        get() = description.ifBlank { title }

    companion object {
        private val BAD_WEATHER_KEYWORDS = listOf(
            "hujan lebat", "hujan petir", "hujan deras", "hujan badai",
            "angin kencang", "angin puting beliung", "angin topan",
            "gelombang tinggi", "banjir", "tanah longsor",
            "badai", "siklon", "tornado", "cuaca ekstrem"
        )

        fun checkBadWeather(title: String, description: String): Boolean {
            val combined = "$title $description".lowercase()
            return BAD_WEATHER_KEYWORDS.any { combined.contains(it) }
        }

        fun extractEventType(title: String, description: String): String {
            val combined = "$title $description".lowercase()
            for (kw in BAD_WEATHER_KEYWORDS) {
                if (combined.contains(kw)) return kw
            }
            return "Peringatan Cuaca"
        }
    }
}

data class WeatherInfo(
    val temperature: String,
    val humidity: String,
    val windSpeed: String,
    val windDirection: String,
    val weatherDesc: String,
    val visibility: String
) {
    companion object {
        val EMPTY = WeatherInfo(
            temperature = "--",
            humidity = "--",
            windSpeed = "--",
            windDirection = "--",
            weatherDesc = "Loading...",
            visibility = "--"
        )

        const val DEFAULT_ADM4 = "31.71.01.1001"
    }
}
