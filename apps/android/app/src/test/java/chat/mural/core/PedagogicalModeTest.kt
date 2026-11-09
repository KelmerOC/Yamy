package chat.mural.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PedagogicalModeTest {

    @Test
    fun educationModeEnforcesImmediateContinuationAfterCorrection() {
        val guidance = TeachingPolicy.pedagogicalModeGuidance("education")
        assertNotNull(guidance)
        assertTrue(guidance.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))
        assertTrue(guidance.contains("MANDATORY CONDUCT"))
        assertTrue(guidance.contains("IMMEDIATELY continue the conversation"))
        assertTrue(guidance.contains("NEVER stop at the correction"))
    }

    @Test
    fun conversationModeProhibitsGrammarCorrectionsAndEnforcesFluidImmersion() {
        val spanish = LanguageRegistry.get("es")!!
        val guidance = TeachingPolicy.pedagogicalModeGuidance("conversation", spanish)
        assertNotNull(guidance)
        assertTrue(guidance.contains("CONVERSATIONAL IMMERSION MODE (NO GRAMMAR CORRECTIONS)"))
        assertTrue(guidance.contains("Do NOT point out, correct, or interrupt for grammatical"))
        assertTrue(guidance.contains("I didn't quite catch that"))
        assertTrue(guidance.contains("Spanish"))
    }

    @Test
    fun voicePromptSwitchesPedagogicalGuidanceDynamically() {
        val french = LanguageRegistry.get("fr")!!
        val learner = LearningEngine.project(emptyList(), french.id)

        val eduPrompt = TeachingPolicy.voice(
            language = french,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            pedagogicalMode = "education"
        )
        assertTrue(eduPrompt.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))
        assertFalse(eduPrompt.contains("CONVERSATIONAL IMMERSION MODE (NO GRAMMAR CORRECTIONS)"))

        val convPrompt = TeachingPolicy.voice(
            language = french,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            pedagogicalMode = "conversation"
        )
        assertTrue(convPrompt.contains("CONVERSATIONAL IMMERSION MODE (NO GRAMMAR CORRECTIONS)"))
        assertFalse(convPrompt.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))
    }

    @Test
    fun spanishConversationModeStrictlyProhibitsGrammarCorrectionsInNativeGuidance() {
        val spanish = LanguageRegistry.get("es")!!
        val learner = LearningEngine.project(emptyList(), spanish.id)

        val prompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            pedagogicalMode = "conversation"
        )

        // Native Spanish conversational immersion
        assertTrue(prompt.contains("MODO CONVERSACIÓN ACTIVO (PROHIBICIÓN TOTAL DE CORRECCIÓN GRAMATICAL)"))
        assertTrue(prompt.contains("NO señales, no corrijas ni interrumpas jamás por errores gramaticales"))
        assertTrue(prompt.contains("NUNCA digas «has dicho X, lo correcto es Y»"))
        // Legacy forced correction rule must NOT be present
        assertFalse(prompt.contains("Corrige un error lingüístico claro"))
    }

    @Test
    fun strictScenarioPersistenceProhibitsAskingToChangeTopic() {
        val spanish = LanguageRegistry.get("es")!!
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val interviewTheme = ConversationTheme(
            id = "tech_interview",
            title = "Entrevista técnica",
            subtitle = "Práctica laboral",
            symbol = "code",
            category = "trabajo",
            situation = "Entrevista de trabajo técnica para desarrollador senior",
            colorIndex = 0
        )

        val voicePrompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = interviewTheme,
            interests = "",
            meaningLanguage = "English",
            pedagogicalMode = "conversation"
        )

        assertTrue(voicePrompt.contains("PERSISTENCIA ESTRICTA EN EL TEMA Y ROL (INQUEBRANTABLE)"))
        assertTrue(voicePrompt.contains("NUNCA preguntes «¿cambiamos de tema?»"))
        assertFalse(voicePrompt.contains("tu siguiente turno debe ser una sola pregunta breve para confirmar el cambio"))

        val typedPrompt = TeachingPolicy.typedReply(
            language = spanish,
            theme = interviewTheme,
            pedagogicalMode = "conversation"
        )
        assertTrue(typedPrompt.contains("PERSISTENCIA ESTRICTA EN EL TEMA Y ROL (INQUEBRANTABLE)"))
        assertTrue(typedPrompt.contains("MODO CONVERSACIÓN ACTIVO (PROHIBICIÓN TOTAL DE CORRECCIÓN GRAMATICAL)"))
        assertFalse(typedPrompt.contains("Corrige un error lingüístico claro"))
    }
}
