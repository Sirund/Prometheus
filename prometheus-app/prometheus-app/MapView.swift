//
//  MapView.swift
//  prometheus-app
//

import SwiftUI

struct MapView: View {
    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        EvacuationStatusBanner()

                        SectionHeader(title: "EVACUATION MAP")
                        MapPlaceholder()

                        SectionHeader(title: "ROUTING DETAILS")
                        RoutingDetailsCard()

                        EvacuationInfoNote()
                    }
                    .padding()
                }
                .scrollContentBackground(.hidden)
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
    }
}

private struct EvacuationStatusBanner: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.triangle.turn.up.right.circle.fill").font(.title3)
            Text("EVACUATION ROUTING")
                .font(.caption.bold().monospaced())
            Spacer()
            Text("STANDBY")
                .font(.caption2.bold().monospaced())
        }
        .padding(12)
        .background(Color.prometheusBlue.opacity(0.1))
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.4), lineWidth: 1))
        .foregroundColor(.prometheusBlue)
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
                Text("Google Maps SDK")
                    .font(.caption.bold().monospaced())
                    .foregroundColor(.white)
                VStack(spacing: 4) {
                    Text("Epicentre pin  ·  safe-radius overlay")
                        .font(.caption2.monospaced())
                        .foregroundColor(.gray)
                    Text("Route appears when a dangerous event is classified")
                        .font(.caption2.monospaced())
                        .foregroundColor(.gray)
                }
            }
            .padding(32)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 240)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

private struct RoutingDetailsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            RouteInfoRow(label: "EPICENTRE", value: "Waiting for event...")
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "USER LOCATION", value: "Not acquired")
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "SAFE RADIUS", value: "TBD per magnitude band")
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "EXIT ROUTE", value: "No active event")
            Divider().background(Color.prometheusBlue.opacity(0.15))
            RouteInfoRow(label: "DIRECTION", value: "--")
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
    }
}

private struct EvacuationInfoNote: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("HOW IT WORKS")
                .font(.caption2.bold().monospaced())
                .foregroundColor(.prometheusBlue)
            Text("On a dangerous classification, the BMKG epicentre is pinned on the map. The shortest driving or walking route from your location to outside the hazard radius is computed via Google Maps Directions API. Gemma 4 delivers a spoken briefing with direction and what to avoid.")
                .font(.caption2.monospaced())
                .foregroundColor(.gray)
                .lineSpacing(4)
        }
        .padding()
        .background(Color.cardBackground.opacity(0.5))
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 1))
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
