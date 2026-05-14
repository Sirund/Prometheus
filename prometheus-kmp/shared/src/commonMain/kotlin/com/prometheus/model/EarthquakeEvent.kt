package com.prometheus.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EarthquakeEvent(
    @SerialName("Tanggal") val Tanggal: String? = null,
    @SerialName("tanggal") val tanggal_: String? = null,
    @SerialName("Jam") val Jam: String? = null,
    @SerialName("jam") val jam_: String? = null,
    @SerialName("DateTime") val DateTime: String? = null,
    @SerialName("Coordinates") val Coordinates: String? = null,
    @SerialName("coordinates") val coordinates_: String? = null,
    @SerialName("Lintang") val Lintang: String? = null,
    @SerialName("Bujur") val Bujur: String? = null,
    @SerialName("Magnitude") val Magnitude: String? = null,
    @SerialName("magnitude") val magnitude_: String? = null,
    @SerialName("Kedalaman") val Kedalaman: String? = null,
    @SerialName("kedalaman") val kedalaman_: String? = null,
    @SerialName("Wilayah") val Wilayah: String? = null,
    @SerialName("wilayah") val wilayah_: String? = null,
    @SerialName("Potensi") val Potensi: String? = null,
    @SerialName("potensi") val potensi_: String? = null,
    @SerialName("Dirasakan") val Dirasakan: String? = null,
    @SerialName("dirasakan") val dirasakan_: String? = null,
    @SerialName("Shakemap") val Shakemap: String? = null
) {
    val _tanggal: String? get() = Tanggal ?: tanggal_
    val _jam: String? get() = Jam ?: jam_
    val _coordinates: String? get() = Coordinates ?: coordinates_
    val _magnitude: String? get() = Magnitude ?: magnitude_
    val _kedalaman: String? get() = Kedalaman ?: kedalaman_
    val _wilayah: String? get() = Wilayah ?: wilayah_
    val _potensi: String? get() = Potensi ?: potensi_
    val _dirasakan: String? get() = Dirasakan ?: dirasakan_

    val id: String
        get() = DateTime ?: "${Tanggal ?: _tanggal}-${Jam ?: _jam}"

    val magnitudeValue: Float?
        get() = (_magnitude)?.trim()?.toFloatOrNull()

    val depthKm: Int?
        get() = _kedalaman?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull()

    val coordinatePair: Pair<Double, Double>?
        get() {
            val raw = _coordinates ?: return null
            val parts = raw.split(",").map { it.trim() }
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return Pair(lat, lon)
        }

    val maxMMI: Int
        get() = parseMaxMMI(_dirasakan)

    val hasTsunamiPotential: Boolean
        get() = checkTsunamiPotential(_potensi)

    val matchedDangerRules: List<DangerRuleMatch>
        get() = DangerClassifier.classify(this)

    val isDangerous: Boolean
        get() = matchedDangerRules.any { it.severity == DangerSeverity.CRITICAL || it.severity == DangerSeverity.HIGH }

    companion object {
        private val romanMap = mapOf(
            'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50,
            'C' to 100, 'D' to 500, 'M' to 1000
        )

        fun parseMaxMMI(dirasakan: String?): Int {
            if (dirasakan == null) return 0
            val regex = Regex("\\b[IVXLCDM]+\\b")
            return regex.findAll(dirasakan).maxOfOrNull { romanToInt(it.value) } ?: 0
        }

        private fun romanToInt(s: String): Int {
            var result = 0
            var prev = 0
            for (char in s.reversed()) {
                val value = romanMap[char] ?: 0
                if (value < prev) result -= value else result += value
                prev = value
            }
            return result
        }

        fun checkTsunamiPotential(potensi: String?): Boolean {
            if (potensi == null) return false
            val lower = potensi.lowercase()
            val hasPositive = lower.contains("tsunami") && lower.contains("berpotensi")
            val isNegated = lower.contains("tidak")
            return hasPositive && !isNegated
        }
    }
}
