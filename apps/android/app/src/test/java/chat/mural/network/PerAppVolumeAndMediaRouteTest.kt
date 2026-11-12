package chat.mural.network

import chat.mural.core.VoiceSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PerAppVolumeAndMediaRouteTest {

    private fun appMainDir(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val direct = File(dir, "src/main")
            if (direct.isDirectory) return direct
            val nested = File(dir, "apps/android/app/src/main")
            if (nested.isDirectory) return nested
            dir = dir.parentFile
        }
        error("Could not locate apps/android/app/src/main from ${File(".").canonicalPath}")
    }

    @Test
    fun perceptualGainMapsBoundariesAndMidpointCorrectly() {
        assertEquals(0f, VoiceSettingsStore.perceptualGain(0f), 0.0001f)
        assertEquals(1f, VoiceSettingsStore.perceptualGain(1f), 0.0001f)
        assertEquals(0.25f, VoiceSettingsStore.perceptualGain(0.5f), 0.0001f)
    }

    @Test
    fun perceptualGainClampsOutOfRangeAndNaNValues() {
        assertEquals(0f, VoiceSettingsStore.perceptualGain(-0.01f), 0.0001f)
        assertEquals(0f, VoiceSettingsStore.perceptualGain(-100f), 0.0001f)
        assertEquals(1f, VoiceSettingsStore.perceptualGain(1.01f), 0.0001f)
        assertEquals(1f, VoiceSettingsStore.perceptualGain(50f), 0.0001f)
        assertEquals(0f, VoiceSettingsStore.perceptualGain(Float.NaN), 0.0001f)
    }

    @Test
    fun audioOutputProfileAndVoiceSettingsConstantsMatchContract() {
        assertNotEquals(AudioOutputProfile.MEDIA, AudioOutputProfile.COMMUNICATION)
        assertEquals(
            listOf(AudioOutputProfile.MEDIA, AudioOutputProfile.COMMUNICATION),
            AudioOutputProfile.values().toList()
        )
        assertEquals(1.0f, VoiceSettingsStore.DEFAULT_APP_VOLUME, 0.0001f)
        assertEquals("background_voice_enabled", VoiceSettingsStore.KEY_BACKGROUND_VOICE)
        assertEquals("media_output_enabled", VoiceSettingsStore.KEY_MEDIA_OUTPUT)
        assertEquals("app_volume", VoiceSettingsStore.KEY_APP_VOLUME)
    }

    @Test
    fun geminiLiveTransportPreservesProtectedAudioInvariants() {
        val mainDir = appMainDir()
        val transportFile = File(mainDir, "java/chat/mural/network/GeminiLiveTransport.kt")
        val routeFile = File(mainDir, "java/chat/mural/network/CommunicationAudioRoute.kt")
        assertTrue("GeminiLiveTransport.kt must exist", transportFile.isFile)
        assertTrue("CommunicationAudioRoute.kt must exist", routeFile.isFile)

        val transportSource = transportFile.readText()
        val routeSource = routeFile.readText()

        // Continuous mic streaming without client-side silence gating
        assertFalse(
            "Client-side SPEECH_RMS_THRESHOLD gating must not be reintroduced",
            transportSource.contains("SPEECH_RMS_THRESHOLD")
        )

        // Software pre-amplification with 1.5f gain
        assertTrue(
            "applyGain(..., 1.5f) must be preserved in GeminiLiveTransport.kt",
            transportSource.contains("applyGain(buffer, read, 1.5f)")
        )

        // Dual payload: audio + mediaChunks
        assertTrue(
            "sendAudioChunk must include audio field",
            transportSource.contains("put(\"audio\", buildJsonObject")
        )
        assertTrue(
            "sendAudioChunk must include mediaChunks field",
            transportSource.contains("put(\"mediaChunks\", buildJsonArray")
        )

        // Media output profile uses MODE_NORMAL and USAGE_MEDIA
        assertTrue(
            "Media output must set AudioManager.MODE_NORMAL",
            transportSource.contains("AudioManager.MODE_NORMAL")
        )
        assertTrue(
            "AudioOutputProfile.MEDIA must map to AudioAttributes.USAGE_MEDIA",
            routeSource.contains("AudioOutputProfile.MEDIA -> AudioAttributes.USAGE_MEDIA")
        )
        assertTrue(
            "AudioOutputProfile.COMMUNICATION must map to AudioAttributes.USAGE_VOICE_COMMUNICATION",
            routeSource.contains("AudioOutputProfile.COMMUNICATION -> AudioAttributes.USAGE_VOICE_COMMUNICATION")
        )

        // Transient audio focus losses must duck/pause without calling fatal fail()
        val duckIdx = transportSource.indexOf("AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->")
        val transientIdx = transportSource.indexOf("AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->")
        val gainIdx = transportSource.indexOf("AudioManager.AUDIOFOCUS_GAIN ->")
        assertTrue("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK handler must exist", duckIdx >= 0)
        assertTrue("AUDIOFOCUS_LOSS_TRANSIENT handler must exist", transientIdx > duckIdx)
        assertTrue("AUDIOFOCUS_GAIN handler must exist", gainIdx > transientIdx)

        val duckBranch = transportSource.substring(duckIdx, transientIdx)
        val transientBranch = transportSource.substring(transientIdx, gainIdx)
        assertFalse("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK must not call fail()", duckBranch.contains("fail("))
        assertFalse("AUDIOFOCUS_LOSS_TRANSIENT must not call fail()", transientBranch.contains("fail("))
    }

    @Test
    fun transportsAndViewModelSupportBidirectionalPauseAndDurationSelection() {
        val mainDir = appMainDir()
        val geminiSource = File(mainDir, "java/chat/mural/network/GeminiLiveTransport.kt").readText()
        val liveSource = File(mainDir, "java/chat/mural/network/LiveTransport.kt").readText()
        val vmSource = File(mainDir, "java/chat/mural/MuralViewModel.kt").readText()

        assertTrue("GeminiLiveTransport must expose setPlaybackPaused", geminiSource.contains("fun setPlaybackPaused(paused: Boolean)"))
        assertTrue("GeminiLiveTransport must track userPaused", geminiSource.contains("val userPaused = AtomicBoolean(false)"))
        assertTrue("LiveTransport must expose setPlaybackPaused", liveSource.contains("fun setPlaybackPaused(paused: Boolean)"))
        assertTrue("LiveTransport must track userPaused", liveSource.contains("val userPaused = AtomicBoolean(false)"))
        assertTrue("MuralViewModel must expose toggleMediaPause()", vmSource.contains("fun toggleMediaPause()"))
        assertTrue("MuralViewModel must expose selectSessionMinutes(minutes: Int)", vmSource.contains("fun selectSessionMinutes(minutes: Int)"))
    }
}
