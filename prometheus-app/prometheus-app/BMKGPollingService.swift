import SwiftUI
import UserNotifications
import AVFoundation
import CoreLocation

// MARK: - Weather and nowcast models

struct WeatherInfo {
    let temperature: String
    let humidity: String
    let windSpeed: String
    let windDirection: String
    let weatherDesc: String
    let visibility: String
    let province: String

    static let empty = WeatherInfo(
        temperature: "--", humidity: "--", windSpeed: "--",
        windDirection: "--", weatherDesc: "--", visibility: "--", province: ""
    )

    static func adm4ForLocation(lat: Double?, lon: Double?) -> String {
        guard let lat, let lon else { return defaultAdm4 }
        let provinces: [(lat: Double, lon: Double, adm4: String)] = [
            (-6.2088,  106.8456, "31.71.01.1001"),  // DKI Jakarta
            (-6.9147,  107.6098, "32.73.01.1001"),  // Jawa Barat
            (-7.1500,  110.1333, "33.74.01.1001"),  // Jawa Tengah
            (-7.7956,  110.3695, "34.71.01.1001"),  // DI Yogyakarta
            (-7.5361,  112.2384, "35.78.01.1001"),  // Jawa Timur
            (-8.3405,  115.0920, "51.71.01.1001"),  // Bali
            ( 2.1154,   99.5451, "12.71.01.1001"),  // Sumatera Utara
            (-0.7399,  100.3450, "13.71.01.1001"),  // Sumatera Barat
            (-5.4500,  105.2667, "18.71.01.1001"),  // Lampung
            (-4.0000,  120.0000, "73.71.01.1001"),  // Sulawesi Selatan
            ( 0.5000,  116.5000, "64.71.01.1001"),  // Kalimantan Timur
            (-8.5833,  116.1167, "52.71.01.1001"),  // Nusa Tenggara Barat
            (-4.0000,  138.0000, "93.71.01.1001"),  // Papua
        ]
        return provinces.min(by: {
            pow($0.lat - lat, 2) + pow($0.lon - lon, 2) < pow($1.lat - lat, 2) + pow($1.lon - lon, 2)
        })?.adm4 ?? defaultAdm4
    }

    static let defaultAdm4 = "31.71.01.1001"
}

struct NowcastAlert: Identifiable {
    let title: String
    let description: String
    let link: String
    let pubDate: String
    let guid: String

    var id: String { guid.isEmpty ? link : guid }
    var summary: String { description.isEmpty ? title : description }

    var isBadWeather: Bool {
        let keywords = ["hujan lebat", "hujan petir", "hujan deras", "angin kencang",
                        "angin puting beliung", "gelombang tinggi", "banjir",
                        "tanah longsor", "badai", "cuaca ekstrem"]
        let combined = "\(title) \(description)".lowercased()
        return keywords.contains { combined.contains($0) }
    }

    var eventType: String {
        let keywords = ["hujan lebat", "hujan petir", "angin kencang", "angin puting beliung",
                        "gelombang tinggi", "banjir", "tanah longsor", "badai", "cuaca ekstrem"]
        let combined = "\(title) \(description)".lowercased()
        return keywords.first { combined.contains($0) } ?? "Peringatan Cuaca"
    }

    var intensity: String {
        let e = eventType
        return e.prefix(1).uppercased() + e.dropFirst()
    }

    var alertDate: String {
        capture(#"pada\s+(\d{1,2}\s+\w+\s+\d{4})"#, in: description)
            ?? capture(#"\d{1,2}\s+\w+\s+\d{4}"#, in: pubDate)
            ?? "--"
    }

    var alertTime: String {
        capture(#"pada\s+\d{1,2}\s+\w+\s+\d{4},\s*(\d{2}[:.]\d{2})"#, in: description)
            ?? capture(#"\d{2}:\d{2}"#, in: pubDate)
            ?? "--"
    }

    var estimatedEnd: String {
        guard let t = capture(#"(?i)berlangsung\s+hingga\s+\d{1,2}\s+\w+\s+\d{4},\s*(\d{2}[:.]\d{2})"#, in: description) else { return "--" }
        return "~ \(t)"
    }

    var potential: String {
        capture(#"(?i)berpotensi\s+menimbulkan\s+dampak\s+berupa\s+(.+?)(?:\.|$)"#, in: description)?
            .trimmingCharacters(in: .whitespaces) ?? "--"
    }

    var specificLocation: String {
        capture(#"(?i)khususnya\s+di\s+(.+?)(?:\.|$)"#, in: description)?
            .trimmingCharacters(in: .whitespaces) ?? "--"
    }

    var provinceName: String {
        for sep in [" - ", " – ", "– ", "- "] {
            if let r = title.range(of: sep) {
                let after = String(title[r.upperBound...]).trimmingCharacters(in: .whitespaces)
                if !after.isEmpty { return after }
            }
        }
        return "--"
    }

    private func capture(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return nil }
        let ns = text as NSString
        guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let idx = match.numberOfRanges > 1 ? 1 : 0
        let r = match.range(at: idx)
        guard r.location != NSNotFound else { return nil }
        return ns.substring(with: r)
    }
}

// MARK: - Location delegate (NSObject, called on main thread since manager is created there)

private final class LocationDelegate: NSObject, CLLocationManagerDelegate, @unchecked Sendable {
    var onLocation: ((Double, Double) -> Void)?

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        onLocation?(loc.coordinate.latitude, loc.coordinate.longitude)
    }
}

// MARK: - BMKG polling service

@Observable
@MainActor
final class BMKGPollingService {
    private var timer: Timer?
    private var lastEventId: String?
    private let synth = AVSpeechSynthesizer()
    private let locationDelegate = LocationDelegate()
    private var locationManager: CLLocationManager?
    private var userLat: Double?
    private var userLon: Double?

    var isMonitoring = false
    var latestEvent: String?
    var latestEarthquakeEvent: EarthquakeEvent?
    var lastChecked: String?
    var dangerLevel: Int = 0
    var latestWeather: WeatherInfo = .empty
    var nowcastAlerts: [NowcastAlert] = []

    var injectionEnabled = false
    var injectionIp = ""
    var injectionPort = 8080

    var currentLocation: (lat: Double, lon: Double)? {
        guard let lat = userLat, let lon = userLon else { return nil }
        return (lat, lon)
    }

    init() {
        locationDelegate.onLocation = { [weak self] lat, lon in
            Task { @MainActor [weak self] in
                self?.userLat = lat
                self?.userLon = lon
            }
        }
        let manager = CLLocationManager()
        manager.delegate = locationDelegate
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        locationManager = manager
    }

    func start() {
        isMonitoring = true
        checkNow()
        timer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.checkNow() }
        }
    }

    func stop() {
        isMonitoring = false
        timer?.invalidate()
        timer = nil
    }

    func checkNow() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        lastChecked = formatter.string(from: Date())

        Task {
            do {
                let urlString = injectionEnabled && !injectionIp.isEmpty
                    ? "http://\(injectionIp):\(injectionPort)/autogempa.json"
                    : "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json"
                guard let url = URL(string: urlString) else { return }
                let (data, _) = try await URLSession.shared.data(from: url)
                let decoded = try JSONDecoder().decode(BMKGResponse.self, from: data)
                if let latest = decoded.events.first {
                    handleEvent(latest)
                }
            } catch {
                latestEvent = "Poll error: \(error.localizedDescription)"
            }
        }
        Task { await fetchWeather() }
        Task { await fetchNowcast() }
    }

    private func fetchWeather() async {
        let adm4 = WeatherInfo.adm4ForLocation(lat: userLat, lon: userLon)
        guard let url = URL(string: "https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=\(adm4)") else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let dataArr = json["data"] as? [[String: Any]],
                  let first = dataArr.first,
                  let cuaca = first["cuaca"] as? [[[String: Any]]],
                  let today = cuaca.first,
                  let current = today.first
            else { return }
            let provinsi = (json["lokasi"] as? [String: Any])?["provinsi"] as? String ?? ""
            func str(_ key: String) -> String {
                if let s = current[key] as? String { return s }
                if let n = current[key] as? NSNumber { return n.stringValue }
                return "--"
            }
            latestWeather = WeatherInfo(
                temperature: str("t"),
                humidity: str("hu"),
                windSpeed: str("ws"),
                windDirection: str("wd"),
                weatherDesc: str("weather_desc"),
                visibility: str("vs_text"),
                province: provinsi
            )
        } catch {}
    }

    private func fetchNowcast() async {
        guard let url = URL(string: "https://www.bmkg.go.id/alerts/nowcast/id") else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let xml = String(data: data, encoding: .utf8) ?? ""
            let alerts = parseNowcastRss(xml)
            nowcastAlerts = alerts
        } catch {}
    }

    private func parseNowcastRss(_ xml: String) -> [NowcastAlert] {
        var alerts: [NowcastAlert] = []
        let parts = xml.components(separatedBy: "<item>")
        for part in parts.dropFirst() {
            let itemXml = part.components(separatedBy: "</item>").first ?? ""
            guard let title = extractXmlTag(itemXml, "title"),
                  let desc  = extractXmlTag(itemXml, "description") else { continue }
            alerts.append(NowcastAlert(
                title: title,
                description: desc,
                link:    extractXmlTag(itemXml, "link")    ?? "",
                pubDate: extractXmlTag(itemXml, "pubDate") ?? "",
                guid:    extractXmlTag(itemXml, "guid")    ?? ""
            ))
        }
        return alerts
    }

    private func extractXmlTag(_ xml: String, _ tag: String) -> String? {
        let open = "<\(tag)>", close = "</\(tag)>"
        guard let start = xml.range(of: open)?.upperBound,
              let end   = xml.range(of: close, range: start..<xml.endIndex)?.lowerBound
        else { return nil }
        let raw = String(xml[start..<end])
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "&amp;",  with: "&")
            .replacingOccurrences(of: "&lt;",   with: "<")
            .replacingOccurrences(of: "&gt;",   with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&apos;", with: "'")
        return raw.isEmpty ? nil : raw
    }

    private func handleEvent(_ event: EarthquakeEvent) {
        guard let eventId = event.DateTime, eventId != lastEventId else { return }
        lastEventId = eventId

        latestEarthquakeEvent = event
        let mag = event.magnitudeValue.map { "\($0)" } ?? "--"
        latestEvent = "M \(mag) — \(event.Wilayah ?? "Unknown")"

        let distance: Double? = {
            guard let lat = userLat, let lon = userLon,
                  let coords = event.coordinatePair else { return nil }
            return GeoUtils.distanceKm(lat1: lat, lon1: lon, lat2: coords.lat, lon2: coords.lon)
        }()

        let sev = classifySeverity(event: event, distanceKm: distance)
        switch sev {
        case .critical, .high:
            dangerLevel = 2
            triggerAlarm(event: event)
        case .medium:
            dangerLevel = 1
            triggerMediumAlert(event: event)
        case .info:
            dangerLevel = 0
        }
    }

    // MARK: - Severity classification

    private enum Severity { case critical, high, medium, info }

    private func classifySeverity(event: EarthquakeEvent, distanceKm: Double?) -> Severity {
        let mag = event.magnitudeValue ?? 0
        let depth = event.depthKm ?? 999

        if event.hasTsunamiPotential { return .critical }
        if let d = distanceKm {
            if mag >= 5 && depth < 70 && d <= 50   { return .critical }
            if mag >= 6 && depth < 70 && d <= 150  { return .high }
            if mag >= 7 && d <= 400                 { return .high }
            if mag >= 5.5 && depth < 70 && d >= 150 && d <= 300 { return .medium }
            if mag >= 6 && depth >= 70 && d <= 100 { return .medium }
        }
        return .info
    }

    // MARK: - Alarm triggers

    private func triggerAlarm(event: EarthquakeEvent) {
        let mag = event.magnitudeValue.map { "\($0)" } ?? "--"
        let loc = event.Wilayah ?? "Unknown location"
        let depth = event.Kedalaman ?? "?"
        let time = "\(event.Tanggal ?? "") \(event.Jam ?? "")".trimmingCharacters(in: .whitespaces)
        let tsunami = event.hasTsunamiPotential ? " ⚠️ TSUNAMI POTENTIAL" : ""

        let content = UNMutableNotificationContent()
        content.title = "🚨 EARTHQUAKE — M\(mag)"
        content.body = "\(loc)\nDepth: \(depth) | \(time)\(tsunami)"
        content.sound = .defaultCritical
        content.interruptionLevel = .critical

        UNUserNotificationCenter.current().add(
            UNNotificationRequest(
                identifier: "earthquake_\(lastEventId ?? UUID().uuidString)",
                content: content, trigger: nil
            )
        )

        let text = EmergencyBriefingFormatter.buildBriefingText(event: event)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "id-ID")
        utterance.rate = 0.5
        synth.speak(utterance)
    }

    private func triggerMediumAlert(event: EarthquakeEvent) {
        let mag = event.magnitudeValue.map { "\($0)" } ?? "--"
        let loc = event.Wilayah ?? "Unknown location"
        let depth = event.Kedalaman ?? "?"

        let content = UNMutableNotificationContent()
        content.title = "Gempa Terdeteksi — M\(mag)"
        content.body = "\(loc) — Depth: \(depth)\nTerasa jauh, tidak perlu panik."
        content.sound = .default

        UNUserNotificationCenter.current().add(
            UNNotificationRequest(
                identifier: "medium_\(lastEventId ?? UUID().uuidString)",
                content: content, trigger: nil
            )
        )
    }
}
