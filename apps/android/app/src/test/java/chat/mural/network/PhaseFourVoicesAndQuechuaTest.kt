package chat.mural.network

import chat.mural.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhaseFourVoicesAndQuechuaTest {

    @Test
    fun quechuaLanguageModuleIsProperlyConfigured() {
        val quechua = LanguageRegistry.get("qu")
        assertNotNull("Quechua module should be retrievable by id 'qu'", quechua)
        assertEquals("Quechua", quechua?.name)
        assertEquals("Runa Simi", quechua?.nativeName)
        assertEquals("Cusco-Collao", quechua?.variety)
        assertEquals("qu-PE", quechua?.locale)
        assertEquals("¡Allillanchu!", quechua?.greeting)
        assertEquals("allillanchu", quechua?.greetingWord)
        assertTrue(quechua?.speechGuidance?.contains("Southern Quechua") == true)
        assertTrue(quechua?.writingGuidance?.contains("trivocalic") == true)
        assertTrue(quechua?.lemmaGuidance?.contains("-y") == true)
        assertEquals(6, quechua?.teachingFocus?.size)
        assertTrue(quechua?.themes?.size == 24)
        assertEquals(5, quechua?.themeOverrides?.size)

        // Verify LanguageRegistry invariants
        assertEquals(
            "Core languages must remain exactly 8 for backwards compatibility",
            listOf("nb", "es", "en", "fr", "de", "it", "pt", "zh"),
            LanguageRegistry.all.map { it.id }
        )
        assertTrue(
            "Supported languages must include all core languages plus Quechua",
            LanguageRegistry.supported.map { it.id }.contains("qu")
        )
    }

    @Test
    fun voiceTimbreCatalogConstantsAndDefaults() {
        assertEquals("Aoede", VoiceSettingsStore.VOICE_AOEDE)
        assertEquals("Kore", VoiceSettingsStore.VOICE_KORE)
        assertEquals("Puck", VoiceSettingsStore.VOICE_PUCK)
        assertEquals("Fenrir", VoiceSettingsStore.VOICE_FENRIR)
        assertEquals("Charon", VoiceSettingsStore.VOICE_CHARON)

        assertEquals(
            listOf("Aoede", "Kore", "Puck", "Fenrir", "Charon"),
            VoiceSettingsStore.ALL_VOICE_TIMBRES
        )
    }

    @Test
    fun lowLatencyMeaningControllerDispatchesQuickly() = runTest {
        val dispatched = mutableListOf<String>()
        // Configured with MuralViewModel's optimized values: delayMillis = 100, incompleteDelayMillis = 350, minimumSpacingMillis = 500
        val controller = MeaningController(
            scope = backgroundScope,
            delayMillis = 100,
            incompleteDelayMillis = 350,
            minimumSpacingMillis = 500,
            now = { testScheduler.currentTime }
        ) {
            dispatched += it.text
            MeaningResult("Translation of: ${it.text}")
        }

        val fragment = Fragment(id = "p1", revision = 0, speaker = Speaker.assistant, text = "Allillanchu, munakuy pana.", startMS = 0, endMS = 1000)
        val request = MeaningRequest("session1", Passage("p1", Speaker.assistant, listOf(fragment)), learningLanguageID = "qu", meaningLanguage = "Spanish")

        controller.update(request)
        runCurrent()
        // At 0ms, not yet dispatched because of 100ms sentence delay
        assertEquals(0, dispatched.size)

        // Advance 105ms: complete sentence should be dispatched immediately (<110ms instead of old 450ms)
        advanceTimeBy(105)
        runCurrent()
        assertEquals(1, dispatched.size)
        assertEquals("Allillanchu, munakuy pana.", dispatched[0])
        assertEquals("Translation of: Allillanchu, munakuy pana.", controller.text)
        assertFalse(controller.isLoading)

        // Advance spacing time (505ms) and dispatch another phrase
        advanceTimeBy(505)
        val fragment2 = Fragment(id = "p2", revision = 0, speaker = Speaker.assistant, text = "Imaynallam kashanki?", startMS = 1000, endMS = 2000)
        val request2 = MeaningRequest("session1", Passage("p2", Speaker.assistant, listOf(fragment2)), learningLanguageID = "qu", meaningLanguage = "Spanish")

        controller.update(request2)
        advanceTimeBy(105)
        runCurrent()
        assertEquals(2, dispatched.size)
        assertEquals("Imaynallam kashanki?", dispatched[1])
    }

    @Test
    fun typedReplyInstructionsAreTargetedWithoutVoiceBloat() {
        val quechua = LanguageRegistry.get("qu")!!
        val theme = quechua.themes.first { it.id == "coffee" }

        val typedInstructions = TeachingPolicy.typedReply(
            language = quechua,
            theme = theme,
            mcerLevel = "A2",
            personality = "friendly",
            themeMemory = "Learner practiced greetings yesterday",
            pedagogicalMode = "education"
        )

        // Must contain role, personality, MCER level, theme memory and mode
        assertTrue(typedInstructions.contains("Mural’s Quechua conversation partner"))
        assertTrue(typedInstructions.contains("Aqha / Café?"))
        assertTrue(typedInstructions.contains("friendly"))
        assertTrue(typedInstructions.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))

        // Must NOT contain voice/audio pipeline directives that were previously bloating written chat
        assertFalse(typedInstructions.contains("BidiGenerateContent"))
        assertFalse(typedInstructions.contains("AudioRecord"))
        assertFalse(typedInstructions.contains("16.000 Hz"))
    }
}
