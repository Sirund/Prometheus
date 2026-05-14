import SwiftUI

struct BackgroundGridPattern: View {
    var body: some View {
        Canvas { context, size in
            let step: CGFloat = 25
            for x in stride(from: 0, to: size.width, by: step) {
                for y in stride(from: 0, to: size.height, by: step) {
                    let rect = CGRect(x: x, y: y, width: 1.5, height: 1.5)
                    context.fill(Path(ellipseIn: rect), with: .color(Color.prometheusBlue.opacity(0.15)))
                }
            }
        }
        .background(Color.black)
        .ignoresSafeArea()
    }
}
