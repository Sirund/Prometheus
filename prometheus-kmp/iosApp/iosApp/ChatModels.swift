import SwiftUI

struct ChatMessage: Identifiable, Codable {
    let id: UUID
    let text: String
    let isUser: Bool

    init(id: UUID = UUID(), text: String, isUser: Bool) {
        self.id = id
        self.text = text
        self.isUser = isUser
    }
}

struct ChatBubble: View {
    let message: ChatMessage

    var body: some View {
        HStack {
            if message.isUser { Spacer() }

            Text(message.text)
                .padding(12)
                .background(message.isUser ? Color.prometheusBlue : Color.cardBackground)
                .foregroundColor(message.isUser ? .black : .white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1)
                )

            if !message.isUser { Spacer() }
        }
    }
}
