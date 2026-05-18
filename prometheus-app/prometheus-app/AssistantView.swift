//
//  AssistantView.swift
//  prometheus-app
//

import SwiftUI
import PhotosUI
import LiteRTLMDownloader

// MARK: - Conversation model

struct Conversation: Identifiable, Codable {
    var id = UUID()
    var messages: [ChatMessage]
}

private let conversationsKey = "prometheus_conversations"

private func saveConversations(_ conversations: [Conversation]) {
    if let data = try? JSONEncoder().encode(conversations) {
        UserDefaults.standard.set(data, forKey: conversationsKey)
    }
}

private func loadConversations() -> [Conversation] {
    guard let data = UserDefaults.standard.data(forKey: conversationsKey),
          let decoded = try? JSONDecoder().decode([Conversation].self, from: data),
          !decoded.isEmpty
    else { return [Conversation(messages: [])] }
    return decoded
}

// MARK: - View

enum AssistantPanel { case chat, vision }

struct AssistantView: View {
    @Environment(InferenceManager.self) private var inference
    @Environment(BMKGPollingService.self) private var pollingService
    @AppStorage("isDarkMode") private var isDarkMode = false
    @AppStorage("tutorialSeen_assistant") private var tutorialSeen = false
    @State private var query = ""
    @FocusState private var inputFocused: Bool
    @State private var conversations: [Conversation] = loadConversations()
    @State private var activeIndex = 0
    @State private var showSidebar = false
    @State private var activePanel: AssistantPanel = .chat
    @State private var showTutorial = false
    @State private var attachedImage: UIImage? = nil
    @State private var photosPickerItem: PhotosPickerItem? = nil
    @State private var showImageSourceDialog = false
    @State private var showPhotosPicker = false
    @State private var showCameraSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                VStack(spacing: 0) {
                    modeSelector
                    Divider().background(Color.prometheusBlue.opacity(0.15))
                    stateContent
                }
            }
            .navigationTitle("Gemma Assistant")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar { toolbarContent }
        }
        .task { await inference.start() }
        .onAppear {
            if !tutorialSeen { tutorialSeen = true; showTutorial = true }
        }
        .onChange(of: pollingService.latestEarthquakeEvent?.DateTime) { _, _ in
            inference.currentEarthquakeEvent = pollingService.latestEarthquakeEvent
        }
        .overlay {
            if showTutorial {
                TutorialOverlay(tabName: "Assistant", steps: TutorialContent.assistant) {
                    showTutorial = false
                }
            }
        }
        .sheet(isPresented: $showSidebar) { sidebarView }
        .sheet(isPresented: $showCameraSheet) { CameraPickerView(onCapture: { img in attachedImage = img }) }
        .photosPicker(isPresented: $showPhotosPicker, selection: $photosPickerItem, matching: .images)
        .confirmationDialog("Attach Image", isPresented: $showImageSourceDialog) {
            Button("Camera") { showCameraSheet = true }
            Button("Photo Library") { showPhotosPicker = true }
            Button("Cancel", role: .cancel) {}
        }
        .onChange(of: photosPickerItem) { _, item in
            Task {
                if let data = try? await item?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    attachedImage = img
                }
            }
        }
        .onChange(of: inference.isGenerating) { _, generating in
            if !generating { syncToConversation() }
        }
    }

    // MARK: - State routing

    @ViewBuilder
    private var stateContent: some View {
        switch activePanel {
        case .vision:
            TalkPanel()
        case .chat:
            switch inference.modelState {
            case .notDownloaded:  downloadView
            case .downloading:    progressView
            case .loading:        loadingView
            case .ready:          chatViewContent
            case .error(let msg): errorView(msg)
            }
        }
    }

    // MARK: - Download screen

    private var downloadView: some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer(minLength: 40)

                Image(systemName: "square.and.arrow.down.fill")
                    .font(.system(size: 56))
                    .foregroundColor(.prometheusBlue.opacity(0.45))

                VStack(spacing: 6) {
                    Text("GEMMA 4 REQUIRED")
                        .inter(17, weight: .bold)
                        .foregroundColor(.primary)
                    Text("Gemma 4 E2B  ·  ~2.4 GB  ·  on-device  ·  offline")
                        .inter(12)
                        .foregroundColor(.secondary)
                }

                VStack(alignment: .leading, spacing: 10) {
                    DownloadFeatureRow(icon: "cross.case", text: "First aid & injury guidance")
                    DownloadFeatureRow(icon: "house.and.flag", text: "Shelter & evacuation planning")
                    DownloadFeatureRow(icon: "drop", text: "Water sourcing & supplies")
                    DownloadFeatureRow(icon: "exclamationmark.triangle", text: "Indonesia hazards — quakes, tsunamis, floods")
                    DownloadFeatureRow(icon: "antenna.radiowaves.left.and.right", text: "Works fully offline after download")
                }
                .padding()
                .background(Color.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.prometheusBlue.opacity(0.25), lineWidth: 1))

                Button(action: { Task { await inference.downloadAndLoad() } }) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.down.circle.fill")
                        Text("DOWNLOAD GEMMA 4  (~2.4 GB)")
                            .inter(12, weight: .bold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.prometheusBlue.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.6), lineWidth: 1))
                    .foregroundColor(.prometheusBlue)
                }
                .buttonStyle(.plain)

                Text("Requires a Wi-Fi connection. Model is stored on-device and never leaves the phone.")
                    .inter(11)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)

                Spacer(minLength: 40)
            }
            .padding()
        }
        .scrollContentBackground(.hidden)
    }

    // MARK: - Download progress screen

    private var progressView: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "arrow.down.circle")
                .font(.system(size: 56))
                .foregroundColor(.prometheusBlue)
                .symbolEffect(.pulse)

            Text("DOWNLOADING GEMMA 4")
                .inter(12, weight: .bold)
                .foregroundColor(.primary)

            VStack(spacing: 8) {
                ProgressView(value: inference.downloader.progress)
                    .tint(.prometheusBlue)
                    .frame(maxWidth: 280)

                let dl = inference.downloader.downloadedBytes / 1_000_000
                let total = (inference.downloader.totalBytes ?? 2_583_085_056) / 1_000_000
                Text("\(dl) MB  /  \(total) MB")
                    .inter(12)
                    .foregroundColor(.secondary)

                Text(String(format: "%.0f%%", inference.downloader.progress * 100))
                    .inter(12, weight: .bold)
                    .foregroundColor(.prometheusBlue)
            }

            Button(action: { inference.cancelDownload() }) {
                Text("CANCEL")
                    .inter(12, weight: .bold)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .foregroundColor(.red)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(Color.red.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.plain)

            Spacer()
        }
    }

    // MARK: - Loading screen

    private var loadingView: some View {
        VStack(spacing: 18) {
            Spacer()
            ProgressView()
                .tint(.prometheusBlue)
                .scaleEffect(1.4)
            Text("LOADING MODEL")
                .inter(12, weight: .bold)
                .foregroundColor(.primary)
            Text("Loading Gemma 4 into memory — this may take a moment.")
                .inter(12)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }

    // MARK: - Error screen

    private func errorView(_ message: String) -> some View {
        let isSimulatorError = message.contains("physical iPhone")
        return VStack(spacing: 20) {
            Spacer()
            Image(systemName: isSimulatorError ? "iphone.slash" : "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundColor(.orange)
            Text(isSimulatorError ? "SIMULATOR" : "ERROR")
                .inter(12, weight: .bold)
                .foregroundColor(.primary)
            Text(message)
                .inter(12)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            if !isSimulatorError {
                Button(action: { Task { await inference.retryLoad() } }) {
                    Text("RETRY")
                        .inter(12, weight: .bold)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color.prometheusBlue.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 1))
                        .foregroundColor(.prometheusBlue)
                }
                .buttonStyle(.plain)
                Button(action: { Task { await inference.redownloadAndLoad() } }) {
                    Text("RE-DOWNLOAD MODEL")
                        .inter(12, weight: .bold)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
    }

    // MARK: - Chat screen

    private var chatViewContent: some View {
        messageList
            .safeAreaInset(edge: .bottom) { inputBar }
    }

    private var modeSelector: some View {
        HStack(spacing: 8) {
            Spacer()
            ModeChipButton(label: "CHAT", active: activePanel == .chat) {
                switchPanel(.chat)
            }
            ModeChipButton(label: "TALK", active: activePanel == .vision) {
                switchPanel(.vision)
            }
            Spacer()
        }
        .padding(.vertical, 8)
        .background(Color.cardBackground)
    }

    private func switchPanel(_ panel: AssistantPanel) {
        guard panel != activePanel else { return }
        syncToConversation()
        activePanel = panel
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    if inference.messages.isEmpty {
                        emptyStateHint
                    }
                    ForEach(inference.messages) { msg in
                        MessageBubble(message: msg) {
                            inference.speak(msg.text)
                        }
                        .id(msg.id)
                    }
                    Color.clear.frame(height: 8).id("bottom")
                }
                .padding(.top, 12)
            }
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: inference.messages.count) { _, _ in
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("bottom") }
            }
            .onChange(of: inference.isGenerating) { _, generating in
                if !generating {
                    withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("bottom") }
                }
            }
        }
    }

    private var emptyStateHint: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 60)
            Image(systemName: "bubble.left.and.bubble.right.fill")
                .font(.system(size: 40))
                .foregroundColor(.prometheusBlue.opacity(0.3))
            Text("Ask something...")
                .inter(12)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 60)
        }
        .frame(maxWidth: .infinity)
    }

    private var inputBar: some View {
        VStack(spacing: 0) {
            if let img = attachedImage {
                HStack(spacing: 8) {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 44, height: 44)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.prometheusBlue.opacity(0.4), lineWidth: 1))
                    Text("Image attached")
                        .inter(11).foregroundColor(.secondary)
                    Spacer()
                    Button(action: { attachedImage = nil; photosPickerItem = nil }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 2)
            }

            HStack(spacing: 0) {
                Button(action: { showImageSourceDialog = true }) {
                    Image(systemName: attachedImage != nil ? "photo.fill" : "camera")
                        .font(.body)
                        .padding(12)
                        .foregroundColor(attachedImage != nil ? .prometheusBlue : .gray.opacity(0.6))
                }
                .buttonStyle(.plain)
                .disabled(activePanel == .vision)

                Divider()
                    .background(Color.prometheusBlue.opacity(0.15))
                    .frame(height: 32)

                TextField(
                    "Ask about survival, first aid, evacuation...",
                    text: $query,
                    axis: .vertical
                )
                .textFieldStyle(.plain)
                .inter(12)
                .lineLimit(1...4)
                .padding(12)
                .focused($inputFocused)
                .disabled(inference.isGenerating)
                .onSubmit { sendMessage() }

                Divider()
                    .background(Color.prometheusBlue.opacity(0.2))
                    .frame(height: 44)

                Button(action: {
                    if inference.isGenerating {
                        inference.cancelGeneration(mode: .survival)
                    } else {
                        sendMessage()
                    }
                }) {
                    Image(systemName: inference.isGenerating ? "stop.circle.fill" : "bolt.fill")
                        .font(.body)
                        .padding(14)
                        .foregroundColor(
                            (query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !inference.isGenerating)
                                ? .gray : .prometheusBlue
                        )
                }
                .disabled(query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !inference.isGenerating)
            }
        }
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(Color.appBackground)
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            HStack(spacing: 12) {
                Button(action: { showSidebar.toggle() }) {
                    Image(systemName: "line.3.horizontal")
                        .foregroundColor(.prometheusBlue)
                }
                Button(action: { isDarkMode.toggle() }) {
                    Image(systemName: isDarkMode ? "sun.max" : "moon")
                        .font(.caption)
                        .foregroundColor(.prometheusBlue)
                }
                Button(action: { showTutorial = true }) {
                    Image(systemName: "questionmark.circle")
                        .font(.caption)
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
    }

    // MARK: - Conversation management

    private var sidebarView: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 0) {
                    Button(action: { createNewConversation(); showSidebar = false }) {
                        HStack {
                            Image(systemName: "plus")
                            Text("New conversation")
                                .inter(12, weight: .bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(Color.prometheusBlue)
                        .foregroundColor(.black)
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)

                    Divider().background(Color.prometheusBlue.opacity(0.15)).padding(.vertical, 8)

                    List {
                        ForEach(Array(conversations.enumerated()), id: \.element.id) { index, conv in
                            let title = conv.messages.first(where: { $0.role == .user })?.text.prefix(40)
                                ?? "New conversation"
                            let isActive = index == activeIndex
                            HStack {
                                Text(String(title))
                                    .inter(12)
                                    .foregroundColor(isActive ? .prometheusBlue : .primary)
                                    .fontWeight(isActive ? .bold : .regular)
                                Spacer()
                                if conversations.count > 1 {
                                    Button {
                                        conversations.remove(at: index)
                                        if activeIndex >= conversations.count {
                                            activeIndex = conversations.count - 1
                                        }
                                        if conversations.isEmpty {
                                            conversations = [Conversation(messages: [])]
                                            activeIndex = 0
                                        }
                                        saveConversations(conversations)
                                    } label: {
                                        Image(systemName: "xmark")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                            .onTapGesture { switchToConversation(at: index) }
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Conversations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { showSidebar = false }
                        .foregroundColor(.prometheusBlue)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func syncToConversation() {
        guard activeIndex < conversations.count else { return }
        conversations[activeIndex].messages = inference.messages.filter { !$0.isStreaming }
        saveConversations(conversations)
    }

    private func createNewConversation() {
        syncToConversation()
        inference.clearHistory(mode: .survival)
        conversations.append(Conversation(messages: []))
        activeIndex = conversations.count - 1
        saveConversations(conversations)
    }

    private func switchToConversation(at index: Int) {
        guard index < conversations.count else { return }
        syncToConversation()
        inference.clearHistory(mode: .survival)
        activeIndex = index
        inference.restoreMessages(conversations[index].messages)
        showSidebar = false
    }

    // MARK: - Actions

    private func sendMessage() {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        query = ""
        inputFocused = false
        let imageToSend = attachedImage
        attachedImage = nil
        photosPickerItem = nil
        Task {
            if let img = imageToSend,
               let data = img.resized(toMaxDimension: 512).jpegData(compressionQuality: 0.7) {
                var visionContext = ""
                await inference.describeImage(data) { token in visionContext = token }
                let combined = visionContext.isEmpty ? text : "\(text)\n\n[Image shows: \(visionContext)]"
                await inference.send(combined, mode: .survival)
            } else {
                await inference.send(text, mode: .survival)
            }
        }
    }
}

// MARK: - Sub-views

private struct MessageBubble: View {
    let message: ChatMessage
    let onSpeak: () -> Void

    var isUser: Bool { message.role == .user }

    private var renderedText: some View {
        let raw = message.text + (message.isStreaming ? "▋" : "")
        if !isUser, !message.isStreaming,
           let attributed = try? AttributedString(
               markdown: raw,
               options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
           ) {
            return AnyView(Text(attributed).inter(12).foregroundColor(.primary))
        }
        return AnyView(Text(raw).inter(12).foregroundColor(.primary))
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            if isUser { Spacer(minLength: 48) }

            VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                Text(isUser ? "YOU" : "GEMMA 4")
                    .inter(11, weight: .bold)
                    .foregroundColor(isUser ? .prometheusBlue : .gray)
                    .padding(.horizontal, 4)

                renderedText
                    .textSelection(.enabled)
                    .padding(12)
                    .background(isUser ? Color.prometheusBlue.opacity(0.18) : Color.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isUser ? Color.prometheusBlue.opacity(0.45) : Color.prometheusBlue.opacity(0.25), lineWidth: 1)
                    )

                if !isUser && !message.isStreaming && !message.text.isEmpty {
                    Button(action: onSpeak) {
                        HStack(spacing: 4) {
                            Image(systemName: "waveform").font(.caption2)
                            Text("SPEAK").inter(11, weight: .bold)
                        }
                        .foregroundColor(.prometheusBlue.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 4)
                }
            }

            if !isUser { Spacer(minLength: 48) }
        }
        .padding(.horizontal)
    }
}

private struct ModeChipButton: View {
    let label: String
    let active: Bool
    var activeColor: Color = .prometheusBlue
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .inter(11, weight: .bold)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(active ? activeColor.opacity(0.2) : Color.clear)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(active ? activeColor.opacity(0.6) : Color.gray.opacity(0.3), lineWidth: 1))
                .foregroundColor(active ? activeColor : .gray)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Camera capture sheet (for image attachment)

private struct CameraPickerView: View {
    let onCapture: (UIImage) -> Void
    @State private var camera = CameraService()
    @State private var isCapturing = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                #if canImport(UIKit)
                CameraPreviewView(session: camera.session)
                    .ignoresSafeArea()
                #endif
                VStack {
                    Spacer()
                    Button(action: capture) {
                        ZStack {
                            Circle().fill(Color.white.opacity(0.2)).frame(width: 72, height: 72)
                            Circle().strokeBorder(Color.white.opacity(0.6), lineWidth: 3).frame(width: 72, height: 72)
                            if isCapturing {
                                ProgressView().tint(.white).scaleEffect(1.3)
                            } else {
                                Image(systemName: "camera.fill").font(.title2).foregroundColor(.white)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(isCapturing)
                    .padding(.bottom, 52)
                }
            }
            .navigationTitle("Take Photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.black.opacity(0.55), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { camera.stop(); dismiss() }
                        .foregroundColor(.white)
                }
            }
        }
        .onAppear { camera.start() }
        .onDisappear { camera.stop() }
    }

    private func capture() {
        isCapturing = true
        Task {
            if let cgImage = await camera.capturePhoto() {
                #if canImport(UIKit)
                onCapture(UIImage(cgImage: cgImage))
                #endif
            }
            isCapturing = false
            dismiss()
        }
    }
}

private struct DownloadFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(.prometheusBlue.opacity(0.7))
                .frame(width: 16)
            Text(text)
                .inter(12)
                .foregroundColor(.secondary)
        }
    }
}
