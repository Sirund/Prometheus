//
//  MapView.swift
//  prometheus-app
//

import SwiftUI
import MapKit

struct MapView: View {
    @Environment(BMKGPollingService.self) private var pollingService
    @AppStorage("isDarkMode") private var isDarkMode = false
    @State private var evacuationRoute: EvacuationRoute?
    @State private var routeLoading = false
    @State private var showDetails = false
    @State private var showEvacuationGuide = false
    @State private var mapPosition: MapCameraPosition = .automatic

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

                    HStack(spacing: 8) {
                        Button(action: { showEvacuationGuide = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: routeLoading ? "hourglass.circle.fill" : "arrow.triangle.turn.up.right.circle.fill")
                                    .font(.body)
                                Text(isDangerous ? "EVACUATE NOW" : routeLoading ? "COMPUTING..." : "VIEW ROUTE")
                                    .font(.caption.bold().monospaced())
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(isDangerous ? Color.red.opacity(0.15) : Color.prometheusBlue.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(
                                isDangerous ? Color.red.opacity(0.6) : Color.prometheusBlue.opacity(0.4),
                                lineWidth: isDangerous ? 2 : 1
                            ))
                            .foregroundColor(isDangerous ? .red : .prometheusBlue)
                        }
                        .buttonStyle(.plain)

                        Button(action: openInMaps) {
                            HStack(spacing: 6) {
                                Image(systemName: "map.fill").font(.body)
                                Text("NAVIGATE").font(.caption.bold().monospaced())
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.green.opacity(evacuationRoute != nil ? 0.15 : 0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.green.opacity(evacuationRoute != nil ? 0.6 : 0.2), lineWidth: 1))
                            .foregroundColor(evacuationRoute != nil ? .green : .gray)
                        }
                        .buttonStyle(.plain)
                        .disabled(evacuationRoute == nil)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)

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
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { isDarkMode.toggle() }) {
                        Image(systemName: isDarkMode ? "sun.max" : "moon")
                            .font(.caption)
                            .foregroundColor(.prometheusBlue)
                    }
                }
            }
        }
        .task(id: pollingService.latestEarthquakeEvent?.DateTime) {
            await fetchRoute()
        }
        .sheet(isPresented: $showEvacuationGuide) {
            EvacuationGuideSheet(
                event: pollingService.latestEarthquakeEvent,
                isDangerous: isDangerous,
                evacuationRoute: evacuationRoute,
                routeLoading: routeLoading,
                dangerRadiusKm: dangerRadiusKm
            )
        }
    }

    @available(iOS 17.0, *)
    private var mapContent: some View {
        Map(position: $mapPosition) {
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
            if let route = evacuationRoute, !route.coordinates.isEmpty {
                MapPolyline(coordinates: route.coordinates.map {
                    CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon)
                })
                .stroke(Color.prometheusBlue, lineWidth: 4)

                if let dest = route.coordinates.last {
                    Annotation("Safe Zone", coordinate: CLLocationCoordinate2D(latitude: dest.lat, longitude: dest.lon)) {
                        ZStack {
                            Circle().fill(Color.green.opacity(0.2)).frame(width: 36, height: 36)
                            Image(systemName: "checkmark.shield.fill")
                                .foregroundColor(.green)
                                .font(.title3)
                        }
                    }
                }
            }
        }
        .mapStyle(.standard)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }

    private func fetchRoute() async {
        evacuationRoute = nil
        guard let event = pollingService.latestEarthquakeEvent,
              let loc = pollingService.currentLocation,
              let pair = event.coordinatePair,
              let radius = dangerRadiusKm else { return }
        routeLoading = true
        evacuationRoute = await EvacuationRouter().fetchEvacuationRoute(
            userLat: loc.lat, userLon: loc.lon,
            epicenterLat: pair.lat, epicenterLon: pair.lon,
            dangerRadiusKm: radius
        )
        routeLoading = false
        fitMapToRoute()
    }

    private func fitMapToRoute() {
        var coords = evacuationRoute?.coordinates.map {
            CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon)
        } ?? []
        if let u = userLocationCoord { coords.append(u) }
        if let e = epicenter { coords.append(e) }
        guard !coords.isEmpty else { return }
        let lats = coords.map { $0.latitude }, lons = coords.map { $0.longitude }
        guard let minLat = lats.min(), let maxLat = lats.max(),
              let minLon = lons.min(), let maxLon = lons.max() else { return }
        let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max((maxLat - minLat) * 1.5, 0.02),
                                    longitudeDelta: max((maxLon - minLon) * 1.5, 0.02))
        mapPosition = .region(MKCoordinateRegion(center: center, span: span))
    }

    private func openInMaps() {
        guard let route = evacuationRoute, let dest = route.coordinates.last else { return }
        let destination = MKMapItem(placemark: MKPlacemark(
            coordinate: CLLocationCoordinate2D(latitude: dest.lat, longitude: dest.lon)
        ))
        destination.name = "Safe Zone Exit"
        MKMapItem.openMaps(with: [destination], launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDefault
        ])
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
            Text("On a dangerous event, the BMKG epicentre is shown on the map with a danger radius. The blue route line shows the fastest road exit from the danger zone via Google Directions. Follow official BMKG and BNPB instructions.")
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

// MARK: - Evacuation guide sheet

private struct EvacuationGuideSheet: View {
    let event: EarthquakeEvent?
    let isDangerous: Bool
    let evacuationRoute: EvacuationRoute?
    let routeLoading: Bool
    let dangerRadiusKm: Double?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                Group {
                    if routeLoading {
                        loadingView
                    } else if let route = evacuationRoute {
                        routeView(route)
                    } else {
                        noRouteView
                    }
                }
            }
            .navigationTitle("EVACUATION ROUTE")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("CLOSE") { dismiss() }
                        .font(.caption.bold().monospaced())
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
    }

    // MARK: States

    private var loadingView: some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView().tint(.prometheusBlue).scaleEffect(1.4)
            Text("COMPUTING EVACUATION ROUTE")
                .font(.caption.bold().monospaced())
                .foregroundColor(.primary)
            Text("Querying Google Directions for the safest exit from the danger zone…")
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }

    private var noRouteView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 44))
                .foregroundColor(.orange)
            Text("NO ROUTE AVAILABLE")
                .font(.caption.bold().monospaced())
                .foregroundColor(.primary)
            Text("Route data requires an active earthquake event with a known location and your current GPS position.")
                .font(.caption2.monospaced())
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }

    private func routeView(_ route: EvacuationRoute) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                statusBanner
                summaryCard(route)

                Button(action: { openInMaps(route) }) {
                    HStack(spacing: 8) {
                        Image(systemName: "map.fill").font(.body)
                        Text("OPEN NAVIGATION IN APPLE MAPS")
                            .font(.caption.bold().monospaced())
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.green.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.green.opacity(0.5), lineWidth: 1))
                    .foregroundColor(.green)
                }
                .buttonStyle(.plain)

                if !route.steps.isEmpty {
                    Text("TURN-BY-TURN DIRECTIONS")
                        .font(.caption2.bold().monospaced())
                        .foregroundColor(.prometheusBlue)
                        .padding(.top, 4)
                    ForEach(Array(route.steps.enumerated()), id: \.offset) { idx, step in
                        TurnStepRow(number: idx + 1, instruction: step)
                    }
                }
                disclaimer
            }
            .padding()
        }
        .scrollContentBackground(.hidden)
    }

    private func openInMaps(_ route: EvacuationRoute) {
        guard let dest = route.coordinates.last else { return }
        let item = MKMapItem(placemark: MKPlacemark(
            coordinate: CLLocationCoordinate2D(latitude: dest.lat, longitude: dest.lon)
        ))
        item.name = "Safe Zone Exit"
        MKMapItem.openMaps(with: [item], launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDefault
        ])
    }

    // MARK: Sub-views

    private var statusBanner: some View {
        let color: Color = isDangerous ? .red : .prometheusBlue
        let label = isDangerous ? "ACTIVE EVENT — EVACUATE NOW" : "EVACUATION ROUTE"
        let icon = isDangerous ? "exclamationmark.triangle.fill" : "arrow.triangle.turn.up.right.circle.fill"

        return HStack(spacing: 10) {
            Image(systemName: icon).font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption.bold().monospaced())
                if let e = event, let mag = e.magnitudeValue {
                    Text("M\(String(format: "%.1f", mag))  ·  \(e.Wilayah ?? "Unknown location")")
                        .font(.caption2.monospaced())
                        .foregroundColor(color.opacity(0.8))
                }
            }
            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.5), lineWidth: 1))
        .foregroundColor(color)
    }

    private func summaryCard(_ route: EvacuationRoute) -> some View {
        func fmt(_ min: Double) -> String {
            if min < 1 { return "< 1 min" }
            if min < 60 { return "\(Int(min.rounded())) min" }
            let h = Int(min / 60), m = Int(min.truncatingRemainder(dividingBy: 60))
            return m > 0 ? "\(h)h \(m)m" : "\(h)h"
        }

        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("ROUTE DISTANCE")
                    .font(.caption2.monospaced()).foregroundColor(.secondary)
                Spacer()
                Text("\(String(format: "%.1f", route.distanceKm)) km")
                    .font(.caption.bold().monospaced()).foregroundColor(.prometheusBlue)
            }
            Divider().background(Color.prometheusBlue.opacity(0.15))
            Text("ESTIMATED TRAVEL TIME")
                .font(.caption2.bold().monospaced()).foregroundColor(.secondary)
            HStack(spacing: 0) {
                TimeCell(icon: "figure.walk",  label: "Walk",  time: fmt(route.walkMin))
                TimeCell(icon: "figure.run",   label: "Run",   time: fmt(route.runMin))
                TimeCell(icon: "bicycle",      label: "Cycle", time: fmt(route.cycleMin))
                TimeCell(icon: "motorcycle",   label: "Motor", time: fmt(route.motorMin))
                TimeCell(icon: "car",          label: "Car",   time: fmt(route.durationMin))
            }
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }

    private var disclaimer: some View {
        Text("Route computed by Google Directions API for driving. Follow official BMKG and BNPB instructions in a real emergency. Road conditions may differ.")
            .font(.caption2.monospaced())
            .foregroundColor(.secondary)
            .lineSpacing(4)
            .padding()
            .background(Color.cardBackground.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 1))
    }
}

private struct TimeCell: View {
    let icon: String
    let label: String
    let time: String

    var body: some View {
        VStack(spacing: 3) {
            Image(systemName: icon)
                .font(.caption).foregroundColor(.prometheusBlue.opacity(0.7))
            Text(label)
                .font(.caption2.monospaced()).foregroundColor(.secondary)
            Text(time)
                .font(.caption2.bold().monospaced()).foregroundColor(.primary)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct TurnStepRow: View {
    let number: Int
    let instruction: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            ZStack {
                Circle().fill(Color.prometheusBlue.opacity(0.15)).frame(width: 28, height: 28)
                Text("\(number)")
                    .font(.caption2.bold().monospaced())
                    .foregroundColor(.prometheusBlue)
            }
            Text(instruction)
                .font(.caption.monospaced())
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.prometheusBlue.opacity(0.2), lineWidth: 1))
    }
}
