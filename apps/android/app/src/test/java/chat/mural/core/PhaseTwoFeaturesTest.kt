package chat.mural.core

import chat.mural.ui.MuralSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseTwoFeaturesTest {

    @Test
    fun heartSymbolExistsInMuralSymbol() {
        val heartSymbol = MuralSymbol.valueOf("Heart")
        assertNotNull(heartSymbol)
        assertEquals(MuralSymbol.Heart, heartSymbol)
    }

    @Test
    fun mcerGuidanceCoversAllCefrLevels() {
        val a1 = TeachingPolicy.mcerGuidance("A1")
        assertTrue(a1.contains("A1") && a1.contains("Beginner"))

        val a2 = TeachingPolicy.mcerGuidance("a2")
        assertTrue(a2.contains("A2") && a2.contains("Elementary"))

        val b1 = TeachingPolicy.mcerGuidance("B1")
        assertTrue(b1.contains("B1") && b1.contains("Intermediate"))

        val b2 = TeachingPolicy.mcerGuidance("B2")
        assertTrue(b2.contains("B2") && b2.contains("Upper-Intermediate"))

        val c1 = TeachingPolicy.mcerGuidance("c1")
        assertTrue(c1.contains("C1") && c1.contains("Advanced"))

        val c2 = TeachingPolicy.mcerGuidance("C2")
        assertTrue(c2.contains("C2") && c2.contains("Mastery"))
    }

    @Test
    fun personalityGuidanceCoversAllProfiles() {
        val friendly = TeachingPolicy.personalityGuidance(TopicEcosystemStore.PERSONALITY_FRIENDLY)
        assertTrue(friendly.contains("Warm, friendly"))

        val professional = TeachingPolicy.personalityGuidance(TopicEcosystemStore.PERSONALITY_PROFESSIONAL)
        assertTrue(professional.contains("Professional and rigorous"))

        val casual = TeachingPolicy.personalityGuidance(TopicEcosystemStore.PERSONALITY_CASUAL)
        assertTrue(casual.contains("Casual conversational friend"))

        val busy = TeachingPolicy.personalityGuidance(TopicEcosystemStore.PERSONALITY_BUSY)
        assertTrue(busy.contains("Busy specialist/merchant"))
    }

    @Test
    fun voicePromptIntegratesThemeEcosystemParameters() {
        val spanish = LanguageRegistry.get("es")!!
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val coffeeTheme = spanish.themes.firstOrNull { it.id == "coffee" } ?: Themes.shared.first()

        val prompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = coffeeTheme,
            interests = "technology",
            meaningLanguage = "English",
            voiceSpeed = VoiceSettingsStore.SPEED_NORMAL,
            mcerLevel = "B2",
            personality = "professional",
            themeMemory = "Learner practiced coffee ordering"
        )

        // Must preserve core invariants
        assertTrue(prompt.contains("Spanish"))
        assertTrue(prompt.contains("Strict Language Restriction"))
        assertTrue(prompt.contains("Suggested situation"))

        // Phase 2 features verified in prompt
        assertTrue(prompt.contains("CEFR Difficulty Level B2"))
        assertTrue(prompt.contains("Professional and rigorous"))
        assertTrue(prompt.contains("Theme Context & Memory"))
        assertTrue(prompt.contains("Learner practiced coffee ordering"))
    }

    @Test
    fun topicEcosystemStoreConstantsAreValid() {
        assertEquals("B1", TopicEcosystemStore.DEFAULT_MCER)
        assertEquals(6, TopicEcosystemStore.MCER_LEVELS.size)
        assertTrue(TopicEcosystemStore.MCER_LEVELS.containsAll(listOf("A1", "A2", "B1", "B2", "C1", "C2")))

        assertEquals("friendly", TopicEcosystemStore.DEFAULT_PERSONALITY)
        assertEquals(4, TopicEcosystemStore.PERSONALITIES.size)
    }

    @Test
    fun createThemePromptHasCorrectStructureAndAvoidsWebSearch() {
        val spanish = LanguageRegistry.get("es")!!
        val prompt = TeachingPolicy.createTheme(spanish)

        assertTrue(prompt.contains("Spanish"))
        assertTrue(prompt.contains("curriculum designer"))
        assertTrue(prompt.contains("title"))
        assertTrue(prompt.contains("situation"))
        assertTrue(prompt.contains("defaultMcerLevel"))
        assertTrue(prompt.contains("tutorPersonality"))
        assertTrue(prompt.contains("systemPromptRole"))
        // Must avoid web search to prevent 429 quota exhaustion
        assertTrue(prompt.contains("Do NOT search the web"))
    }
}
