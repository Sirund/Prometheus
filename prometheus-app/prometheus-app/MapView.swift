//
//  MapView.swift
//  prometheus-app
//

import SwiftUI
import MapKit

struct MapView: View {
    @Environment(BMKGPollingService.self) private var pollingService
    @State private var evacuationRoute: EvacuationRoute?
    @State private var routeLoading = false
    @State private var showDetails = false

    private var epicenter: CLLocationCoordinate2D? {
        guard let pair = pollingService.latestEarthquakeEvent?.coordinatePair else { return nil }
        return CLLocationCoordinate2D(latitude: pair.lat, longitude: pair.lon)
    }

    private var userLocationCoord: CLLocationCoordinate2D? {
        guard let loc = pollingService.currentLocation else { return nil }
        return CLLocationCoordinate2D(latitude: loc.lat, longitude: loc.lon)
    }

    private var dangerRadiusKm: Double? {
        pollingService.latestEarthquakeEvent?.dangerRadiusKm
    }

    private var isDangerous: Bool {
        pollingService.dangerLevel >= 2
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    EvacuationStatusBanner(isDangerous: isDangerous)

                    if #available(iOS 17.0, *) {
                        mapContent
                            .frame(maxWidth: .infinity)
                            .layoutPriority(1)
                            .padding(16)
                    } else {
                        MapPlaceholder()
                            .frame(maxWidth: .infinity)
                            .frame(height: 240)
                            .padding(16)
                    }

                    DisclosureGroup(
                        isExpanded: $showDetails,
                        content: {
                            RoutingDetailsCard(
                                event: pollingService.latestEarthquakeEvent,
                                userLocation: pollingService.currentLocation,
                                isDangerous: isDangerous,
                                dangerRadiusKm: dangerRadiusKm,
                                evacuationRoute: evacuationRoute,
                                routeLoading: routeLoading
                            )
                            .padding(.top, 8)
                        },
                        label: {
                            Text("ROUTING DETAILS")
                                .font(.caption.bold().monospaced())
                                .foregroundColor(.prometheusBlue)
                        }
                    )
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .tint(.prometheusBlue)

                    EvacuationInfoNote()
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)

                    Spacer(minLength: 0)
                }
            }
            .navigationTitle("Evacuation")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Label("Maps", systemImage: "map")
                        .font(.caption.monospaced())
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
        .task(id: pollingService.latestEarthquakeEvent?.DateTime) {
            await computeRoute()
        }
    }

    @available(iOS 17.0, *)
    private var mapContent: some View {
        Map {
            if let epi = epicenter {
                Annotation("Epicenter", coordinate: epi) {
                    Image(systemName: "target")
                        .foregroundColor(.red)
                        .font(.title3)
                }
                if let km = dangerRadiusKm {
                    MapCircle(center: epi, radius: km * 1000)
                        .foregroundStyle(.red.opacity(0.15))
                        .stroke(.red.opacity(0.5), lineWidth: 1)
                }
            }
            if let user = userLocationCoord {
                Annotation("You", coordinate: user) {
                    Image(systemName: "location.fill")
                        .foregroundColor(.prometheusBlue)
                        .font(.title3)
                }
            }
        }
        .mapStyle(.standard)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }

    private func computeRoute() async {
        evacuationRoute = nil
        guard isDangerous,
              let event = pollingService.latestEarthquakeEvent,
              let loc = pollingService.currentLocation,
              let pair = event.coordinatePair,
              let radius = dangerRadiusKm else { return }
        routeLoading = true
        evacuationRoute = EvacuationRoute.compute(
            userLat: loc.lat, userLon: loc.lon,
            epicenterLat: pair.lat, epicenterLon: pair.lon,
            dangerRadiusKm: radius
        )
        routeLoading = false
    }
}

// MARK: - Supporting views

private struct EvacuationStatusBanner: View {
    let isDangerous: Bool

    var body: some View {
        let color: Color = isDangerous ? .red : .prometheusBlue
        let label = isDangerous ? "EVACUATION ROUTING — ACTIVE" : "EVACUATION ROUTING — STANDBY"
        let icon = isDangerous
            ? "exclamationmark.triangle.fill"
            : "arrow.triangle.turn.up.right.circle.fill"

        HStack(spacing: 10) {
            Image(systemName: icon).font(.title3)
            Text(label).font(.caption.bold().monospaced())
            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.4), lineWidth: 1))
        .foregroundColor(color)
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }
}

private struct MapPlaceholder: View {
    var body: some View {
        ZStack {
            Color.cardBackground
            VStack(spacing: 14) {
                Image(systemName: "map.fill")
                    .font(.system(size: 52))
                    .foregroundColor(.prometheusBlue.opacity(0.35))
                Text("Map requires iOS 17+")
                    .font(.caption.bold().monospaced())
                    .foregroundColor(.primary)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 8, x: 0, y: 4)
    }
}

private struct RoutingDetailsCard: View {
    let event: EarthquakeEvent?
    let userLocation: (lat: Double, lon: Double)?
    let isDangerous: Bool
    let dangerRadiusKm: Double?
    let evacuationRoute: EvacuationRoute?
    let routeLoading: Bool

    private var epicenterStr: String {
        guard let e = event else { return "Waiting for event..." }
        let loc = e.Wilayah ?? "Unknown"
        if let pair = e.coordinatePair {
            return "\(loc) (\(String(format: "%.2f", pair.lat))°, \(String(format: "%.2f", pair.lon))°)"
        }
        return loc
    }

    private var userLocStr: String {
        guard let loc = userLocation else { return "Not acquired" }
        return "\(String(format: "%.4f", loc.lat))°, \(String(format: "%.4f", loc.lon))°"
    }

    private var radiusStr: String {
        guard let km = dangerRadiusKm else { return "--" }
        return "\(Int(km)) km"
    }

    private func formatTime(_ minutes: Double) -> String {
        if minutes < 1 { return "< 1 min" }
        if minutes < 60 { return "\(Int(minutes.rounded())) min" }
        let h = Int(minutes / 60)
        let m = Int(minutes.truncatingRemainder(dividingBy: 60))
        return m > 0 ? "\(h)h \(m)m" : "\(h)h"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            RouteInfoRow(label: "EPICENTRE", value: epicenterStr)
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "YOUR LOCATION", value: userLocStr)
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "SAFE RADIUS", value: radiusStr)

            if let r = evacuationRoute {
                Divider().background(Color.prometheusBlue.opacity(0.15))
                RouteInfoRow(label: "DISTANCE", value: "\(String(format: "%.1f", r.distanceKm)) km")
                Divider().background(Color.prometheusBlue.opacity(0.15))
                Text("ESTIMATED TIME")
                    .font(.caption2.bold().monospaced())
                    .foregroundColor(.secondary)
                    .padding(.vertical, 2)
                TransportTimeRow(icon: "figure.walk",  label: "Walk",  time: formatTime(r.walkMin))
                TransportTimeRow(icon: "figure.run",   label: "Run",   time: formatTime(r.runMin))
                TransportTimeRow(icon: "bicycle",      label: "Cycle", time: formatTime(r.cycleMin))
                TransportTimeRow(icon: "motorcycle",   label: "Motor", time: formatTime(r.motorMin))
                TransportTimeRow(icon: "car",          label: "Car",   time: formatTime(r.durationMin))
            } else {
                Divider().background(Color.prometheusBlue.opacity(0.15))
                RouteInfoRow(
                    label: "EXIT ROUTE",
                    value: routeLoading ? "Computing..."
                        : isDangerous ? "Outside danger zone"
                        : "No active event"
                )
            }
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
        .shadow(color: .black.opacity(0.3), radius: 6, x: 0, y: 3)
    }
}

private struct TransportTimeRow: View {
    let icon: String
    let label: String
    let time: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption2).foregroundColor(.secondary).frame(width: 16)
            Text(label)
                .font(.caption2.monospaced()).foregroundColor(.secondary).frame(width: 48, alignment: .leading)
            Spacer()
            Text(time)
                .font(.caption2.bold().monospaced()).foregroundColor(.primary)
        }
    }
}

struct RouteInfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
                .frame(width: 128, alignment: .leading)
            Text(value)
                .font(.caption.monospaced())
                .foregroundColor(.primary)
            Spacer()
        }
    }
}

private struct EvacuationInfoNote: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("HOW IT WORKS")
                .font(.caption2.bold().monospaced())
                .foregroundColor(.prometheusBlue)
            Text("On a dangerous event, the BMKG epicentre is shown on the map with a danger radius. Evacuation times are estimated from your current location using distance and average travel speeds. Follow official BMKG and BNPB instructions.")
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
                .lineSpacing(4)
        }
        .padding()
        .background(Color.cardBackground.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 1))
    }
}
