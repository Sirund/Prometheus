//
//  prometheus_appApp.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 01/05/26.
//

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
