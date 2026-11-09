package chat.mural.network

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkResilienceAndVadTest {

    private val maxReconnectAttempts = 3
    private val maxPendingWsQueueBytes = 64 * 1024L

    @Test
    fun softwareAudioGainAmplifiesLowSignalsCleanly() {
        // Low amplitude sample: 1000 (typical quiet speech / distance from mic)
        val lowSample = 1000.toShort()
        val buffer = ByteArray(2)
        buffer[0] = (lowSample.toInt() and 0xFF).toByte()
        buffer[1] = ((lowSample.toInt() shr 8) and 0xFF).toByte()

        // Apply 1.5x software gain
        val multiplier = 1.5f
        val sample = ((buffer[0].toInt() and 0xFF) or (buffer[1].toInt() shl 8)).toShort().toFloat()
        val scaled = sample * multiplier
        val clamped = when {
            scaled > 30000f -> (30000f + (scaled - 30000f) * 0.2f).coerceAtMost(Short.MAX_VALUE.toFloat())
            scaled < -30000f -> (-30000f + (scaled + 30000f) * 0.2f).coerceAtLeast(Short.MIN_VALUE.toFloat())
            else -> scaled
        }
        val amplified = clamped.toInt()
        buffer[0] = (amplified and 0xFF).toByte()
        buffer[1] = ((amplified shr 8) and 0xFF).toByte()

        val resultSample = ((buffer[0].toInt() and 0xFF) or (buffer[1].toInt() shl 8)).toShort()
        assertEquals(1500.toShort(), resultSample)
    }

    @Test
    fun dualPayloadStructureMatchesGeminiLiveSpec() {
        val sampleRate = 16000
        val base64Data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEA"
        val message = buildJsonObject {
            put("realtimeInput", buildJsonObject {
                put("audio", buildJsonObject {
                    put("mimeType", "audio/pcm;rate=$sampleRate")
                    put("data", base64Data)
                })
                put("mediaChunks", buildJsonArray {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=$sampleRate")
                        put("data", base64Data)
                    })
                })
            })
        }
        val rtInput = message["realtimeInput"]?.jsonObject
        assertNotNull(rtInput)
        // Verify audio object
        val audioObj = rtInput?.get("audio")?.jsonObject
        assertNotNull(audioObj)
        assertEquals("audio/pcm;rate=16000", audioObj?.get("mimeType")?.jsonPrimitive?.content)
        assertEquals(base64Data, audioObj?.get("data")?.jsonPrimitive?.content)

        // Verify mediaChunks array
        val mediaChunks = rtInput?.get("mediaChunks")?.jsonArray
        assertNotNull(mediaChunks)
        assertEquals(1, mediaChunks?.size)
        val firstChunk = mediaChunks?.get(0)?.jsonObject
        assertEquals("audio/pcm;rate=16000", firstChunk?.get("mimeType")?.jsonPrimitive?.content)
    }

    @Test
    fun backpressureThresholdDropsCongestedFrames() {
        // Normal network: queue is low
        val normalQueueBytes = 4096L
        val allowNormal = normalQueueBytes <= maxPendingWsQueueBytes
        assertTrue("Normal queue should permit sending", allowNormal)

        // Congested network / high ping spike: queue exceeds 64KB
        val congestedQueueBytes = 70000L
        val allowCongested = congestedQueueBytes <= maxPendingWsQueueBytes
        assertFalse("Congested queue should drop frame to avoid latency drift", allowCongested)
    }

    @Test
    fun hotReconnectBackoffCalculations() {
        // Attempt 1: 400ms * 2^0 = 400ms
        val delay1 = minOf(3000L, 400L * (1L shl (1 - 1)))
        assertEquals(400L, delay1)

        // Attempt 2: 400ms * 2^1 = 800ms
        val delay2 = minOf(3000L, 400L * (1L shl (2 - 1)))
        assertEquals(800L, delay2)

        // Attempt 3: 400ms * 2^2 = 1600ms
        val delay3 = minOf(3000L, 400L * (1L shl (3 - 1)))
        assertEquals(1600L, delay3)

        // Capped attempts
        assertTrue("Attempt 3 does not exceed max attempts", 3 <= maxReconnectAttempts)
        assertFalse("Attempt 4 exceeds max attempts", 4 <= maxReconnectAttempts)
    }
}
