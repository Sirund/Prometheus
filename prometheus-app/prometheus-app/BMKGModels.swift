import Foundation

// MARK: - API response wrappers

struct BMKGResponse: Decodable {
    let Infogempa: InfoGempaWrapper?
    var events: [EarthquakeEvent] { Infogempa?.events ?? [] }
}

struct InfoGempaWrapper: Decodable {
    let events: [EarthquakeEvent]

    enum CodingKeys: String, CodingKey { case gempa }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let arr = try? c.decode([EarthquakeEvent].self, forKey: .gempa) {
            events = arr
        } else if let single = try? c.decode(EarthquakeEvent.self, forKey: .gempa) {
            events = [single]
        } else {
            events = []
        }
    }
}

// MARK: - Earthquake event

struct EarthquakeEvent: Decodable {
    let Tanggal: String?
    let Jam: String?
    let DateTime: String?
    let Coordinates: String?
    let Lintang: String?
    let Bujur: String?
    let Magnitude: String?
    let Kedalaman: String?
    let Wilayah: String?
    let Potensi: String?
    let Dirasakan: String?
    let Shakemap: String?

    var magnitudeValue: Float? {
        guard let m = Magnitude?.trimmingCharacters(in: .whitespaces) else { return nil }
        return Float(m)
    }

    var depthKm: Int? {
        guard let k = Kedalaman?.trimmingCharacters(in: .whitespaces) else { return nil }
        return Int(k.split(separator: " ").first ?? "")
    }

    var coordinatePair: (lat: Double, lon: Double)? {
        guard let raw = Coordinates else { return nil }
        let parts = raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        guard parts.count == 2, let lat = Double(parts[0]), let lon = Double(parts[1]) else { return nil }
        return (lat, lon)
    }

    var hasTsunamiPotential: Bool {
        guard let p = Potensi?.lowercased() else { return false }
        return p.contains("tsunami") && p.contains("berpotensi") && !p.contains("tidak")
    }
}

extension EarthquakeEvent {
    var dangerRadiusKm: Double? {
        guard !hasTsunamiPotential else { return nil }
        let mag = magnitudeValue ?? 0
        if mag >= 7 { return 200.0 }
        if mag >= 6 { return 150.0 }
        if mag >= 5 { return 50.0 }
        return nil
    }
}

// MARK: - Geo utilities

enum GeoUtils {
    static func distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let r = 6371.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

// MARK: - Evacuation route

struct EvacuationRoute {
    let distanceKm: Double
    let walkMin: Double
    let runMin: Double
    let cycleMin: Double
    let motorMin: Double
    let durationMin: Double

    /// Returns nil if the user is already outside the danger radius.
    static func compute(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ) -> EvacuationRoute? {
        let distToEpicenter = GeoUtils.distanceKm(
            lat1: userLat, lon1: userLon,
            lat2: epicenterLat, lon2: epicenterLon
        )
        guard distToEpicenter < dangerRadiusKm else { return nil }
        let km = max((dangerRadiusKm - distToEpicenter) * 1.3, 0.5)
        return EvacuationRoute(
            distanceKm: km,
            walkMin:    km / 5.0  * 60,
            runMin:     km / 12.0 * 60,
            cycleMin:   km / 20.0 * 60,
            motorMin:   km / 60.0 * 60,
            durationMin: km / 80.0 * 60
        )
    }
}

// MARK: - Emergency briefing text builder

enum EmergencyBriefingFormatter {
    static func buildBriefingText(event: EarthquakeEvent) -> String {
        var lines = ["Earthquake detected."]
        if let mag = event.magnitudeValue { lines.append("Magnitude \(mag).") }
        if let loc = event.Wilayah { lines.append("Near \(String(loc.prefix(60))).") }
        lines.append(event.hasTsunamiPotential
            ? "Tsunami possible. Move to high ground immediately."
            : "No tsunami threat.")
        lines.append("Drop, cover, and hold on. Stay away from windows. Follow BMKG instructions.")
        return lines.joined(separator: " ")
    }
}
