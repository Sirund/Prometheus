import SwiftUI
import AVFoundation
import Speech

// MARK: - Speech service

final class SpeechService: NSObject, @unchecked Sendable {
    private let recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()

    var onResult: ((String) -> Void)?
    var onError: ((String) -> Void)?

    override init() {
        recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        super.init()
    }

    static func requestPermissions(completion: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async { completion(status == .authorized) }
        }
    }

    func startListening() throws {
        #if !os(macOS)
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)
        #endif

        request = SFSpeechAudioBufferRecognitionRequest()
        guard let request, let recognizer else { return }
        request.shouldReportPartialResults = false

        let inputNode = engine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.request?.append(buffer)
        }

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            if let result, result.isFinal {
                let text = result.bestTranscription.formattedString
                self?.onResult?(text)
            } else if let error {
                self?.onError?(error.localizedDescription)
            }
        }

        engine.prepare()
        try engine.start()
    }

    // Sets callbacks then stops audio — callbacks fire naturally after endAudio().
    func stopAndAwait(onResult: @escaping (String) -> Void, onError: @escaping () -> Void) {
        self.onResult = { text in
            self.onResult = nil
            self.onError = nil
            onResult(text)
        }
        self.onError = { _ in
            self.onResult = nil
            self.onError = nil
            onError()
        }
        engine.stop()
        if engine.inputNode.numberOfInputs > 0 {
            engine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        request = nil

        #if !os(macOS)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
        #endif
    }

    func shutdown() {
        task?.cancel()
        task = nil
        if engine.isRunning {
            engine.stop()
            if engine.inputNode.numberOfInputs > 0 {
                engine.inputNode.removeTap(onBus: 0)
            }
        }
        request?.endAudio()
        request = nil
    }
}

// MARK: - Talk state

enum TalkState { case idle, recording, transcribing, sending, result }

// MARK: - TalkPanel (embedded in AssistantView)

struct TalkPanel: View {
    @Environment(InferenceManager.self) private var inference
    @Environment(BMKGPollingService.self) private var pollingService

    @State private var camera = CameraService()
    @State private var speech = SpeechService()
    @State private var talkState: TalkState = .idle
    @State private var lastResponse: String?
    @State private var hasSpeechPermission = false
    @State private var micScale: CGFloat = 1.0

    var body: some View {
        ZStack {
            cameraBackground
            stateDimOverlay
            VStack(spacing: 0) {
                Spacer()
                responseCard.padding(.horizontal, 16).padding(.bottom, 12)
                stateLabel.padding(.bottom, 16)
                micButton.padding(.bottom, 90)
            }
            modelStatusPill
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding(.top, 8).padding(.trailing, 16)
        }
        .ignoresSafeArea(edges: .bottom)
        .onAppear {
            camera.start()
            requestPermissions()
        }
        .onDisappear {
            camera.stop()
            speech.shutdown()
        }
    }

    // MARK: - Sub-views

    private var cameraBackground: some View {
        ZStack {
            #if canImport(UIKit)
            CameraPreviewView(session: camera.session)
                .ignoresSafeArea()
            #else
            Color.black.ignoresSafeArea()
            #endif

            if talkState == .recording {
                Rectangle()
                    .stroke(Color.red.opacity(0.8), lineWidth: 3)
                    .ignoresSafeArea()
                    .animation(.easeInOut(duration: 0.2), value: talkState == .recording)
            }
        }
    }

    @ViewBuilder
    private var stateDimOverlay: some View {
        if talkState == .sending || talkState == .transcribing {
            Color.black.opacity(0.35).ignoresSafeArea()
        }
    }

    private var modelStatusPill: some View {
        let isReady: Bool = {
            if case .ready = inference.modelState { return true }
            return false
        }()
        return HStack(spacing: 4) {
            Circle().fill(isReady ? Color.green : Color.orange).frame(width: 6, height: 6)
            Text(isReady ? "GEMMA 4 · READY" : "MODEL NOT LOADED")
                .inter(10, weight: .bold).foregroundColor(.white.opacity(0.85))
        }
        .padding(.horizontal, 10).padding(.vertical, 5)
        .background(Color.black.opacity(0.55))
        .clipShape(Capsule())
    }

    private var responseCard: some View {
        let cardText: String = {
            switch talkState {
            case .idle:         return lastResponse ?? "Say something to Gemma"
            case .recording:    return "Listening…"
            case .transcribing: return "Transcribing…"
            case .sending:      return "Analyzing with Gemma…"
            case .result:       return lastResponse ?? ""
            }
        }()
        let hasContent = lastResponse != nil

        return HStack(alignment: .top, spacing: 8) {
            Group {
                if talkState == .transcribing || talkState == .sending {
                    ProgressView().tint(.prometheusBlue).scaleEffect(0.7).frame(width: 14, height: 14)
                } else if talkState == .recording {
                    Image(systemName: "waveform")
                        .font(.caption).foregroundColor(.red)
                        .symbolEffect(.pulse)
                        .frame(width: 14, height: 14)
                }
            }
            Text(cardText)
                .inter(12)
                .foregroundColor(hasContent && talkState != .recording ? .white : .white.opacity(0.6))
                .lineLimit(5)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Color.black.opacity(0.72))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.prometheusBlue.opacity(0.4), lineWidth: 1))
        .animation(.easeInOut(duration: 0.2), value: talkState == .idle)
    }

    @ViewBuilder
    private var stateLabel: some View {
        switch talkState {
        case .idle:
            Text(hasSpeechPermission ? "Hold mic to speak" : "Microphone access required")
                .inter(11).foregroundColor(.white.opacity(0.55))
        case .recording:
            Text("Release to send")
                .inter(11, weight: .bold).foregroundColor(.red.opacity(0.9))
        case .transcribing:
            Text("Transcribing audio…")
                .inter(11).foregroundColor(.prometheusBlue)
        case .sending:
            Text("Sending to Gemma…")
                .inter(11).foregroundColor(.prometheusBlue)
        case .result:
            Text("Hold mic to ask again")
                .inter(11).foregroundColor(.white.opacity(0.55))
        }
    }

    private var micButton: some View {
        let isRecording = talkState == .recording
        let isBusy = talkState == .transcribing || talkState == .sending

        return ZStack {
            if isRecording {
                Circle()
                    .fill(Color.red.opacity(0.18))
                    .frame(width: 88, height: 88)
                    .scaleEffect(micScale)
            }
            Circle()
                .fill(isRecording ? Color.red.opacity(0.25) : Color.white.opacity(0.18))
                .frame(width: 64, height: 64)

            if isBusy {
                ProgressView().tint(.prometheusBlue).scaleEffect(1.3)
            } else {
                Image(systemName: "mic.fill")
                    .font(.system(size: 28))
                    .foregroundColor(isRecording ? .red : .white.opacity(0.85))
            }
        }
        .frame(width: 88, height: 88)
        .scaleEffect(isRecording ? 1.08 : 1.0)
        .animation(.easeInOut(duration: 0.15), value: isRecording)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard talkState == .idle || talkState == .result else { return }
                    startRecording()
                }
                .onEnded { _ in
                    guard talkState == .recording else { return }
                    stopRecordingAndProcess()
                }
        )
        .disabled(isBusy || !hasSpeechPermission)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                micScale = 1.18
            }
        }
    }

    // MARK: - Permissions

    private func requestPermissions() {
        AVAudioApplication.requestRecordPermission { granted in
            guard granted else { return }
            SpeechService.requestPermissions { authorized in
                Task { @MainActor in hasSpeechPermission = authorized }
            }
        }
    }

    // MARK: - Recording flow

    private func startRecording() {
        guard hasSpeechPermission else { return }
        inference.stopSpeaking()
        lastResponse = nil
        talkState = .recording
        speech = SpeechService()
        do {
            try speech.startListening()
        } catch {
            talkState = .idle
        }
    }

    private func stopRecordingAndProcess() {
        talkState = .transcribing

        Task {
            let transcribed = await withCheckedContinuation { (cont: CheckedContinuation<String?, Never>) in
                var done = false
                let finish = { (val: String?) in
                    guard !done else { return }
                    done = true
                    cont.resume(returning: val)
                }
                speech.stopAndAwait(
                    onResult: { text in finish(text.isEmpty ? nil : text) },
                    onError:  { finish(nil) }
                )
                Task {
                    try? await Task.sleep(for: .seconds(5))
                    finish(nil)
                }
            }

            guard let question = transcribed else {
                talkState = .idle
                return
            }

            talkState = .sending
            let cgImage = await camera.capturePhoto()

            var visionContext = ""
            if let cgImage {
                #if canImport(UIKit)
                let uiImage = UIImage(cgImage: cgImage)
                let resized = uiImage.resized(toMaxDimension: 512)
                if let imageData = resized.jpegData(compressionQuality: 0.7) {
                    await inference.describeImage(imageData) { token in visionContext = token }
                }
                #endif
            }

            let combined = visionContext.isEmpty
                ? question
                : "\(question)\n\n[Camera shows: \(visionContext)]"

            let response: String
            if case .ready = inference.modelState {
                let bmkgCtx = InferenceManager.buildBmkgContext(event: pollingService.latestEarthquakeEvent)
                let systemPrompt = bmkgCtx.isEmpty
                    ? InferenceManager.generalPrompt
                    : "\(InferenceManager.generalPrompt)\n\n\(bmkgCtx)"
                response = await inference.sendOneShot(combined, systemPrompt: systemPrompt)
            } else {
                response = visionContext.isEmpty
                    ? "Model not loaded. Download Gemma 4 in the Assistant tab."
                    : visionContext
            }

            lastResponse = response
            talkState = .result

            if !response.isEmpty {
                inference.speak(response)
            }
        }
    }
}
