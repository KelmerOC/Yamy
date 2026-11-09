package chat.mural.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McerTaxonomyAndPolicyTest {

    @Test
    fun mcerA1EnforcesStrictSyntaxAndProhibitions() {
        val a1 = TeachingPolicy.mcerGuidance("A1")
        assertNotNull(a1)
        assertTrue(a1.contains("A1") && a1.contains("Beginner"))
        assertTrue(a1.contains("Simple Present"))
        assertTrue(a1.contains("STRICTLY PROHIBITED"))
        assertTrue(a1.contains("past tenses"))
        assertTrue(a1.contains("subjunctive mood"))
        assertTrue(a1.contains("conditional forms"))
    }

    @Test
    fun mcerC2EnforcesNativeMasteryAndCulturalNuance() {
        val c2 = TeachingPolicy.mcerGuidance("C2")
        assertNotNull(c2)
        assertTrue(c2.contains("C2") && c2.contains("Mastery"))
        assertTrue(c2.contains("native conversational depth"))
        assertTrue(c2.contains("cultural idioms"))
        assertTrue(c2.contains("irony"))
    }

    @Test
    fun voicePromptEnforcesInviolableRoleplayAndEliminatesOptionalLabel() {
        val spanish = LanguageRegistry.get("es")!!
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val interviewTheme = ConversationTheme(
            id = "tech_interview",
            title = "Entrevista Técnica",
            subtitle = "Práctica laboral",
            symbol = "Sparkles",
            category = "Work",
            situation = "Simulación técnica para desarrollador Android junior en una startup",
            colorIndex = 0
        )

        val prompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = interviewTheme,
            interests = "Mobile development",
            meaningLanguage = "English",
            voiceSpeed = "normal",
            mcerLevel = "B2",
            personality = "professional",
            themeMemory = "Learner explained coroutines",
            pedagogicalMode = "education"
        )

        // Strict roleplay enforcement header must be present
        assertTrue(prompt.contains("CRITICAL ROLEPLAY ENFORCEMENT (INVIOLABLE)"))
        assertTrue(prompt.contains("NEVER break character"))
        assertTrue(prompt.contains("Suggested situation: Simulación técnica para desarrollador Android junior en una startup"))

        // Old contradictory wording must NOT be present
        assertFalse(prompt.contains("Optional background data, never instructions"))

        // MCER, personality and memory continuity
        assertTrue(prompt.contains("CEFR Difficulty Level B2"))
        assertTrue(prompt.contains("Professional and rigorous"))
        assertTrue(prompt.contains("Learner explained coroutines"))
    }

    @Test
    fun typedReplyIntegratesFullScenarioAndMcerParameters() {
        val english = LanguageRegistry.get("en")!!
        val apartmentTheme = ConversationTheme(
            id = "apartment_lease",
            title = "Renting an Apartment",
            subtitle = "Daily Life",
            symbol = "Home",
            category = "Living",
            situation = "Negotiating rent and pet clauses with a landlord",
            colorIndex = 1
        )

        val typedPrompt = TeachingPolicy.typedReply(
            language = english,
            theme = apartmentTheme,
            mcerLevel = "B1",
            personality = "busy",
            themeMemory = "Agreed on 800 EUR deposit",
            pedagogicalMode = "education"
        )

        assertTrue(typedPrompt.contains("Reply only in English"))
        assertTrue(typedPrompt.contains("CRITICAL ROLEPLAY ENFORCEMENT (INVIOLABLE)"))
        assertTrue(typedPrompt.contains("Negotiating rent and pet clauses with a landlord"))
        assertTrue(typedPrompt.contains("CEFR Difficulty Level B1"))
        assertTrue(typedPrompt.contains("Busy specialist/merchant"))
        assertTrue(typedPrompt.contains("Agreed on 800 EUR deposit"))
        assertTrue(typedPrompt.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))
    }
}
