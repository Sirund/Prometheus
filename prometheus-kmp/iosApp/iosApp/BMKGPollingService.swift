import SwiftUI
import shared
import UserNotifications
import AVFoundation

@Observable
class BMKGPollingService {
    private var timer: Timer?
    private var lastEventId: String?
    private let synth = AVSpeechSynthesizer()

    var isMonitoring = false
    var latestEvent: String?
    var lastChecked: String?

    func start() {
        isMonitoring = true
        checkNow()
        timer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            self?.checkNow()
        }
    }

    func stop() {
        isMonitoring = false
        timer?.invalidate()
        timer = nil
    }

    private func checkNow() {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        lastChecked = formatter.string(from: Date())

        Task {
            do {
                let client = BMKGClient()
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
    }

    @MainActor
    private func handleEvent(_ event: EarthquakeEvent) {
        guard let eventId = event.dateTime, eventId != lastEventId else { return }
        lastEventId = eventId

        latestEvent = "M \(event.magnitudeValue) — \(event.wilayah_)"

        if event.isDangerous {
            triggerAlarm(event: event)
        }
    }

    private func triggerAlarm(event: EarthquakeEvent) {
        let mag = event.magnitudeValue
        let loc = event.wilayah_ ?? "Unknown location"

        let content = UNMutableNotificationContent()
        content.title = "🚨 EARTHQUAKE DETECTED"
        content.body = "M \(mag) — \(loc)"
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
}
