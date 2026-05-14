//
//  MonitorView.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 06/05/26.
//

import SwiftUI

struct MonitorView: View {
    @State private var dangerLevel: DangerLevel = .none
    @State private var lastRefresh: String = "Not yet refreshed"

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        DangerStatusBanner(level: dangerLevel)

                        SectionHeader(title: "LATEST BMKG EVENT")
                        BMKGEventCard(
                            magnitude: "--",
                            location: "Waiting for data...",
                            depth: "--",
                            felt: "--",
                            potential: "--",
                            timestamp: lastRefresh
                        )

                        SectionHeader(title: "ALARM & BRIEFING")
                        AlarmStatusCard()

                        SectionHeader(title: "RECENT EVENTS")
                        Text("No data loaded. Tap refresh to poll BMKG.")
                            .font(.caption.monospaced())
                            .foregroundColor(.gray)
                            .padding(.horizontal, 4)

                        Button(action: { /* TODO: BMKGMonitor.fetch() */ }) {
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
        }
    }
}

// MARK: - Supporting views

enum DangerLevel { case none, watch, danger }

struct DangerStatusBanner: View {
    let level: DangerLevel

    var color: Color {
        switch level {
        case .none:   return .prometheusBlue
        case .watch:  return .orange
        case .danger: return .red
        }
    }
    var label: String {
        switch level {
        case .none:   return "NO ACTIVE ALERTS"
        case .watch:  return "WATCH — MONITOR CLOSELY"
        case .danger: return "DANGER — TAKE ACTION NOW"
        }
    }
    var icon: String {
        switch level {
        case .none:   return "checkmark.shield.fill"
        case .watch:  return "exclamationmark.triangle.fill"
        case .danger: return "alarm.fill"
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
    let potential: String
    let timestamp: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("M \(magnitude)")
                        .font(.title.bold().monospaced())
                        .foregroundColor(.prometheusBlue)
                    Text(location)
                        .font(.caption.monospaced())
                        .foregroundColor(.white)
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
                    .font(.caption2.monospaced())
                    .foregroundColor(.gray)
            }
        }
        .padding()
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
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
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
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
                .font(.caption2.monospaced())
                .foregroundColor(.gray)
            Spacer()
            Text(status)
                .font(.caption2.bold().monospaced())
                .foregroundColor(statusColor)
        }
    }
}
