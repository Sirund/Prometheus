import SwiftUI
import UserNotifications
import AVFoundation
import CoreLocation

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
                let url = URL(string: "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json")!
                let (data, _) = try await URLSession.shared.data(from: url)
                let decoded = try JSONDecoder().decode(BMKGResponse.self, from: data)
                if let latest = decoded.events.first {
                    handleEvent(latest)
                }
            } catch {
                latestEvent = "Poll error: \(error.localizedDescription)"
            }
        }
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
