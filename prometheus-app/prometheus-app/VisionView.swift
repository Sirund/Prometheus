//
//  VisionView.swift
//  prometheus-app
//

import SwiftUI

struct VisionView: View {
    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Camera viewfinder placeholder
                    ZStack {
                        Color.cardBackground
                        VStack(spacing: 16) {
                            Image(systemName: "camera.viewfinder")
                                .font(.system(size: 64))
                                .foregroundColor(.prometheusBlue.opacity(0.45))
                            Text("CAMERA FEED")
                                .font(.caption.bold().monospaced())
                                .foregroundColor(.white)
                            Text("AVCaptureSession  →  Gemma 4 Vision  →  Speech")
                                .font(.caption2.monospaced())
                                .foregroundColor(.gray)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                    .shadow(color: .black.opacity(0.4), radius: 10, x: 0, y: 5)
                    .padding(.horizontal)
                    .padding(.top)

                    // Response strip
                    HStack(spacing: 10) {
                        Image(systemName: "waveform")
                            .font(.body)
                            .foregroundColor(.prometheusBlue.opacity(0.5))
                        Text("Spoken response will play here — no screen reading required")
                            .font(.caption.monospaced())
                            .foregroundColor(.gray)
                            .lineLimit(2)
                        Spacer()
                    }
                    .padding(12)
                    .background(Color.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.2), lineWidth: 1))
                    .padding(.horizontal)
                    .padding(.top, 12)

                    Spacer()

                    // Accessibility note
                    VStack(alignment: .leading, spacing: 6) {
                        Text("VISION ACCESSIBILITY MODE")
                            .font(.caption2.bold().monospaced())
                            .foregroundColor(.prometheusBlue)
                        Text("Point the camera at surroundings, signage, or injuries. Gemma 4 describes what it sees in calm spoken language — no typing or screen reading needed. Designed for visually impaired users and high-stress situations.")
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                            .lineSpacing(4)
                    }
                    .padding()
                    .background(Color.cardBackground.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 1))
                    .padding(.horizontal)

                    Spacer()

                    // Large tap target — accessibility-first design
                    Button(action: { /* TODO: trigger AVCaptureSession + Gemma vision */ }) {
                        VStack(spacing: 10) {
                            Image(systemName: "camera.circle.fill")
                                .font(.system(size: 60))
                            Text("TAP TO DESCRIBE SURROUNDINGS")
                                .font(.caption.bold().monospaced())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(28)
                        .background(Color.prometheusBlue.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 2))
                        .foregroundColor(.prometheusBlue)
                    }
                    .buttonStyle(.plain)
                    .padding()
                    .disabled(true)
                }
            }
            .navigationTitle("Vision Assist")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Label("Accessibility", systemImage: "figure.wave")
                        .font(.caption.monospaced())
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
    }
}
