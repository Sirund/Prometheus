import SwiftUI
import LiteRTLM
import LiteRTLMDownloader

@Observable
class InferenceManager {
    var engine: LMEngine?
    var conversation: LMConversation?
    var isModelLoaded = false
    var statusMessage = "Initializing..."

    @MainActor
    func setupGemma() async {
        do {
            let downloader = ModelDownloader()
            statusMessage = "Checking model..."

            if downloader.modelPath(for: ModelRegistry.gemma4E2B) == nil {
                statusMessage = "Downloading Gemma 4 (2.4GB)..."
                try await downloader.download(model: ModelRegistry.gemma4E2B)
            }

            statusMessage = "Loading to GPU..."
            let config = EngineConfiguration(
                modelPath: downloader.modelPath(for: ModelRegistry.gemma4E2B)!
            )
            .backend(.gpu)
            .visionBackend(.cpu)
            .audioBackend(.cpu)

            let newEngine = LMEngine(configuration: config)
            try await newEngine.load()

            self.engine = newEngine
            self.conversation = try await newEngine.createConversation()
            self.isModelLoaded = true
            self.statusMessage = "Gemma 4 Online"
        } catch {
            statusMessage = "Error: \(error.localizedDescription)"
        }
    }

    func sendMessage(_ text: String, history: [ChatMessage] = [], completion: @escaping (String) -> Void) async {
        guard let engine = engine else { return }

        do {
            let survivalPrompt = """
You are a calm, practical survival assistant embedded in a mobile app for users in Indonesia and similar tropical settings.

Rules:
- Give short, actionable steps. Prefer numbered lists for emergencies.
- If the user describes a medical emergency, tell them to call local emergency services (e.g. 119/112/118 where applicable) and give only safe, widely accepted first-aid guidance; do not invent diagnoses.
- You may not know real-time hazard data unless the user pastes it; never claim you received an official BMKG alert unless the app explicitly supplied that context.
- Avoid fear-mongering. Be direct about real risks (flood, tsunami, earthquake aftershocks, landslides, fire).
- Topics: shelter, water, food safety, evacuation mindset, communication when offline, heat, hygiene, basic navigation, and mental readiness.

The user may be offline. Keep answers concise unless they ask for depth.
"""
            var prompt = "\(survivalPrompt)\n\n"
            for msg in history {
                let role = msg.isUser ? "User" : "Assistant"
                prompt += "\(role): \(msg.text)\n"
            }
            prompt += "User: \(text)\nAssistant:"

            conversation?.close()
            let newConv = try await engine.createConversation()
            conversation = newConv

            var fullResponse = ""
            let stream = try await newConv.sendStream(prompt)

            for try await token in stream {
                fullResponse += token
                await MainActor.run {
                    completion(fullResponse)
                }
            }
        } catch {
            await MainActor.run {
                completion("Inference failed: \(error.localizedDescription)")
            }
        }
    }
}
