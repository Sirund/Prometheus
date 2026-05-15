//
//  BMKGModels.swift
//  prometheus-app
//
//  BMKG earthquake data models matching the live JSON structure.
//  Designed by Andi for Pelangi's app integration.
//
//  Usage:
//    let decoder = JSONDecoder()
//    let response = try decoder.decode(BMKGResponse.self, from: jsonData)
//    // response.events → [EarthquakeEvent]
//
//  Updated: 2026-05-12
//  Owner: Andi
//

import Foundation

// MARK: - Top-level response

struct BMKGResponse: Codable {
    let Infogempa: InfoGempaWrapper?
    let infogempa: InfoGempaWrapper?  // fallback for lowercase variant
    
    /// Convenience: access whichever key is present.
    var info: InfoGempaWrapper? { Infogempa ?? infogempa }
    
    /// Extracted earthquake events (always an array, never nil).
    var events: [EarthquakeEvent] {
        guard let wrapper = info else { return [] }
        if let single = wrapper.gempa as? EarthquakeEvent {
            return [single]
        }
        if let array = wrapper.gempaList {
            return array
        }
        return []
    }
}

struct InfoGempaWrapper: Codable {
    /// autogempa → single object, gempaterkini/gempadirasakan → array.
    /// We decode it as either via a custom decoder.
    var gempa: CodableValue?
    var gempaList: [EarthquakeEvent]?
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        // Try decoding as single object first
        if let single = try? container.decode(EarthquakeEvent.self, forKey: .gempa) {
            gempa = .single(single)
            gempaList = [single]
            return
        }
        
        // Fall back to array
        if let array = try? container.decode([EarthquakeEvent].self, forKey: .gempa) {
            gempaList = array
            if array.count == 1 {
                gempa = .single(array[0])
            } else if array.count > 1 {
                gempa = .multiple(array)
            }
            return
        }
    }
    
    enum CodableValue {
        case single(EarthquakeEvent)
        case multiple([EarthquakeEvent])
    }
    
    private enum CodingKeys: String, CodingKey {
        case gempa
    }
}

// MARK: - Earthquake Event

struct EarthquakeEvent: Codable, Identifiable, Equatable {
    /// Unique ID from DateTime.
    var id: String { DateTime ?? "\(Tanggal ?? "")-\(Jam ?? "")" }
    
    // All fields from BMKG JSON (with fallback key variants)
    let Tanggal: String?       // "12 Mei 2026"
    let tanggal: String?
    
    let Jam: String?           // "04:06:20 WIB"
    let jam: String?
    
    let DateTime: String?      // "2026-05-11T21:06:20+00:00" — use as unique ID
    
    let Coordinates: String?   // "-4.08,121.79"
    let coordinates: String?
    
    let Lintang: String?       // "4.08 LS"
    let Bujur: String?         // "121.79 BT"
    
    let Magnitude: String?     // "2.9"
    let magnitude: String?
    
    let Kedalaman: String?     // "10 km"
    let kedalaman: String?
    
    let Wilayah: String?       // "Pusat gempa berada di darat 12 km barat daya Kolaka Timur"
    let wilayah: String?
    
    let Potensi: String?       // "Tidak berpotensi tsunami"
    let potensi: String?
    
    let Dirasakan: String?     // "III Kolaka Timur, II-III Kolaka" — may be nil (gempaterkini)
    let dirasakan: String?
    
    let Shakemap: String?      // "20260512040620.mmi.jpg" — autogempa only
    
    // MARK: - Computed Parsed Values
    
    /// Parsed magnitude as Float, or nil if unparseable.
    var magnitudeValue: Float? {
        let raw = Magnitude ?? magnitude
        guard let raw = raw else { return nil }
        return Float(raw.trimmingCharacters(in: .whitespaces))
    }
    
    /// Parsed depth in km as Int, or nil.
    var depthKm: Int? {
        let raw = Kedalaman ?? kedalaman
        guard let raw = raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        let parts = trimmed.split(separator: " ")
        guard let first = parts.first else { return nil }
        return Int(first)
    }
    
    /// Parsed coordinates as (latitude, longitude) tuple, or nil.
    var coordinatePair: (lat: Double, lon: Double)? {
        let raw = Coordinates ?? coordinates
        guard let raw = raw else { return nil }
        let parts = raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        guard parts.count == 2,
              let lat = Double(parts[0]),
              let lon = Double(parts[1]) else { return nil }
        return (lat, lon)
    }
    
    /// Maximum MMI value parsed from Dirasakan string.
    var maxMMI: Int {
        let raw = Dirasakan ?? dirasakan
        return Self.parseMaxMMI(from: raw)
    }
    
    /// Whether the Potensi field indicates tsunami potential.
    var hasTsunamiPotential: Bool {
        let raw = Potensi ?? potensi
        return Self.checkTsunamiPotential(raw)
    }
    
    // MARK: - Danger Classification
    
    /// All matched danger rules for this event.
    var matchedDangerRules: [DangerRuleMatch] {
        DangerClassifier.classify(self)
    }
    
    /// Shortcut: is this event dangerous?
    var isDangerous: Bool {
        matchedDangerRules.contains(where: { $0.severity == .critical || $0.severity == .high })
    }
    
    // MARK: - Static Helpers
    
    private static let romanMap: [Character: Int] = [
        "I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000
    ]
    
    static func parseMaxMMI(from dirasakan: String?) -> Int {
        guard let dirasakan = dirasakan else { return 0 }
        
        // Find Roman numeral sequences
        let pattern = try! NSRegularExpression(pattern: "\\b[IVXLCDM]+\\b")
        let range = NSRange(dirasakan.startIndex..., in: dirasakan)
        let matches = pattern.matches(in: dirasakan, range: range)
        
        var maxVal = 0
        for match in matches {
            let numeral = String(dirasakan[Range(match.range, in: dirasakan)!])
            let val = Self.romanToInt(numeral)
            if val > maxVal { maxVal = val }
        }
        return maxVal
    }
    
    private static func romanToInt(_ s: String) -> Int {
        var result = 0
        var prev = 0
        for char in s.reversed() {
            let val = romanMap[char] ?? 0
            if val < prev {
                result -= val
            } else {
                result += val
                prev = val
            }
        }
        return result
    }
    
    static func checkTsunamiPotential(_ potensi: String?) -> Bool {
        guard let potensi = potensi else { return false }
        let lower = potensi.lowercased()
        let hasPositive = lower.contains("tsunami") && lower.contains("berpotensi")
        let isNegated = lower.contains("tidak")
        return hasPositive && !isNegated
    }
}

// MARK: - Danger Classification

enum DangerSeverity: String, Codable {
    case critical = "CRITICAL"
    case high = "HIGH"
    case medium = "MEDIUM"
    case info = "INFO"
}

struct DangerRuleMatch: Identifiable, Equatable {
    let id: String
    let severity: DangerSeverity
    let ruleName: String
    let tindakanAlarm: String
}

struct DangerClassifier {
    typealias RuleCheck = (EarthquakeEvent, Double?) -> Bool

    struct Rule {
        let id: String
        let ruleName: String
        let severity: DangerSeverity
        let tindakanAlarm: String
        let check: RuleCheck
    }

    static let rules: [Rule] = [
        Rule(id: "local_danger", ruleName: "Local Danger", severity: .critical, tindakanAlarm: "Sirine Utama Berbunyi, Evakuasi Mandiri.") { event, dist in
            guard let mag = event.magnitudeValue, let depth = event.depthKm, let d = dist else { return false }
            return mag >= 5.0 && depth < 70 && d <= 50
        },
        Rule(id: "regional_major", ruleName: "Regional Major", severity: .high, tindakanAlarm: "Alarm Keras, Bersiap Siaga.") { event, dist in
            guard let mag = event.magnitudeValue, let depth = event.depthKm, let d = dist else { return false }
            return mag >= 6.0 && depth < 70 && d <= 150
        },
        Rule(id: "mega_earthquake", ruleName: "Mega Earthquakes", severity: .high, tindakanAlarm: "Alarm Keras, potensi ayunan kuat.") { event, dist in
            guard let mag = event.magnitudeValue, let d = dist else { return false }
            return mag >= 7.0 && d <= 400
        },
        Rule(id: "distant_felt", ruleName: "Distant Felt", severity: .medium, tindakanAlarm: "Notifikasi HP / Buzzer Pendek.") { event, dist in
            guard let mag = event.magnitudeValue, let depth = event.depthKm, let d = dist else { return false }
            return mag >= 5.5 && depth < 70 && d >= 150 && d <= 300
        },
        Rule(id: "deep_close", ruleName: "Deep & Close", severity: .medium, tindakanAlarm: "Notifikasi / Alarm Lemah.") { event, dist in
            guard let mag = event.magnitudeValue, let depth = event.depthKm, let d = dist else { return false }
            return mag >= 6.0 && depth >= 70 && d <= 100
        },
        Rule(id: "tsunami_potential", ruleName: "Tsunami Potential", severity: .critical, tindakanAlarm: "Sirine Tsunami, Evakuasi ke Tempat Tinggi.") { event, _ in
            event.hasTsunamiPotential
        },
    ]

    static func classify(_ event: EarthquakeEvent, distanceKm: Double? = nil) -> [DangerRuleMatch] {
        rules.compactMap { rule in
            rule.check(event, distanceKm) ? DangerRuleMatch(id: rule.id, severity: rule.severity, ruleName: rule.ruleName, tindakanAlarm: rule.tindakanAlarm) : nil
        }
    }
}