package chat.mural.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.util.Log
import chat.mural.R
import chat.mural.core.VoiceSettingsStore
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** Native PCM audio and bidirectional WebSocket client for Google Gemini Multimodal Live API. */
class GeminiLiveTransport(
    context: Context,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = defaultClient,
    private val webSocketUrl: String? = null,
) {
    var onEvent: ((JsonObject) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var onLevels: ((Double, Double) -> Unit)? = null

    private val applicationContext = context.applicationContext
    private val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioScope = CoroutineScope(SupervisorJob() + AUDIO_DISPATCHER)
    private val generation = AtomicLong(0)
    private val lock = Any()

    @Volatile private var activeSession: Session? = null
    @Volatile private var startedState = false
    @Volatile private var mutedState = false

    val started: Boolean get() = startedState
    val isMuted: Boolean get() = mutedState

    suspend fun connect(
        key: String,
        instructions: String,
        history: JsonArray = JsonArray(emptyList()),
        language: String? = null,
        voiceName: String = DEFAULT_VOICE,
        useMediaOutput: Boolean = true,
        initialAppVolume: Float = VoiceSettingsStore.DEFAULT_APP_VOLUME,
    ) = withContext(AUDIO_DISPATCHER) {
        val attemptGen = detachSession()
        emitZeroLevels()

        if (applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw LiveTransport.microphoneException(applicationContext)
        }

        val sanitizedVolume = if (initialAppVolume.isNaN()) {
            VoiceSettingsStore.DEFAULT_APP_VOLUME
        } else {
            initialAppVolume.coerceIn(0f, 1f)
        }

        val session = Session(
            id = attemptGen,
            key = key,
            instructions = instructions,
            history = history,
            voiceName = voiceName,
            previousAudioMode = audioManager.mode,
            previousSpeakerphone = if (Build.VERSION.SDK_INT < 31) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn
            } else false,
            useMediaOutput = useMediaOutput,
            initialAppVolume = sanitizedVolume,
        )

        synchronized(lock) {
            if (generation.get() != attemptGen) throw CancellationException("Gemini Live connection superseded")
            activeSession = session
            startedState = false
            mutedState = false
        }

        try {
            configureAudio(session)
            startAudioRecording(session)
            startAudioPlayback(session)
            startMetering(session)
            connectWebSocket(session)
            try {
                withTimeout(READY_TIMEOUT_MS) { session.setupComplete.await() }
            } catch (e: TimeoutCancellationException) {
                throw IOException(applicationContext.getString(R.string.error_transport_timeout))
            }
        } catch (t: Throwable) {
            cleanupSession(session)
            throw t
        }
    }

    fun send(event: JsonObject): Boolean {
        val session = activeSession ?: return false
        val ws = session.webSocket ?: return false
        val type = (event["type"] as? JsonPrimitive)?.contentOrNull ?: return false
        val eventId = (event["event_id"] as? JsonPrimitive)?.contentOrNull ?: UUID.randomUUID().toString()

        return try {
            when (type) {
                "session.commentary.append" -> {
                    session.isTyping.set(false)
                    val delegationId = (event["delegation_id"] as? JsonPrimitive)?.contentOrNull
                    val content = (event["content"] as? JsonPrimitive)?.contentOrNull ?: ""
                    if (delegationId != null) {
                        val toolResponse = buildJsonObject {
                            put("toolResponse", buildJsonObject {
                                put("functionResponses", buildJsonArray {
                                    add(buildJsonObject {
                                        put("id", delegationId)
                                        put("response", buildJsonObject {
                                            put("output", buildJsonObject {
                                                put("commentary", content)
                                            })
                                        })
                                    })
                                })
                            })
                        }
                        session.pendingToolCallIds.remove(delegationId)
                        ws.send(toolResponse.toString())
                    } else {
                        // Spoken commentary for user's typed reply
                        val promptText = if (!session.pendingThinkingContext.isNullOrBlank()) {
                            val ctx = session.pendingThinkingContext
                            session.pendingThinkingContext = null
                            "The learner wrote in text: \"$ctx\". Speak this reply aloud to the learner: $content"
                        } else {
                            "Speak this reply aloud to the learner: $content"
                        }
                        val commentaryMsg = buildJsonObject {
                            put("clientContent", buildJsonObject {
                                put("turns", buildJsonArray {
                                    add(buildJsonObject {
                                        put("role", "user")
                                        put("parts", buildJsonArray {
                                            add(buildJsonObject {
                                                put("text", promptText)
                                            })
                                        })
                                    })
                                })
                                put("turnComplete", true)
                            })
                        }
                        ws.send(commentaryMsg.toString())
                    }
                    emitEvent(buildJsonObject {
                        put("type", "session.commentary.appended")
                        put("client_event_id", eventId)
                    })
                    true
                }
                "session.typing.start" -> {
                    session.isTyping.set(true)
                    session.playbackQueue.clear()
                    session.audioTrack?.let { track ->
                        try {
                            track.pause()
                            track.flush()
                            track.play()
                        } catch (_: Exception) { }
                    }
                    session.outputLevel = 0.0
                    emitEvent(buildJsonObject {
                        put("type", "session.typing.started")
                        put("client_event_id", eventId)
                    })
                    true
                }
                "session.typing.stop" -> {
                    session.isTyping.set(false)
                    emitEvent(buildJsonObject {
                        put("type", "session.typing.stopped")
                        put("client_event_id", eventId)
                    })
                    true
                }
                "session.instructions.append" -> {
                    val content = (event["content"] as? JsonPrimitive)?.contentOrNull ?: ""
                    val isFirstGreeting = !session.initialGreetingDone.getAndSet(true)
                    if (isFirstGreeting) {
                        session.isAwaitingInitialGreeting.set(true)
                        session.greetingSafetyJob?.cancel()
                        session.greetingSafetyJob = audioScope.launch {
                            delay(1800)
                            session.isAwaitingInitialGreeting.set(false)
                        }
                    }
                    val instructionMsg = buildJsonObject {
                        put("clientContent", buildJsonObject {
                            put("turns", buildJsonArray {
                                add(buildJsonObject {
                                    put("role", "user")
                                    put("parts", buildJsonArray {
                                        add(buildJsonObject { put("text", content) })
                                    })
                                })
                            })
                            put("turnComplete", isFirstGreeting)
                        })
                    }
                    ws.send(instructionMsg.toString())
                    emitEvent(buildJsonObject {
                        put("type", "session.instructions.appended")
                        put("client_event_id", eventId)
                    })
                    true
                }
                "session.thinking.append" -> {
                    val content = (event["content"] as? JsonPrimitive)?.contentOrNull ?: ""
                    session.pendingThinkingContext = content
                    emitEvent(buildJsonObject {
                        put("type", "session.thinking.appended")
                        put("client_event_id", eventId)
                    })
                    true
                }
                "session.input_audio.mute" -> {
                    mute(true)
                    true
                }
                "session.input_audio.unmute" -> {
                    mute(false)
                    true
                }
                "session.close" -> {
                    close()
                    true
                }
                else -> {
                    ws.send(event.toString())
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun mute(muted: Boolean) {
        mutedState = muted
    }

    fun setAppVolume(volume: Float) {
        val clamped = if (volume.isNaN()) 0f else volume.coerceIn(0f, 1f)
        val session = activeSession ?: return
        session.appVolume = clamped
        applyPlaybackVolume(session)
    }

    fun setPlaybackPaused(paused: Boolean) {
        val session = activeSession ?: return
        session.userPaused.set(paused)
        applyPlaybackVolume(session)
    }

    private fun applyPlaybackVolume(session: Session) {
        val track = session.audioTrack ?: return
        val gain = if (session.userPaused.get() || session.focusPaused.get()) {
            0f
        } else {
            (VoiceSettingsStore.perceptualGain(session.appVolume) * session.duckMultiplier).coerceIn(0f, 1f)
        }
        try {
            track.setVolume(gain)
        } catch (_: Exception) { }
    }

    fun close() {
        val session = activeSession ?: return
        session.closing.set(true)
        mutedState = true
        val elapsed = ((System.currentTimeMillis() - session.startedAtMs) / 1000.0).coerceAtLeast(0.0)
        emitEvent(buildJsonObject {
            put("type", "session.closed")
            put("usage", buildJsonObject {
                put("seconds", elapsed)
            })
        })
        audioScope.launch {
            session.webSocket?.close(1000, "Normal closure")
        }
    }

    fun disconnect() {
        val detached = detachSession()
        audioScope.launch { emitZeroLevels() }
    }

    private fun detachSession(): Long = synchronized(lock) {
        val prev = activeSession
        activeSession = null
        startedState = false
        mutedState = false
        if (prev != null) cleanupSession(prev)
        generation.incrementAndGet()
    }

    private fun configureAudio(session: Session) {
        val profile = if (session.useMediaOutput) AudioOutputProfile.MEDIA else AudioOutputProfile.COMMUNICATION
        val attributes = audioAttributesFor(profile)
        val focusGain = if (session.useMediaOutput) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT

        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    session.focusPaused.set(false)
                    session.duckMultiplier = 0.25f
                    applyPlaybackVolume(session)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    session.focusPaused.set(true)
                    session.duckMultiplier = 0f
                    applyPlaybackVolume(session)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    session.focusPaused.set(false)
                    session.duckMultiplier = 1.0f
                    applyPlaybackVolume(session)
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    if (!session.closing.get()) {
                        fail(session, applicationContext.getString(R.string.error_transport_audio_interrupted))
                    }
                }
            }
        }

        val focusRequest = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
        session.focusRequest = focusRequest

        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw LiveTransport.audioFocusException(applicationContext)
        }
        session.ownsAudioFocus = true
        audioManager.mode = if (session.useMediaOutput) {
            AudioManager.MODE_NORMAL
        } else {
            AudioManager.MODE_IN_COMMUNICATION
        }
        session.ownsAudioMode = true

        if (!session.useMediaOutput) {
            routeCommunicationAudio(session)

            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = reroute()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = reroute()
                private fun reroute() {
                    audioScope.launch {
                        if (activeSession?.id == session.id && !session.closing.get()) {
                            try { routeCommunicationAudio(session) } catch (_: Exception) { }
                        }
                    }
                }
            }
            session.deviceCallback = callback
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    @Suppress("DEPRECATION")
    private fun routeCommunicationAudio(session: Session) {
        if (Build.VERSION.SDK_INT >= 31) {
            val current = audioManager.communicationDevice
            val available = audioManager.availableCommunicationDevices
            val selected = selectCommunicationDevice(current, available, { a, b -> a.id == b.id }) { it.type }
            if (selected != null && current?.id != selected.id) {
                if (audioManager.setCommunicationDevice(selected)) {
                    session.ownsCommunicationRoute = true
                }
            }
        } else {
            val legacyTypes = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
            if (shouldUseLegacySpeakerphone(legacyTypes, audioManager.isBluetoothScoOn)) {
                audioManager.isSpeakerphoneOn = true
                session.ownsLegacySpeakerphone = true
            }
        }
    }

    private fun connectWebSocket(session: Session) {
        try { session.webSocket?.cancel() } catch (_: Exception) { }
        session.webSocket = null
        val url = webSocketUrl ?: (DEFAULT_WS_URL + "?key=" + session.key)
        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                audioScope.launch {
                    if (activeSession?.id != session.id) { webSocket.cancel(); return@launch }
                    sendInitialSetup(webSocket, session)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                audioScope.launch {
                    if (activeSession?.id != session.id) return@launch
                    handleIncomingMessage(session, text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audioScope.launch {
                    if (activeSession?.id != session.id) return@launch
                    handleIncomingMessage(session, bytes.utf8())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLiveTransport", "WebSocket failure: ${t.message}, httpCode=${response?.code}", t)
                audioScope.launch {
                    if (activeSession?.id != session.id || session.webSocket !== webSocket) return@launch
                    if (!session.closing.get() && session.reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
                        attemptHotReconnect(session, t.message ?: "Network error")
                    } else {
                        val message = response?.message?.takeIf { it.isNotBlank() }
                            ?: t.message?.takeIf { it.isNotBlank() }
                            ?: applicationContext.getString(R.string.error_transport_network_lost)
                        fail(session, message)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("GeminiLiveTransport", "WebSocket onClosed: code=$code, reason=$reason")
                audioScope.launch {
                    if (activeSession?.id != session.id || session.webSocket !== webSocket) return@launch
                    if (!session.closing.get()) {
                        if (code != 1000 && session.reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
                            attemptHotReconnect(session, reason.ifBlank { "Channel closed" })
                        } else {
                            val message = if (reason.isNotBlank()) reason else applicationContext.getString(R.string.error_transport_channel_closed)
                            fail(session, message)
                        }
                    }
                }
            }
        })
        session.webSocket = ws
    }

    private fun attemptHotReconnect(session: Session, reason: String) {
        if (session.closing.get() || activeSession?.id != session.id) return
        val attempt = session.reconnectAttempts.incrementAndGet()
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            Log.e("GeminiLiveTransport", "Max hot-reconnect attempts ($MAX_RECONNECT_ATTEMPTS) exceeded. Failing session.")
            fail(session, applicationContext.getString(R.string.error_transport_network_lost))
            return
        }

        session.isReconnecting.set(true)
        session.reconnectJob?.cancel()
        session.reconnectJob = audioScope.launch {
            val backoffMs = minOf(3000L, 400L * (1L shl (attempt - 1)))
            Log.w("GeminiLiveTransport", "Hot-reconnecting attempt #$attempt in ${backoffMs}ms due to: $reason")
            delay(backoffMs)
            if (session.closing.get() || activeSession?.id != session.id) return@launch
            try { session.webSocket?.cancel() } catch (_: Exception) { }
            session.webSocket = null
            connectWebSocket(session)
        }
    }

    private fun sendInitialSetup(ws: WebSocket, session: Session) {
        val promptSuffix = "\n\n" +
            "IMPORTANT: You are in an active, real-time spoken voice conversation with the learner. " +
            "Actively listen to the learner's voice at all times. " +
            "Whenever the learner speaks, reply immediately and naturally in spoken audio. " +
            "Keep each turn concise (1-2 sentences), warm, and end with a natural follow-up question. " +
            "Transcribe and interpret the learner's voice strictly within the designated languages, without guessing words in unrelated foreign languages. " +
            "Treat non-lexical audio artifacts, background breathing, throat clearing, keyboard clicks, or quiet room noise strictly as silence. NEVER interpret ambient hums, clicks, or room noise as spoken words."
        val fullInstructions = session.instructions + promptSuffix

        val modelName = session.modelName
        val setupMessage = buildJsonObject {
            put("setup", buildJsonObject {
                put("model", if (modelName.startsWith("models/")) modelName else "models/$modelName")
                put("generationConfig", buildJsonObject {
                    put("responseModalities", buildJsonArray {
                        add(JsonPrimitive("AUDIO"))
                    })
                    put("speechConfig", buildJsonObject {
                        put("voiceConfig", buildJsonObject {
                            put("prebuiltVoiceConfig", buildJsonObject {
                                put("voiceName", session.voiceName)
                            })
                        })
                    })
                })
                put("inputAudioTranscription", buildJsonObject {})
                put("outputAudioTranscription", buildJsonObject {})
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", fullInstructions) })
                    })
                })
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            add(buildJsonObject {
                                put("name", "delegate_helper")
                                put("description", "Delegate a research, source-backed lookup, or teaching helper query to the learning assistant helper. Use this when the learner asks about current events, specific facts outside your immediate knowledge, or requests a lookup.")
                                put("parameters", buildJsonObject {
                                    put("type", "OBJECT")
                                    put("properties", buildJsonObject {
                                        put("query", buildJsonObject {
                                            put("type", "STRING")
                                            put("description", "The specific query, topic, or question to delegate and look up.")
                                        })
                                    })
                                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                                })
                            })
                        })
                    })
                })
            })
        }
        ws.send(setupMessage.toString())
    }

    private fun sendHistoryTurns(session: Session) {
        val turns = buildJsonArray {
            for (msg in session.history) {
                val obj = msg as? JsonObject ?: continue
                val role = (obj["role"] as? JsonPrimitive)?.contentOrNull
                val geminiRole = if (role == "user") "user" else "model"
                val content = obj["content"] as? JsonArray
                val firstContent = content?.firstOrNull() as? JsonObject
                val text = (firstContent?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
                if (text.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", geminiRole)
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", text) })
                        })
                    })
                }
            }
        }
        if (turns.isNotEmpty()) {
            val historyMsg = buildJsonObject {
                put("clientContent", buildJsonObject {
                    put("turns", turns)
                    put("turnComplete", false)
                })
            }
            session.webSocket?.send(historyMsg.toString())
        }
    }

    private fun handleIncomingMessage(session: Session, messageText: String) {
        val json = try {
            JSON.parseToJsonElement(messageText).jsonObject
        } catch (_: Exception) {
            return
        }

        Log.d("GeminiLiveTransport", "WS incoming frame keys: ${json.keys}")

        val errorObj = (json["error"] ?: json["error_message"]) as? JsonObject
        if (errorObj != null) {
            val message = (errorObj["message"] as? JsonPrimitive)?.contentOrNull ?: "Gemini Live error"
            val code = (errorObj["code"] as? JsonPrimitive)?.contentOrNull
            Log.e("GeminiLiveTransport", "Server reported error: code=$code, message=$message")
            fail(session, message)
            return
        }

        val isSetupDone = json.containsKey("setupComplete") ||
            json.containsKey("setup_complete") ||
            json.containsKey("bidiGenerateContentSetupComplete") ||
            json.containsKey("bidi_generate_content_setup_complete")
        if (isSetupDone) {
            startedState = true
            session.setupComplete.complete(Unit)
            val isFirstSetup = session.hasCompletedInitialSetup.compareAndSet(false, true)
            if (isFirstSetup) {
                emitEvent(buildJsonObject {
                    put("type", "session.started")
                    put("event_id", UUID.randomUUID().toString())
                    put("session", buildJsonObject { put("id", "gemini-" + session.id) })
                })
                if (session.history.isNotEmpty()) {
                    sendHistoryTurns(session)
                }
            } else {
                session.reconnectAttempts.set(0)
                session.isReconnecting.set(false)
                Log.i("GeminiLiveTransport", "Hot-reconnection established successfully for session ${session.id}")
            }
            return
        }

        // Check for tool calls at root level (camelCase and snake_case)
        val toolCall = (json["toolCall"] ?: json["tool_call"]) as? JsonObject
        val rootFunctionCalls = ((toolCall?.get("functionCalls") ?: toolCall?.get("function_calls")) as? JsonArray)
        if (rootFunctionCalls != null) {
            for (callElem in rootFunctionCalls) {
                val callObj = callElem as? JsonObject ?: continue
                handleFunctionCall(session, callObj)
            }
        }

        val serverContent = (json["serverContent"] ?: json["server_content"]) as? JsonObject ?: return

        val turnComplete = (serverContent["turnComplete"] ?: serverContent["turn_complete"]) as? JsonPrimitive
        if (turnComplete?.content == "true") {
            session.isAwaitingInitialGreeting.set(false)
            session.greetingSafetyJob?.cancel()
        }

        val isInterrupted = (serverContent["interrupted"] as? JsonPrimitive)?.content == "true"
        if (isInterrupted && !session.isAwaitingInitialGreeting.get()) {
            // Barge-in: flush playback queue immediately
            session.playbackQueue.clear()
            session.audioTrack?.let { track ->
                try {
                    track.pause()
                    track.flush()
                    track.play()
                } catch (_: Exception) { }
            }
            session.outputLevel = 0.0
        }

        // Handle user input speech transcription (supports both camelCase and snake_case)
        val inputTranscription = (serverContent["inputTranscription"]
            ?: serverContent["input_transcription"]
            ?: serverContent["interimInputTranscription"]
            ?: serverContent["interim_input_transcription"]) as? JsonObject
        val inputText = ((inputTranscription?.get("text") ?: inputTranscription?.get("parts")) as? JsonPrimitive)?.contentOrNull
            ?: ((inputTranscription?.get("parts") as? JsonArray)?.firstOrNull() as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        if (!inputText.isNullOrBlank()) {
            val sanitized = sanitizeInputTranscription(inputText, session.instructions)
            if (!sanitized.isNullOrBlank()) {
                val offset = (System.currentTimeMillis() - session.startedAtMs).toInt().coerceAtLeast(0)
                emitEvent(buildJsonObject {
                    put("type", "session.input_transcript.delta")
                    put("delta", JsonPrimitive(sanitized))
                    put("start_ms", offset)
                    put("end_ms", offset + 50)
                    put("event_id", UUID.randomUUID().toString())
                })
            }
        }

        val outputTranscription = (serverContent["outputTranscription"] ?: serverContent["output_transcription"]) as? JsonObject
        val outputText = ((outputTranscription?.get("text") ?: outputTranscription?.get("parts")) as? JsonPrimitive)?.contentOrNull
            ?: ((outputTranscription?.get("parts") as? JsonArray)?.firstOrNull() as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        if (!outputText.isNullOrBlank()) {
            val offset = (System.currentTimeMillis() - session.startedAtMs).toInt().coerceAtLeast(0)
            emitEvent(buildJsonObject {
                put("type", "session.output_transcript.delta")
                put("delta", outputText)
                put("start_ms", offset)
                put("end_ms", offset + 50)
                put("event_id", UUID.randomUUID().toString())
            })
        }

        val modelTurn = (serverContent["modelTurn"] ?: serverContent["model_turn"]) as? JsonObject
        if (modelTurn != null) {
            val parts = modelTurn["parts"] as? JsonArray ?: JsonArray(emptyList())
            for (part in parts) {
                val partObj = part as? JsonObject ?: continue

                // Check for function call part inside model turn
                val functionCall = (partObj["functionCall"] ?: partObj["function_call"]) as? JsonObject
                if (functionCall != null) {
                    handleFunctionCall(session, functionCall)
                }

                // Audio output chunk
                val inlineData = (partObj["inlineData"] ?: partObj["inline_data"]) as? JsonObject
                if (inlineData != null) {
                    val base64Audio = (inlineData["data"] as? JsonPrimitive)?.contentOrNull
                    if (!base64Audio.isNullOrBlank()) {
                        val pcm = try { Base64.decode(base64Audio, Base64.NO_WRAP) } catch (_: Exception) { null }
                        if (pcm != null && pcm.isNotEmpty()) {
                            session.playbackQueue.offer(pcm)
                        }
                    }
                }

                // Text transcript delta (if not already handled by outputAudioTranscription)
                val text = (partObj["text"] as? JsonPrimitive)?.contentOrNull
                if (!text.isNullOrEmpty() && outputText == null) {
                    val offset = (System.currentTimeMillis() - session.startedAtMs).toInt().coerceAtLeast(0)
                    emitEvent(buildJsonObject {
                        put("type", "session.output_transcript.delta")
                        put("delta", text)
                        put("start_ms", offset)
                        put("end_ms", offset + 50)
                        put("event_id", UUID.randomUUID().toString())
                    })
                }
            }
        }
    }

    private fun handleFunctionCall(session: Session, callObj: JsonObject) {
        val id = (callObj["id"] as? JsonPrimitive)?.contentOrNull ?: UUID.randomUUID().toString()
        session.pendingToolCallIds.add(id)
        emitEvent(buildJsonObject {
            put("type", "session.delegation.created")
            put("event_id", UUID.randomUUID().toString())
            put("delegation", buildJsonObject {
                put("target", "client")
                put("id", id)
            })
        })
    }

    private fun startAudioRecording(session: Session) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_BYTES)

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )

        val recordBufferSize = maxOf(minBufferSize * 4, CHUNK_BYTES * 8, 16000)

        var audioRecord: AudioRecord? = null
        for (source in sources) {
            try {
                val candidate = AudioRecord(
                    source,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize,
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = candidate
                    Log.i("GeminiLiveTransport", "AudioRecord initialized with source=$source, buffer=$recordBufferSize")
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w("GeminiLiveTransport", "Failed to initialize AudioRecord with source=$source: ${e.message}")
            }
        }

        if (audioRecord == null) {
            fail(session, applicationContext.getString(R.string.error_transport_audio_stopped))
            return
        }

        session.audioRecord = audioRecord

        // Bind hardware AEC and NoiseSuppressor to the audio session ID if available
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)
                if (aec != null) {
                    aec.enabled = true
                    session.aec = aec
                    Log.i("GeminiLiveTransport", "AcousticEchoCanceler enabled on session ${audioRecord.audioSessionId}")
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiLiveTransport", "Failed to enable AcousticEchoCanceler: ${e.message}")
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(audioRecord.audioSessionId)
                if (ns != null) {
                    ns.enabled = true
                    session.ns = ns
                    Log.i("GeminiLiveTransport", "NoiseSuppressor enabled on session ${audioRecord.audioSessionId}")
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiLiveTransport", "Failed to enable NoiseSuppressor: ${e.message}")
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(audioRecord.audioSessionId)
                if (agc != null) {
                    agc.enabled = true
                    session.agc = agc
                    Log.i("GeminiLiveTransport", "AutomaticGainControl enabled on session ${audioRecord.audioSessionId}")
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiLiveTransport", "Failed to enable AutomaticGainControl: ${e.message}")
        }

        try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w("GeminiLiveTransport", "AudioRecord startRecording() did not enter RECORDSTATE_RECORDING: ${audioRecord.recordingState}")
            }
        } catch (e: Exception) {
            Log.e("GeminiLiveTransport", "Failed to startRecording", e)
            fail(session, applicationContext.getString(R.string.error_transport_audio_stopped))
            return
        }

        session.recordingJob = audioScope.launch(Dispatchers.IO) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Exception) {
                Log.w("GeminiLiveTransport", "Could not set recording thread priority: ${e.message}")
            }
            val buffer = ByteArray(CHUNK_BYTES)
            var logTick = 0
            while (isActive && activeSession?.id == session.id && !session.closing.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    applyGain(buffer, read, 1.5f)
                    val inputLevel = calculateRms(buffer, read)
                    session.inputLevel = if (mutedState) 0.0 else inputLevel

                    val isGreeting = session.isAwaitingInitialGreeting.get()
                    val isTyping = session.isTyping.get()

                    // Local Instant Barge-In (<10ms):
                    // If AI is playing audio (outputLevel > 0.02 or playbackQueue has chunks)
                    // and user starts speaking (inputLevel > 0.08 for at least 2 frames = 100ms),
                    // immediately silence AI playback and flush audio track to prevent talking over user.
                    if (!mutedState && !isTyping && !isGreeting && (session.outputLevel > 0.02 || !session.playbackQueue.isEmpty())) {
                        if (inputLevel > 0.08) {
                            session.userSpeechFramesCount++
                            if (session.userSpeechFramesCount >= 2) {
                                session.playbackQueue.clear()
                                try {
                                    session.audioTrack?.pause()
                                    session.audioTrack?.flush()
                                    session.audioTrack?.play()
                                } catch (_: Exception) { }
                                session.outputLevel = 0.0
                                session.userSpeechFramesCount = 0
                            }
                        } else {
                            session.userSpeechFramesCount = 0
                        }
                    } else {
                        session.userSpeechFramesCount = 0
                    }

                    if (isGreeting && inputLevel > 0.12) {
                        session.isAwaitingInitialGreeting.set(false)
                        session.greetingSafetyJob?.cancel()
                    }

                    if (!mutedState && session.setupComplete.isCompleted && !isGreeting && !session.isReconnecting.get() && !isTyping) {
                        val currentWs = session.webSocket
                        val qSize = currentWs?.queueSize() ?: 0L
                        if (qSize <= MAX_PENDING_WS_QUEUE_BYTES) {
                            flushPreRollBuffer(session, currentWs)
                            sendAudioChunk(currentWs, buffer, read)
                        } else {
                            Log.w("GeminiLiveTransport", "Dropping audio frame due to WebSocket backpressure ($qSize bytes in queue)")
                        }
                    } else if (!mutedState && !isTyping) {
                        accumulatePreRoll(session, buffer, read)
                    }
                    logTick++
                    if (logTick % 40 == 0) {
                        Log.d("GeminiLiveTransport", "Mic streaming: read=$read, rms=$inputLevel, isTyping=$isTyping")
                    }
                } else if (read < 0) {
                    Log.w("GeminiLiveTransport", "AudioRecord read returned error code: $read")
                    delay(50)
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun sendAudioChunk(ws: WebSocket?, buffer: ByteArray, length: Int): Boolean {
        if (ws == null) return false
        val base64 = Base64.encodeToString(buffer, 0, length, Base64.NO_WRAP)
        val message = buildJsonObject {
            put("realtimeInput", buildJsonObject {
                put("audio", buildJsonObject {
                    put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                    put("data", base64)
                })
                put("mediaChunks", buildJsonArray {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                        put("data", base64)
                    })
                })
            })
        }
        return ws.send(message.toString())
    }

    private fun accumulatePreRoll(session: Session, buffer: ByteArray, length: Int) {
        if (session.preRollBuffer.size >= PRE_ROLL_CHUNKS) {
            session.preRollBuffer.poll()
        }
        session.preRollBuffer.offer(buffer.copyOf(length))
    }

    private fun flushPreRollBuffer(session: Session, ws: WebSocket?) {
        if (ws == null) return
        while (true) {
            val chunk = session.preRollBuffer.poll() ?: break
            sendAudioChunk(ws, chunk, chunk.size)
        }
    }

    private fun startAudioPlayback(session: Session) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val profile = if (session.useMediaOutput) AudioOutputProfile.MEDIA else AudioOutputProfile.COMMUNICATION
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributesFor(profile))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            fail(session, applicationContext.getString(R.string.error_transport_audio_stopped))
            return
        }

        session.audioTrack = audioTrack
        try {
            applyPlaybackVolume(session)
            audioTrack.play()
        } catch (_: Exception) { }

        session.playbackJob = audioScope.launch(Dispatchers.IO) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Exception) {
                Log.w("GeminiLiveTransport", "Could not set playback thread priority: ${e.message}")
            }
            while (isActive && activeSession?.id == session.id && !session.closing.get()) {
                val chunk = session.playbackQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    session.outputLevel = calculateRms(chunk, chunk.size)
                    audioTrack.write(chunk, 0, chunk.size)
                } else {
                    session.outputLevel = 0.0
                    delay(15)
                }
            }
        }
    }

    private fun startMetering(session: Session) {
        session.meteringJob = audioScope.launch {
            var smoothedInput = 0.0
            var smoothedOutput = 0.0
            var tickCount = 0
            while (isActive && activeSession?.id == session.id) {
                val scaledInput = minOf(1.0, Math.sqrt(session.inputLevel) * 2.2)
                smoothedInput = smoothedInput * 0.35 + scaledInput * 0.65
                smoothedOutput = smoothedOutput * 0.35 + minOf(1.0, session.outputLevel * 4.0) * 0.65
                onLevels?.invoke(if (mutedState) 0.0 else smoothedInput, smoothedOutput)
                tickCount++
                if (tickCount % 10 == 0) {
                    val elapsed = ((System.currentTimeMillis() - session.startedAtMs) / 1000.0).coerceAtLeast(0.0)
                    emitEvent(buildJsonObject {
                        put("type", "session.usage.updated")
                        put("usage", buildJsonObject {
                            put("seconds", elapsed)
                        })
                    })
                }
                delay(100)
            }
        }
    }

    private fun applyGain(buffer: ByteArray, length: Int, multiplier: Float) {
        if (multiplier == 1.0f) return
        for (i in 0 until length - 1 step 2) {
            val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort().toFloat()
            val scaled = sample * multiplier
            val clamped = when {
                scaled > 30000f -> (30000f + (scaled - 30000f) * 0.2f).coerceAtMost(Short.MAX_VALUE.toFloat())
                scaled < -30000f -> (-30000f + (scaled + 30000f) * 0.2f).coerceAtLeast(Short.MIN_VALUE.toFloat())
                else -> scaled
            }
            val amplified: Int = clamped.toInt()
            buffer[i] = (amplified and 0xFF).toByte()
            buffer[i + 1] = ((amplified shr 8) and 0xFF).toByte()
        }
    }

    private fun calculateRms(bytes: ByteArray, length: Int): Double {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0.0
        for (i in 0 until length - 1 step 2) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            val shortVal = sample.toShort().toDouble()
            sum += shortVal * shortVal
        }
        val rms = Math.sqrt(sum / sampleCount)
        return (rms / 32768.0).coerceIn(0.0, 1.0)
    }

    private fun emitEvent(event: JsonObject) {
        scope.launch { onEvent?.invoke(event) }
    }

    private fun emitZeroLevels() {
        onLevels?.invoke(0.0, 0.0)
    }

    private fun fail(session: Session, message: String) {
        if (activeSession?.id != session.id) return
        if (!session.setupComplete.isCompleted) {
            session.setupComplete.completeExceptionally(IOException(message))
        }
        cleanupSession(session)
        scope.launch { onFailure?.invoke(message) }
    }

    private fun cleanupSession(session: Session) {
        session.closing.set(true)
        if (!session.setupComplete.isCompleted) {
            session.setupComplete.completeExceptionally(IOException("Session closed"))
        }
        session.recordingJob?.cancel()
        session.playbackJob?.cancel()
        session.meteringJob?.cancel()
        session.reconnectJob?.cancel()
        session.greetingSafetyJob?.cancel()

        try { session.webSocket?.cancel() } catch (_: Exception) { }
        session.webSocket = null

        try { session.aec?.release() } catch (_: Exception) { }
        session.aec = null
        try { session.ns?.release() } catch (_: Exception) { }
        session.ns = null
        try { session.agc?.release() } catch (_: Exception) { }
        session.agc = null

        try {
            session.audioRecord?.stop()
            session.audioRecord?.release()
        } catch (_: Exception) { }
        session.audioRecord = null

        try {
            session.audioTrack?.stop()
            session.audioTrack?.release()
        } catch (_: Exception) { }
        session.audioTrack = null

        session.playbackQueue.clear()
        session.preRollBuffer.clear()

        session.deviceCallback?.let {
            try { audioManager.unregisterAudioDeviceCallback(it) } catch (_: Exception) { }
        }
        session.deviceCallback = null

        if (Build.VERSION.SDK_INT >= 31 && session.ownsCommunicationRoute) {
            try { audioManager.clearCommunicationDevice() } catch (_: Exception) { }
            session.ownsCommunicationRoute = false
        }
        if (session.ownsLegacySpeakerphone) {
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = session.previousSpeakerphone
            } catch (_: Exception) { }
            session.ownsLegacySpeakerphone = false
        }
        if (session.ownsAudioFocus && session.focusRequest != null) {
            audioManager.abandonAudioFocusRequest(session.focusRequest!!)
            session.ownsAudioFocus = false
        }
        if (session.ownsAudioMode) {
            audioManager.mode = session.previousAudioMode
            session.ownsAudioMode = false
        }
    }

    private class Session(
        val id: Long,
        val key: String,
        val instructions: String,
        val history: JsonArray,
        val voiceName: String,
        val previousAudioMode: Int,
        val previousSpeakerphone: Boolean = false,
        val useMediaOutput: Boolean = true,
        initialAppVolume: Float = VoiceSettingsStore.DEFAULT_APP_VOLUME,
    ) {
        val modelName: String = DEFAULT_MODEL
        @Volatile var appVolume: Float = initialAppVolume.coerceIn(0f, 1f)
        @Volatile var duckMultiplier: Float = 1.0f
        val focusPaused = AtomicBoolean(false)
        val userPaused = AtomicBoolean(false)
        val setupComplete = CompletableDeferred<Unit>()
        val closing = AtomicBoolean(false)
        val isAwaitingInitialGreeting = AtomicBoolean(false)
        val initialGreetingDone = AtomicBoolean(false)
        var greetingSafetyJob: Job? = null
        val isTyping = AtomicBoolean(false)
        val preRollBuffer = ConcurrentLinkedQueue<ByteArray>()
        var userSpeechFramesCount = 0
        val startedAtMs = System.currentTimeMillis()
        val pendingToolCallIds = ConcurrentHashMap.newKeySet<String>()
        val hasCompletedInitialSetup = AtomicBoolean(false)
        val reconnectAttempts = AtomicInteger(0)
        val isReconnecting = AtomicBoolean(false)
        var reconnectJob: Job? = null
        var webSocket: WebSocket? = null
        var audioRecord: AudioRecord? = null
        var audioTrack: AudioTrack? = null
        val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
        var recordingJob: Job? = null
        var playbackJob: Job? = null
        var meteringJob: Job? = null
        var focusRequest: AudioFocusRequest? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        var agc: AutomaticGainControl? = null
        var pendingThinkingContext: String? = null
        var ownsAudioFocus = false
        var ownsAudioMode = false
        var ownsCommunicationRoute = false
        var ownsLegacySpeakerphone = false
        var deviceCallback: AudioDeviceCallback? = null
        @Volatile var inputLevel: Double = 0.0
        @Volatile var outputLevel: Double = 0.0
    }

    companion object {
        private val AUDIO_DISPATCHER = Executors.newSingleThreadExecutor { r ->
            Thread(r, "mural-gemini-live-audio").apply { priority = Thread.MAX_PRIORITY }
        }.asCoroutineDispatcher()

        private const val DEFAULT_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "gemini-3.8-live"
        const val DEFAULT_VOICE = "Puck"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHUNK_BYTES = 1600
        private const val READY_TIMEOUT_MS = 15000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val MAX_PENDING_WS_QUEUE_BYTES = 64 * 1024L
        private const val PRE_ROLL_CHUNKS = 4 // 4 * 50ms = 200ms
        private val JSON = Json { ignoreUnknownKeys = true }

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun sanitizeInputTranscription(text: String, instructions: String? = null): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            // 1. Filter out pure non-lexical annotations and bracketed audio artifacts
            if (trimmed.matches(Regex("^\\[.*?\\]$")) || trimmed.matches(Regex("^\\(.*?\\)$"))) {
                return null
            }
            if (trimmed.equals("[laughter]", ignoreCase = true) ||
                trimmed.equals("[applause]", ignoreCase = true) ||
                trimmed.equals("[cough]", ignoreCase = true) ||
                trimmed.equals("[throat-clearing]", ignoreCase = true) ||
                trimmed.equals("[music]", ignoreCase = true) ||
                trimmed.equals("[sigh]", ignoreCase = true) ||
                trimmed.equals("(inaudible)", ignoreCase = true)
            ) {
                return null
            }

            // 2. Filter out pure noise punctuation (e.g. "...", "???", "*", "---")
            if (trimmed.matches(Regex("^[\\s.,;:!?'\"`~*\\-_/\\\\()\\[\\]{}]+$"))) {
                return null
            }

            // 3. Filter out repetitive hallucinated tokens (e.g., "und und und und", "ja ja ja ja")
            val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 3) {
                val distinctWords = words.map { it.lowercase() }.distinct()
                if (distinctWords.size == 1) {
                    return null
                }
            }

            // 4. Incompatible script filter: if practicing non-Cyrillic/non-Arabic languages, filter out accidental noise-induced Cyrillic or Arabic
            val hasCyrillic = trimmed.any { it in '\u0400'..'\u04FF' }
            val hasArabic = trimmed.any { it in '\u0600'..'\u06FF' }
            val isTargetingNonCyrillicArabic = instructions?.let {
                !it.contains("Russian", ignoreCase = true) && !it.contains("Arabic", ignoreCase = true)
            } ?: true

            if (isTargetingNonCyrillicArabic && (hasCyrillic || hasArabic)) {
                return null
            }

            return trimmed
        }
    }
}
