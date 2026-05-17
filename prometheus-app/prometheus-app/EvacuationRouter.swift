import Foundation

// MARK: - Google Directions API response models

private struct DirectionsResponse: Codable {
    let status: String
    let routes: [DirectionsRoute]
}

private struct DirectionsRoute: Codable {
    let legs: [DirectionsLeg]
    let overview_polyline: PolylineData?
}

private struct PolylineData: Codable {
    let points: String
}

private struct DirectionsLeg: Codable {
    let distance: MeasureValue
    let duration: MeasureValue
    let steps: [DirectionsStep]
}

private struct DirectionsStep: Codable {
    let maneuver: String?
    let html_instructions: String?
}

private struct MeasureValue: Codable {
    let value: Int
}

// MARK: - Google polyline decoder

enum PolylineDecoder {
    static func decode(_ encoded: String) -> [(lat: Double, lon: Double)] {
        var result: [(Double, Double)] = []
        var lat = 0, lng = 0
        var i = encoded.startIndex

        func readChunk() -> Int {
            var value = 0, shift = 0
            while i < encoded.endIndex {
                let b = Int(encoded[i].asciiValue ?? 63) - 63
                i = encoded.index(after: i)
                value |= (b & 0x1f) << shift
                shift += 5
                if b < 0x20 { break }
            }
            return (value & 1) != 0 ? ~(value >> 1) : value >> 1
        }

        while i < encoded.endIndex {
            lat += readChunk()
            lng += readChunk()
            result.append((Double(lat) / 1e5, Double(lng) / 1e5))
        }
        return result
    }
}

// MARK: - Router

struct EvacuationRouter: Sendable {

    static let apiKey = "AIzaSyD2S0REeAlSuzkJVDAd8oW9cAk18IgmztQ"

    private func haversineKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let dlat = (lat2 - lat1) * .pi / 180
        let dlon = (lon2 - lon1) * .pi / 180
        let a = sin(dlat/2)*sin(dlat/2) + cos(lat1 * .pi/180)*cos(lat2 * .pi/180)*sin(dlon/2)*sin(dlon/2)
        return 2 * atan2(sqrt(a), sqrt(1 - a)) * 6371.0
    }

    // Generate 12 candidate exit points spread around the safe perimeter
    private func generateCandidates(
        userLat: Double, userLon: Double,
        epicLat: Double, epicLon: Double,
        dangerRadiusKm: Double
    ) -> [(lat: Double, lon: Double)] {
        let bearingRad = atan2(
            sin((userLon - epicLon) * .pi/180) * cos(userLat * .pi/180),
            cos(epicLat * .pi/180) * sin(userLat * .pi/180) -
            sin(epicLat * .pi/180) * cos(userLat * .pi/180) * cos((userLon - epicLon) * .pi/180)
        )
        let awayDeg = (bearingRad * 180 / .pi + 180).truncatingRemainder(dividingBy: 360)
        let angularDist = (dangerRadiusKm * 1.1) / 6371.0

        return (0..<12).map { i in
            let angleDeg = awayDeg + Double(i) * 30
            let rad = angleDeg * .pi / 180
            let lat1 = userLat * .pi / 180
            let lon1 = userLon * .pi / 180
            let dlat = asin(sin(lat1)*cos(angularDist) + cos(lat1)*sin(angularDist)*cos(rad))
            let dlon = lon1 + atan2(sin(rad)*sin(angularDist)*cos(lat1), cos(angularDist) - sin(lat1)*sin(dlat))
            return (dlat * 180 / .pi, (dlon * 180 / .pi + 540).truncatingRemainder(dividingBy: 360) - 180)
        }
    }

    private struct FetchedRoute: Sendable {
        let polyline: [(Double, Double)]
        let steps: [String]
        let distanceKm: Double
        let durationMin: Double
    }

    private func fetchDirections(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ) async -> FetchedRoute? {
        let urlStr = "https://maps.googleapis.com/maps/api/directions/json"
            + "?origin=\(startLat),\(startLon)"
            + "&destination=\(endLat),\(endLon)"
            + "&mode=driving"
            + "&key=\(Self.apiKey)"
        guard let url = URL(string: urlStr) else { return nil }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            let decoded = try JSONDecoder().decode(DirectionsResponse.self, from: data)
            guard decoded.status == "OK",
                  let route = decoded.routes.first,
                  let leg = route.legs.first,
                  let poly = route.overview_polyline else { return nil }
            guard !leg.steps.contains(where: { $0.maneuver == "ferry" }) else { return nil }
            let coords = PolylineDecoder.decode(poly.points)
            guard !coords.isEmpty else { return nil }
            let steps = leg.steps.compactMap { step -> String? in
                guard let html = step.html_instructions else { return nil }
                return html
                    .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                    .replacingOccurrences(of: "&nbsp;", with: " ")
                    .replacingOccurrences(of: "&amp;", with: "&")
                    .trimmingCharacters(in: .whitespaces)
            }.filter { !$0.isEmpty }
            return FetchedRoute(
                polyline: coords,
                steps: steps,
                distanceKm: Double(leg.distance.value) / 1000.0,
                durationMin: Double(leg.duration.value) / 60.0
            )
        } catch { return nil }
    }

    func fetchEvacuationRoute(
        userLat: Double, userLon: Double,
        epicenterLat: Double, epicenterLon: Double,
        dangerRadiusKm: Double
    ) async -> EvacuationRoute? {
        let candidates = generateCandidates(
            userLat: userLat, userLon: userLon,
            epicLat: epicenterLat, epicLon: epicenterLon,
            dangerRadiusKm: dangerRadiusKm
        )

        let fetched: [FetchedRoute] = await withTaskGroup(of: FetchedRoute?.self) { group in
            for c in candidates {
                group.addTask { [self] in
                    await self.fetchDirections(
                        startLat: userLat, startLon: userLon,
                        endLat: c.lat, endLon: c.lon
                    )
                }
            }
            var out: [FetchedRoute] = []
            for await r in group { if let r { out.append(r) } }
            return out
        }

        func cumulativeKm(_ poly: [(Double, Double)]) -> Double {
            zip(poly, poly.dropFirst()).reduce(0.0) { $0 + haversineKm($1.0.0, $1.0.1, $1.1.0, $1.1.1) }
        }

        func firstExitIndex(_ poly: [(Double, Double)]) -> Int? {
            poly.indices.first { haversineKm(poly[$0].0, poly[$0].1, epicenterLat, epicenterLon) > dangerRadiusKm }
        }

        struct Scored { let route: FetchedRoute; let score: Double }

        let scored: [Scored] = fetched.compactMap { r in
            guard r.polyline.count >= 2, let exitIdx = firstExitIndex(r.polyline) else { return nil }
            let exit = r.polyline[exitIdx]
            let exitDist = haversineKm(exit.0, exit.1, epicenterLat, epicenterLon)
            let toExit = cumulativeKm(Array(r.polyline.prefix(exitIdx + 1)))
            return Scored(route: r, score: toExit / max(exitDist - dangerRadiusKm, 0.1))
        }

        guard let best = scored.min(by: { $0.score < $1.score })?.route else { return nil }

        return EvacuationRoute(
            coordinates: best.polyline.map { (lat: $0.0, lon: $0.1) },
            steps: best.steps,
            distanceKm: best.distanceKm,
            walkMin: best.distanceKm / 5.0 * 60,
            runMin: best.distanceKm / 10.0 * 60,
            cycleMin: best.distanceKm / 15.0 * 60,
            motorMin: best.distanceKm / 40.0 * 60,
            durationMin: best.durationMin
        )
    }
}
