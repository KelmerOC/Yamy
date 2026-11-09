package chat.mural.core

import chat.mural.ui.MuralSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseTwoEcosystemAndIconsTest {

    @Test
    fun translateSymbolExistsInMuralSymbol() {
        val translateSymbol = MuralSymbol.valueOf("Translate")
        assertNotNull(translateSymbol)
        assertEquals(MuralSymbol.Translate, translateSymbol)
    }

    @Test
    fun startWrittenActionExistsInCloudAction() {
        val action: CloudAction = CloudAction.StartWritten
        assertNotNull(action)
        assertEquals(CloudAction.StartWritten, action)
    }

    @Test
    fun topicEcosystemStorePedagogicalModeConstantsAreValid() {
        assertEquals("education", TopicEcosystemStore.DEFAULT_MODE)
        assertEquals("education", TopicEcosystemStore.MODE_EDUCATION)
        assertEquals("conversation", TopicEcosystemStore.MODE_CONVERSATION)
        assertEquals(2, TopicEcosystemStore.PEDAGOGICAL_MODES.size)
        assertTrue(TopicEcosystemStore.PEDAGOGICAL_MODES.containsAll(listOf("education", "conversation")))
    }

    @Test
    fun themeMemorySyncPreservesContextAcrossOralAndWrittenPrompts() {
        val spanish = LanguageRegistry.get("es")!!
        val theme = ConversationTheme(
            id = "restaurant_order",
            title = "Pedir en el Restaurante",
            subtitle = "Vida Cotidiana",
            symbol = "Coffee",
            category = "Comida",
            situation = "Ordenar comida típica y consultar sobre alérgenos con el mesero",
            colorIndex = 2
        )
        val priorMemory = "Learner asked for gluten-free options and ordered paella"

        // Test in Oral voice prompt
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val voicePrompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = theme,
            interests = "Gastronomía",
            meaningLanguage = "English",
            themeMemory = priorMemory,
            pedagogicalMode = TopicEcosystemStore.MODE_EDUCATION
        )
        assertTrue(voicePrompt.contains("Theme Context & Memory"))
        assertTrue(voicePrompt.contains(priorMemory))
        assertTrue(voicePrompt.contains("Suggested situation"))
        assertTrue(voicePrompt.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))

        // Test in Written prompt
        val typedPrompt = TeachingPolicy.typedReply(
            language = spanish,
            theme = theme,
            themeMemory = priorMemory,
            pedagogicalMode = TopicEcosystemStore.MODE_EDUCATION
        )
        assertTrue(typedPrompt.contains("Theme Context & Memory"))
        assertTrue(typedPrompt.contains(priorMemory))
        assertTrue(typedPrompt.contains("ACTIVE EDUCATION MODE (CORRECT THEN CONTINUE)"))
    }

    @Test
    fun topicEcosystemStoreMcerLevelConstantsAreValid() {
        assertEquals("B1", TopicEcosystemStore.DEFAULT_MCER)
        assertEquals(6, TopicEcosystemStore.MCER_LEVELS.size)
        assertTrue(TopicEcosystemStore.MCER_LEVELS.containsAll(listOf("A1", "A2", "B1", "B2", "C1", "C2")))
    }

    @Test
    fun casualUserWithoutThemeReceivesMcerAndPedagogicalInstructions() {
        val spanish = LanguageRegistry.get("es")!!

        // Written prompt for casual user with no theme selected
        val typedPrompt = TeachingPolicy.typedReply(
            language = spanish,
            theme = null,
            mcerLevel = "B2",
            pedagogicalMode = TopicEcosystemStore.MODE_CONVERSATION
        )
        assertTrue("Should include B2 guidance even without active theme", typedPrompt.contains("CEFR Difficulty Level B2"))
        assertTrue("Should include conversation mode guidance", typedPrompt.contains("CONVERSATIONAL IMMERSION MODE"))

        // Voice prompt for casual user with no theme selected
        val learner = LearningEngine.project(emptyList(), spanish.id)
        val voicePrompt = TeachingPolicy.voice(
            language = spanish,
            learner = learner,
            theme = null,
            interests = "",
            meaningLanguage = "English",
            voiceSpeed = "standard",
            mcerLevel = "A1",
            pedagogicalMode = TopicEcosystemStore.MODE_EDUCATION
        )
        assertTrue("Should include A1 guidance even without active theme", voicePrompt.contains("CEFR Difficulty Level A1"))
        assertTrue("Should include active education mode guidance", voicePrompt.contains("ACTIVE EDUCATION MODE"))
    }

    @Test
    fun mcerGuidanceProducesDistinctDirectivesForAllSixLevels() {
        val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
        levels.forEach { level ->
            val guidance = TeachingPolicy.mcerGuidance(level)
            assertTrue("Guidance for $level must mention $level", guidance.contains(level))
        }
    }
}
