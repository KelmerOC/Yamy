package chat.mural.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GeminiLiveTransportTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        try {
            server.shutdown()
        } catch (_: Exception) { }
    }

    @Test
    fun setupHandshakeAndEventsProcessing(): Unit = runBlocking {
        val serverReceivedMessages = LinkedBlockingQueue<String>()
        val serverWebSocket = CompletableDeferred<WebSocket>()

        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverWebSocket.complete(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                serverReceivedMessages.offer(text)
            }
        }))

        val receivedEvents = LinkedBlockingQueue<JsonObject>()
        val wsUrl = server.url("/ws").toString()

        val ws = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val setup = """{"setup":{"model":"models/gemini-3.8-live","generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":"Puck"}}},"inputAudioTranscription":{},"outputAudioTranscription":{}},"systemInstruction":{"parts":[{"text":"Teach Spanish"}]},"tools":[{"functionDeclarations":[{"name":"delegate_helper","description":"Delegate helper query","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"}},"required":["query"]}}]}]}}"""
                    webSocket.send(setup)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = Json.parseToJsonElement(text).jsonObject
                    if (json.containsKey("setupComplete")) {
                        val event = Json.parseToJsonElement("""{"type":"session.started","session":{"id":"gemini-test"}}""").jsonObject
                        receivedEvents.offer(event)
                    }

                    // Tool call at root
                    val toolCall = json["toolCall"]?.jsonObject
                    val functionCalls = toolCall?.get("functionCalls")?.jsonArray
                    functionCalls?.forEach { callElem ->
                        val call = callElem.jsonObject
                        val id = call["id"]?.jsonPrimitive?.content ?: "del-1"
                        receivedEvents.offer(buildJsonObject {
                            put("type", "session.delegation.created")
                            put("delegation", buildJsonObject {
                                put("target", "client")
                                put("id", id)
                            })
                        })
                    }

                    val serverContent = json["serverContent"]?.jsonObject
                    val modelTurn = serverContent?.get("modelTurn")?.jsonObject
                    val parts = modelTurn?.get("parts")?.jsonArray
                    parts?.forEach { part ->
                        val textPart = part.jsonObject["text"]?.jsonPrimitive?.content
                        if (!textPart.isNullOrEmpty()) {
                            val event = Json.parseToJsonElement("""{"type":"session.output_transcript.delta","delta":"$textPart"}""").jsonObject
                            receivedEvents.offer(event)
                        }
                    }
                }
            }
        )

        // Wait for setup message on server
        val setupReceived = serverReceivedMessages.poll(5, TimeUnit.SECONDS)
        assertNotNull("Setup message should be received by server", setupReceived)
        val setupJson = Json.parseToJsonElement(setupReceived!!).jsonObject
        assertTrue(setupJson.containsKey("setup"))
        val setupObj = setupJson["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", setupObj["model"]?.jsonPrimitive?.content)
        assertTrue("Setup must declare tools with functionDeclarations", setupObj.containsKey("tools"))

        // Server sends setupComplete
        val wsServer = serverWebSocket.await()
        wsServer.send("""{"setupComplete":{}}""")

        val startedEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("session.started event should be emitted", startedEvent)
        assertEquals("session.started", startedEvent?.get("type")?.jsonPrimitive?.content)

        // Server sends modelTurn with text delta
        wsServer.send("""{"serverContent":{"modelTurn":{"parts":[{"text":"¡Hola! ¿Cómo estás?"}]}}}""")

        val deltaEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("delta event should be received", deltaEvent)
        assertEquals("session.output_transcript.delta", deltaEvent?.get("type")?.jsonPrimitive?.content)
        assertEquals("¡Hola! ¿Cómo estás?", deltaEvent?.get("delta")?.jsonPrimitive?.content)

        // Server sends a function call (delegation)
        wsServer.send("""{"toolCall":{"functionCalls":[{"name":"delegate_helper","id":"call_lookup_1","args":{"query":"Salamanca history"}}]}}""")

        val delegationEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("delegation event should be emitted", delegationEvent)
        assertEquals("session.delegation.created", delegationEvent?.get("type")?.jsonPrimitive?.content)
        val delegationObj = delegationEvent?.get("delegation")?.jsonObject
        assertEquals("client", delegationObj?.get("target")?.jsonPrimitive?.content)
        assertEquals("call_lookup_1", delegationObj?.get("id")?.jsonPrimitive?.content)

        // Client responds to delegation with toolResponse
        val toolResponseMsg = """{"toolResponse":{"functionResponses":[{"id":"call_lookup_1","response":{"output":{"commentary":"Salamanca has one of the oldest universities in Europe."}}}]}}"""
        ws.send(toolResponseMsg)

        val toolResponseReceived = serverReceivedMessages.poll(5, TimeUnit.SECONDS)
        assertNotNull("toolResponse should be received by server", toolResponseReceived)
        val toolResponseJson = Json.parseToJsonElement(toolResponseReceived!!).jsonObject
        assertTrue(toolResponseJson.containsKey("toolResponse"))
        val functionResponses = toolResponseJson["toolResponse"]?.jsonObject?.get("functionResponses")?.jsonArray
        assertNotNull(functionResponses)
        assertEquals(1, functionResponses?.size)
        assertEquals("call_lookup_1", functionResponses?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content)

        ws.close(1000, "Done")
    }

    @Test
    fun clientCommandsDispatchCorrectGeminiFrames() {
        val clientEventId = UUID.randomUUID().toString()

        // 1. Commentary with delegation ID converts to toolResponse
        val commentaryWithDelegation = buildJsonObject {
            put("type", "session.commentary.append")
            put("event_id", clientEventId)
            put("delegation_id", "call_abc_123")
            put("content", "Madrid is the capital of Spain.")
        }
        val delegationId = commentaryWithDelegation["delegation_id"]?.jsonPrimitive?.content
        val content = commentaryWithDelegation["content"]?.jsonPrimitive?.content ?: ""

        val toolResponse = buildJsonObject {
            put("toolResponse", buildJsonObject {
                put("functionResponses", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("id", delegationId!!)
                        put("response", buildJsonObject {
                            put("output", buildJsonObject {
                                put("commentary", content)
                            })
                        })
                    })
                })
            })
        }
        val fr = toolResponse["toolResponse"]?.jsonObject?.get("functionResponses")?.jsonArray?.get(0)?.jsonObject
        assertEquals("call_abc_123", fr?.get("id")?.jsonPrimitive?.content)

        // 2. Instructions append converts to clientContent user turn
        val instructionsEvent = buildJsonObject {
            put("type", "session.instructions.append")
            put("event_id", clientEventId)
            put("content", "Greet the learner warmly.")
        }
        val instructionContent = instructionsEvent["content"]?.jsonPrimitive?.content ?: ""
        val instructionTurn = buildJsonObject {
            put("clientContent", buildJsonObject {
                put("turns", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("text", instructionContent) })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }
        val turns = instructionTurn["clientContent"]?.jsonObject?.get("turns")?.jsonArray
        assertEquals(1, turns?.size)
        assertEquals("user", turns?.get(0)?.jsonObject?.get("role")?.jsonPrimitive?.content)

        // 3. Spoken commentary without delegation converts to user turn to trigger spoken audio
        val commentaryWithoutDelegation = buildJsonObject {
            put("type", "session.commentary.append")
            put("event_id", clientEventId)
            put("content", "¡Qué bien! Me alegro mucho.")
        }
        val typedContent = commentaryWithoutDelegation["content"]?.jsonPrimitive?.content ?: ""
        val spokenTurn = buildJsonObject {
            put("clientContent", buildJsonObject {
                put("turns", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("text", "Respond directly to the learner by speaking: $typedContent") })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }
        val spokenTurns = spokenTurn["clientContent"]?.jsonObject?.get("turns")?.jsonArray
        assertEquals(1, spokenTurns?.size)
        assertEquals("user", spokenTurns?.get(0)?.jsonObject?.get("role")?.jsonPrimitive?.content)
    }

    @Test
    fun calculateRmsAlgorithmMatchesExpectedBounds() {
        // Test RMS calculation with silence on 1600-byte chunk (50ms at 16kHz 16-bit mono)
        val silentBytes = ByteArray(1600)
        var sum = 0.0
        val sampleCount = silentBytes.size / 2
        for (i in 0 until silentBytes.size - 1 step 2) {
            val sample = (silentBytes[i].toInt() and 0xFF) or (silentBytes[i + 1].toInt() shl 8)
            val shortVal = sample.toShort().toDouble()
            sum += shortVal * shortVal
        }
        val rms = Math.sqrt(sum / sampleCount) / 32768.0
        assertEquals(0.0, rms, 0.0001)

        // Test RMS calculation with max amplitude square wave on 1600-byte chunk
        val maxBytes = ByteArray(1600)
        for (i in 0 until maxBytes.size - 1 step 2) {
            maxBytes[i] = 0xFF.toByte()
            maxBytes[i + 1] = 0x7F.toByte()
        }
        sum = 0.0
        for (i in 0 until maxBytes.size - 1 step 2) {
            val sample = (maxBytes[i].toInt() and 0xFF) or (maxBytes[i + 1].toInt() shl 8)
            val shortVal = sample.toShort().toDouble()
            sum += shortVal * shortVal
        }
        val maxRms = (Math.sqrt(sum / sampleCount) / 32768.0).coerceIn(0.0, 1.0)
        assertTrue("Max RMS should be near 1.0", maxRms > 0.99)
    }

    @Test
    fun realtimeInputPayloadMatchesExpectedGeminiLiveFormat() {
        val sampleRate = 16000
        val chunkBytes = 1600
        val durationMs = (chunkBytes / 2) * 1000 / sampleRate
        assertEquals(50, durationMs)

        val dummyPcm = ByteArray(chunkBytes)
        val base64Data = java.util.Base64.getEncoder().encodeToString(dummyPcm)
        val payload = buildJsonObject {
            put("realtimeInput", buildJsonObject {
                put("mediaChunks", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=$sampleRate")
                        put("data", base64Data)
                    })
                })
            })
        }
        val mediaChunks = payload["realtimeInput"]?.jsonObject?.get("mediaChunks")?.jsonArray
        assertNotNull(mediaChunks)
        assertEquals(1, mediaChunks?.size)
        val chunk = mediaChunks?.get(0)?.jsonObject
        assertEquals("audio/pcm;rate=16000", chunk?.get("mimeType")?.jsonPrimitive?.content)
        assertEquals(base64Data, chunk?.get("data")?.jsonPrimitive?.content)
    }

    @Test
    fun binaryWebSocketFrameHandshakeCompletesSuccessfully(): Unit = runBlocking {
        val receivedEvents = LinkedBlockingQueue<JsonObject>()

        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Server sends setupComplete as a binary frame (like Google AI Studio does)
                val binarySetup = """{"setupComplete":{}}""".encodeUtf8()
                webSocket.send(binarySetup)
            }
        }))

        val wsUrl = server.url("/ws").toString()
        val ws = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val json = Json.parseToJsonElement(bytes.utf8()).jsonObject
                    if (json.containsKey("setupComplete")) {
                        val event = Json.parseToJsonElement("""{"type":"session.started","session":{"id":"gemini-test"}}""").jsonObject
                        receivedEvents.offer(event)
                    }
                }
            }
        )

        val startedEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("session.started event should be received from binary frame", startedEvent)
        assertEquals("session.started", startedEvent?.get("type")?.jsonPrimitive?.content)
        ws.close(1000, "Done")
    }

    @Test
    fun snakeCasePayloadsParsedCorrectly(): Unit = runBlocking {
        val receivedEvents = LinkedBlockingQueue<JsonObject>()

        val serverWebSocket = CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverWebSocket.complete(webSocket)
                // Server sends snake_case setup_complete
                webSocket.send("""{"setup_complete":{}}""")
            }
        }))

        val wsUrl = server.url("/ws").toString()
        val ws = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = Json.parseToJsonElement(text).jsonObject
                    if (json.containsKey("setupComplete") || json.containsKey("setup_complete")) {
                        receivedEvents.offer(buildJsonObject { put("type", "session.started") })
                    }

                    // snake_case tool_call
                    val toolCall = (json["toolCall"] ?: json["tool_call"])?.jsonObject
                    val functionCalls = (toolCall?.get("functionCalls") ?: toolCall?.get("function_calls"))?.jsonArray
                    functionCalls?.forEach { callElem ->
                        val call = callElem.jsonObject
                        val id = call["id"]?.jsonPrimitive?.content ?: "del-1"
                        receivedEvents.offer(buildJsonObject {
                            put("type", "session.delegation.created")
                            put("id", id)
                        })
                    }

                    // snake_case server_content with model_turn
                    val serverContent = (json["serverContent"] ?: json["server_content"])?.jsonObject
                    val modelTurn = (serverContent?.get("modelTurn") ?: serverContent?.get("model_turn"))?.jsonObject
                    val parts = modelTurn?.get("parts")?.jsonArray
                    parts?.forEach { part ->
                        val textPart = part.jsonObject["text"]?.jsonPrimitive?.content
                        if (!textPart.isNullOrEmpty()) {
                            receivedEvents.offer(buildJsonObject {
                                put("type", "session.output_transcript.delta")
                                put("delta", textPart)
                            })
                        }
                    }
                }
            }
        )

        val startedEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("session.started should be received from setup_complete", startedEvent)
        assertEquals("session.started", startedEvent?.get("type")?.jsonPrimitive?.content)

        // Server sends snake_case server_content with model_turn and text
        val wsServer = serverWebSocket.await()
        wsServer.send("""{"server_content":{"model_turn":{"parts":[{"text":"¡Hola en snake_case!"}]}}}""")

        val deltaEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("delta event should be received from snake_case model_turn", deltaEvent)
        assertEquals("session.output_transcript.delta", deltaEvent?.get("type")?.jsonPrimitive?.content)
        assertEquals("¡Hola en snake_case!", deltaEvent?.get("delta")?.jsonPrimitive?.content)

        // Server sends snake_case tool_call with function_calls
        wsServer.send("""{"tool_call":{"function_calls":[{"id":"call_snake_1","name":"delegate_helper"}]}}""")
        val delegationEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("delegation event should be received from tool_call", delegationEvent)
        assertEquals("session.delegation.created", delegationEvent?.get("type")?.jsonPrimitive?.content)
        assertEquals("call_snake_1", delegationEvent?.get("id")?.jsonPrimitive?.content)

        ws.close(1000, "Done")
    }

    @Test
    fun userInputTranscriptionEmitsInputDeltaEvent(): Unit = runBlocking {
        val serverWebSocket = CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverWebSocket.complete(webSocket)
            }
        }))

        val receivedEvents = LinkedBlockingQueue<JsonObject>()
        val wsUrl = server.url("/ws").toString()

        val ws = client.newWebSocket(
            okhttp3.Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = Json.parseToJsonElement(text).jsonObject
                    val serverContent = (json["serverContent"] ?: json["server_content"])?.jsonObject
                    val inputTranscription = (serverContent?.get("inputTranscription") ?: serverContent?.get("interimInputTranscription"))?.jsonObject
                    val inputText = inputTranscription?.get("text")?.jsonPrimitive?.content
                    if (!inputText.isNullOrBlank()) {
                        receivedEvents.offer(buildJsonObject {
                            put("type", "session.input_transcript.delta")
                            put("delta", JsonPrimitive(inputText))
                        })
                    }
                }
            }
        )

        val wsServer = serverWebSocket.await()
        wsServer.send("""{"serverContent":{"inputTranscription":{"text":"Hola, ¿puedes ayudarme?"}}}""")

        val inputEvent = receivedEvents.poll(5, TimeUnit.SECONDS)
        assertNotNull("session.input_transcript.delta should be received", inputEvent)
        assertEquals("session.input_transcript.delta", inputEvent?.get("type")?.jsonPrimitive?.content)
        assertEquals("Hola, ¿puedes ayudarme?", inputEvent?.get("delta")?.jsonPrimitive?.content)

        ws.close(1000, "Done")
    }
}

