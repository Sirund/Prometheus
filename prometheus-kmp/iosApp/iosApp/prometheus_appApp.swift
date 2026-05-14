import SwiftUI

@main
struct prometheus_appApp: App {
    @State private var delegate = PrometheusAppDelegate()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(delegate.pollingService)
        }
    }
}
