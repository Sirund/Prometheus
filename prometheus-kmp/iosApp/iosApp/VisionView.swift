import SwiftUI
import AVFoundation

// MARK: - Camera Service

final class CameraService: NSObject, @unchecked Sendable {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var captureContinuation: CheckedContinuation<CGImage?, Never>?

    override init() {
        super.init()
        session.sessionPreset = .photo
        guard
            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input),
            session.canAddOutput(output)
        else { return }
        session.addInput(input)
        session.addOutput(output)
    }

    func start() { if !session.isRunning { session.startRunning() } }
    func stop()  { if  session.isRunning { session.stopRunning()  } }

    func capturePhoto() async -> CGImage? {
        await withCheckedContinuation { continuation in
            captureContinuation = continuation
            output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }
}

extension CameraService: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil,
              let data = photo.fileDataRepresentation(),
              let provider = CGDataProvider(data: data as CFData),
              let cgImage = CGImage(jpegDataProviderSource: provider, decode: nil,
                                    shouldInterpolate: false, intent: .defaultIntent)
        else {
            captureContinuation?.resume(returning: nil)
            captureContinuation = nil
            return
        }
        captureContinuation?.resume(returning: cgImage)
        captureContinuation = nil
    }
}

// MARK: - Camera Preview

#if canImport(UIKit)
struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .black
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let layer = uiView.layer.sublayers?.first as? AVCaptureVideoPreviewLayer else { return }
        layer.frame = uiView.bounds
    }
}
#endif

// MARK: - Vision View

struct VisionView: View {
    @Environment(InferenceManager.self) private var inference
    @State private var camera = CameraService()
    @State private var isCapturing = false
    @State private var capturedImage: CGImage?
    @State private var description: String?
    private let synthesizer = AVSpeechSynthesizer()

    var body: some View {
        NavigationStack {
            ZStack {
                Color.darkBackground.ignoresSafeArea()

                VStack(spacing: 8) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(inference.isModelLoaded ? Color.green : Color.orange)
                            .frame(width: 6, height: 6)
                        Text(inference.isModelLoaded ? "VISION READY" : inference.statusMessage)
                            .font(.caption2.monospaced())
                            .foregroundColor(.gray)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 16)
                    .padding(.top, 4)

                    cameraArea
                    descriptionStrip
                    infoCard
                    captureButton
                }
            }
            .navigationTitle("Vision Assist")
            .toolbarBackground(Color.cardBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
        .onAppear { camera.start() }
        .onDisappear { camera.stop() }
    }

    private var cameraArea: some View {
        ZStack {
            #if canImport(UIKit)
            CameraPreviewView(session: camera.session)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            #else
            Color.black
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            #endif

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
    }

    private var descriptionStrip: some View {
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
    }

    private var infoCard: some View {
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
    }

    private var captureButton: some View {
        let hasCapture = capturedImage != nil
        return Button(action: captureAndDescribe) {
            VStack(spacing: 10) {
                Image(systemName: isCapturing
                    ? "hourglass.circle.fill"
                    : hasCapture ? "arrow.circlepath" : "camera.circle.fill")
                    .font(.system(size: 60))
                Text(isCapturing
                    ? "DESCRIBING..."
                    : hasCapture ? "TAP FOR NEW CAPTURE" : "TAP TO DESCRIBE SURROUNDINGS")
                    .font(.caption.bold().monospaced())
            }
            .frame(maxWidth: .infinity)
            .padding(28)
            .background(isCapturing
                ? Color.prometheusBlue.opacity(0.25)
                : Color.prometheusBlue.opacity(0.12))
            .overlay(Rectangle().stroke(Color.prometheusBlue.opacity(0.5), lineWidth: 2))
            .foregroundColor(.prometheusBlue)
        }
        .buttonStyle(.plain)
        .padding(.horizontal)
        .disabled(isCapturing || !inference.isModelLoaded)
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
            guard let cgImage = await camera.capturePhoto() else {
                isCapturing = false
                description = "Capture failed. Try again."
                return
            }

            #if canImport(UIKit)
            guard let imageData = UIImage(cgImage: cgImage).jpegData(compressionQuality: 0.8) else {
                isCapturing = false
                description = "Failed to encode image."
                return
            }

            capturedImage = cgImage
            description = "Analyzing image..."

            await inference.describeImage(imageData) { text in
                description = text
            }
            isCapturing = false

            if let text = description, !text.isEmpty {
                let utterance = AVSpeechUtterance(string: text)
                utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
                utterance.rate = 0.5
                synthesizer.speak(utterance)
            }
            #else
            isCapturing = false
            description = "Camera capture not supported on this platform."
            #endif
        }
    }
}
