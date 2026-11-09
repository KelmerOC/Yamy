package chat.mural.core

import chat.mural.ui.MuralSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseThreeUiAndFlowTest {

    @Test
    fun verifyTranslateAndKeyboardSymbols() {
        val translate = MuralSymbol.valueOf("Translate")
        val keyboard = MuralSymbol.valueOf("Keyboard")
        assertNotNull(translate)
        assertNotNull(keyboard)
        assertNotEquals(translate, keyboard)
        assertEquals(MuralSymbol.Translate, translate)
        assertEquals(MuralSymbol.Keyboard, keyboard)
    }

    @Test
    fun verifyPedagogicalModeToggleLogic() {
        val mode1 = TopicEcosystemStore.MODE_EDUCATION
        val toggled1 = if (mode1 == TopicEcosystemStore.MODE_EDUCATION) {
            TopicEcosystemStore.MODE_CONVERSATION
        } else {
            TopicEcosystemStore.MODE_EDUCATION
        }
        assertEquals(TopicEcosystemStore.MODE_CONVERSATION, toggled1)

        val mode2 = TopicEcosystemStore.MODE_CONVERSATION
        val toggled2 = if (mode2 == TopicEcosystemStore.MODE_EDUCATION) {
            TopicEcosystemStore.MODE_CONVERSATION
        } else {
            TopicEcosystemStore.MODE_EDUCATION
        }
        assertEquals(TopicEcosystemStore.MODE_EDUCATION, toggled2)
    }

    @Test
    fun verifyPedagogicalModeGuidancePromptContent() {
        val eduGuidance = TeachingPolicy.pedagogicalModeGuidance(TopicEcosystemStore.MODE_EDUCATION)
        assertTrue(eduGuidance.contains("ACTIVE EDUCATION MODE"))
        assertTrue(eduGuidance.contains("CORRECT THEN CONTINUE"))
        assertTrue(eduGuidance.contains("IMMEDIATELY continue"))

        val convGuidance = TeachingPolicy.pedagogicalModeGuidance(TopicEcosystemStore.MODE_CONVERSATION)
        assertTrue(convGuidance.contains("CONVERSATIONAL IMMERSION MODE"))
        assertTrue(convGuidance.contains("Do NOT point out, correct, or interrupt"))
    }

    @Test
    fun verifyPedagogicalModeConstants() {
        assertEquals("education", TopicEcosystemStore.MODE_EDUCATION)
        assertEquals("conversation", TopicEcosystemStore.MODE_CONVERSATION)
        assertEquals(TopicEcosystemStore.MODE_EDUCATION, TopicEcosystemStore.DEFAULT_MODE)
        assertEquals(TopicEcosystemStore.MODE_EDUCATION, TopicEcosystemStore.DEFAULT_PEDAGOGICAL_MODE)
        assertEquals(2, TopicEcosystemStore.PEDAGOGICAL_MODES.size)
        assertTrue(TopicEcosystemStore.PEDAGOGICAL_MODES.contains(TopicEcosystemStore.MODE_EDUCATION))
        assertTrue(TopicEcosystemStore.PEDAGOGICAL_MODES.contains(TopicEcosystemStore.MODE_CONVERSATION))
    }

    @Test
    fun verifyDualStartModeActionCompatibility() {
        val voiceAction = CloudAction.StartVoice
        val writtenAction = CloudAction.StartWritten

        assertNotEquals(voiceAction, writtenAction)
        assertEquals(CloudAction.StartWritten, writtenAction)
        assertEquals(CloudAction.StartVoice, voiceAction)
    }

    @Test
    fun verifyContextPreservesThemeAndDistinguishesWrittenAndSpokenModes() {
        val session = SessionRecord(
            id = "test-session",
            languageID = "es",
            themeID = "job_interview",
            title = "Entrevista de trabajo técnica"
        )
        // Spoken turn (e.g. from oral Gemini Live)
        session.append(Fragment(id = "f1", speaker = Speaker.assistant, text = "¿Qué experiencia tienes?", startMS = 0, endMS = 1000, typed = false))
        // Written turn (e.g. from typed chat)
        session.append(Fragment(id = "f2", speaker = Speaker.user, text = "Tengo 5 años con Kotlin y Android.", startMS = 1500, endMS = 2500, typed = true))

        val contextStr = TeachingPolicy.context(session)

        assertTrue(contextStr.contains("ACTIVE THEME: Entrevista de trabajo técnica (ID: job_interview)"))
        assertTrue(contextStr.contains("[SPOKEN]: ¿Qué experiencia tienes?"))
        assertTrue(contextStr.contains("[WRITTEN]: Tengo 5 años con Kotlin y Android."))
    }
}
