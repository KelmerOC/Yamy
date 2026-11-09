import Foundation
import AVFoundation

@MainActor final class GeminiLiveTransport: NSObject {
    var onEvent: (([String: Any]) -> Void)?
    var onLevels: ((Double, Double) -> Void)?
    var onFailure: ((String) -> Void)?

    private(set) var started = false
    private(set) var isMuted = false

    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?
    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var meterTask: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?

    private var attempt = UUID()
    private var closing = false
    private var ownsAudioActivation = false
    private var startedAt = Date()
    private var pendingToolCallIDs = Set<String>()
    private var pendingThinkingContext: String?
    private var isTyping = false

    private var lastInput = 0.0
    private var lastOutput = 0.0
    private var currentInputRms = 0.0
    private var currentOutputRms = 0.0

    private let inputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true)!
    private let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 24000, channels: 1, interleaved: true)!

    private static let defaultWsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    private static let defaultModel = "models/gemini-3.8-live"
    private static let defaultVoice = "Puck"

    func connect(
        key: String,
        instructions: String,
        history: [[String: Any]] = [],
        voiceName: String = defaultVoice
    ) async throws {
        disconnect()
        closing = false
        let token = UUID()
        attempt = token
        startedAt = Date()
        pendingToolCallIDs.removeAll()

        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else { throw LiveTransport.TransportError.microphone }
        try Task.checkCancellation()
        guard attempt == token else { throw CancellationError() }

        // Configure audio session for voice chat with AEC and noise suppression
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setActive(true)
            ownsAudioActivation = true
        } catch {
            throw error
        }

        try setupAudioEngine()
        try connectWebSocket(key: key, instructions: instructions, history: history, voiceName: voiceName)

        let readyDeadline = Date().addingTimeInterval(15)
        while !started {
            try await Task.sleep(for: .milliseconds(100))
            guard attempt == token else { throw CancellationError() }
            guard Date() < readyDeadline else { throw LiveTransport.TransportError.timeout }
        }

        startMetering()
    }

    @discardableResult
    func send(_ event: [String: Any]) -> Bool {
        guard let type = event["type"] as? String else { return false }
        let eventID = (event["event_id"] as? String) ?? UUID().uuidString

        switch type {
        case "session.commentary.append":
            isTyping = false
            let delegationID = event["delegation_id"] as? String
            let content = (event["content"] as? String) ?? ""
            if let delegationID {
                pendingToolCallIDs.remove(delegationID)
                let toolResponseMsg: [String: Any] = [
                    "toolResponse": [
                        "functionResponses": [
                            [
                                "id": delegationID,
                                "response": [
                                    "output": [
                                        "commentary": content
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
                sendRaw(toolResponseMsg)
            } else {
                let promptText: String
                if let ctx = pendingThinkingContext, !ctx.isEmpty {
                    pendingThinkingContext = nil
                    promptText = "The learner wrote in text: \"\(ctx)\". Speak this reply aloud to the learner: \(content)"
                } else {
                    promptText = "Speak this reply aloud to the learner: \(content)"
                }
                let commentaryMsg: [String: Any] = [
                    "clientContent": [
                        "turns": [
                            [
                                "role": "user",
                                "parts": [["text": promptText]]
                            ]
                        ],
                        "turnComplete": true
                    ]
                ]
                sendRaw(commentaryMsg)
            }
            onEvent?(["type": "session.commentary.appended", "client_event_id": eventID])
            return true

        case "session.instructions.append":
            let content = (event["content"] as? String) ?? ""
            let instructionMsg: [String: Any] = [
                "clientContent": [
                    "turns": [
                        [
                            "role": "user",
                            "parts": [["text": content]]
                        ]
                    ],
                    "turnComplete": true
                ]
            ]
            sendRaw(instructionMsg)
            onEvent?(["type": "session.instructions.appended", "client_event_id": eventID])
            return true

        case "session.thinking.append":
            let content = (event["content"] as? String) ?? ""
            pendingThinkingContext = content
            onEvent?(["type": "session.thinking.appended", "client_event_id": eventID])
            return true

        case "session.input_audio.mute":
            mute(true)
            return true

        case "session.input_audio.unmute":
            mute(false)
            return true

        case "session.typing.start":
            isTyping = true
            playerNode?.pause()
            playerNode?.stop()
            onEvent?(["type": "session.typing.started", "client_event_id": eventID])
            return true

        case "session.typing.stop":
            isTyping = false
            onEvent?(["type": "session.typing.stopped", "client_event_id": eventID])
            return true

        case "session.close":
            close()
            return true

        default:
            sendRaw(event)
            return true
        }
    }

    private func sendRaw(_ message: [String: Any]) {
        guard let task = webSocketTask, task.state == .running,
              let data = try? JSONSerialization.data(withJSONObject: message),
              let text = String(data: data, encoding: .utf8) else { return }
        task.send(.string(text)) { _ in }
    }

    private func sendMediaChunk(_ base64Audio: String) {
        let chunkMessage: [String: Any] = [
            "realtimeInput": [
                "audio": [
                    "mimeType": "audio/pcm;rate=16000",
                    "data": base64Audio
                ],
                "mediaChunks": [
                    [
                        "mimeType": "audio/pcm;rate=16000",
                        "data": base64Audio
                    ]
                ]
            ]
        ]
        sendRaw(chunkMessage)
    }

    func mute(_ muted: Bool) {
        isMuted = muted
    }

    func close() {
        closing = true
        isMuted = true
        let elapsed = max(0, Date().timeIntervalSince(startedAt))
        onEvent?(["type": "session.closed", "usage": ["seconds": elapsed]])
        webSocketTask?.cancel(with: .normalClosure, reason: nil)
    }

    func disconnect() {
        attempt = UUID()
        meterTask?.cancel()
        meterTask = nil
        receiveTask?.cancel()
        receiveTask = nil

        started = false
        closing = true

        stopAudioEngine()

        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil

        if ownsAudioActivation {
            let session = AVAudioSession.sharedInstance()
            try? session.setActive(false)
            ownsAudioActivation = false
        }

        lastInput = 0
        lastOutput = 0
        currentInputRms = 0
        currentOutputRms = 0
        onLevels?(0, 0)
    }

    private func setupAudioEngine() throws {
        let engine = AVAudioEngine()
        let player = AVAudioPlayerNode()

        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: outputFormat)

        let inputNode = engine.inputNode
        let hwFormat = inputNode.inputFormat(forBus: 0)

        guard let converter = AVAudioConverter(from: hwFormat, to: inputFormat) else {
            throw LiveTransport.TransportError.connection
        }

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: hwFormat) { [weak self] buffer, _ in
            guard let self, !self.isMuted, self.started else {
                Task { @MainActor [weak self] in self?.currentInputRms = 0 }
                return
            }

            let ratio = 16000.0 / hwFormat.sampleRate
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio + 100)
            guard let outBuffer = AVAudioPCMBuffer(pcmFormat: self.inputFormat, frameCapacity: capacity) else { return }

            var isConsumed = false
            var convError: NSError?
            converter.convert(to: outBuffer, error: &convError) { _, outStatus in
                if !isConsumed {
                    isConsumed = true
                    outStatus.pointee = .haveData
                    return buffer
                } else {
                    outStatus.pointee = .noDataNow
                    return nil
                }
            }

            guard convError == nil, outBuffer.frameLength > 0, let channelData = outBuffer.int16ChannelData else { return }

            let frameLength = Int(outBuffer.frameLength)
            let samples = channelData[0]
            var sum: Double = 0
            for i in 0..<frameLength {
                let s = Double(samples[i])
                sum += s * s
            }
            let rms = (sqrt(sum / Double(frameLength)) / 32768.0).clamped(to: 0...1)

            let byteCount = frameLength * 2
            let pcmData = Data(bytes: samples, count: byteCount)
            let base64Audio = pcmData.base64EncodedString()

            Task { @MainActor [weak self] in
                guard let self, !self.isMuted, self.started, !self.isTyping else { return }
                self.currentInputRms = rms
                self.sendMediaChunk(base64Audio)
            }
        }

        try engine.start()
        player.play()

        self.audioEngine = engine
        self.playerNode = player
    }

    private func stopAudioEngine() {
        if let engine = audioEngine {
            engine.inputNode.removeTap(onBus: 0)
            playerNode?.stop()
            engine.stop()
        }
        audioEngine = nil
        playerNode = nil
    }

    private func connectWebSocket(
        key: String,
        instructions: String,
        history: [[String: Any]],
        voiceName: String
    ) throws {
        guard let url = URL(string: "\(Self.defaultWsUrl)?key=\(key)") else {
            throw LiveTransport.TransportError.connection
        }

        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: url)
        self.urlSession = session
        self.webSocketTask = task

        task.resume()

        // Send initial setup frame
        let setupMessage: [String: Any] = [
            "setup": [
                "model": Self.defaultModel,
                "generationConfig": [
                    "responseModalities": ["AUDIO"],
                    "speechConfig": [
                        "voiceConfig": [
                            "prebuiltVoiceConfig": [
                                "voiceName": voiceName
                            ]
                        ]
                    ],
                    "inputAudioTranscription": [:],
                    "outputAudioTranscription": [:]
                ],
                "systemInstruction": [
                    "parts": [
                        ["text": instructions]
                    ]
                ],
                "tools": [
                    [
                        "functionDeclarations": [
                            [
                                "name": "delegate_helper",
                                "description": "Delegate a research, source-backed lookup, or teaching helper query to the learning assistant helper. Use this when the learner asks about current events, specific facts outside your immediate knowledge, or requests a lookup.",
                                "parameters": [
                                    "type": "OBJECT",
                                    "properties": [
                                        "query": [
                                            "type": "STRING",
                                            "description": "The specific query, topic, or question to delegate and look up."
                                        ]
                                    ],
                                    "required": ["query"]
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        ]

        guard let setupData = try? JSONSerialization.data(withJSONObject: setupMessage),
              let setupText = String(data: setupData, encoding: .utf8) else {
            throw LiveTransport.TransportError.connection
        }

        task.send(.string(setupText)) { [weak self] error in
            if error != nil {
                Task { @MainActor [weak self] in
                    self?.onFailure?("The voice connection couldn’t be established. Check your connection and try again.")
                }
            }
        }

        startReceiving(history: history)
    }

    private func startReceiving(history: [[String: Any]]) {
        receiveTask?.cancel()
        receiveTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, let task = self.webSocketTask, task.state == .running else { break }
                do {
                    let message = try await task.receive()
                    switch message {
                    case .string(let text):
                        self.handleIncomingText(text, history: history)
                    case .data(let data):
                        if let text = String(data: data, encoding: .utf8) {
                            self.handleIncomingText(text, history: history)
                        }
                    @unknown default:
                        break
                    }
                } catch {
                    guard !Task.isCancelled, !self.closing else { break }
                    self.onFailure?("The voice connection ended unexpectedly. Your conversation has been saved.")
                    break
                }
            }
        }
    }

    private func handleIncomingText(_ text: String, history: [[String: Any]]) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        if json["setupComplete"] != nil || json["setup_complete"] != nil {
            started = true
            onEvent?(["type": "session.started", "event_id": UUID().uuidString, "session": ["id": "gemini-\(attempt.uuidString)"]])
            if !history.isEmpty {
                sendHistoryTurns(history)
            }
            return
        }

        // Check for root-level tool calls (camelCase and snake_case)
        let toolCall = (json["toolCall"] ?? json["tool_call"]) as? [String: Any]
        if let functionCalls = (toolCall?["functionCalls"] ?? toolCall?["function_calls"]) as? [[String: Any]] {
            for call in functionCalls {
                handleFunctionCall(call)
            }
        }

        guard let serverContent = (json["serverContent"] ?? json["server_content"]) as? [String: Any] else { return }

        // Interruption (Barge-in)
        if let interrupted = (serverContent["interrupted"] as? Bool) ?? (serverContent["interrupted"] as? String == "true"), interrupted {
            handleInterruption()
        }

        // User speech transcription
        let inputTranscription = (serverContent["inputTranscription"] ?? serverContent["input_transcription"] ?? serverContent["interimInputTranscription"] ?? serverContent["interim_input_transcription"]) as? [String: Any]
        let inputText = (inputTranscription?["text"] as? String) ??
            ((inputTranscription?["parts"] as? [[String: Any]])?.first?["text"] as? String)
        if let inputText, !inputText.isEmpty {
            let offset = Int(Date().timeIntervalSince(startedAt) * 1000)
            onEvent?([
                "type": "session.input_transcript.delta",
                "delta": inputText,
                "event_id": UUID().uuidString,
                "start_ms": max(0, offset),
                "end_ms": max(0, offset) + 50
            ])
        }

        // Assistant speech transcription
        let outputTranscription = (serverContent["outputTranscription"] ?? serverContent["output_transcription"]) as? [String: Any]
        let outputText = (outputTranscription?["text"] as? String) ??
            ((outputTranscription?["parts"] as? [[String: Any]])?.first?["text"] as? String)
        if let outputText, !outputText.isEmpty {
            let offset = Int(Date().timeIntervalSince(startedAt) * 1000)
            onEvent?([
                "type": "session.output_transcript.delta",
                "delta": outputText,
                "event_id": UUID().uuidString,
                "start_ms": max(0, offset),
                "end_ms": max(0, offset) + 50
            ])
        }

        guard let modelTurn = (serverContent["modelTurn"] ?? serverContent["model_turn"]) as? [String: Any],
              let parts = modelTurn["parts"] as? [[String: Any]] else { return }

        for part in parts {
            // Function call part
            if let functionCall = (part["functionCall"] ?? part["function_call"]) as? [String: Any] {
                handleFunctionCall(functionCall)
            }

            // Audio playback
            if let inlineData = (part["inlineData"] ?? part["inline_data"]) as? [String: Any],
               let base64String = inlineData["data"] as? String,
               let pcmData = Data(base64Encoded: base64String) {
                playPcmChunk(pcmData)
            }

            // Transcript text delta (if not already handled by outputAudioTranscription)
            if let textPart = part["text"] as? String, !textPart.isEmpty, outputText == nil {
                let offset = Int(Date().timeIntervalSince(startedAt) * 1000)
                onEvent?([
                    "type": "session.output_transcript.delta",
                    "delta": textPart,
                    "event_id": UUID().uuidString,
                    "start_ms": max(0, offset),
                    "end_ms": max(0, offset) + 50
                ])
            }
        }
    }

    private func handleFunctionCall(_ call: [String: Any]) {
        let id = (call["id"] as? String) ?? UUID().uuidString
        pendingToolCallIDs.insert(id)
        onEvent?([
            "type": "session.delegation.created",
            "event_id": UUID().uuidString,
            "delegation": [
                "target": "client",
                "id": id
            ]
        ])
    }

    private func handleInterruption() {
        playerNode?.stop()
        currentOutputRms = 0
        playerNode?.play()
    }

    private func playPcmChunk(_ pcmData: Data) {
        let sampleCount = pcmData.count / 2
        guard sampleCount > 0 else { return }
        let frameCapacity = AVAudioFrameCount(sampleCount)

        guard let buffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: frameCapacity) else { return }
        buffer.frameLength = frameCapacity

        pcmData.withUnsafeBytes { rawBuffer in
            if let baseAddress = rawBuffer.baseAddress, let channelData = buffer.int16ChannelData?[0] {
                channelData.initialize(from: baseAddress.assumingMemoryBound(to: Int16.self), count: sampleCount)
            }
        }

        // Calculate output RMS
        if let samples = buffer.int16ChannelData?[0] {
            var sum: Double = 0
            for i in 0..<sampleCount {
                let s = Double(samples[i])
                sum += s * s
            }
            currentOutputRms = (sqrt(sum / Double(sampleCount)) / 32768.0).clamped(to: 0...1)
        }

        playerNode?.scheduleBuffer(buffer)
        if playerNode?.isPlaying == false {
            playerNode?.play()
        }
    }

    private func sendHistoryTurns(_ history: [[String: Any]]) {
        var turns: [[String: Any]] = []
        for msg in history {
            guard let role = msg["role"] as? String else { continue }
            let geminiRole = (role == "user") ? "user" : "model"
            if let content = msg["content"] as? [[String: Any]],
               let first = content.first,
               let text = first["text"] as? String, !text.isEmpty {
                turns.append([
                    "role": geminiRole,
                    "parts": [["text": text]]
                ])
            }
        }

        if !turns.isEmpty {
            let historyMessage: [String: Any] = [
                "clientContent": [
                    "turns": turns,
                    "turnComplete": false
                ]
            ]
            sendRaw(historyMessage)
        }
    }

    private func startMetering() {
        meterTask?.cancel()
        meterTask = Task { [weak self] in
            var tickCount = 0
            while !Task.isCancelled {
                guard let self, self.started else { break }
                let input = self.currentInputRms
                let output = self.currentOutputRms

                self.lastInput = self.lastInput * 0.35 + min(1, input * 4) * 0.65
                self.lastOutput = self.lastOutput * 0.35 + min(1, output * 4) * 0.65
                self.onLevels?(self.isMuted ? 0 : self.lastInput, self.lastOutput)

                // Decay output RMS after reading
                self.currentOutputRms = max(0, self.currentOutputRms * 0.7)

                tickCount += 1
                if tickCount % 10 == 0 {
                    let elapsed = max(0, Date().timeIntervalSince(self.startedAt))
                    self.onEvent?(["type": "session.usage.updated", "usage": ["seconds": elapsed]])
                }

                try? await Task.sleep(for: .milliseconds(100))
            }
        }
    }
}

private extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
