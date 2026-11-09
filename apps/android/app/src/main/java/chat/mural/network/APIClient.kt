package chat.mural.network

import chat.mural.core.SourceLink
import chat.mural.core.ProviderFailureKind
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

data class APIUsage(val input: Int = 0, val output: Int = 0, val searches: Int = 0)
data class APIResult(val text: String, val sources: List<SourceLink>, val usage: APIUsage)

class APIClient private constructor(
    private val readCredential: () -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = API_BASE_URL,
    private val geminiBaseUrl: HttpUrl = GEMINI_BASE_URL,
) : TeachingClient, LiveSessionProvider {
    constructor(credentials: CredentialStore) : this(credentials::read)

    internal constructor(key: String?, client: OkHttpClient, baseUrl: HttpUrl, geminiBaseUrl: HttpUrl = GEMINI_BASE_URL) :
        this({ key }, client, baseUrl, geminiBaseUrl)

    override val providerType: CredentialStore.ProviderType?
        get() = CredentialStore.providerFor(readCredential())

    override val apiKey: String?
        get() = readCredential()

    override suspend fun createLiveSession(request: LiveSessionRequest): LiveSessionConnection {
        val result = post("live/sessions", buildJsonObject {
            put("session", buildJsonObject {
                put("model", "gpt-live-1"); put("instructions", request.instructions); put("input", request.history)
                put("store", false)
                put("delegation", buildJsonObject { put("type", "client") })
                put("audio", buildJsonObject { put("output", buildJsonObject { put("voice", "marin") }) })
            })
            put("transport", buildJsonObject { put("type", "webrtc"); put("sdp", request.sdp) })
        })
        val transport = result["transport"] as? JsonObject ?: throw APIException.InvalidResponse
        val answer = (transport["sdp"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (transport["type"] != JsonPrimitive("webrtc") || answer.isNullOrBlank()) throw APIException.InvalidResponse
        val id = ((result["session"] as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return LiveSessionConnection(answer, id)
    }

    suspend fun post(path: String, body: JsonObject): JsonObject {
        if (!VALID_PATH.matches(path) || path.contains("..") || path.startsWith('/')) {
            throw APIException.InvalidResponse
        }
        val key = readCredential() ?: throw APIException.MissingKey
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer $key")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeRequest(request)
    }

    private val testOkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(100, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun executeRequest(request: Request, purpose: HelperPurpose? = null): JsonObject {
        val targetClient = if (purpose == HelperPurpose.TEST) testOkHttpClient else client
        return suspendCancellableCoroutine { continuation ->
            val call = targetClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            if (it.code !in 200..299) {
                                val errorCode = runCatching {
                                    val payload = it.peekBody(16_385).string()
                                    if (payload.toByteArray(Charsets.UTF_8).size > 16_384) null else {
                                        val errObj = (JSON.parseToJsonElement(payload).jsonObject["error"] as? JsonObject)
                                        errObj?.get("code")?.jsonPrimitive?.contentOrNull
                                            ?: errObj?.get("status")?.jsonPrimitive?.contentOrNull
                                    }
                                }.getOrNull()
                                throw APIException.Http(it.code, errorCode, it.header("x-request-id"))
                            }
                            val payload = it.readBoundedBody()
                            try { JSON.parseToJsonElement(payload).jsonObject }
                            catch (_: Exception) { throw APIException.InvalidResponse }
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    override suspend fun respond(
        instructions: String,
        input: String,
        schema: JsonObject?,
        search: Boolean,
        purpose: HelperPurpose?,
    ): APIResult {
        val key = readCredential() ?: throw APIException.MissingKey
        val isGemini = key.startsWith("AIza") || key.startsWith("AQ") || !key.startsWith("sk-")
        return if (isGemini) {
            respondGemini(key, instructions, input, schema, search, purpose)
        } else {
            val body = responseBody(instructions, input, schema, search)
            val response = post("responses", body)
            decodeTeachingResponse(response)
        }
    }

    private suspend fun respondGemini(
        key: String,
        instructions: String,
        input: String,
        schema: JsonObject?,
        search: Boolean,
        purpose: HelperPurpose?,
    ): APIResult {
        val body = geminiRequestBody(instructions, input, schema, search, purpose)
        val candidateModels = listOf(GEMINI_MODEL, GEMINI_FLASH_MODEL, GEMINI_38_MODEL).distinct()
        var lastException: Exception? = null

        for (model in candidateModels) {
            val url = geminiBaseUrl.newBuilder()
                .addPathSegment("models")
                .addPathSegment("$model:generateContent")
                .addQueryParameter("key", key)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .header("x-goog-api-key", key)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            for (attempt in 0..1) {
                try {
                    val response = executeRequest(request, purpose)
                    return decodeGeminiTeachingResponse(response)
                } catch (e: APIException.Http) {
                    lastException = e
                    if (e.status == 404) {
                        break
                    }
                    if (e.status == 429 || e.status == 503) {
                        if (attempt == 0) {
                            delay(350)
                            continue
                        } else {
                            break
                        }
                    }
                    throw e
                }
            }
        }
        throw lastException ?: APIException.InvalidResponse
    }

    private fun responseBody(instructions: String, input: String, schema: JsonObject?, search: Boolean): JsonObject {
        return buildJsonObject {
            put("model", "gpt-5.6-luna")
            put("store", false)
            put("instructions", instructions)
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", input)
                })
            })
            put("max_output_tokens", if (schema == null) 1_400 else 2_200)
            put("reasoning", buildJsonObject { put("effort", "low") })
            if (schema != null) {
                put("text", buildJsonObject {
                    put("format", buildJsonObject {
                        put("type", "json_schema")
                        put("name", "mural_result")
                        put("strict", true)
                        put("schema", schema)
                    })
                })
            }
            if (search) {
                put("tools", buildJsonArray { add(buildJsonObject { put("type", "web_search") }) })
                put("tool_choice", "auto")
                put("max_tool_calls", 1)
            }
        }
    }

    private fun geminiRequestBody(
        instructions: String,
        input: String,
        schema: JsonObject?,
        search: Boolean,
        purpose: HelperPurpose? = null,
    ): JsonObject {
        val maxTokens = when {
            purpose == HelperPurpose.TEST -> 8_192
            schema != null -> 2_200
            purpose == HelperPurpose.TOPIC -> 2_000
            purpose == HelperPurpose.TYPED_REPLY -> 1_000
            purpose == HelperPurpose.MEANING -> 500
            else -> 1_400
        }
        val safeInput = if (input.isBlank()) " " else input
        return buildJsonObject {
            if (instructions.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", instructions) })
                    })
                })
            }
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", safeInput) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", maxTokens)
                put("thinkingConfig", buildJsonObject {
                    put("thinkingLevel", "low")
                })
                if (schema != null || purpose == HelperPurpose.TOPIC || purpose == HelperPurpose.TEST) {
                    put("responseMimeType", "application/json")
                    if (schema != null) {
                        put("responseSchema", toGeminiSchema(schema))
                    }
                }
            })
            if (search) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("googleSearch", buildJsonObject {})
                    })
                })
            }
        }
    }

    private fun toGeminiSchema(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        return buildJsonObject {
            for ((key, value) in element) {
                when (key) {
                    "type" -> {
                        val typeStr = (value as? JsonPrimitive)?.contentOrNull
                        put("type", (typeStr?.uppercase() ?: "OBJECT"))
                    }
                    "properties" -> {
                        if (value is JsonObject) {
                            put("properties", buildJsonObject {
                                for ((propName, propSchema) in value) {
                                    put(propName, toGeminiSchema(propSchema))
                                }
                            })
                        }
                    }
                    "items" -> put("items", toGeminiSchema(value))
                    "additionalProperties", "minimum", "maximum", "maxItems", "minItems" -> { /* omitted in Gemini OpenAPI schema */ }
                    else -> put(key, value)
                }
            }
        }
    }

    override suspend fun streamMeaning(instructions: String, input: String, onText: (String) -> Unit): APIResult {
        if (input.isBlank()) {
            return APIResult("", emptyList(), APIUsage(0, 0, 0))
        }
        val key = readCredential() ?: throw APIException.MissingKey
        val isGemini = key.startsWith("AIza") || key.startsWith("AQ") || !key.startsWith("sk-")
        return if (isGemini) {
            try {
                streamMeaningGemini(key, instructions, input, onText)
            } catch (e: Exception) {
                if (e is APIException.Refused || e is APIException.MissingKey) throw e
                android.util.Log.w("APIClient", "streamMeaningGemini failed, falling back to respondGemini", e)
                val fallback = respondGemini(key, instructions, input, null, false, HelperPurpose.MEANING)
                onText(fallback.text)
                fallback
            }
        } else {
            val body = buildJsonObject {
                responseBody(instructions, input, null, false).forEach { (key, value) -> put(key, value) }
                put("stream", true)
            }
            val request = Request.Builder().url(baseUrl.newBuilder().addPathSegments("responses").build())
                .header("Authorization", "Bearer $key").header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            val callbacks = currentCoroutineContext().minusKey(Job)
            val result = streamingResponse(client, request) { response ->
                if (!response.isSuccessful) {
                    val code = runCatching { JSON.parseToJsonElement(response.peekBody(16_384).string()).jsonObject["error"]
                        ?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull }.getOrNull()
                    throw APIException.Http(response.code, code, response.header("x-request-id"))
                }
                if (response.header("Content-Type")?.startsWith("text/event-stream", ignoreCase = true) != true) throw APIException.InvalidResponse
                var text = ""
                readTextEvents(response) { event ->
                    when (event["type"]?.jsonPrimitive?.contentOrNull) {
                        "response.output_text.delta" -> {
                            text += event["delta"]?.jsonPrimitive?.contentOrNull ?: throw APIException.InvalidResponse
                            if (text.toByteArray(Charsets.UTF_8).size > 65_536) throw APIException.InvalidResponse
                            withContext(callbacks) { onText(text) }; null
                        }
                        "response.completed" -> event["response"] as? JsonObject ?: throw APIException.Incomplete
                        "response.refusal.delta", "response.refusal.done" -> throw APIException.Refused
                        "error", "response.failed", "response.incomplete" -> throw APIException.Incomplete
                        else -> null
                    }
                }
            }
            decodeTeachingResponse(result)
        }
    }

    private suspend fun streamMeaningGemini(
        key: String,
        instructions: String,
        input: String,
        onText: (String) -> Unit,
    ): APIResult {
        if (input.isBlank()) {
            return APIResult("", emptyList(), APIUsage(0, 0, 0))
        }
        val body = geminiRequestBody(instructions, input, null, false, HelperPurpose.MEANING)
        val candidateModels = listOf(GEMINI_MODEL, GEMINI_FLASH_MODEL, GEMINI_38_MODEL).distinct()
        var lastException: Exception? = null

        val streamingClient = client.newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        for (model in candidateModels) {
            val url = geminiBaseUrl.newBuilder()
                .addPathSegment("models")
                .addPathSegment("$model:streamGenerateContent")
                .addQueryParameter("alt", "sse")
                .addQueryParameter("key", key)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .header("Accept", "text/event-stream")
                .header("x-goog-api-key", key)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val callbacks = currentCoroutineContext().minusKey(Job)
            var accumulatedText = ""
            var lastCandidate: JsonObject? = null
            var lastUsage: JsonObject? = null

            try {
                streamingResponse(streamingClient, request) { response ->
                    if (!response.isSuccessful) {
                        val code = runCatching {
                            val payload = response.peekBody(16_384).string()
                            val err = JSON.parseToJsonElement(payload).jsonObject["error"] as? JsonObject
                            err?.get("code")?.jsonPrimitive?.contentOrNull ?: err?.get("status")?.jsonPrimitive?.contentOrNull
                        }.getOrNull()
                        throw APIException.Http(response.code, code, response.header("x-request-id"))
                    }
                    if (response.header("Content-Type")?.startsWith("text/event-stream", ignoreCase = true) != true) {
                        throw APIException.InvalidResponse
                    }

                    readTextEvents(response, allowNaturalEnd = true) { event ->
                        val candidate = (event["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                        if (candidate != null) {
                            lastCandidate = candidate
                            val finishReason = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull
                            if (finishReason in listOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT")) {
                                throw APIException.Refused
                            }
                            val parts = (candidate["content"] as? JsonObject)?.get("parts") as? JsonArray
                            if (parts != null) {
                                for (part in parts) {
                                    val partObj = part as? JsonObject ?: continue
                                    val isThought = (partObj["thought"] as? JsonPrimitive)?.booleanOrNull == true
                                    if (isThought) continue
                                    val chunk = partObj["text"]?.jsonPrimitive?.contentOrNull
                                    if (!chunk.isNullOrEmpty()) {
                                        accumulatedText += chunk
                                        if (accumulatedText.toByteArray(Charsets.UTF_8).size > 65_536) {
                                            throw APIException.InvalidResponse
                                        }
                                        withContext(callbacks) { onText(accumulatedText) }
                                    }
                                }
                            }
                        }
                        val usage = event["usageMetadata"] as? JsonObject
                        if (usage != null) {
                            lastUsage = usage
                        }
                        val finish = (lastCandidate?.get("finishReason") as? JsonPrimitive)?.contentOrNull
                        if (finish != null && finish.isNotEmpty() && finish != "null") {
                            event
                        } else null
                    }
                }

                if (accumulatedText.isEmpty() && lastCandidate != null) {
                    val fallbackParts = (lastCandidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray
                    if (fallbackParts != null) {
                        for (part in fallbackParts) {
                            val chunk = (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                            if (!chunk.isNullOrEmpty()) {
                                accumulatedText += chunk
                                withContext(callbacks) { onText(accumulatedText) }
                            }
                        }
                    }
                }

                if (accumulatedText.isEmpty()) throw APIException.Incomplete

                val inputTokens = ((lastUsage?.get("promptTokenCount") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0).coerceIn(0, 1_000_000_000)
                val outputTokens = ((lastUsage?.get("candidatesTokenCount") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0).coerceIn(0, 1_000_000_000)
                return APIResult(accumulatedText, emptyList(), APIUsage(inputTokens, outputTokens, 0))
            } catch (e: APIException.Http) {
                lastException = e
                if (e.status == 404 || e.status == 429 || e.status == 503) {
                    delay(350)
                    continue
                }
                throw e
            }
        }
        throw lastException ?: APIException.InvalidResponse
    }

    private fun Response.readBoundedBody(): String {
        val responseBody = body ?: throw APIException.InvalidResponse
        if (responseBody.contentLength() > MAX_RESPONSE_BYTES) throw APIException.InvalidResponse
        val source = responseBody.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val count = source.read(buffer, minOf(8_192L, MAX_RESPONSE_BYTES + 1L - total))
            if (count == -1L) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw APIException.InvalidResponse
        }
        return buffer.readString(Charsets.UTF_8)
    }

    sealed class APIException(message: String, cause: Throwable? = null) : IOException(message, cause) {
        data object MissingKey : APIException("Add your Google Gemini API key in Settings to begin.")
        data object InvalidResponse : APIException("The AI provider returned an incomplete response. Please try again.")
        data object Incomplete : APIException("The AI provider returned an incomplete response. Please try again.")
        data object Refused : APIException("Yamy couldn't complete that request. Try a different topic.")
        class Http(val status: Int, code: String? = null, reference: String? = null) : APIException(messageFor(status)) {
            val code = ProviderFailureKind.safeCode(code)
            val reference = ProviderFailureKind.safeReference(reference)
            val kind get() = ProviderFailureKind.classify(status, code)
        }

        companion object {
            private fun messageFor(status: Int): String = when (status) {
                401 -> "Your API key wasn't accepted. Check it in Settings."
                403, 404 -> "This API key may not have access to the requested model. Check your Google Cloud / AI Studio project."
                429 -> "The AI provider's usage or rate limit was reached. Check your project limits."
                else -> "The AI provider couldn't complete the request (HTTP $status). Please try again."
            }
        }
    }

    companion object {
        private val API_BASE_URL = HttpUrl.Builder()
            .scheme("https")
            .host("api.openai.com")
            .addPathSegment("v1")
            .addPathSegment("")
            .build()
        private val GEMINI_BASE_URL = HttpUrl.Builder()
            .scheme("https")
            .host("generativelanguage.googleapis.com")
            .addPathSegment("v1beta")
            .build()
        const val GEMINI_MODEL = "gemini-3.5-flash-lite"
        const val GEMINI_LITE_MODEL = "gemini-3.5-flash-lite"
        const val GEMINI_FLASH_MODEL = "gemini-3.5-flash"
        const val GEMINI_38_MODEL = "gemini-3.8-flash"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VALID_PATH = Regex("[a-z0-9][a-z0-9_/-]*")
        private const val MAX_RESPONSE_BYTES = 1_048_576L
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .build()


    }
}
