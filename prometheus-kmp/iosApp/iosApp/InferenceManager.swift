import SwiftUI
import LiteRTLM
import LiteRTLMDownloader

@Observable
class InferenceManager {
    var isModelLoaded = false
    var statusMessage = "Initializing..."

    private var engine: LMEngine?
    private var conversation: LMConversation?
    private var visionConversation: LMConversation?
    private let downloader = ModelDownloader()

    @MainActor
    func setupGemma() async {
        guard !isModelLoaded, engine == nil else { return }
        do {
            statusMessage = "Checking model..."

            if !downloader.isDownloaded(ModelRegistry.gemma4E2B) {
                statusMessage = "Downloading Gemma 4 (2.4GB)..."
                await downloader.download(model: ModelRegistry.gemma4E2B)
            }

            guard let url = downloader.modelPath(for: ModelRegistry.gemma4E2B) else {
                statusMessage = "Model file not found"
                return
            }

            statusMessage = "Loading model..."
            let candidates: [(String, EngineConfiguration)] = [
                ("GPU", EngineConfiguration(modelPath: url).backend(.gpu)),
                ("CPU", EngineConfiguration(modelPath: url).backend(.cpu)),
            ]
            var loadedEngine: LMEngine?
            for (label, config) in candidates {
                statusMessage = "Trying \(label)..."
                let e = LMEngine(configuration: config)
                do {
                    try await e.load()
                    loadedEngine = e
                    break
                } catch {
                    print("[InferenceManager] \(label) failed: \(error)")
                }
            }
            guard let newEngine = loadedEngine else {
                statusMessage = "Failed to load model on this device"
                return
            }

            engine = newEngine
            conversation = try await newEngine.createConversation()
            isModelLoaded = true
            statusMessage = "Gemma 4 Online"
        } catch {
            statusMessage = "Error: \(error.localizedDescription)"
        }
    }

    func sendMessage(_ text: String, history: [ChatMessage] = [], completion: @escaping (String) -> Void) async {
        guard let engine else { return }
        do {
            let systemPrompt = """
You are a calm, practical survival assistant embedded in a mobile app for users in Indonesia and similar tropical settings.

Rules:
- Give short, actionable steps. Prefer numbered lists for emergencies.
- If the user describes a medical emergency, tell them to call local emergency services (e.g. 119/112/118 where applicable) and give only safe, widely accepted first-aid guidance; do not invent diagnoses.
- You may not know real-time hazard data unless the user pastes it; never claim you received an official BMKG alert unless the app explicitly supplied that context.
- Avoid fear-mongering. Be direct about real risks (flood, tsunami, earthquake aftershocks, landslides, fire).
- Topics: shelter, water, food safety, evacuation mindset, communication when offline, heat, hygiene, basic navigation, and mental readiness.

The user may be offline. Keep answers concise unless they ask for depth.
"""
            var prompt = "\(systemPrompt)\n\n"
            for msg in history {
                let role = msg.isUser ? "User" : "Assistant"
                prompt += "\(role): \(msg.text)\n"
            }
            prompt += "User: \(text)\nAssistant:"

            conversation?.close()
            let newConv = try await engine.createConversation()
            conversation = newConv

            var fullResponse = ""
            let stream = try newConv.sendStream(prompt)
            for try await token in stream {
                fullResponse += token
                await MainActor.run { completion(fullResponse) }
            }
        } catch {
            await MainActor.run { completion("Inference failed: \(error.localizedDescription)") }
        }
    }

    func describeImage(_ data: Data, onToken: @escaping (String) -> Void) async {
        guard let engine else { return }
        do {
            if visionConversation == nil {
                visionConversation = try await engine.createConversation()
            }
            guard let conv = visionConversation else { return }
            var full = ""
            let stream = try await conv.sendStream("Describe what you see.", images: [data])
            for try await token in stream {
                full += token
                onToken(full)
            }
        } catch {
            onToken("Vision failed: \(error.localizedDescription)")
        }
    }
}
