import SwiftUI
import shared

struct MonitorView: View {
    @Environment(BMKGPollingService.self) private var pollingService
    @State private var showInjectionSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        DangerStatusBanner(level: dangerLevel)

                        SectionHeader(title: "EARTHQUAKE INFO")
                        if let event = pollingService.latestEarthquakeEvent {
                            BMKGEventCard(
                                magnitude: event.magnitudeValue?.formatted() ?? "--",
                                location: event.wilayah_ ?? "--",
                                depth: event.kedalaman_ ?? "--",
                                felt: event.dirasakan_ ?? "--",
                                latLon: "\(event.Lintang ?? "--"), \(event.Bujur ?? "--")",
                                potential: event.potensi_ ?? "--",
                                timestamp: "\(event.tanggal_ ?? "") \(event.jam_ ?? "")"
                            )
                        } else {
                            BMKGEventCard(
                                magnitude: "--",
                                location: "Waiting for data...",
                                depth: "--",
                                felt: "--",
                                latLon: "--",
                                potential: "--",
                                timestamp: pollingService.lastChecked ?? "Not yet refreshed"
                            )
                        }

                        SectionHeader(title: "WEATHER")
                        WeatherInfoCard(weather: pollingService.weatherInfo)

                        SectionHeader(title: "WEATHER WARNING")
                        if let latestAlert = pollingService.nowcastAlerts.max(by: { $0.guid < $1.guid }) {
                            NowcastAlertCard(alert: latestAlert)
                        } else {
                            NowcastClearCard()
                        }

                        SectionHeader(title: "RECENT EVENTS")
                        if let latest = pollingService.latestEvent {
                            Text(latest)
                                .font(.caption.monospaced())
                                .foregroundColor(.white)
                                .padding(.horizontal, 4)
                        } else {
                            Text("No data loaded. Tap refresh to poll BMKG.")
                                .font(.caption.monospaced())
                                .foregroundColor(.gray)
                                .padding(.horizontal, 4)
                        }

                        Button(action: { pollingService.checkNow() }) {
                            HStack {
                                Image(systemName: "arrow.clockwise")
                                Text("REFRESH BMKG")
                                    .font(.caption.bold().monospaced())
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.prometheusBlue.opacity(0.15))
                            .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 1))
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
                ToolbarItem(placement: .navigationBarTrailing) {
                    Label("BMKG", systemImage: "checkmark.shield")
                        .font(.caption.monospaced())
                        .foregroundColor(.prometheusBlue)
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

enum DangerLevel { case none, watch, medium, danger }

struct DangerStatusBanner: View {
    let level: DangerLevel

    private var color: Color {
        switch level {
        case .none:   return .green
        case .watch:  return .orange
        case .medium: return Color(red: 1.0, green: 0.55, blue: 0.0)
        case .danger: return .red
        }
    }
    var label: String {
        switch level {
        case .none:   return "NO ACTIVE ALERTS"
        case .watch:  return "WATCH \u2014 MONITOR CLOSELY"
        case .medium: return "MEDIUM ALERT \u2014 NOTIFIED"
        case .danger: return "DANGER \u2014 TAKE ACTION NOW"
        }
    }
    var icon: String {
        switch level {
        case .none:   return "checkmark.shield.fill"
        case .watch:  return "exclamationmark.triangle.fill"
        case .danger: return "alarm.fill"
        case .medium: return "exclamationmark.triangle.fill"
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.title3)
            Text(label).font(.caption.bold().monospaced())
            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.15))
        .overlay(Rectangle().stroke(color.opacity(0.6), lineWidth: 1))
        .foregroundColor(color)
    }
}

struct BMKGEventCard: View {
    let magnitude: String
    let location: String
    let depth: String
    let felt: String
    let latLon: String
    let potential: String
    let timestamp: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 0) {
                StatColumn(
                    icon: "\u{1F4CA}",
                    value: "M \(magnitude)",
                    label: "MAGNITUDE",
                    valueColor: .prometheusBlue
                )
                StatColumn(
                    icon: "\u{2B07}\u{FE0F}",
                    value: depth,
                    label: "DEPTH",
                    valueColor: .white
                )
                VStack(alignment: .center, spacing: 2) {
                    Text("\u{1F4CD}").font(.title3)
                    Text(felt)
                        .font(.caption.bold().monospaced())
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    if latLon != "--" {
                        Text(latLon)
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            Divider().background(Color.prometheusBlue.opacity(0.3))
            Text(potential).font(.caption.bold().monospaced()).foregroundColor(.white)
            Divider().background(Color.prometheusBlue.opacity(0.3))
            Text(location).font(.caption.bold().monospaced()).foregroundColor(.white)
            if !timestamp.isEmpty {
                HStack {
                    Spacer()
                    Text(timestamp)
                        .font(.caption2.monospaced())
                        .foregroundColor(.gray)
                }
            }
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct StatColumn: View {
    let icon: String
    let value: String
    let label: String
    let valueColor: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(icon).font(.title3)
            Text(value)
                .font(.caption.bold().monospaced())
                .foregroundColor(valueColor)
                .multilineTextAlignment(.center)
                .lineLimit(2)
            Text(label)
                .font(.caption2.monospaced())
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

struct WeatherInfoCard: View {
    let weather: shared.WeatherInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 0) {
                WeatherStatColumn(icon: "\u{1F321}\u{FE0F}", value: "\(weather.temperature)\u{00B0}", label: "TEMP")
                WeatherStatColumn(icon: "\u{1F4A7}", value: "\(weather.humidity)%", label: "HUMIDITY")
                WeatherStatColumn(icon: "\u{1F4A8}", value: "\(weather.windSpeed) km/j", label: "WIND")
            }
            if !weather.weatherDesc.isEmpty && weather.weatherDesc != "--" {
                Divider().background(Color.prometheusBlue.opacity(0.3))
                Text(weather.weatherDesc)
                    .font(.caption2.monospaced())
                    .foregroundColor(.gray)
            }
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct WeatherStatColumn: View {
    let icon: String
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(icon).font(.title3)
            Text(value)
                .font(.caption.bold().monospaced())
                .foregroundColor(.white)
            Text(label)
                .font(.caption2.monospaced())
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

struct NowcastAlertCard: View {
    let alert: shared.NowcastAlert
    @State private var expanded = false

    var body: some View {
        HStack(spacing: 10) {
            Text(alert.isBadWeather ? "\u{26A0}\u{FE0F}" : "\u{1F6E1}\u{FE0F}")
                .font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(alert.eventType)
                    .font(.caption.bold().monospaced())
                    .foregroundColor(alert.isBadWeather ? .red : .orange)
                Text(alert.summary)
                    .font(.caption2.monospaced())
                    .foregroundColor(.gray)
                    .lineLimit(expanded ? nil : 2)
                Text(expanded ? "\u{25B2} Tap to collapse" : "\u{25BC} Tap to expand")
                    .font(.caption2.monospaced())
                    .foregroundColor(.gray)
                    .padding(.top, 2)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
        .onTapGesture { expanded.toggle() }
    }
}

struct NowcastClearCard: View {
    var body: some View {
        HStack(spacing: 10) {
            Text("\u{2600}\u{FE0F}")
                .font(.title3)
            Text("Cuaca baik \u2014 Tidak ada peringatan")
                .font(.caption2.monospaced())
                .foregroundColor(.green)
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct InjectionStatusCard: View {
    let enabled: Bool
    let ip: String
    let port: Int

    var body: some View {
        let statusColor: Color = enabled && !ip.isEmpty ? .green : .gray
        let statusText: String = enabled && !ip.isEmpty ? "ACTIVE \u2014 \(ip):\(port)" : "DISABLED"

        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("INJECTION")
                    .font(.caption2.monospaced())
                    .foregroundColor(.gray)
                Spacer()
                Text(statusText)
                    .font(.caption2.bold().monospaced())
                    .foregroundColor(statusColor)
            }
            Text("Tap to configure local earthquake data injection")
                .font(.caption2.monospaced())
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
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
                Color.darkBackground.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 12) {
                    Text("Run 'python3 tools/local_injector.py' on your PC, then enter its IP and port below.")
                        .font(.caption.monospaced())
                        .foregroundColor(.gray)

                    Toggle("Enable Injection", isOn: $enabled)
                        .tint(.prometheusBlue)
                        .foregroundColor(.white)
                        .font(.caption.monospaced())

                    TextField("PC IP Address (e.g. 192.168.1.42)", text: $ip)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.white)
                        .font(.caption.monospaced())
                        .disabled(!enabled)

                    TextField("Port", value: $port, format: .number)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.white)
                        .font(.caption.monospaced())
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
                    Button("APPLY") {
                        onApply(enabled, ip, port)
                        dismiss()
                    }
                    .font(.caption.bold().monospaced())
                    .foregroundColor(.prometheusBlue)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("CANCEL") {
                        dismiss()
                    }
                    .font(.caption.monospaced())
                    .foregroundColor(.gray)
                }
            }
        }
    }
}

struct EventField: View {
    let label: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.caption2.monospaced()).foregroundColor(.gray)
            Text(value).font(.caption.bold().monospaced()).foregroundColor(.white)
        }
    }
}

struct SectionHeader: View {
    let title: String
    var body: some View {
        Text(title)
            .font(.caption.bold().monospaced())
            .foregroundColor(.prometheusBlue)
            .padding(.top, 4)
    }
}
