//
//  Theme.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 03/05/26.
//

import SwiftUI

extension Color {
    static let prometheusBlue = Color(red: 0.0, green: 0.75, blue: 1.0)
    static let darkBackground = Color.black
    static let cardBackground = Color(white: 0.1)
}

struct TacticalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding()
            .background(configuration.isPressed ? Color.prometheusBlue.opacity(0.3) : Color.cardBackground)
            .contentShape(Rectangle())
            .border(Color.prometheusBlue.opacity(0.5), width: 1)
    }
}
