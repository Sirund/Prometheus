import SwiftUI
import shared
import UserNotifications
import AVFoundation
import CoreLocation

@Observable
class BMKGPollingService: NSObject, CLLocationManagerDelegate {
    private var timer: Timer?
    private var lastEventId: String?
    private var weatherTimer: Timer?
    private var nowcastTimer: Timer?
    private let synth = AVSpeechSynthesizer()
    private let locationManager = CLLocationManager()
    private let weatherClient = BMKGWeatherClient()
    var currentLocation: UserLocation?

    var isMonitoring = false
    var latestEvent: String?
    var latestEarthquakeEvent: EarthquakeEvent?
    var lastChecked: String?
    var dangerLevel: Int = 0
    var weatherInfo = shared.WeatherInfo(temperature: "--", humidity: "--", windSpeed: "--", windDirection: "--", weatherDesc: "Loading...", visibility: "--")
    var nowcastAlerts: [shared.NowcastAlert] = []

    var injectionEnabled = false
    var injectionIp = ""
    var injectionPort = 8080

    private var injectionBaseUrl: String? {
        guard injectionEnabled, !injectionIp.isEmpty else { return nil }
        return "http://\(injectionIp):\(injectionPort)"
    }

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let loc = locations.last {
            currentLocation = UserLocation(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude)
        }
    }

    func start() {
        isMonitoring = true
        checkNow()
        timer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            self?.checkNow()
        }
        startWeatherPolling()
        startNowcastPolling()
    }

    func stop() {
        isMonitoring = false
        timer?.invalidate()
        timer = nil
        weatherTimer?.invalidate()
        weatherTimer = nil
        nowcastTimer?.invalidate()
        nowcastTimer = nil
    }

    func checkNow() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        lastChecked = formatter.string(from: Date())

        Task {
            do {
                let baseUrl = injectionBaseUrl as NSString?
                let client = BMKGClient(baseUrlOverride: baseUrl)
                let events = try await client.fetchAutogempa()
                if let latest = events.first {
                    await handleEvent(latest)
                }
            } catch {
                await MainActor.run {
                    latestEvent = "Poll error: \(error.localizedDescription)"
                }
            }
        }
        Task { await pollWeather() }
        Task { await pollNowcast() }
    }

    // MARK: - Weather Polling

    private func startWeatherPolling() {
        Task { await pollWeather() }
        weatherTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { [weak self] in await self?.pollWeather() }
        }
    }

    private func pollWeather() async {
        do {
            let info = try await weatherClient.fetchWeatherForecast(adm4: "31.71.01.1001")
            await MainActor.run { weatherInfo = info }
        } catch {
            print("Weather poll error: \(error)")
        }
    }

    // MARK: - Nowcast Polling

    private func startNowcastPolling() {
        Task { await pollNowcast() }
        nowcastTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { [weak self] in await self?.pollNowcast() }
        }
    }

    private var knownAlertLinks: Set<String> = []

    private func pollNowcast() async {
        do {
            let alerts = try await weatherClient.fetchNowcastAlerts()
            await MainActor.run {
                nowcastAlerts = alerts
                let badAlerts = alerts.filter { $0.isBadWeather }
                let newBad = badAlerts.filter { !knownAlertLinks.contains($0.link) }
                if !newBad.isEmpty {
                    knownAlertLinks.formUnion(newBad.map { $0.link })
                    for alert in newBad {
                        sendNowcastNotification(alert: alert)
                    }
                }
            }
        } catch {
            print("Nowcast poll error: \(error)")
        }
    }

    private func sendNowcastNotification(alert: shared.NowcastAlert) {
        let content = UNMutableNotificationContent()
        content.title = "\u{26A0}\u{FE0F} Weather Warning — \(alert.eventType)"
        content.body = alert.summary
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "nowcast_\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    @MainActor
    private func handleEvent(_ event: EarthquakeEvent) {
        guard let eventId = event.dateTime, eventId != lastEventId else { return }
        lastEventId = eventId

        latestEarthquakeEvent = event
        latestEvent = "M \(event.magnitudeValue) — \(event.wilayah_)"

        let distance: Double? = {
            guard let loc = currentLocation, let coords = event.coordinatePair else { return nil }
            let d = GeoUtils().distanceKm(
                lat1: loc.latitude, lon1: loc.longitude,
                lat2: coords.first.doubleValue, lon2: coords.second.doubleValue
            )
            return d.doubleValue
        }()

        let sev = classifySeverity(event: event, distanceKm: distance)

        switch sev {
        case .critical, .high:
            dangerLevel = 2
            triggerAlarm(event: event)
        case .medium:
            dangerLevel = 1
            triggerMediumAlert(event: event)
        default:
            dangerLevel = 0
        }
    }

    private func classifySeverity(event: EarthquakeEvent, distanceKm: Double?) -> DangerSeverity {
        let mag = event.magnitudeValue?.floatValue ?? 0
        let depth = event.depthKm?.intValue ?? 999

        if event.hasTsunamiPotential { return .critical }
        if let d = distanceKm {
            if mag >= 5 && depth < 70 && d <= 50 { return .critical }
            if mag >= 6 && depth < 70 && d <= 150 { return .high }
            if mag >= 7 && d <= 400 { return .high }
            if mag >= 5.5 && depth < 70 && d >= 150 && d <= 300 { return .medium }
            if mag >= 6 && depth >= 70 && d <= 100 { return .medium }
        }
        return .info
    }

    private func triggerAlarm(event: EarthquakeEvent) {
        let mag = event.magnitudeValue
        let loc = event.wilayah_ ?? "Unknown location"
        let depth = event.kedalaman_ ?? "?"
        let time = "\(event.tanggal_ ?? "") \(event.jam_ ?? "")".trimmingCharacters(in: .whitespaces)
        let tsunami = event.hasTsunamiPotential ? " ⚠️ TSUNAMI POTENTIAL" : ""
        let felt = event.dirasakan_?.isEmpty == false ? "\nFelt: \(event.dirasakan_!)" : ""

        let content = UNMutableNotificationContent()
        content.title = "\u{1F6A8} EARTHQUAKE DETECTED — M\(mag)"
        content.body = "\(loc)\nDepth: \(depth) | \(time)\(tsunami)\(felt)"
        content.sound = .defaultCritical
        content.interruptionLevel = .critical

        let request = UNNotificationRequest(
            identifier: "earthquake_\(lastEventId ?? UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)

        let text = EmergencyBriefingFormatter.shared.buildBriefingText(event: event)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "id-ID")
        utterance.rate = 0.5
        synth.speak(utterance)
    }

    private func triggerMediumAlert(event: EarthquakeEvent) {
        let mag = event.magnitudeValue
        let loc = event.wilayah_ ?? "Unknown location"
        let depth = event.kedalaman_ ?? "?"

        let content = UNMutableNotificationContent()
        content.title = "Gempa Terdeteksi — M\(mag)"
        content.body = "\(loc) — Depth: \(depth)\nTerasa jauh, tidak perlu panik."
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "medium_\(lastEventId ?? UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }
}
