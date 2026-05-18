import SwiftUI

struct ContentView: View {
    @State private var inference = InferenceManager()
    @State private var isDarkMode = true
    @State private var showHelp = false

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
            HStack(spacing: 8) {
                Button(action: { showHelp = true }) {
                    Image(systemName: "questionmark.circle")
                        .font(.title3)
                        .foregroundColor(.prometheusBlue)
                        .padding(12)
                        .background(Color.cardBackground)
                        .clipShape(Circle())
                }
                Button(action: { isDarkMode.toggle() }) {
                    Image(systemName: isDarkMode ? "sun.max" : "moon")
                        .font(.title3)
                        .foregroundColor(.prometheusBlue)
                        .padding(12)
                        .background(Color.cardBackground)
                        .clipShape(Circle())
                }
            }
            .padding(.top, 8)
            .padding(.trailing, 12)
        }
        .alert("How to Use", isPresented: $showHelp) {
            Button("GOT IT", role: .cancel) {}
        } message: {
            Text(
                "MONITOR — Real-time BMKG earthquake data, weather, and danger alerts.\n\n" +
                "EVACUATE — Live evacuation map with epicentre, danger radius, and routing.\n\n" +
                "SURVIVAL — Offline AI assistant powered by Gemma 4 for survival guidance.\n\n" +
                "VISION — Camera-based accessibility mode with voice AI analysis."
            )
        }
    }
}
