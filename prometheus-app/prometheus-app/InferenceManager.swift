//
//  InferenceManager.swift
//  prometheus-app
//

import Foundation
import LiteRTLM
import LiteRTLMDownloader
import AVFoundation
import Vision
import UIKit

// MARK: - Chat types

enum ChatMode: Equatable {
    case survival
    case emergency
}

struct ChatMessage: Identifiable, Codable {
    var id = UUID()
    let role: Role
    var text: String
    var isStreaming: Bool = false

    enum Role: String, Codable { case user, assistant }
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
    private(set) var hasVisionBackend = false

    /// Exposed directly so views can observe download progress without mirroring.
    let downloader = ModelDownloader()

    // MARK: Private

    private var engine: LMEngine?
    private var survivalConversation: LMConversation?
    private var emergencyConversation: LMConversation?
    private let synthesizer = AVSpeechSynthesizer()
    private let ttsDelegate = TTSDelegate()

    // Earthquake context injected from outside for emergency mode
    var currentEarthquakeEvent: EarthquakeEvent?

    // MARK: System prompts (Arund's domain — swap these out when final prompts are ready)

    static let survivalPrompt = """
    You are a calm, practical survival assistant for Indonesia. \
    Provide guidance on first aid, shelter, water sourcing, evacuation planning, \
    and hazard awareness — earthquakes, tsunamis, volcanic eruptions, and floods. \
    Keep every answer short and actionable. \
    Write plain text only — no markdown, no bullet points, no headers.
    """

    static let visionPrompt = """
    You are a calm, practical vision assistant for visually impaired users in a disaster situation. \
    Describe what the user's camera shows in 2-4 short sentences. Focus on: \
    people, injuries, or hazards (fires, floods, debris, downed power lines); \
    signage, exits, or evacuation-related text; general surroundings for spatial awareness. \
    Use plain spoken language only — no markdown, no bullet points. \
    Keep it brief and calm. If you cannot see anything clearly, say so honestly.
    """

    static let emergencyBasePrompt = """
    You are Prometheus, an emergency AI for earthquake disasters in Indonesia. \
    Generate a clear, structured emergency briefing. Include: severity assessment, \
    immediate protective actions, tsunami risk, and evacuation recommendations. \
    Be urgent, concise, and actionable. Plain text only — no markdown, no bullet points.
    """

    func buildEmergencySystemPrompt() -> String {
        guard let event = currentEarthquakeEvent else { return Self.emergencyBasePrompt }
        var ctx = "Current event:"
        if let mag = event.magnitudeValue { ctx += " M\(mag)" }
        if let loc = event.Wilayah       { ctx += " near \(loc)" }
        if let d   = event.Kedalaman    { ctx += ", depth \(d)" }
        if event.hasTsunamiPotential     { ctx += " — TSUNAMI POTENTIAL" }
        return "\(ctx)\n\n\(Self.emergencyBasePrompt)"
    }

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

    func redownloadAndLoad() async {
        print("[InferenceManager] redownloadAndLoad() — forcing fresh download")
        engine = nil
        survivalConversation = nil
        modelState = .notDownloaded
        await downloadAndLoad()
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
        #if targetEnvironment(simulator)
        print("[InferenceManager] loadEngine() — simulator detected, skipping engine load")
        modelState = .error("On-device inference requires a physical iPhone.\nRun the app on a real device to use Survival Assistant.")
        return
        #endif

        guard let url = downloader.modelPath(for: ModelRegistry.gemma4E2B) else {
            print("[InferenceManager] loadEngine() — modelPath returned nil")
            modelState = .error("Model file not found on disk")
            return
        }
        let fileExists = FileManager.default.fileExists(atPath: url.path)
        print("[InferenceManager] loadEngine() — url=\(url.path) fileExists=\(fileExists)")
        guard fileExists else {
            modelState = .error("Model file missing. Please re-download.")
            return
        }
        modelState = .loading

        // Gemma 4 is natively multimodal — vision is in the model weights, no separate visionBackend needed.
        // Setting visionBackend causes litert_lm_engine_create to return NULL on most devices.
        let candidates: [(String, EngineConfiguration)] = [
            ("GPU", EngineConfiguration(modelPath: url).backend(.gpu)),
            ("CPU", EngineConfiguration(modelPath: url).backend(.cpu)),
        ]

        var lastError: Error?
        for (label, config) in candidates {
            print("[InferenceManager] loadEngine() — trying \(label)…")
            let e = LMEngine(configuration: config)
            do {
                try await e.load()
                engine = e
                // gemma-4-E2B-it.litertlm is text-only; vision encoder profile is absent.
                // Sending images crashes the C layer (EXC_BAD_ACCESS). Keep false until a
                // vision-capable model variant is used.
                hasVisionBackend = false
                modelState = .ready
                print("[InferenceManager] loadEngine() — \(label) engine ready (vision=false, text-only model)")
                return
            } catch {
                print("[InferenceManager] loadEngine() — \(label) failed: \(error)")
                lastError = error
            }
        }
        modelState = .error(lastError?.localizedDescription ?? "Unable to load model")
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
        case .survival:  survivalConversation?.cancel()
        case .emergency: emergencyConversation?.cancel()
        }
    }

    func restoreMessages(_ messages: [ChatMessage]) {
        self.messages = messages.map { var m = $0; m.isStreaming = false; return m }
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

    // MARK: Vision

    func describeImage(_ data: Data, onToken: @escaping (String) -> Void) async {
        guard let cgImage = UIImage(data: data)?.cgImage else {
            onToken("Could not read image.")
            return
        }

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        var recognizedTexts: [String] = []
        var sceneLabels: [String] = []
        var peopleCount = 0

        let textReq = VNRecognizeTextRequest { req, _ in
            guard let obs = req.results as? [VNRecognizedTextObservation] else { return }
            recognizedTexts = obs.compactMap { $0.topCandidates(1).first?.string }
        }
        textReq.recognitionLevel = .accurate
        textReq.usesLanguageCorrection = true

        let classifyReq = VNClassifyImageRequest { req, _ in
            guard let obs = req.results as? [VNClassificationObservation] else { return }
            sceneLabels = obs.filter { $0.confidence > 0.4 }.prefix(4).map { $0.identifier }
        }

        let peopleReq = VNDetectHumanRectanglesRequest { req, _ in
            peopleCount = req.results?.count ?? 0
        }

        do {
            try handler.perform([textReq, classifyReq, peopleReq])
        } catch {
            onToken("Analysis failed: \(error.localizedDescription)")
            return
        }

        var parts: [String] = []

        switch peopleCount {
        case 0: break
        case 1: parts.append("One person visible.")
        default: parts.append("\(peopleCount) people visible.")
        }

        let scene = sceneLabels
            .map { $0.replacingOccurrences(of: "_", with: " ") }
            .prefix(3)
            .joined(separator: ", ")
        if !scene.isEmpty {
            parts.append("Scene: \(scene).")
        }

        let texts = recognizedTexts.prefix(6).joined(separator: " · ")
        if !texts.isEmpty {
            parts.append("Visible text: \(texts).")
        }

        if parts.isEmpty {
            parts.append("No clear details detected. Try moving closer or improving the lighting.")
        }

        onToken(parts.joined(separator: " "))
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
                    .systemPrompt(buildEmergencySystemPrompt())
                    .maxOutputTokens(768)
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
