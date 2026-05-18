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
                        let classification: String = {
                            switch dangerLevel {
                            case .none: return "ALL CLEAR"
                            case .watch: return "WATCH"
                            case .medium: return "MEDIUM ALERT"
                            case .danger: return "DANGER"
                            }
                        }()

                        SectionHeader(title: "EARTHQUAKE INFO — \(classification)")
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

                        let weatherLabel: String = {
                            let w = pollingService.weatherInfo
                            if !w.weatherDesc.isEmpty && w.weatherDesc != "--" {
                                return "WEATHER — \(w.weatherDesc)"
                            }
                            return "WEATHER"
                        }()
                        SectionHeader(title: weatherLabel)
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
                                .inter(12)
                                .foregroundColor(.white)
                                .padding(.horizontal, 4)
                        } else {
                            Text("No data loaded. Tap refresh to poll BMKG.")
                                .inter(12)
                                .foregroundColor(.gray)
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
            .navigationTitle("Monitor")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
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
                        .inter(12, weight: .bold)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    if latLon != "--" {
                        Text(latLon)
                            .inter(11)
                            .foregroundColor(.gray)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            Divider().background(Color.prometheusBlue.opacity(0.3))
            Text(potential).inter(11).foregroundColor(.gray)
            Divider().background(Color.prometheusBlue.opacity(0.3))
            Text(location).inter(11).foregroundColor(.gray)
            if !timestamp.isEmpty {
                HStack {
                    Spacer()
                    Text(timestamp)
                        .inter(11)
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
                .inter(12, weight: .bold)
                .foregroundColor(valueColor)
                .multilineTextAlignment(.center)
                .lineLimit(2)
            Text(label)
                .inter(11)
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
                .inter(12, weight: .bold)
                .foregroundColor(.white)
            Text(label)
                .inter(11)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity)
    }
}

struct NowcastAlertCard: View {
    let alert: shared.NowcastAlert

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Text(alert.isBadWeather ? "\u{26A0}\u{FE0F}" : "\u{1F6E1}\u{FE0F}")
                    .font(.title)
                Text(alert.intensity.uppercased())
                    .inter(22, weight: .bold)
                    .foregroundColor(alert.isBadWeather ? Color(red: 0.72, green: 0.11, blue: 0.11) : .orange)
            }

            Text("Beresiko \(alert.potential.lowercased().trimmingCharacters(in: .punctuation)).")
                .inter(14)
                .foregroundColor(.textSecondary)

            HStack(spacing: 6) {
                Text("\u{1F552}")
                    .font(.caption)
                Text("\(alert.alertDate) \u{2022} \(alert.alertTime) \u{2014} \(alert.estimatedEnd.replacingOccurrences(of: "~ ", with: "")) WIB")
                    .inter(12)
                    .foregroundColor(.textSecondary)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("AFFECTED AREAS - \(alert.provinceName.uppercased())")
                    .inter(10, weight: .bold)
                    .foregroundColor(.textSecondary)
                if !alert.specificLocation.isEmpty && alert.specificLocation != "--" {
                    Text(alert.specificLocation)
                        .inter(12)
                        .foregroundColor(.textPrimary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.surfaceElevated)
            .cornerRadius(8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

struct NowcastClearCard: View {
    var body: some View {
        HStack(spacing: 10) {
            Text("\u{2600}\u{FE0F}")
                .font(.title3)
            Text("Cuaca baik \u2014 Tidak ada peringatan")
                .inter(11)
                .foregroundColor(.green)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
                    .inter(11)
                    .foregroundColor(.gray)
                Spacer()
                Text(statusText)
                    .inter(11, weight: .bold)
                    .foregroundColor(statusColor)
            }
            Text("Tap to configure local earthquake data injection")
                .inter(11)
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
                        .inter(12)
                        .foregroundColor(.gray)

                    Toggle("Enable Injection", isOn: $enabled)
                        .tint(.prometheusBlue)
                        .foregroundColor(.white)
                        .inter(12)

                    TextField("PC IP Address (e.g. 192.168.1.42)", text: $ip)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.white)
                        .inter(12)
                        .disabled(!enabled)

                    TextField("Port", value: $port, format: .number)
                        .textFieldStyle(.plain)
                        .padding()
                        .background(Color.cardBackground)
                        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.white)
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
                    Button("APPLY") {
                        onApply(enabled, ip, port)
                        dismiss()
                    }
                    .inter(12, weight: .bold)
                    .foregroundColor(.prometheusBlue)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("CANCEL") {
                        dismiss()
                    }
                    .inter(12)
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
            Text(label).inter(11).foregroundColor(.gray)
            Text(value).inter(12, weight: .bold).foregroundColor(.white)
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
