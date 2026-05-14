import SwiftUI

struct VisionView: View {
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Text("Vision Module Active")
                .font(.monospaced(.body)())
                .foregroundColor(.blue)
        }
    }
}
