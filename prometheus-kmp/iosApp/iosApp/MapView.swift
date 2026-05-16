import SwiftUI
import MapKit
import shared

private let googleDirectionsApiKey = "AIzaSyD2S0REeAlSuzkJVDAd8oW9cAk18IgmztQ"

struct MapView: View {
    @Environment(BMKGPollingService.self) private var pollingService
    @State private var evacuationRoute: EvacuationRoute?
    @State private var routeLoading = false

    private var epicenter: CLLocationCoordinate2D? {
        guard let pair = pollingService.latestEarthquakeEvent?.coordinatePair else { return nil }
        return CLLocationCoordinate2D(latitude: pair.first.doubleValue, longitude: pair.second.doubleValue)
    }

    private var userLocation: CLLocationCoordinate2D? {
        guard let loc = pollingService.currentLocation else { return nil }
        return CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude)
    }

    private var dangerRadiusKm: Double? {
        guard let event = pollingService.latestEarthquakeEvent else { return nil }
        if event.hasTsunamiPotential { return nil }
        let mag = event.magnitudeValue?.floatValue ?? 0
        if mag >= 5 { return 50.0 }
        if mag >= 6 { return 150.0 }
        return nil
    }

    private var isDangerous: Bool {
        pollingService.dangerLevel >= 2
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    EvacuationStatusBanner(isDangerous: isDangerous)

                    if #available(iOS 17.0, *) {
                        mapContent
                            .frame(maxWidth: .infinity)
                            .padding(16)
                    } else {
                        MapPlaceholder()
                            .frame(maxWidth: .infinity)
                            .frame(height: 240)
                            .padding(16)
                    }

                    RoutingDetailsCard(
                        event: pollingService.latestEarthquakeEvent,
                        isDangerous: isDangerous,
                        dangerRadiusKm: dangerRadiusKm,
                        evacuationRoute: evacuationRoute,
                        routeLoading: routeLoading
                    )
                    .padding(.horizontal, 16)

                    Spacer()
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
        .task(id: pollingService.latestEarthquakeEvent?.dateTime) {
            await fetchRoute()
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
            if let user = userLocation {
                Annotation("You", coordinate: user) {
                    Image(systemName: "location.fill")
                        .foregroundColor(.blue)
                        .font(.title3)
                }
            }
            if let route = evacuationRoute {
                let coords = route.coordinates.map { c in
                    CLLocationCoordinate2D(latitude: c.first.doubleValue, longitude: c.second.doubleValue)
                }
                MapPolyline(coordinates: coords)
                    .stroke(.prometheusBlue, lineWidth: 3)
            }
        }
        .mapStyle(.standard)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }

    private func fetchRoute() async {
        evacuationRoute = nil
        guard isDangerous,
              let event = pollingService.latestEarthquakeEvent,
              let userLoc = pollingService.currentLocation,
              let pair = event.coordinatePair else { return }
        let userLat = userLoc.latitude
        let userLon = userLoc.longitude
        let epiLat = pair.first.doubleValue
        let epiLon = pair.second.doubleValue
        let radius = dangerRadiusKm ?? 50.0

        routeLoading = true
        let router = EvacuationRouter(googleApiKey: googleDirectionsApiKey)
        do {
            let route = try await router.fetchEvacuationRoute(
                userLat: userLat, userLon: userLon,
                epicenterLat: epiLat, epicenterLon: epiLon,
                dangerRadiusKm: radius
            )
            await MainActor.run { evacuationRoute = route }
        } catch {
            await MainActor.run { evacuationRoute = nil }
        }
        routeLoading = false
    }
}

private struct EvacuationStatusBanner: View {
    let isDangerous: Bool

    var body: some View {
        let color = isDangerous ? Color.red : Color.prometheusBlue
        let label = isDangerous ? "EVACUATION ROUTING — ACTIVE" : "EVACUATION ROUTING — STANDBY"

        HStack(spacing: 10) {
            Image(systemName: isDangerous ? "exclamationmark.triangle.fill" : "arrow.triangle.turn.up.right.circle.fill")
                .font(.title3)
            Text(label)
                .font(.caption.bold().monospaced())
            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.1))
        .overlay(Rectangle().stroke(color.opacity(0.4), lineWidth: 1))
        .foregroundColor(color)
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
                    .foregroundColor(.white)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 240)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

private struct RoutingDetailsCard: View {
    let event: EarthquakeEvent?
    let isDangerous: Bool
    let dangerRadiusKm: Double?
    let evacuationRoute: EvacuationRoute?
    let routeLoading: Bool

    private var epicenterStr: String {
        guard let e = event else { return "Waiting for event..." }
        let loc = e.wilayah_ ?? "Unknown"
        if let pair = e.coordinatePair {
            return "\(loc) (\(String(format: "%.2f", pair.first.doubleValue)), \(String(format: "%.2f", pair.second.doubleValue)))"
        }
        return loc
    }

    private var userLocStr: String {
        guard let loc = event?.coordinatePair else { return "Not acquired" }
        return "\(String(format: "%.4f", loc.first.doubleValue)), \(String(format: "%.4f", loc.second.doubleValue))"
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
        return "\(h)h \(m)m"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            RouteInfoRow(label: "EPICENTRE", value: epicenterStr)
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "USER LOCATION", value: userLocStr)
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "SAFE RADIUS", value: radiusStr)

            if let r = evacuationRoute {
                Divider().background(Color.prometheusBlue.opacity(0.15))
                RouteInfoRow(label: "DISTANCE", value: "\(String(format: "%.1f", r.distanceKm)) km")
                Divider().background(Color.prometheusBlue.opacity(0.15))
                Text("ESTIMATED TIME")
                    .font(.caption2.bold().monospaced())
                    .foregroundColor(.gray)
                    .padding(.vertical, 2)
                TransportTimeRow(icon: "figure.walk", label: "Walk", time: formatTime(r.walkMin))
                TransportTimeRow(icon: "figure.run", label: "Run", time: formatTime(r.runMin))
                TransportTimeRow(icon: "bicycle", label: "Cycle", time: formatTime(r.cycleMin))
                TransportTimeRow(icon: "motorcycle", label: "Motor", time: formatTime(r.motorMin))
                TransportTimeRow(icon: "car", label: "Car", time: formatTime(r.durationMin))
            } else {
                Divider().background(Color.prometheusBlue.opacity(0.15))
                RouteInfoRow(label: "EXIT ROUTE", value: routeLoading ? "Computing..." : (isDangerous ? "Unavailable" : "--"))
            }
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

private struct TransportTimeRow: View {
    let icon: String
    let label: String
    let time: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon).font(.caption2).foregroundColor(.gray).frame(width: 16)
            Text(label).font(.caption2.monospaced()).foregroundColor(.gray).frame(width: 48, alignment: .leading)
            Spacer()
            Text(time).font(.caption2.bold().monospaced()).foregroundColor(.white)
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
                .foregroundColor(.gray)
                .frame(width: 128, alignment: .leading)
            Text(value)
                .font(.caption.monospaced())
                .foregroundColor(.white)
            Spacer()
        }
    }
}
