package com.prometheus.model

import kotlin.math.*

data class NowcastAlert(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String,
    val guid: String = ""
) : Comparable<NowcastAlert> {
    override fun compareTo(other: NowcastAlert): Int = guid.compareTo(other.guid)

    val isBadWeather: Boolean
        get() = checkBadWeather(title, description)

    val eventType: String
        get() = extractEventType(title, description)

    val summary: String
        get() = description.ifBlank { title }

    val intensity: String
        get() {
            val regex = Regex("intensitas\\s+(\\d+)\\s*mm/jam", RegexOption.IGNORE_CASE)
            return regex.find(description)?.groupValues?.getOrNull(1)?.let { "$it mm/jam" }
                ?: eventType.replaceFirstChar { it.uppercase() }
        }

    val alertDate: String
        get() {
            val regex = Regex("\\d{1,2}\\s+\\w+\\s+\\d{4}")
            return regex.find(pubDate)?.value
                ?: regex.find(description)?.value
                ?: pubDate.take(16)
        }

    val alertTime: String
        get() {
            val pubRegex = Regex("\\d{2}:\\d{2}:\\d{2}")
            val pubTime = pubRegex.find(pubDate)?.value?.dropLast(3)
            if (pubTime != null) return pubTime
            val descRegex = Regex("pukul\\s+(\\d{2}\\.\\d{2}|\\d{2}:\\d{2})")
            return descRegex.find(description)?.groupValues?.getOrNull(1)?.replace(".", ":") ?: "--"
        }

    val estimatedEnd: String
        get() {
            val regex = Regex("(?:hingga|sampai|berlangsung\\s+hingga|berlangsung\\s+sampai)\\s*(?:pukul\\s+)?(\\d{2}[.:]\\d{2})", RegexOption.IGNORE_CASE)
            return regex.find(description)?.groupValues?.getOrNull(1)?.replace(".", ":")?.let { "~ $it" }
                ?: "--"
        }

    val provinceName: String
        get() = extractProvince(title)

    val provinceCenter: Pair<Double, Double>?
        get() = PROVINCE_CENTERS[provinceName]

    fun distanceKmFrom(lat: Double, lon: Double): Double? {
        val center = provinceCenter ?: return null
        return haversine(lat, lon, center.first, center.second)
    }

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

        fun extractProvince(title: String): String {
            val idx = title.indexOf(" di ")
            return if (idx >= 0) title.substring(idx + 4).trim() else title
        }

        fun nearestAlert(alerts: List<NowcastAlert>, userLat: Double, userLon: Double): NowcastAlert? {
            val maxRadiusKm = 300.0
            return alerts.filter {
                val d = it.distanceKmFrom(userLat, userLon)
                d != null && d <= maxRadiusKm
            }.minByOrNull { it.distanceKmFrom(userLat, userLon)!! }
        }

        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dlat = (lat2 - lat1) * PI / 180.0
            val dlon = (lon2 - lon1) * PI / 180.0
            val a = sin(dlat / 2).pow(2) + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dlon / 2).pow(2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        val PROVINCE_CENTERS: Map<String, Pair<Double, Double>> = mapOf(
            "Aceh" to (4.6951 to 96.7494),
            "Sumatera Utara" to (2.1154 to 99.5451),
            "Sumatera Barat" to (-0.7399 to 100.3450),
            "Riau" to (0.5073 to 101.8476),
            "Kep. Riau" to (3.9457 to 108.1429),
            "Kepulauan Riau" to (3.9457 to 108.1429),
            "Jambi" to (-1.6093 to 103.6130),
            "Sumatera Selatan" to (-3.3194 to 103.9143),
            "Bangka Belitung" to (-2.7411 to 106.4406),
            "Kep. Bangka Belitung" to (-2.7411 to 106.4406),
            "Bengkulu" to (-3.8000 to 102.2667),
            "Lampung" to (-5.4500 to 105.2667),
            "Banten" to (-6.4058 to 106.0640),
            "DKI Jakarta" to (-6.2088 to 106.8456),
            "Jakarta" to (-6.2088 to 106.8456),
            "Jawa Barat" to (-6.9147 to 107.6098),
            "Jawa Tengah" to (-7.1500 to 110.1333),
            "DI Yogyakarta" to (-7.7956 to 110.3695),
            "Yogyakarta" to (-7.7956 to 110.3695),
            "Jawa Timur" to (-7.5361 to 112.2384),
            "Bali" to (-8.3405 to 115.0920),
            "Nusa Tenggara Barat" to (-8.5833 to 116.1167),
            "Nusa Tenggara Timur" to (-10.1833 to 123.5833),
            "Kalimantan Barat" to (0.0000 to 110.5000),
            "Kalimantan Tengah" to (-2.0000 to 113.5000),
            "Kalimantan Selatan" to (-3.3333 to 115.5000),
            "Kalimantan Timur" to (0.5000 to 116.5000),
            "Kalimantan Utara" to (3.0000 to 116.5000),
            "Sulawesi Utara" to (0.5000 to 123.0000),
            "Gorontalo" to (0.4167 to 123.0000),
            "Sulawesi Tengah" to (-1.0000 to 121.0000),
            "Sulawesi Barat" to (-2.5000 to 119.5000),
            "Sulawesi Selatan" to (-4.0000 to 120.0000),
            "Sulawesi Tenggara" to (-4.0000 to 122.0000),
            "Maluku" to (-3.0000 to 129.0000),
            "Maluku Utara" to (1.0000 to 128.0000),
            "Papua Barat" to (-1.0000 to 133.0000),
            "Papua Barat Daya" to (-1.0000 to 131.0000),
            "Papua" to (-4.0000 to 138.0000),
            "Papua Tengah" to (-4.0000 to 136.0000),
            "Papua Pegunungan" to (-4.5000 to 138.5000),
            "Papua Selatan" to (-6.0000 to 140.0000)
        )
    }
}

data class WeatherInfo(
    val temperature: String,
    val humidity: String,
    val windSpeed: String,
    val windDirection: String,
    val weatherDesc: String,
    val visibility: String,
    val province: String = ""
) {
    fun matchingNowcastAlert(alerts: List<NowcastAlert>): NowcastAlert? {
        return alerts.firstOrNull { a ->
            province.isNotBlank() && a.provinceName.lowercase() == province.lowercase()
        }
    }

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

        fun adm4ForLocation(lat: Double, lon: Double): String {
            val nearest = PROVINCE_ADM4.entries.minByOrNull { (_, center) ->
                NowcastAlert.haversine(lat, lon, center.first, center.second)
            }
            val province = nearest?.key ?: return DEFAULT_ADM4
            return PROVINCE_ADM4_CODE[province] ?: DEFAULT_ADM4
        }

        private val PROVINCE_ADM4: Map<String, Pair<Double, Double>> = mapOf(
            "Aceh" to (4.6951 to 96.7494),
            "Sumatera Utara" to (2.1154 to 99.5451),
            "Sumatera Barat" to (-0.7399 to 100.3450),
            "Riau" to (0.5073 to 101.8476),
            "Kep. Riau" to (3.9457 to 108.1429),
            "Jambi" to (-1.6093 to 103.6130),
            "Sumatera Selatan" to (-3.3194 to 103.9143),
            "Bangka Belitung" to (-2.7411 to 106.4406),
            "Bengkulu" to (-3.8000 to 102.2667),
            "Lampung" to (-5.4500 to 105.2667),
            "Banten" to (-6.4058 to 106.0640),
            "DKI Jakarta" to (-6.2088 to 106.8456),
            "Jawa Barat" to (-6.9147 to 107.6098),
            "Jawa Tengah" to (-7.1500 to 110.1333),
            "DI Yogyakarta" to (-7.7956 to 110.3695),
            "Jawa Timur" to (-7.5361 to 112.2384),
            "Bali" to (-8.3405 to 115.0920),
            "Nusa Tenggara Barat" to (-8.5833 to 116.1167),
            "Nusa Tenggara Timur" to (-10.1833 to 123.5833),
            "Kalimantan Barat" to (0.0000 to 110.5000),
            "Kalimantan Tengah" to (-2.0000 to 113.5000),
            "Kalimantan Selatan" to (-3.3333 to 115.5000),
            "Kalimantan Timur" to (0.5000 to 116.5000),
            "Kalimantan Utara" to (3.0000 to 116.5000),
            "Sulawesi Utara" to (0.5000 to 123.0000),
            "Gorontalo" to (0.4167 to 123.0000),
            "Sulawesi Tengah" to (-1.0000 to 121.0000),
            "Sulawesi Barat" to (-2.5000 to 119.5000),
            "Sulawesi Selatan" to (-4.0000 to 120.0000),
            "Sulawesi Tenggara" to (-4.0000 to 122.0000),
            "Maluku" to (-3.0000 to 129.0000),
            "Maluku Utara" to (1.0000 to 128.0000),
            "Papua Barat" to (-1.0000 to 133.0000),
            "Papua Barat Daya" to (-1.0000 to 131.0000),
            "Papua" to (-4.0000 to 138.0000),
            "Papua Tengah" to (-4.0000 to 136.0000),
            "Papua Pegunungan" to (-4.5000 to 138.5000),
            "Papua Selatan" to (-6.0000 to 140.0000)
        )

        private val PROVINCE_ADM4_CODE: Map<String, String> = mapOf(
            "Aceh" to "11.71.01.1001",
            "Sumatera Utara" to "12.71.01.1001",
            "Sumatera Barat" to "13.71.01.1001",
            "Riau" to "14.71.01.1001",
            "Kep. Riau" to "21.71.01.1001",
            "Jambi" to "15.71.01.1001",
            "Sumatera Selatan" to "16.71.01.1001",
            "Bangka Belitung" to "19.71.01.1001",
            "Bengkulu" to "17.71.01.1001",
            "Lampung" to "18.71.01.1001",
            "Banten" to "36.71.01.1001",
            "DKI Jakarta" to "31.71.01.1001",
            "Jawa Barat" to "32.73.01.1001",
            "Jawa Tengah" to "33.74.01.1001",
            "DI Yogyakarta" to "34.71.01.1001",
            "Jawa Timur" to "35.78.01.1001",
            "Bali" to "51.71.01.1001",
            "Nusa Tenggara Barat" to "52.71.01.1001",
            "Nusa Tenggara Timur" to "53.71.01.1001",
            "Kalimantan Barat" to "61.71.01.1001",
            "Kalimantan Tengah" to "62.71.01.1001",
            "Kalimantan Selatan" to "63.71.01.1001",
            "Kalimantan Timur" to "64.71.01.1001",
            "Kalimantan Utara" to "65.71.01.1001",
            "Sulawesi Utara" to "71.71.01.1001",
            "Gorontalo" to "75.71.01.1001",
            "Sulawesi Tengah" to "72.71.01.1001",
            "Sulawesi Barat" to "76.71.01.1001",
            "Sulawesi Selatan" to "73.71.01.1001",
            "Sulawesi Tenggara" to "74.71.01.1001",
            "Maluku" to "81.71.01.1001",
            "Maluku Utara" to "82.71.01.1001",
            "Papua Barat" to "91.71.01.1001",
            "Papua Barat Daya" to "92.71.01.1001",
            "Papua" to "93.71.01.1001",
            "Papua Tengah" to "94.71.01.1001",
            "Papua Pegunungan" to "95.71.01.1001",
            "Papua Selatan" to "96.71.01.1001"
        )
    }
}
