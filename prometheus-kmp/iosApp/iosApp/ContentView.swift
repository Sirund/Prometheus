import SwiftUI

struct ContentView: View {
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
        .tint(.prometheusBlue)
        .preferredColorScheme(.dark)
    }
}
