//
//  ContentView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 01/05/26.
//

import SwiftUI

struct ContentView: View {
    @State private var inference = InferenceManager()
    @AppStorage("isDarkMode") private var isDarkMode = false

    var body: some View {
        TabView {
            MonitorView()
                .tabItem { Label("MONITOR", systemImage: "antenna.radiowaves.left.and.right") }

            MapView()
                .tabItem { Label("EVACUATE", systemImage: "map") }

            AssistantView()
                .tabItem { Label("SURVIVAL", systemImage: "bubble.left.and.bubble.right") }

            VisionView()
                .tabItem { Label("VISION", systemImage: "camera.viewfinder") }
        }
        .environment(inference)
        .tint(.prometheusBlue)
        .preferredColorScheme(isDarkMode ? .dark : .light)
    }
}
