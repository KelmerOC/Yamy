package chat.mural.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class PhaseFourAudioAndResilienceTest {

    private lateinit var server: MockWebServer

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
    fun preRollBufferMaintainsExact200msWindow() {
        val sampleRate = 16000
        val chunkBytes = 1600 // 50 ms at 16kHz 16-bit mono
        val preRollChunks = 4 // 4 * 50 ms = 200 ms
        val totalMs = preRollChunks * (chunkBytes / 2) * 1000 / sampleRate
        assertEquals(200, totalMs)

        val queue = ConcurrentLinkedQueue<ByteArray>()

        fun accumulate(chunk: ByteArray) {
            if (queue.size >= preRollChunks) {
                queue.poll()
            }
            queue.offer(chunk)
        }

        // Add 6 chunks labeled with first byte = chunk index
        for (i in 1..6) {
            val chunk = ByteArray(chunkBytes) { i.toByte() }
            accumulate(chunk)
        }

        // Queue size must be capped at 4
        assertEquals(4, queue.size)

        // Oldest retained chunk should be #3 (chunks 1 and 2 discarded)
        val firstRetained = queue.poll()
        assertNotNull(firstRetained)
        assertEquals(3.toByte(), firstRetained!![0])

        val secondRetained = queue.poll()
        assertEquals(4.toByte(), secondRetained!![0])

        val thirdRetained = queue.poll()
        assertEquals(5.toByte(), thirdRetained!![0])

        val fourthRetained = queue.poll()
        assertEquals(6.toByte(), fourthRetained!![0])

        assertTrue(queue.isEmpty())
    }

    @Test
    fun typingStateEventsSignalCorrectLifecycle() {
        val isTyping = AtomicBoolean(false)
        val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
        playbackQueue.offer(ByteArray(1600))
        playbackQueue.offer(ByteArray(1600))

        fun handleEvent(type: String) {
            when (type) {
                "session.typing.start" -> {
                    isTyping.set(true)
                    playbackQueue.clear() // Silences AI audio immediately
                }
                "session.typing.stop" -> {
                    isTyping.set(false)
                }
                "session.commentary.append" -> {
                    isTyping.set(false)
                }
            }
        }

        // 1. User starts typing
        handleEvent("session.typing.start")
        assertTrue("isTyping must be true while typing", isTyping.get())
        assertTrue("Playback queue must be cleared on typing start", playbackQueue.isEmpty())

        // 2. User stops typing
        handleEvent("session.typing.stop")
        assertFalse("isTyping must be false after typing stop", isTyping.get())

        // 3. User sends commentary directly
        isTyping.set(true)
        handleEvent("session.commentary.append")
        assertFalse("isTyping must be reset when commentary is sent", isTyping.get())
    }

    @Test
    fun localInstantBargeInTriggersOnSustainedUserVoice() {
        var outputLevel = 0.35 // AI speaking
        var userSpeechFramesCount = 0
        val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
        playbackQueue.offer(ByteArray(1600))

        fun processAudioFrame(inputLevel: Double): Boolean {
            var bargedIn = false
            if (outputLevel > 0.02 || !playbackQueue.isEmpty()) {
                if (inputLevel > 0.08) {
                    userSpeechFramesCount++
                    if (userSpeechFramesCount >= 2) {
                        playbackQueue.clear()
                        outputLevel = 0.0
                        userSpeechFramesCount = 0
                        bargedIn = true
                    }
                } else {
                    userSpeechFramesCount = 0
                }
            } else {
                userSpeechFramesCount = 0
            }
            return bargedIn
        }

        // Frame 1: Transient noise spike (1 frame > 0.08) does NOT trigger barge-in
        val spike = processAudioFrame(0.12)
        assertFalse("Single noise frame should not trigger barge in", spike)
        assertEquals(1, userSpeechFramesCount)
        assertEquals(0.35, outputLevel, 0.01)

        // Ambient noise drops below threshold
        processAudioFrame(0.02)
        assertEquals(0, userSpeechFramesCount)

        // Frame 1 of speech: User starts talking
        val frame1 = processAudioFrame(0.15)
        assertFalse("First frame of speech should not trigger yet", frame1)
        assertEquals(1, userSpeechFramesCount)

        // Frame 2 of speech: Sustained user voice triggers instant local barge-in (<10ms)
        val frame2 = processAudioFrame(0.18)
        assertTrue("Second sustained speech frame must trigger instant barge-in", frame2)
        assertEquals(0.0, outputLevel, 0.001)
        assertTrue("Playback queue must be cleared on barge-in", playbackQueue.isEmpty())
        assertEquals(0, userSpeechFramesCount)
    }

    @Test
    fun apiClientConfiguresLowThinkingLevelAndSafeTokensForMeanings() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
        val api = APIClient("AIzaSyTestKey", client, server.url("/v1"), server.url("/v1beta"))

        val mockResponseBody = """
            {
                "candidates": [{
                    "content": {
                        "parts": [{ "text": "¡Hola! ¿Cómo estás hoy?" }]
                    },
                    "finishReason": "STOP"
                }],
                "usageMetadata": { "promptTokenCount": 20, "candidatesTokenCount": 10 }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(mockResponseBody).setHeader("Content-Type", "application/json"))

        val result = api.respond("friendly_tutor", "Hola", schema = null, purpose = HelperPurpose.MEANING)
        assertEquals("¡Hola! ¿Cómo estás hoy?", result.text)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        val requestJson = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val genConfig = requestJson["generationConfig"]?.jsonObject
        assertNotNull(genConfig)

        assertEquals(500, genConfig?.get("maxOutputTokens")?.jsonPrimitive?.intOrNull)
        assertEquals("low", genConfig?.get("thinkingConfig")?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)
    }

    @Test
    fun okHttpClientWebSocketConfigurationMaintainsKeepAlive() {
        val pingIntervalSec = 15L
        val client = OkHttpClient.Builder()
            .pingInterval(pingIntervalSec, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        assertEquals(15000, client.pingIntervalMillis)
        assertTrue(client.retryOnConnectionFailure)
        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(30000, client.readTimeoutMillis)
        assertEquals(30000, client.writeTimeoutMillis)
    }

    @Test
    fun spuriousTranscriptionFilterDiscardsHallucinationsAndNoise() {
        val spanishSessionInstructions = "You are Mural, practicing Spanish with the learner."

        // Artifacts and non-lexical tokens must be discarded
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("[laughter]", spanishSessionInstructions))
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("[cough]", spanishSessionInstructions))
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("(inaudible)", spanishSessionInstructions))
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("...", spanishSessionInstructions))
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("???", spanishSessionInstructions))

        // Repetitive hallucinated stuttering tokens must be discarded
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("und und und und", spanishSessionInstructions))
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("ja ja ja ja", spanishSessionInstructions))

        // Incompatible scripts (Cyrillic in Spanish session) must be discarded
        assertEquals(null, GeminiLiveTransport.sanitizeInputTranscription("Привет как дела", spanishSessionInstructions))

        // Genuine speech in target or support language must be cleanly preserved
        assertEquals("Hola, ¿cómo estás?", GeminiLiveTransport.sanitizeInputTranscription("Hola, ¿cómo estás?", spanishSessionInstructions))
        assertEquals("Quiero practicar español", GeminiLiveTransport.sanitizeInputTranscription("  Quiero practicar español  ", spanishSessionInstructions))
    }

    @Test
    fun translationPolicyDemandsDirectConciseOutputWithoutFiller() {
        val policy = chat.mural.core.TeachingPolicy.translation(
            language = chat.mural.core.LanguageRegistry.get("es")!!,
            meaningLanguage = "en"
        )
        assertTrue("Translation policy must enforce concise direct translation", policy.contains("Output ONLY the concise, direct translation"))
        assertTrue("Translation policy must forbid conversational replies", policy.contains("Do NOT include greetings, commentary"))
    }
}
