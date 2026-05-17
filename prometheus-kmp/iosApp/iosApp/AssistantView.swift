import SwiftUI

struct Conversation: Identifiable, Codable {
    let id = UUID()
    var title: String
    var messages: [ChatMessage]
}

struct AssistantView: View {
    @Environment(InferenceManager.self) private var manager
    @State private var query: String = ""
    @State private var conversations: [Conversation] = loadConversations()
    @State private var activeIndex = 0
    @State private var showSidebar = false

    private var chatHistory: [ChatMessage] {
        guard activeIndex < conversations.count else { return [] }
        return conversations[activeIndex].messages
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    ModeIndicatorBar()

                    if chatHistory.isEmpty {
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
                    } else {
                        ScrollViewReader { proxy in
                            ScrollView {
                                VStack(alignment: .leading, spacing: 16) {
                                    ForEach(chatHistory) { message in
                                        ChatBubble(message: message)
                                    }
                                }
                                .padding()
                            }
                        }
                    }

                    HStack(spacing: 0) {
                        TextField("Ask anything about survival...", text: $query)
                            .textFieldStyle(.plain)
                            .font(.caption.monospaced())
                            .padding(12)
                            .background(Color.cardBackground)
                            .disabled(!manager.isModelLoaded)

                        Divider()
                            .background(Color.prometheusBlue.opacity(0.2))
                            .frame(height: 44)

                        Button(action: { /* TODO: TTS playback */ }) {
                            Image(systemName: "waveform")
                                .padding(14)
                                .background(Color.cardBackground)
                                .foregroundColor(manager.isModelLoaded ? .prometheusBlue : .gray)
                        }
                        .disabled(!manager.isModelLoaded)

                        Button(action: { sendToGemma() }) {
                            Image(systemName: "bolt.fill")
                                .padding(14)
                                .background(manager.isModelLoaded ? Color.prometheusBlue : Color.cardBackground)
                                .foregroundColor(manager.isModelLoaded ? .black : .gray)
                        }
                        .disabled(!manager.isModelLoaded || query.isEmpty)
                    }
                    .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                    .padding()
                }
            }
            .navigationTitle("Survival Assistant")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showSidebar.toggle() }) {
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.prometheusBlue)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(manager.isModelLoaded ? .green : .orange)
                            .frame(width: 6, height: 6)
                        Text(manager.statusMessage)
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                    }
                }
            }
        }
        .sheet(isPresented: $showSidebar) {
            sidebarView
        }
        .onAppear {
            Task { await manager.setupGemma() }
        }
        .onChange(of: conversations) { _, _ in
            saveConversations(conversations)
        }
    }

    private var sidebarView: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                    Text("Conversations")
                        .font(.title3.bold().monospaced())
                        .foregroundColor(.prometheusBlue)
                        .padding()

                    Button(action: {
                        conversations.append(Conversation(title: "New conversation", messages: []))
                        activeIndex = conversations.count - 1
                        showSidebar = false
                    }) {
                        HStack {
                            Image(systemName: "plus")
                            Text("New conversation")
                                .font(.caption.bold().monospaced())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(Color.prometheusBlue)
                        .foregroundColor(.black)
                    }
                    .padding(.horizontal)

                    Divider().background(Color.prometheusBlue.opacity(0.15)).padding(.vertical, 8)

                    List {
                        ForEach(Array(conversations.enumerated()), id: \.element.id) { index, conv in
                            let title = conv.messages.first(where: { $0.isUser })?.text.prefix(40) ?? "New conversation"
                            let isActive = index == activeIndex
                            HStack {
                                Text(String(title))
                                    .font(.caption.monospaced())
                                    .foregroundColor(isActive ? .prometheusBlue : .white)
                                    .fontWeight(isActive ? .bold : .regular)
                                Spacer()
                                if conversations.count > 1 {
                                    Button(action: {
                                        conversations.remove(at: index)
                                        if activeIndex >= conversations.count {
                                            activeIndex = conversations.count - 1
                                        }
                                        if conversations.isEmpty {
                                            conversations = [Conversation(title: "New conversation", messages: [])]
                                            activeIndex = 0
                                        }
                                    }) {
                                        Image(systemName: "xmark")
                                            .font(.caption2)
                                            .foregroundColor(.gray)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                activeIndex = index
                                showSidebar = false
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Conversations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { showSidebar = false }
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    func sendToGemma() {
        let userText = query
        query = ""

        let history = chatHistory
        let aiIndex = history.count + 1

        conversations[activeIndex].messages.append(ChatMessage(text: userText, isUser: true))
        conversations[activeIndex].messages.append(ChatMessage(text: "...", isUser: false))

        Task {
            await manager.sendMessage(userText, history: history) { updatedText in
                if aiIndex < conversations[activeIndex].messages.count {
                    conversations[activeIndex].messages[aiIndex] = ChatMessage(text: updatedText, isUser: false)
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

private let conversationsKey = "saved_conversations"

private func saveConversations(_ conversations: [Conversation]) {
    if let data = try? JSONEncoder().encode(conversations) {
        UserDefaults.standard.set(data, forKey: conversationsKey)
    }
}

private func loadConversations() -> [Conversation] {
    guard let data = UserDefaults.standard.data(forKey: conversationsKey),
          let decoded = try? JSONDecoder().decode([Conversation].self, from: data),
          !decoded.isEmpty
    else { return [Conversation(title: "New conversation", messages: [])] }
    return decoded
}
