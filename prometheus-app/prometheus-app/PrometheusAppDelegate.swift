import UserNotifications

final class PrometheusAppDelegate: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    let pollingService = BMKGPollingService()

    override init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        Task { @MainActor in self.pollingService.start() }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
