package chat.mural.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class APIClientTest {
    @Test fun providerErrorCategoriesAreBoundedAndNeverShowRawMessagesOrRetry() = runBlocking {
        for (body in listOf("""{"error":{"code":"insufficient_quota","message":"private billing data"}}""", "x".repeat(16_385), "not json")) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("x-request-id", "req_support").setBody(body))
            try { api.post("responses", buildJsonObject {}); fail("accepted error") }
            catch (error: APIClient.APIException.Http) {
                assertEquals("req_support", error.reference)
                assertFalse(error.message.orEmpty().contains("private"))
                assertEquals(if (body.startsWith('{')) "insufficient_quota" else null, error.code)
            }
        }
        assertEquals(3, server.requestCount)
    }
    private lateinit var server: MockWebServer
    private lateinit var api: APIClient
    private lateinit var geminiApi: APIClient
    @Before fun setup() {
        server = MockWebServer(); server.start()
        api = APIClient("sk-fake-test-only", OkHttpClient.Builder().followRedirects(false).build(), server.url("/v1/"), server.url("/gemini/"))
        geminiApi = APIClient("AIzaSy-fake-test-key", OkHttpClient.Builder().followRedirects(false).build(), server.url("/v1/"), server.url("/gemini/"))
    }
    @After fun teardown() { server.shutdown() }
    private fun response(text: String = "Hola") = """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"$text"}]}],"usage":{"input_tokens":12,"output_tokens":7}}"""
    @Test fun streamsUnicodeBeforeCompletionAndKeepsFinalUsage() = runBlocking {
        val body = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\r\n\r\n" +
            "data: {\"type\":\"response.output_text.delta\",\n" + "data: \"delta\":\" café\"}\n\n" +
            "data: {\"type\":\"response.completed\",\"response\":${response("你好 café")}}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body).setChunkedBody(body, 1))
        val seen = mutableListOf<String>()
        val result = api.streamMeaning("policy", "Hei") { seen += it }
        assertEquals(listOf("你好", "你好 café"), seen)
        assertEquals("你好 café", result.text); assertEquals(APIUsage(12, 7), result.usage)
        val sent = server.takeRequest()
        assertEquals("text/event-stream", sent.getHeader("Accept"))
        assertEquals(JsonPrimitive(true), Json.parseToJsonElement(sent.body.readUtf8()).jsonObject["stream"])
        assertEquals(1, server.requestCount)
    }
    @Test fun streamErrorsAndMissingCompletionNeverBecomeSuccessfulRepliesOrRetry() = runBlocking {
        val events = listOf("response.failed", "response.incomplete", "error", "response.refusal.delta")
        val bodies = events.map { "data: {\"type\":\"$it\"}\n\n" } + listOf(
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n", "data: [DONE]\n\n", "data: " + "x".repeat(65_537))
        for (body in bodies) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
            try { api.streamMeaning("policy", "Hei") {}; fail("Accepted incomplete stream") }
            catch (_: Exception) { }
        }
        assertEquals(bodies.size, server.requestCount)
    }
    @Test fun cancellationClosesAStalledStreamPromptly() = runBlocking {
        // Choose throttling after reading the request; enqueueing it would throttle the upload too.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\n\n").throttleBody(1, 1, TimeUnit.SECONDS)
        }
        val job = launch { api.streamMeaning("policy", "Hei") {} }
        withTimeout(5000) { while (server.requestCount == 0) delay(10) }
        withTimeout(1500) { job.cancelAndJoin() }
        assertTrue(job.isCancelled); assertEquals(1, server.requestCount)
    }
    @Test fun usesExpectedEndpointModelConsentIndependentStoreFalseAndParsesUsage() = runBlocking {
        server.enqueue(MockResponse().setBody(response()))
        val result = api.respond("policy", "hello")
        val request = server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("/v1/responses",request.path)
        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("gpt-5.6-luna",body["model"]!!.jsonPrimitive.content)
        assertFalse(body["store"]!!.jsonPrimitive.boolean)
        assertEquals("Hola",result.text); assertEquals(APIUsage(12,7,0),result.usage)
    }
    @Test fun rejectsRedirectWithoutFollowingOrLeakingCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/other")))
        try { api.post("responses",buildJsonObject{}); fail("accepted redirect") } catch (e: APIClient.APIException.Http) { assertEquals(302,e.status) }
        assertEquals(1,server.requestCount)
    }
    @Test fun missingKeyAndUnsafePathsNeverSendRequests() = runBlocking {
        val missing=APIClient(null,OkHttpClient(),server.url("/v1/"))
        try { missing.post("responses",buildJsonObject{}); fail("missing key accepted") } catch (_: APIClient.APIException.MissingKey) { }
        for(path in listOf("https://example.com","../other","/responses")) {
            try { api.post(path,buildJsonObject{}); fail("unsafe path accepted") } catch (_: APIClient.APIException.InvalidResponse) { }
        }
        assertEquals(0,server.requestCount)
    }
    @Test fun incompleteRefusalAndMalformedResultsDoNotBecomeReplies() = runBlocking {
        for(body in listOf("{}","not json","""{"status":"incomplete"}""","""{"status":"completed","output":[{"content":[{"type":"refusal","refusal":"no"}]}]}""")) {
            server.enqueue(MockResponse().setBody(body))
            try { api.respond("p","q"); fail("bad result accepted") } catch (_: APIClient.APIException) { }
        }
    }
    @Test fun retainsOnlySafeDeduplicatedCitationsAndCountsSearches() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"completed","output":[{"type":"web_search_call"},{"content":[{"type":"output_text","text":"news","annotations":[{"type":"url_citation","url":"http://bad.example"},{"type":"url_citation","url":"https://user@example.com"},{"type":"url_citation","url":"https://example.com","title":"Good"},{"type":"url_citation","url":"https://example.com","title":"Duplicate"}]}]}]}"""))
        val result=api.respond("p","q",search=true)
        assertEquals(1,result.sources.size); assertEquals("Good",result.sources.single().title); assertEquals(1,result.usage.searches)
        val body=Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(1,body["max_tool_calls"]!!.jsonPrimitive.int)
    }
    @Test fun cancellationStopsAResponseThatStallsMidBody() = runBlocking {
        server.enqueue(MockResponse().setBody(response()).throttleBody(1, 1, TimeUnit.SECONDS))
        val job = launch { api.respond("p", "q") }
        delay(200)
        withTimeout(1500) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }
    @Test fun largeResponsesAreBounded() = runBlocking {
        server.enqueue(MockResponse().setBody(" ".repeat(1_048_577)))
        try { api.post("responses",buildJsonObject{}); fail("large response accepted") } catch (_: APIClient.APIException.InvalidResponse) { }
    }
    @Test fun providerInterfaceKeepsByokVoiceModelAndTransportPayload() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"session":{"id":"provider-live"},"transport":{"type":"webrtc","sdp":"v=0\\r\\n"}}"""))
        val provider: LiveSessionProvider = api
        val result = provider.createLiveSession(LiveSessionRequest("v=0", "Teaching policy", language = "es-ES"))
        val request = server.takeRequest(); val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("/v1/live/sessions", request.path)
        assertEquals("Bearer sk-fake-test-only", request.getHeader("Authorization"))
        assertEquals("gpt-live-1", body["session"]!!.jsonObject["model"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(false), body["session"]!!.jsonObject["store"])
        assertEquals(JsonPrimitive("Teaching policy"), body["session"]!!.jsonObject["instructions"])
        assertEquals("provider-live", result.providerSessionID); assertNull(result.lease)
        assertNull(body["language"])
    }

    @Test fun geminiRespondsWithGroundingSourcesAndUsage() = runBlocking {
        val geminiBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "Noticias sobre tecnología" }],
                "role": "model"
              },
              "finishReason": "STOP",
              "groundingMetadata": {
                "webSearchQueries": ["noticias tech"],
                "groundingChunks": [
                  { "web": { "uri": "https://example.com/tech", "title": "Tech News" } },
                  { "web": { "uri": "http://insecure.example.com", "title": "Insecure" } }
                ]
              }
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 25,
            "candidatesTokenCount": 18
          }
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(geminiBody))
        val result = geminiApi.respond("instrucciones", "hola", search = true)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!

        assertTrue(recorded.path?.startsWith("/gemini/models/${APIClient.GEMINI_MODEL}:generateContent?key=AIzaSy-fake-test-key") == true)
        val sentJson = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertTrue(sentJson.containsKey("systemInstruction"))
        assertTrue(sentJson.containsKey("contents"))
        assertTrue(sentJson.containsKey("tools"))

        assertEquals("Noticias sobre tecnología", result.text)
        assertEquals(1, result.sources.size)
        assertEquals("Tech News", result.sources.first().title)
        assertEquals("https://example.com/tech", result.sources.first().url)
        assertEquals(APIUsage(input = 25, output = 18, searches = 1), result.usage)
    }

    @Test fun geminiStructuredAssessmentUsesResponseSchema() = runBlocking {
        val geminiBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "{\"outcome\":\"success\"}" }],
                "role": "model"
              },
              "finishReason": "STOP"
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 40,
            "candidatesTokenCount": 10
          }
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(geminiBody))
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("outcome", buildJsonObject { put("type", "string") })
                put("level", buildJsonObject { put("type", "integer"); put("minimum", 0); put("maximum", 5) })
                put("words", buildJsonObject { put("type", "array"); put("maxItems", 12) })
            })
            put("required", buildJsonArray { add(JsonPrimitive("outcome")) })
            put("additionalProperties", false)
        }
        val result = geminiApi.respond("assess", "student response", schema = schema)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("AIzaSy-fake-test-key", recorded.getHeader("x-goog-api-key"))
        val sentJson = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val genConfig = sentJson["generationConfig"]!!.jsonObject
        assertEquals("application/json", genConfig["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals("low", genConfig["thinkingConfig"]?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)

        val respSchema = genConfig["responseSchema"]!!.jsonObject
        assertEquals("OBJECT", respSchema["type"]!!.jsonPrimitive.content)
        assertFalse(respSchema.containsKey("additionalProperties"))
        val levelProp = respSchema["properties"]!!.jsonObject["level"]!!.jsonObject
        assertFalse(levelProp.containsKey("minimum"))
        assertFalse(levelProp.containsKey("maximum"))
        val wordsProp = respSchema["properties"]!!.jsonObject["words"]!!.jsonObject
        assertFalse(wordsProp.containsKey("maxItems"))
        assertEquals("{\"outcome\":\"success\"}", result.text)
    }

    @Test fun geminiStreamCompletesGracefullyWithoutExplicitFinishReason() = runBlocking {
        val chunk1 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Bonjour\"}]}}]}\n\n"
        val chunk2 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" le monde\"}]}}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5}}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(chunk1 + chunk2))

        val seen = mutableListOf<String>()
        val result = geminiApi.streamMeaning("translate", "hello world") { seen += it }
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("AIzaSy-fake-test-key", recorded.getHeader("x-goog-api-key"))
        assertEquals("Bonjour le monde", result.text)
        assertEquals(listOf("Bonjour", "Bonjour le monde"), seen)
        assertEquals(APIUsage(10, 5, 0), result.usage)
    }

    @Test fun geminiStreamsMeaningChunksAndParsesFinalUsage() = runBlocking {
        val chunk1 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Bonjour\"}]}}]}\n\n"
        val chunk2 = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" tout le monde\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":15,\"candidatesTokenCount\":8}}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(chunk1 + chunk2))

        val seen = mutableListOf<String>()
        val result = geminiApi.streamMeaning("translate", "hello everyone") { seen += it }

        assertEquals(listOf("Bonjour", "Bonjour tout le monde"), seen)
        assertEquals("Bonjour tout le monde", result.text)
        assertEquals(APIUsage(input = 15, output = 8, searches = 0), result.usage)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(recorded.path?.contains(":streamGenerateContent") == true)
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
    }

    @Test fun geminiRefusalThrowsException() = runBlocking {
        val geminiBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "" }],
                "role": "model"
              },
              "finishReason": "SAFETY"
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(geminiBody))
        try {
            geminiApi.respond("policy", "bad query")
            fail("Expected refusal exception")
        } catch (_: APIClient.APIException.Refused) { }
    }

    @Test fun geminiKeyWithAQPrefixRoutesToGemini() = runBlocking {
        assertEquals(CredentialStore.ProviderType.GEMINI, CredentialStore.providerFor("AQ.fake-unit-test-key-0000000000"))

        val aqClient = APIClient(
            "AQ.fake-unit-test-key-0000000000",
            OkHttpClient.Builder().followRedirects(false).build(),
            server.url("/v1/"),
            server.url("/gemini/"),
        )
        val geminiBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "Respuesta correcta con clave AQ." }],
                "role": "model"
              }
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(geminiBody))
        val result = aqClient.respond("instrucciones", "hola")
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!

        assertTrue(recorded.path?.startsWith("/gemini/models/${APIClient.GEMINI_MODEL}:generateContent?key=AQ.fake-unit-test-key-0000000000") == true)
        assertEquals("Respuesta correcta con clave AQ.", result.text)
    }

    @Test fun geminiTypedReplyConfiguresThinkingLevelAndFastTokens() = runBlocking {
        val geminiBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "¡Me alegro de que estés bien!" }],
                "role": "model"
              }
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(geminiBody))
        val result = geminiApi.respond("instrucciones", "bien", purpose = HelperPurpose.TYPED_REPLY)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!

        val sentJson = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val genConfig = sentJson["generationConfig"]?.jsonObject
        assertNotNull(genConfig)
        assertEquals(1000, genConfig?.get("maxOutputTokens")?.jsonPrimitive?.content?.toInt())
        assertEquals("low", genConfig?.get("thinkingConfig")?.jsonObject?.get("thinkingLevel")?.jsonPrimitive?.content)
        assertEquals("¡Me alegro de que estés bien!", result.text)
    }

    @Test fun geminiRateLimit429FailsOverToFallbackCandidate() = runBlocking {
        // Enqueue 429 for gemini-3.5-flash-lite (attempt 0 and attempt 1)
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"))
        // Enqueue 200 OK for fallback model (gemini-3.5-flash)
        val successBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "Respuesta desde modelo de respaldo." }],
                "role": "model"
              }
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = geminiApi.respond("instrucciones", "hola")
        assertEquals("Respuesta desde modelo de respaldo.", result.text)

        val req1 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req1.path?.contains("gemini-3.5-flash-lite:generateContent") == true)
        val req2 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req2.path?.contains("gemini-3.5-flash-lite:generateContent") == true)
        val req3 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req3.path?.contains("gemini-3.5-flash:generateContent") == true)
    }

    @Test fun geminiNotFound404FailsOverImmediatelyToFallbackCandidate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}"))
        val successBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "Respuesta inmediata tras 404." }],
                "role": "model"
              }
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = geminiApi.respond("instrucciones", "hola")
        assertEquals("Respuesta inmediata tras 404.", result.text)

        val req1 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req1.path?.contains("gemini-3.5-flash-lite:generateContent") == true)
        val req2 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req2.path?.contains("gemini-3.5-flash:generateContent") == true)
    }

    @Test fun geminiStreamMeaningFailsOverOn429() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"))
        val chunk = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Bonjour monde\"}]}}],\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":4}}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(chunk))

        val seen = mutableListOf<String>()
        val result = geminiApi.streamMeaning("translate", "hello world") { seen += it }
        assertEquals("Bonjour monde", result.text)

        val req1 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req1.path?.contains("gemini-3.5-flash-lite:streamGenerateContent") == true)
        val req2 = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(req2.path?.contains("gemini-3.5-flash:streamGenerateContent") == true)
    }

    @Test fun geminiTestPurposeAllocates8192Tokens() = runBlocking {
        val successBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [{ "text": "{\"items\":[]}" }],
                "role": "model"
              }
            }
          ]
        }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        geminiApi.respond("instrucciones", "hola", purpose = HelperPurpose.TEST)
        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        val bodyJson = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        val genConfig = bodyJson["generationConfig"]?.jsonObject
        val maxTokens = genConfig?.get("maxOutputTokens")?.jsonPrimitive?.int
        assertEquals(8192, maxTokens)
    }
}

