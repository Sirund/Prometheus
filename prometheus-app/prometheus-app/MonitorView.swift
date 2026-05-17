import SwiftUI

struct MonitorView: View {
    @Environment(BMKGPollingService.self) private var pollingService
    @AppStorage("isDarkMode") private var isDarkMode = false
    @AppStorage("tutorialSeen_monitor") private var tutorialSeen = false
    @State private var showInjectionSheet = false
    @State private var showTutorial = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        DangerStatusBanner(level: dangerLevel)

                        SectionHeader(title: "LATEST BMKG EVENT")
                        if let event = pollingService.latestEarthquakeEvent {
                            BMKGEventCard(
                                magnitude: event.magnitudeValue.map { "\($0)" } ?? "--",
                                location: event.Wilayah ?? "--",
                                depth: event.Kedalaman ?? "--",
                                felt: event.Dirasakan ?? "--",
                                potential: event.Potensi ?? "--",
                                timestamp: "\(event.Tanggal ?? "") \(event.Jam ?? "")".trimmingCharacters(in: .whitespaces)
                            )
                        } else {
                            BMKGEventCard(
                                magnitude: "--",
                                location: "Waiting for data...",
                                depth: "--",
                                felt: "--",
                                potential: "--",
                                timestamp: pollingService.lastChecked ?? "Not yet refreshed"
                            )
                        }

                        SectionHeader(title: "ALARM & BRIEFING")
                        AlarmStatusCard()

                        SectionHeader(title: "RECENT EVENTS")
                        if let latest = pollingService.latestEvent {
                            Text(latest)
                                .inter(12)
                                .foregroundColor(.primary)
                                .padding(.horizontal, 4)
                        } else {
                            Text("No data loaded. Tap refresh to poll BMKG.")
                                .inter(12)
                                .foregroundColor(.secondary)
                                .padding(.horizontal, 4)
                        }

                        Button(action: { pollingService.checkNow() }) {
                            HStack {
                                Image(systemName: "arrow.clockwise")
                                Text("REFRESH BMKG")
                                    .inter(12, weight: .bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.prometheusBlue.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 1))
                            .foregroundColor(.prometheusBlue)
                        }
                        .buttonStyle(.plain)

                        SectionHeader(title: "LOCAL INJECTION")
                        InjectionStatusCard(
                            enabled: pollingService.injectionEnabled,
                            ip: pollingService.injectionIp,
                            port: pollingService.injectionPort
                        )
                        .onTapGesture { showInjectionSheet = true }
                    }
                    .padding()
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Prometheus")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    HStack(spacing: 12) {
                        Button(action: { isDarkMode.toggle() }) {
                            Image(systemName: isDarkMode ? "sun.max" : "moon")
                                .font(.caption)
                                .foregroundColor(.prometheusBlue)
                        }
                        Button(action: { showTutorial = true }) {
                            Image(systemName: "questionmark.circle")
                                .font(.caption)
                                .foregroundColor(.prometheusBlue)
                        }
                    }
                }
            }
            .onAppear {
                if !tutorialSeen { tutorialSeen = true; showTutorial = true }
            }
            .overlay {
                if showTutorial {
                    TutorialOverlay(tabName: "Monitor", steps: TutorialContent.monitor) {
                        showTutorial = false
                    }
                }
            }
            .sheet(isPresented: $showInjectionSheet) {
                InjectionSettingsView(
                    enabled: pollingService.injectionEnabled,
                    ip: pollingService.injectionIp,
                    port: pollingService.injectionPort
                ) { enabled, ip, port in
                    pollingService.injectionEnabled = enabled
                    pollingService.injectionIp = ip
                    pollingService.injectionPort = port
                }
            }
        }
    }

    private var dangerLevel: DangerLevel {
        switch pollingService.dangerLevel {
        case 2: return .danger
        case 1: return .medium
        default: return .none
        }
    }
}

// MARK: - Supporting views

enum DangerLevel { case none, watch, medium, danger }

struct DangerStatusBanner: View {
    let level: DangerLevel

    var color: Color {
        switch level {
        case .none:   return .prometheusBlue
        case .watch:  return .yellow
        case .medium: return .orange
        case .danger: return .red
        }
    }
    var label: String {
        switch level {
        case .none:   return "NO ACTIVE ALERTS"
        case .watch:  return "WATCH — MONITOR CLOSELY"
        case .medium: return "ELEVATED — STAY ALERT"
        case .danger: return "DANGER — TAKE ACTION NOW"
        }
    }
    var icon: String {
        switch level {
        case .none:   return "checkmark.shield.fill"
        case .watch:  return "eye.fill"
        case .medium: return "exclamationmark.triangle.fill"
        case .danger: return "alarm.fill"
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.title3)
            Text(label).inter(12, weight: .bold)
            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.6), lineWidth: 1))
        .foregroundColor(color)
    }
}

struct BMKGEventCard: View {
    let magnitude: String
    let location: String
    let depth: String
    let felt: String
    let potential: String
    let timestamp: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("M \(magnitude)")
                        .inter(28, weight: .bold)
                        .foregroundColor(.prometheusBlue)
                    Text(location)
                        .inter(12)
                        .foregroundColor(.primary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    EventField(label: "DEPTH", value: depth)
                    EventField(label: "FELT", value: felt)
                }
            }
            Divider().background(Color.prometheusBlue.opacity(0.3))
            HStack {
                EventField(label: "TSUNAMI POTENTIAL", value: potential)
                Spacer()
                Text(timestamp)
                    .inter(11)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 8, x: 0, y: 4)
    }
}

struct EventField: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).inter(11).foregroundColor(.secondary)
            Text(value).inter(12, weight: .bold).foregroundColor(.primary)
        }
    }
}

struct SectionHeader: View {
    let title: String
    var body: some View {
        Text(title)
            .inter(12, weight: .bold)
            .foregroundColor(.prometheusBlue)
            .padding(.top, 4)
    }
}

struct AlarmStatusCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                AlarmIndicatorRow(icon: "alarm.fill", label: "AUDIBLE ALARM", status: "ARMED", statusColor: .prometheusBlue)
                Spacer()
            }
            Divider().background(Color.prometheusBlue.opacity(0.15))
            AlarmIndicatorRow(icon: "waveform", label: "TTS BRIEFING", status: "READY", statusColor: .prometheusBlue)
            Divider().background(Color.prometheusBlue.opacity(0.15))
            AlarmIndicatorRow(icon: "brain.head.profile", label: "GEMMA 4 EMERGENCY PROMPT", status: "NOT LOADED", statusColor: .gray)
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct AlarmIndicatorRow: View {
    let icon: String
    let label: String
    let status: String
    let statusColor: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(statusColor.opacity(0.7))
                .frame(width: 16)
            Text(label)
                .inter(11)
                .foregroundColor(.secondary)
            Spacer()
            Text(status)
                .inter(11, weight: .bold)
                .foregroundColor(statusColor)
        }
    }
}

// MARK: - Local injection

struct InjectionStatusCard: View {
    let enabled: Bool
    let ip: String
    let port: Int

    var body: some View {
        let active = enabled && !ip.isEmpty
        let statusColor: Color = active ? .green : .secondary
        let statusText = active ? "ACTIVE — \(ip):\(port)" : "DISABLED"

        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("INJECTION")
                    .inter(11)
                    .foregroundColor(.secondary)
                Spacer()
                Text(statusText)
                    .inter(11, weight: .bold)
                    .foregroundColor(statusColor)
            }
            Text("Tap to configure local earthquake data injection")
                .inter(11)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct InjectionSettingsView: View {
    @State var enabled: Bool
    @State var ip: String
    @State var port: Int
    let onApply: (Bool, String, Int) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 12) {
                    Text("Run 'python3 tools/local_injector.py' on your PC, then enter its IP and port below.")
                        .inter(12)
                        .foregroundColor(.secondary)

                    Toggle("Enable Injection", isOn: $enabled)
                        .tint(.prometheusBlue)
                        .foregroundColor(.primary)
                        .inter(12)

                    TextField("PC IP Address (e.g. 192.168.1.42)", text: $ip)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.primary)
                        .inter(12)
                        .disabled(!enabled)

                    TextField("Port", value: $port, format: .number)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.primary)
                        .inter(12)
                        .disabled(!enabled)

                    Spacer()
                }
                .padding()
            }
            .navigationTitle("LOCAL INJECTION")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("APPLY") { onApply(enabled, ip, port); dismiss() }
                        .inter(12, weight: .bold)
                        .foregroundColor(.prometheusBlue)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("CANCEL") { dismiss() }
                        .inter(12)
                        .foregroundColor(.secondary)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
