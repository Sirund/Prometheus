import SwiftUI

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

extension Color {
    static let prometheusBlue = Color(red: 0.31, green: 0.76, blue: 0.97)
    static let background = Color(white: 0.07)
    static let surface = Color(white: 0.12)
    static let surfaceElevated = Color(white: 0.17)
    static let danger = Color(red: 0.96, green: 0.26, blue: 0.21)
    static let warning = Color(red: 1.0, green: 0.6, blue: 0.0)
    static let success = Color(red: 0.4, green: 0.73, blue: 0.42)
    static let textPrimary = Color(white: 0.88)
    static let textSecondary = Color(white: 0.62)
    static let cardBackground = Color(white: 0.12)
}
