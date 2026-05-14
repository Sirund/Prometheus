//
//  AssistantView.swift
//  prometheus-app
//

import SwiftUI

struct AssistantView: View {
    @State private var query = ""

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    ModeIndicatorBar()

                    // Chat area placeholder
                    Spacer()
                    VStack(spacing: 14) {
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.prometheusBlue.opacity(0.35))
                        Text("SURVIVAL ASSISTANT")
                            .font(.headline.bold().monospaced())
                            .foregroundColor(.white)
                        Text("Gemma 4  ·  on-device  ·  offline")
                            .font(.caption.monospaced())
                            .foregroundColor(.gray)
                        VStack(spacing: 4) {
                            CapabilityPill(icon: "cross.case", label: "first aid")
                            CapabilityPill(icon: "house.and.flag", label: "shelter & evacuation")
                            CapabilityPill(icon: "drop", label: "water & supplies")
                            CapabilityPill(icon: "exclamationmark.triangle", label: "Indonesia hazards")
                        }
                        .padding(.top, 4)
                    }
                    Spacer()

                    // Input bar
                    HStack(spacing: 0) {
                        TextField("Ask anything about survival...", text: $query)
                            .textFieldStyle(.plain)
                            .font(.caption.monospaced())
                            .padding(12)
                            .background(Color.cardBackground)
                            .disabled(true)

                        Divider()
                            .background(Color.prometheusBlue.opacity(0.2))
                            .frame(height: 44)

                        Button(action: { /* TODO: TTS playback */ }) {
                            Image(systemName: "waveform")
                                .padding(14)
                                .background(Color.cardBackground)
                                .foregroundColor(.gray)
                        }
                        .disabled(true)

                        Button(action: { /* TODO: send query */ }) {
                            Image(systemName: "bolt.fill")
                                .padding(14)
                                .background(Color.cardBackground)
                                .foregroundColor(.gray)
                        }
                        .disabled(true)
                    }
                    .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                    .padding()
                }
            }
            .navigationTitle("Survival Assistant")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.gray)
                            .frame(width: 6, height: 6)
                        Text("MODEL NOT LOADED")
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                    }
                }
            }
        }
    }
}

private struct ModeIndicatorBar: View {
    var body: some View {
        HStack {
            Spacer()
            ModeChip(label: "SURVIVAL CHAT", active: true)
            ModeChip(label: "EMERGENCY BRIEF", active: false)
            Spacer()
        }
        .padding(.vertical, 8)
        .background(Color.cardBackground)
        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 0.5))
    }
}

private struct ModeChip: View {
    let label: String
    let active: Bool

    var body: some View {
        Text(label)
            .font(.caption2.bold().monospaced())
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(active ? Color.prometheusBlue.opacity(0.2) : Color.clear)
            .overlay(Rectangle().stroke(active ? Color.prometheusBlue.opacity(0.6) : Color.gray.opacity(0.3), lineWidth: 1))
            .foregroundColor(active ? .prometheusBlue : .gray)
    }
}

private struct CapabilityPill: View {
    let icon: String
    let label: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption2)
            Text(label)
                .font(.caption2.monospaced())
        }
        .foregroundColor(.gray)
    }
}
