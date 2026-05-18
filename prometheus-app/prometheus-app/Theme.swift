//
//  Theme.swift
//  prometheus-app
//
//  Created by Pelangi Masita Wati on 03/05/26.
//

import SwiftUI

extension Color {
    static let prometheusBlue = Color(red: 0.0, green: 0.75, blue: 1.0)
    #if canImport(UIKit)
    static let appBackground = Color(UIColor { tc in
        tc.userInterfaceStyle == .dark ? .black : UIColor(white: 0.96, alpha: 1)
    })
    static let cardBackground = Color(UIColor { tc in
        tc.userInterfaceStyle == .dark ? UIColor(white: 0.10, alpha: 1) : .white
    })
    #else
    static let appBackground = Color.black
    static let cardBackground = Color(white: 0.1)
    #endif
}

extension Font {
    static func inter(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom("Inter", size: size).weight(weight)
    }
}

extension View {
    func inter(_ size: CGFloat, weight: Font.Weight = .regular) -> some View {
        self.font(.inter(size, weight: weight))
    }
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
