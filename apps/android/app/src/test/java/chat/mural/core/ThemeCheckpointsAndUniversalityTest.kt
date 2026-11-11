package chat.mural.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ThemeCheckpointsAndUniversalityTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun themePersistentMemoryCheckpointSerialization() {
        val original = ThemePersistentMemory(
            themeId = "custom_bechamel",
            checkpointSummary = "El estudiante aprendió a preparar el roux y preguntó sobre la proporción de mantequilla y harina.",
            keyTopicsDiscussed = listOf("Roux", "Proporciones", "Leche tibia"),
            lastSessionSummary = "Hicimos la base de la salsa y añadimos la nuez moscada.",
            contextHistory = listOf("Sesión 1: Ingredientes", "Sesión 2: Cocción"),
            frequentErrors = listOf("Confusión entre roux rubio y roux oscuro"),
            practicedWords = listOf("saucepan", "whisk", "roux", "nutmeg"),
            sessionsCount = 2,
            completedTestsCount = 1,
            bestTestScore = 90
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ThemePersistentMemory>(encoded)

        assertEquals("custom_bechamel", decoded.themeId)
        assertEquals("El estudiante aprendió a preparar el roux y preguntó sobre la proporción de mantequilla y harina.", decoded.checkpointSummary)
        assertEquals(3, decoded.keyTopicsDiscussed.size)
        assertTrue(decoded.keyTopicsDiscussed.contains("Roux"))
        assertEquals("Hicimos la base de la salsa y añadimos la nuez moscada.", decoded.lastSessionSummary)
        assertEquals(1, decoded.frequentErrors.size)
        assertEquals(4, decoded.practicedWords.size)
        assertEquals(2, decoded.sessionsCount)
    }

    @Test
    fun legacyPersistentMemoryDecodesWithDefaultCheckpointFields() {
        // Validar compatibilidad hacia atrás con JSON de memoria antigua
        val legacyJson = """
            {
              "themeId": "custom_old",
              "contextHistory": ["Sesión antigua"],
              "frequentErrors": ["Error viejo"],
              "practicedWords": ["word1"],
              "sessionsCount": 1,
              "completedTestsCount": 0,
              "bestTestScore": 0,
              "lastUpdatedAt": 123456789
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ThemePersistentMemory>(legacyJson)
        assertEquals("custom_old", decoded.themeId)
        assertEquals("", decoded.checkpointSummary)
        assertTrue(decoded.keyTopicsDiscussed.isEmpty())
        assertEquals("", decoded.lastSessionSummary)
        assertEquals(1, decoded.contextHistory.size)
    }

    @Test
    fun cachedThemeTranslationSerialization() {
        val translation = CachedThemeTranslation(
            title = "Preparar salsa bechamel",
            subtitle = "Un clásico de la cocina",
            situation = "Eres un chef francés enseñando a preparar salsa bechamel paso a paso."
        )

        val encoded = json.encodeToString(translation)
        val decoded = json.decodeFromString<CachedThemeTranslation>(encoded)

        assertEquals("Preparar salsa bechamel", decoded.title)
        assertEquals("Un clásico de la cocina", decoded.subtitle)
        assertEquals("Eres un chef francés enseñando a preparar salsa bechamel paso a paso.", decoded.situation)
    }

    @Test
    fun defaultThemesArePreTranslatedInAllTwelveLanguagesAtZeroTokens() {
        val coffeeTheme = Themes.shared.first { it.id == "coffee" }
        val supportedLangs = listOf("es", "en", "ko", "ja", "hi", "pt", "fr", "de", "it", "zh", "nb", "qu")

        for (lang in supportedLangs) {
            val loc = LocalizedThemes.get(coffeeTheme, lang)
            assertNotNull("Idioma $lang debe tener traducción nativa de coffee", loc)
            assertFalse("Título en $lang no debe estar vacío", loc.title.isBlank())
            assertFalse("Subtítulo en $lang no debe estar vacío", loc.subtitle.isBlank())
            assertFalse("Situación en $lang no debe estar vacía", loc.situation.isBlank())
        }

        // Verificaciones específicas de textos nativos predefinidos
        assertEquals("Un café", LocalizedThemes.get(coffeeTheme, "es").title)
        assertEquals("커피 한 잔?", LocalizedThemes.get(coffeeTheme, "ko").title)
        assertEquals("コーヒーでも？", LocalizedThemes.get(coffeeTheme, "ja").title)
        assertEquals("Un café ?", LocalizedThemes.get(coffeeTheme, "fr").title)
        assertEquals("Ein Kaffee?", LocalizedThemes.get(coffeeTheme, "de").title)
        assertEquals("¿Aqha icha kaphichu?", LocalizedThemes.get(coffeeTheme, "qu").title)
    }

    @Test
    fun teachingPolicyPromptsContainCheckpointAndTranslationInvariants() {
        val module = LanguageRegistry.get("es")!!

        val checkpointPrompt = TeachingPolicy.themeCheckpointPrompt(module)
        assertTrue("Debe mencionar checkpointSummary", checkpointPrompt.contains("checkpointSummary"))
        assertTrue("Debe mencionar lastSessionSummary", checkpointPrompt.contains("lastSessionSummary"))
        assertTrue("Debe mencionar keyTopicsDiscussed", checkpointPrompt.contains("keyTopicsDiscussed"))
        assertTrue("Debe mencionar frequentErrors", checkpointPrompt.contains("frequentErrors"))
        assertTrue("Debe mencionar practicedWords", checkpointPrompt.contains("practicedWords"))

        val transPrompt = TeachingPolicy.themeTranslationPrompt("fr", "French")
        assertTrue("Debe pedir title", transPrompt.contains("\"title\""))
        assertTrue("Debe pedir subtitle", transPrompt.contains("\"subtitle\""))
        assertTrue("Debe pedir situation", transPrompt.contains("\"situation\""))
        assertTrue("Debe especificar French", transPrompt.contains("French"))
    }

    @Test
    fun teachingPolicyVoiceAndTypedReplyInjectCheckpointMemory() {
        val module = LanguageRegistry.get("en")!!
        val theme = ConversationTheme(
            id = "custom_bechamel",
            title = "Making Béchamel Sauce",
            subtitle = "A culinary classic",
            symbol = "sparkles",
            category = "Cooking",
            situation = "You are Chef Pierre teaching béchamel sauce.",
            colorIndex = 1
        )
        val checkpointText = "Punto de control previo: El estudiante ya preparó el roux de mantequilla y harina."

        val voicePrompt = TeachingPolicy.voice(
            language = module,
            learner = LearnerState(0, 0, "", emptyList(), emptyList()),
            theme = theme,
            interests = "Cooking",
            meaningLanguage = "Spanish",
            themeMemory = checkpointText
        )

        assertTrue("Voice prompt debe inyectar el punto de control", voicePrompt.contains("CHECKPOINT & MEMORY OF PRIOR CONVERSATIONS"))
        assertTrue("Voice prompt debe incluir el texto del checkpoint", voicePrompt.contains(checkpointText))
        assertTrue("Voice prompt debe exigir no comenzar desde cero", voicePrompt.contains("Do NOT start from zero"))
        assertTrue("Voice prompt debe exigir estricta adaptación al idioma de práctica", voicePrompt.contains("TARGET PRACTICE LANGUAGE ENFORCEMENT"))

        val typedPrompt = TeachingPolicy.typedReply(
            language = module,
            theme = theme,
            themeMemory = checkpointText
        )

        assertTrue("Typed prompt debe inyectar el punto de control", typedPrompt.contains("CHECKPOINT & MEMORY OF PRIOR CONVERSATIONS"))
        assertTrue("Typed prompt debe incluir el texto del checkpoint", typedPrompt.contains(checkpointText))
        assertTrue("Typed prompt debe exigir estricta adaptación al idioma de práctica", typedPrompt.contains("TARGET PRACTICE LANGUAGE ENFORCEMENT"))
    }
}
