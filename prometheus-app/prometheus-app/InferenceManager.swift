//
//  InferenceManager.swift
//  prometheus-app
//

import Foundation
import LiteRTLM
import LiteRTLMDownloader
import AVFoundation

// MARK: - Chat types

enum ChatMode: Equatable {
    case survival
    case emergency
}

struct ChatMessage: Identifiable {
    let id = UUID()
    let role: Role
    var text: String
    var isStreaming: Bool = false

    enum Role { case user, assistant }
}

// MARK: - InferenceManager

@Observable
@MainActor
final class InferenceManager {

    enum ModelState: CustomStringConvertible {
        case notDownloaded
        case downloading
        case loading
        case ready
        case error(String)

        var description: String {
            switch self {
            case .notDownloaded: return "notDownloaded"
            case .downloading:   return "downloading"
            case .loading:       return "loading"
            case .ready:         return "ready"
            case .error(let m):  return "error(\(m))"
            }
        }
    }

    // MARK: Observable state

    var modelState: ModelState = .notDownloaded
    var messages: [ChatMessage] = []
    var isGenerating = false
    var isSpeaking = false

    /// Exposed directly so views can observe download progress without mirroring.
    let downloader = ModelDownloader()

    // MARK: Private

    private var engine: LMEngine?
    private var survivalConversation: LMConversation?
    private var emergencyConversation: LMConversation?
    private let synthesizer = AVSpeechSynthesizer()
    private let ttsDelegate = TTSDelegate()

    // MARK: System prompts (Arund's domain — swap these out when final prompts are ready)

    static let survivalPrompt = """
    You are a calm, practical survival assistant for Indonesia. \
    Provide guidance on first aid, shelter, water sourcing, evacuation planning, \
    and hazard awareness — earthquakes, tsunamis, volcanic eruptions, and floods. \
    Keep every answer short and actionable. \
    Write plain text only — no markdown, no bullet points, no headers.
    """

    static let emergencyPrompt = """
    You are an emergency voice briefing system. Given a hazard event, \
    respond with a spoken briefing of at most 60 words: \
    what happened, what to do right now, and what to avoid. \
    Plain text only — no markdown, no lists, no headers.
    """

    // MARK: Init

    init() {
        ttsDelegate.onFinish = { [weak self] in
            Task { @MainActor [weak self] in self?.isSpeaking = false }
        }
        synthesizer.delegate = ttsDelegate

        let alreadyDownloaded = downloader.isDownloaded(ModelRegistry.gemma4E2B)
        print("[InferenceManager] init — isDownloaded=\(alreadyDownloaded)")
        if alreadyDownloaded {
            if let path = downloader.modelPath(for: ModelRegistry.gemma4E2B) {
                print("[InferenceManager] init — model path: \(path.path)")
                let exists = FileManager.default.fileExists(atPath: path.path)
                print("[InferenceManager] init — file exists on disk: \(exists)")
            } else {
                print("[InferenceManager] init — modelPath returned nil despite isDownloaded=true")
            }
            modelState = .loading
        }
    }

    // MARK: Model lifecycle

    /// Called from AssistantView.task — loads the engine if the model is already on disk.
    func start() async {
        print("[InferenceManager] start() called — modelState=\(modelState)")
        guard case .loading = modelState else { return }
        await loadEngine()
    }

    func downloadAndLoad() async {
        print("[InferenceManager] downloadAndLoad() called — modelState=\(modelState)")
        guard case .notDownloaded = modelState else { return }
        modelState = .downloading
        await downloader.download(model: ModelRegistry.gemma4E2B)
        print("[InferenceManager] download finished — downloader.state=\(downloader.state)")
        switch downloader.state {
        case .completed:
            await loadEngine()
        case .failed(let msg):
            print("[InferenceManager] download failed: \(msg)")
            modelState = .error(msg)
        default:
            modelState = .error("Download did not complete")
        }
    }

    func cancelDownload() {
        print("[InferenceManager] cancelDownload()")
        downloader.cancel()
        modelState = .notDownloaded
    }

    func retryLoad() async {
        let isDownloaded = downloader.isDownloaded(ModelRegistry.gemma4E2B)
        print("[InferenceManager] retryLoad() — isDownloaded=\(isDownloaded)")
        if isDownloaded {
            modelState = .loading
            await loadEngine()
        } else {
            modelState = .notDownloaded
        }
    }

    private func loadEngine() async {
        guard let url = downloader.modelPath(for: ModelRegistry.gemma4E2B) else {
            print("[InferenceManager] loadEngine() — modelPath returned nil")
            modelState = .error("Model file not found on disk")
            return
        }
        let fileExists = FileManager.default.fileExists(atPath: url.path)
        print("[InferenceManager] loadEngine() — url=\(url.path) fileExists=\(fileExists)")
        modelState = .loading

        #if targetEnvironment(simulator)
        print("[InferenceManager] loadEngine() — using CPU backend (simulator)")
        let config = EngineConfiguration(modelPath: url).backend(.cpu)
        #else
        print("[InferenceManager] loadEngine() — using GPU backend (device)")
        let config = EngineConfiguration(modelPath: url).backend(.gpu)
        #endif

        print("[InferenceManager] loadEngine() — creating LMEngine…")
        let e = LMEngine(configuration: config)
        do {
            print("[InferenceManager] loadEngine() — calling e.load()…")
            try await e.load()
            engine = e
            modelState = .ready
            print("[InferenceManager] loadEngine() — engine ready")
        } catch {
            print("[InferenceManager] loadEngine() — FAILED: \(error)")
            print("[InferenceManager] loadEngine() — localizedDescription: \(error.localizedDescription)")
            modelState = .error(error.localizedDescription)
        }
    }

    // MARK: Chat

    func send(_ text: String, mode: ChatMode) async {
        guard let engine, !isGenerating else { return }

        messages.append(ChatMessage(role: .user, text: text))
        let idx = messages.count
        messages.append(ChatMessage(role: .assistant, text: "", isStreaming: true))

        isGenerating = true
        do {
            let conv = try await getConversation(mode: mode, engine: engine)
            let stream = try conv.sendStream(text)
            for try await token in stream {
                messages[idx].text += token
            }
        } catch {
            messages[idx].text = "Error: \(error.localizedDescription)"
        }
        messages[idx].isStreaming = false
        isGenerating = false
    }

    func cancelGeneration(mode: ChatMode) {
        switch mode {
        case .survival: survivalConversation?.cancel()
        case .emergency: emergencyConversation?.cancel()
        }
    }

    func clearHistory(mode: ChatMode) {
        switch mode {
        case .survival:
            survivalConversation?.close()
            survivalConversation = nil
        case .emergency:
            emergencyConversation?.close()
            emergencyConversation = nil
        }
        messages.removeAll()
    }

    // MARK: TTS

    func speak(_ text: String) {
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.48
        isSpeaking = true
        synthesizer.speak(utterance)
    }

    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
        isSpeaking = false
    }

    // MARK: Private helpers

    private func getConversation(mode: ChatMode, engine: LMEngine) async throws -> LMConversation {
        switch mode {
        case .survival:
            if let c = survivalConversation, c.isActive { return c }
            let c = try await engine.createConversation(
                configuration: ConversationConfiguration()
                    .systemPrompt(Self.survivalPrompt)
                    .maxOutputTokens(512)
            )
            survivalConversation = c
            return c
        case .emergency:
            if let c = emergencyConversation, c.isActive { return c }
            let c = try await engine.createConversation(
                configuration: ConversationConfiguration()
                    .systemPrompt(Self.emergencyPrompt)
                    .maxOutputTokens(128)
            )
            emergencyConversation = c
            return c
        }
    }
}

// MARK: - TTS delegate helper (needs NSObject for AVFoundation)

private final class TTSDelegate: NSObject, AVSpeechSynthesizerDelegate, @unchecked Sendable {
    var onFinish: @Sendable () -> Void = {}

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        onFinish()
    }
}
