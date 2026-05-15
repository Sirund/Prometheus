import SwiftUI
import AVFoundation

// MARK: - Camera Service

@Observable
class CameraService: NSObject {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var captureContinuation: CheckedContinuation<CGImage?, Never>?

    override init() {
        super.init()
        session.sessionPreset = .photo
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input),
              session.canAddOutput(output)
        else { return }
        session.addInput(input)
        session.addOutput(output)
    }

    func start() {
        if !session.isRunning { session.startRunning() }
    }

    func stop() {
        if session.isRunning { session.stopRunning() }
    }

    func capturePhoto() async -> CGImage? {
        await withCheckedContinuation { continuation in
            captureContinuation = continuation
            let settings = AVCapturePhotoSettings()
            output.capturePhoto(with: settings, delegate: self)
        }
    }
}

extension CameraService: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil,
              let data = photo.fileDataRepresentation(),
              let provider = CGDataProvider(data: data as CFData),
              let cgImage = CGImage(jpegDataProviderSource: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
        else {
            captureContinuation?.resume(returning: nil)
            captureContinuation = nil
            return
        }
        captureContinuation?.resume(returning: cgImage)
        captureContinuation = nil
    }
}

// MARK: - Camera Preview Layer (UIViewRepresentable)

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .black
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = view.bounds
        previewLayer.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.layer.addSublayer(previewLayer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

// MARK: - Vision Inference Manager

@Observable
class VisionInferenceManager {
    var engine: LMEngine?
    var conversation: LMConversation?
    var isModelLoaded = false
    var statusMessage = "Initializing..."

    @MainActor
    func setup() async {
        do {
            let downloader = ModelDownloader()
            statusMessage = "Checking model..."
            let modelPath: String

            if let path = downloader.modelPath(for: ModelRegistry.gemma4E2B) {
                modelPath = path
            } else {
                statusMessage = "Downloading Gemma 4 (2.4GB)..."
                try await downloader.download(model: ModelRegistry.gemma4E2B)
                guard let path = downloader.modelPath(for: ModelRegistry.gemma4E2B) else {
                    statusMessage = "Model download failed"
                    return
                }
                modelPath = path
            }

            statusMessage = "Loading model for vision..."
            let config = EngineConfiguration(modelPath: modelPath)
                .backend(.cpu)
                .visionBackend(.cpu)

            let newEngine = LMEngine(configuration: config)
            try await newEngine.load()

            self.engine = newEngine
            self.conversation = try await newEngine.createConversation()
            self.isModelLoaded = true
            self.statusMessage = "Vision ready"
        } catch {
            statusMessage = "Error: \(error.localizedDescription)"
        }
    }

    func describeImage(_ cgImage: CGImage, completion: @escaping (String) -> Void) async {
        guard let conversation = conversation else {
            await MainActor.run { completion("Model not loaded.") }
            return
        }

        do {
            let prompt = """
You are a calm, practical vision assistant for visually impaired users in a disaster situation.
Describe what you see in 2-4 short sentences. Focus on:
- People, injuries, or hazards (fires, floods, debris, downed power lines)
- Signage, exits, or evacuation-related text
- General surroundings for spatial awareness

Use plain, spoken language. Do not use markdown. Keep it brief and calm.
"""
            var fullResponse = ""
            let stream = try await conversation.sendStream(prompt, image: cgImage)
            for try await token in stream {
                fullResponse += token
                await MainActor.run {
                    completion(fullResponse)
                }
            }
        } catch {
            await MainActor.run {
                completion("Vision failed: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - Vision View

struct VisionView: View {
    @State private var visionManager = VisionInferenceManager()
    @State private var cameraService = CameraService()
    @State private var isCapturing = false
    @State private var capturedImage: CGImage?
    @State private var description: String?
    private let synthesizer = AVSpeechSynthesizer()

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 8) {
                    // Camera / Captured image area
                    ZStack {
                        CameraPreviewView(session: cameraService.session)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                        if let image = capturedImage {
                            Image(decorative: image, scale: 1)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }

                        if isCapturing {
                            Color.black.opacity(0.5)
                            Text("CAPTURING...")
                                .font(.caption.bold().monospaced())
                                .foregroundColor(.prometheusBlue)
                        }
                    }
                    .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.3), lineWidth: 1))
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .layoutPriority(1)

                    // Description / status
                    HStack(spacing: 10) {
                        Image(systemName: isCapturing ? "hourglass" : capturedImage != nil ? "photo.fill" : "waveform")
                            .font(.body)
                            .foregroundColor(.prometheusBlue.opacity(0.5))
                        Text(description ?? "Point camera and tap Describe to hear surroundings")
                            .font(.caption.monospaced())
                            .foregroundColor(.gray)
                            .lineLimit(3)
                        Spacer()
                    }
                    .padding(12)
                    .background(Color.cardBackground)
                    .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.2), lineWidth: 1))
                    .padding(.horizontal)

                    // Info card
                    VStack(alignment: .leading, spacing: 6) {
                        Text("VISION ACCESSIBILITY MODE")
                            .font(.caption2.bold().monospaced())
                            .foregroundColor(.prometheusBlue)
                        Text("Point the camera at surroundings, signage, or injuries. Gemma 4 describes what it sees in calm spoken language.")
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                            .lineSpacing(4)
                    }
                    .padding()
                    .background(Color.cardBackground.opacity(0.5))
                    .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.15), lineWidth: 1))
                    .padding(.horizontal)

                    // Capture button
                    let hasCapture = capturedImage != nil
                    Button(action: captureAndDescribe) {
                        VStack(spacing: 10) {
                            Image(systemName: isCapturing ? "hourglass.circle.fill" : hasCapture ? "arrow.circlepath" : "camera.circle.fill")
                                .font(.system(size: 60))
                            Text(isCapturing ? "DESCRIBING..." : hasCapture ? "TAP FOR NEW CAPTURE" : "TAP TO DESCRIBE SURROUNDINGS")
                                .font(.caption.bold().monospaced())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(28)
                        .background(
                            isCapturing
                            ? Color.prometheusBlue.opacity(0.25)
                            : Color.prometheusBlue.opacity(0.12)
                        )
                        .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 2))
                        .foregroundColor(.prometheusBlue)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal)
                    .disabled(isCapturing || !visionManager.isModelLoaded)
                }
            }
            .navigationTitle("Vision Assist")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(visionManager.isModelLoaded ? .green : .orange)
                            .frame(width: 6, height: 6)
                        Text(visionManager.statusMessage)
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                    }
                }
            }
        }
        .onAppear {
            cameraService.start()
            Task { await visionManager.setup() }
        }
        .onDisappear {
            cameraService.stop()
        }
    }

    private func captureAndDescribe() {
        if capturedImage != nil {
            capturedImage = nil
            description = nil
            return
        }

        isCapturing = true
        description = "Capturing..."

        Task {
            guard let cgImage = await cameraService.capturePhoto() else {
                isCapturing = false
                description = "Capture failed. Try again."
                return
            }

            capturedImage = cgImage
            description = "Analyzing image..."
            await visionManager.describeImage(cgImage) { text in
                description = text
            }
            isCapturing = false

            if let text = description, !text.isEmpty {
                let utterance = AVSpeechUtterance(string: text)
                utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
                utterance.rate = 0.5
                synthesizer.speak(utterance)
            }
        }
    }
}
