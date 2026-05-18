import SwiftUI

// MARK: - Data

struct TutorialStep {
    let icon: String
    let iconColor: Color
    let title: String
    let description: String
}

enum TutorialContent {
    static let monitor: [TutorialStep] = [
        TutorialStep(
            icon: "antenna.radiowaves.left.and.right",
            iconColor: .prometheusBlue,
            title: "BMKG Monitor",
            description: "Prometheus connects to Indonesia's official earthquake agency (BMKG) and polls for real-time seismic events, keeping you informed around the clock."
        ),
        TutorialStep(
            icon: "alarm.fill",
            iconColor: .red,
            title: "Danger Status Banner",
            description: "The banner at the top changes colour with threat level — blue means no active alert, orange is elevated, red means danger. Act immediately when it turns red."
        ),
        TutorialStep(
            icon: "waveform.badge.magnifyingglass",
            iconColor: .prometheusBlue,
            title: "Latest BMKG Event",
            description: "Shows magnitude, location, depth, tsunami potential, and timestamp of the most recent earthquake. Tap REFRESH BMKG to poll for the latest data at any time."
        ),
        TutorialStep(
            icon: "network",
            iconColor: .prometheusBlue,
            title: "Local Injection",
            description: "Tap the LOCAL INJECTION card to connect a PC running the local injector script. This lets you simulate earthquake events for testing without a real seismic event."
        ),
        TutorialStep(
            icon: "moon",
            iconColor: .yellow,
            title: "Dark / Bright Mode",
            description: "Tap the sun or moon icon in the top-left on any tab to switch display modes. Your preference is saved automatically and applies across the entire app."
        ),
    ]

    static let evacuate: [TutorialStep] = [
        TutorialStep(
            icon: "map.fill",
            iconColor: .prometheusBlue,
            title: "Evacuation Map",
            description: "The live map shows the earthquake epicentre (red target) and a danger radius circle. Your location appears as a blue location pin."
        ),
        TutorialStep(
            icon: "arrow.triangle.turn.up.right.circle.fill",
            iconColor: .prometheusBlue,
            title: "Google Directions Route",
            description: "When an earthquake is detected, Prometheus queries Google Directions to find the fastest driving route out of the danger zone. The route is drawn as a blue line on the map."
        ),
        TutorialStep(
            icon: "checkmark.shield.fill",
            iconColor: .green,
            title: "Safe Zone Destination",
            description: "The green shield marks the evacuation destination — a point safely outside the danger radius. The map automatically zooms to frame the entire route."
        ),
        TutorialStep(
            icon: "map",
            iconColor: .green,
            title: "Navigate in Apple Maps",
            description: "Tap NAVIGATE to open Apple Maps with real turn-by-turn navigation to the safe zone. Tap VIEW ROUTE for in-app directions, travel times, and step-by-step instructions."
        ),
        TutorialStep(
            icon: "ruler",
            iconColor: .prometheusBlue,
            title: "Routing Details",
            description: "Expand the ROUTING DETAILS accordion to see your coordinates, the epicentre location, danger radius, and estimated travel times on foot, bike, motorbike, and by car."
        ),
    ]

    static let talk: [TutorialStep] = [
        TutorialStep(
            icon: "mic.fill",
            iconColor: .red,
            title: "Hold to Speak",
            description: "Hold the mic button and ask any question aloud — about your surroundings, what to do, or emergency guidance. Release to send."
        ),
        TutorialStep(
            icon: "camera.viewfinder",
            iconColor: .prometheusBlue,
            title: "Auto Camera Capture",
            description: "When you release the mic, the camera automatically captures a photo of your surroundings. Gemma sees both your question and the image to give a more relevant answer."
        ),
        TutorialStep(
            icon: "waveform.and.mic",
            iconColor: .prometheusBlue,
            title: "Spoken Response",
            description: "Gemma's answer is read aloud automatically after analysis — ideal when your hands are occupied or you need information quickly in an emergency."
        ),
        TutorialStep(
            icon: "square.and.arrow.down.fill",
            iconColor: .prometheusBlue,
            title: "Download Gemma 4 First",
            description: "The Talk tab requires Gemma 4 to be downloaded (~2.4 GB). Download it in the Assistant tab. After that, Talk works fully offline."
        ),
    ]

    static let assistant: [TutorialStep] = [
        TutorialStep(
            icon: "bubble.left.and.bubble.right.fill",
            iconColor: .prometheusBlue,
            title: "Survival Assistant",
            description: "Powered by Gemma 4 running entirely on-device. Ask anything about first aid, water sourcing, shelter, evacuation routes, or Indonesia-specific hazards — fully offline."
        ),
        TutorialStep(
            icon: "square.and.arrow.down.fill",
            iconColor: .prometheusBlue,
            title: "Download Gemma 4",
            description: "The AI model is ~2.4 GB and downloaded once over Wi-Fi. After that, the assistant works with no internet connection — ideal for disaster scenarios when networks are down."
        ),
        TutorialStep(
            icon: "camera.viewfinder",
            iconColor: .prometheusBlue,
            title: "Vision Mode",
            description: "Switch to VISION to use the camera. Point it at your surroundings — the app detects people, reads visible text (signs, exits), and describes the scene, spoken aloud automatically."
        ),
        TutorialStep(
            icon: "line.3.horizontal",
            iconColor: .prometheusBlue,
            title: "Conversation History",
            description: "Tap the menu icon (top-left) to browse past conversations, switch between them, or start a new one. All conversations are saved automatically on your device."
        ),
        TutorialStep(
            icon: "waveform",
            iconColor: .prometheusBlue,
            title: "Voice Readout",
            description: "Tap SPEAK below any assistant response to hear it read aloud. Especially useful when your hands are occupied or visibility is limited during an emergency."
        ),
    ]
}

// MARK: - Overlay

struct TutorialOverlay: View {
    let tabName: String
    let steps: [TutorialStep]
    let onDismiss: () -> Void

    @AppStorage("isDarkMode") private var isDarkMode = false
    @State private var page = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { onDismiss() }

            VStack(spacing: 0) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("HOW TO USE")
                            .inter(11, weight: .bold)
                            .foregroundColor(.prometheusBlue.opacity(0.7))
                        Text(tabName.uppercased())
                            .inter(12, weight: .bold)
                            .foregroundColor(.prometheusBlue)
                    }
                    Spacer()
                    Button(action: onDismiss) {
                        Text("SKIP")
                            .inter(12, weight: .bold)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .background(Color.prometheusBlue.opacity(0.08))
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 4)

                // Paged cards
                TabView(selection: $page) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { idx, step in
                        TutorialStepCard(step: step, stepNumber: idx + 1, total: steps.count)
                            .tag(idx)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(height: 280)
                .animation(.easeInOut, value: page)

                // Dot indicators
                HStack(spacing: 8) {
                    ForEach(0..<steps.count, id: \.self) { i in
                        Capsule()
                            .fill(i == page ? Color.prometheusBlue : Color.prometheusBlue.opacity(0.2))
                            .frame(width: i == page ? 20 : 6, height: 6)
                            .animation(.spring(response: 0.3), value: page)
                    }
                }
                .padding(.bottom, 14)

                // Action button
                Button(action: {
                    if page < steps.count - 1 {
                        withAnimation(.easeInOut(duration: 0.25)) { page += 1 }
                    } else {
                        onDismiss()
                    }
                }) {
                    Text(page < steps.count - 1 ? "NEXT →" : "GOT IT")
                        .inter(12, weight: .bold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.prometheusBlue.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.6), lineWidth: 1))
                        .foregroundColor(.prometheusBlue)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            .background(Color(UIColor.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.prometheusBlue.opacity(0.25), lineWidth: 1))
            .shadow(color: .black.opacity(0.4), radius: 24, x: 0, y: 8)
            .padding(.horizontal, 24)
            .preferredColorScheme(isDarkMode ? .dark : .light)
        }
    }
}

// MARK: - Step card

private struct TutorialStepCard: View {
    let step: TutorialStep
    let stepNumber: Int
    let total: Int

    var body: some View {
        VStack(spacing: 18) {
            ZStack {
                Circle()
                    .fill(step.iconColor.opacity(0.12))
                    .frame(width: 90, height: 90)
                Circle()
                    .strokeBorder(step.iconColor.opacity(0.25), lineWidth: 1)
                    .frame(width: 90, height: 90)
                Image(systemName: step.icon)
                    .font(.system(size: 36))
                    .foregroundColor(step.iconColor)
            }

            VStack(spacing: 8) {
                Text("\(stepNumber) / \(total)")
                    .inter(11, weight: .bold)
                    .foregroundColor(.prometheusBlue.opacity(0.5))

                Text(step.title)
                    .inter(15, weight: .bold)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)

                Text(step.description)
                    .inter(12)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
            }
        }
        .padding(.horizontal, 20)
    }
}
