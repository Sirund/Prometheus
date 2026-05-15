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
            var prompt = ""
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
