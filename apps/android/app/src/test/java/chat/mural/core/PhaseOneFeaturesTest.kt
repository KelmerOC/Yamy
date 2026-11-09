package chat.mural.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseOneFeaturesTest {

    @Test
    fun voiceCadenceGuidanceProvidesCorrectProfiles() {
        val beginner = TeachingPolicy.voiceCadenceGuidance(VoiceSettingsStore.SPEED_BEGINNER)
        assertTrue(beginner.contains("100-110 words per minute"))
        assertTrue(beginner.contains("Iniciante") || beginner.contains("BEGINNER") || beginner.contains("slowly"))

        val normal = TeachingPolicy.voiceCadenceGuidance(VoiceSettingsStore.SPEED_NORMAL)
        assertTrue(normal.contains("130-140 words per minute"))
        assertTrue(normal.contains("conversational pace"))

        val advanced = TeachingPolicy.voiceCadenceGuidance(VoiceSettingsStore.SPEED_ADVANCED)
        assertTrue(advanced.contains("160-180 words per minute"))
        assertTrue(advanced.contains("native speed"))

        // Default fallback to normal
        val unknown = TeachingPolicy.voiceCadenceGuidance("other_value")
        assertTrue(unknown.contains("130-140 words per minute"))
    }

    @Test
    fun languagePinningGuidanceEnforcesActiveLanguages() {
        val englishModule = LanguageRegistry.get("en")!!
        val pinningEnEs = TeachingPolicy.languagePinningGuidance(englishModule, "Spanish")
        assertTrue(pinningEnEs.contains("English"))
        assertTrue(pinningEnEs.contains("Spanish"))
        assertTrue(pinningEnEs.contains("Never hallucinate or transcribe words in third languages"))

        val spanishModule = LanguageRegistry.get("es")!!
        val pinningEsEn = TeachingPolicy.languagePinningGuidance(spanishModule, "English")
        assertTrue(pinningEsEn.contains("Spanish"))
        assertTrue(pinningEsEn.contains("English"))
        assertFalse(pinningEsEn.contains("Norwegian"))
    }

    @Test
    fun voiceInstructionsIntegrateCadenceAndLanguagePinning() {
        val spanish = LanguageRegistry.get("es")!!
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val voiceBeginner = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            voiceSpeed = VoiceSettingsStore.SPEED_BEGINNER
        )

        assertTrue(voiceBeginner.contains("Spanish"))
        assertTrue(voiceBeginner.contains("English"))
        assertTrue(voiceBeginner.contains("100-110 words per minute"))
        assertTrue(voiceBeginner.contains("Strict Language Restriction"))
        assertFalse(voiceBeginner.contains("Norwegian"))

        val voiceAdvanced = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            voiceSpeed = VoiceSettingsStore.SPEED_ADVANCED
        )
        assertTrue(voiceAdvanced.contains("160-180 words per minute"))
    }
}
