import SwiftUI

struct ContentView: View {
    @State private var inference = InferenceManager()
    @State private var isDarkMode = true

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
        .overlay(alignment: .topTrailing) {
            Button(action: { isDarkMode.toggle() }) {
                Image(systemName: isDarkMode ? "sun.max" : "moon")
                    .font(.title3)
                    .foregroundColor(.prometheusBlue)
                    .padding(12)
                    .background(Color.cardBackground)
                    .clipShape(Circle())
            }
            .padding(.top, 8)
            .padding(.trailing, 12)
        }
    }
}
