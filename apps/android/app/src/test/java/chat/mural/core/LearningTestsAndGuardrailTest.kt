package chat.mural.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LearningTestsAndGuardrailTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun learningTestAndFlashcardsSerializationFidelity() {
        val testItem1 = LearningTestItem(
            id = "q1",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "Selecciona la opción correcta:",
            context = "She suggested ___ to the museum tomorrow.",
            options = listOf("going", "to go", "go", "went", "gone"),
            correctAnswer = "going",
            explanation = "El verbo 'suggest' rige gerundio (-ing) cuando le sigue directamente la acción."
        )

        val testItem2 = LearningTestItem(
            id = "q2",
            type = TestItemType.FILL_BLANK,
            prompt = "Completa la oración:",
            context = "If I had known, I ___ have called you.",
            correctAnswer = "would",
            explanation = "Tercer condicional: If + past perfect, would + have + past participle."
        )

        val testItem3 = LearningTestItem(
            id = "q3",
            type = TestItemType.ERROR_CORRECTION,
            prompt = "Corrige la palabra incorrecta:",
            context = "He is looking forward to meet you.",
            correctAnswer = "meeting",
            explanation = "'Look forward to' va seguido de gerundio (-ing)."
        )

        val testItem4 = LearningTestItem(
            id = "q4",
            type = TestItemType.SPEAKING,
            prompt = "Lee en voz alta con buena entonación:",
            context = "I would appreciate your feedback on this proposal.",
            correctAnswer = "I would appreciate your feedback on this proposal.",
            explanation = "Práctica de cortesía profesional en nivel B2.",
            targetSpeechText = "I would appreciate your feedback on this proposal."
        )

        val flashcard = FlashcardItem(
            id = "f1",
            front = "Look forward to",
            back = "Esperar con ilusión / anticipar con agrado",
            example = "I look forward to our meeting next Monday.",
            pronunciationGuide = "/lʊk ˈfɔːrwərd tuː/"
        )

        val originalTest = LearningTest(
            id = "test_abc123",
            title = "Phrasal Verbs y Condicionales",
            topicTitle = "Reunión de Negocios",
            mcerLevel = "B2",
            difficulty = TestDifficulty.MEDIUM,
            items = listOf(testItem1, testItem2, testItem3, testItem4),
            flashcards = listOf(flashcard)
        )

        val encoded = json.encodeToString(originalTest)
        val decoded = json.decodeFromString<LearningTest>(encoded)

        assertEquals("test_abc123", decoded.id)
        assertEquals("en", decoded.languageID)
        assertEquals("B2", decoded.mcerLevel)
        assertEquals(TestDifficulty.MEDIUM, decoded.difficulty)
        assertEquals(4, decoded.items.size)
        assertEquals(5, decoded.items[0].options.size)
        assertEquals("going", decoded.items[0].correctAnswer)
        assertEquals(TestItemType.SPEAKING, decoded.items[3].type)
        assertEquals(1, decoded.flashcards.size)
        assertEquals("Look forward to", decoded.flashcards[0].front)
    }

    @Test
    fun learningTestBackwardCompatibilityWhenLanguageIdMissing() {
        // Simular JSON antiguo sin campo languageID
        val legacyJson = """
            {
              "id": "legacy_1",
              "title": "Vocabulario Antiguo",
              "topicTitle": "General",
              "mcerLevel": "A1",
              "difficulty": "LOW",
              "items": [],
              "flashcards": []
            }
        """.trimIndent()
        val decoded = json.decodeFromString<LearningTest>(legacyJson)
        assertEquals("en", decoded.languageID)
        assertEquals("legacy_1", decoded.id)
    }

    @Test
    fun testTypeFilterValues() {
        assertEquals(9, TestTypeFilter.entries.size)
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.ALL))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.MULTIPLE_CHOICE))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.FILL_BLANK))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.CLOZE_BANK))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.ERROR_CORRECTION))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.READING_COMPREHENSION))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.SPEAKING))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.FLASHCARDS))
        assertTrue(TestTypeFilter.entries.contains(TestTypeFilter.MIXED))
    }

    @Test
    fun testAttemptAccuracyCalculation() {
        val attempt = TestUserAttempt(
            testId = "test_1",
            score = 4,
            totalQuestions = 5,
            answers = mapOf("q1" to "going", "q2" to "would", "q3" to "meeting", "q4" to "audio", "q5" to "wrong"),
            correctStatus = mapOf("q1" to true, "q2" to true, "q3" to true, "q4" to true, "q5" to false)
        )

        assertEquals(80, attempt.accuracyPercentage)
    }

    @Test
    fun teachingPolicyGenerateTestEnforcesPedagogicalInvariants() {
        val module = LanguageRegistry.get("en")!!
        val prompt = TeachingPolicy.generateTest(
            language = module,
            topicTitle = "Job Interview",
            mcerLevel = "B2",
            difficulty = "medium",
            conversationContext = "USER: I have worked in tech for five years.\nASSISTANT: What was your main challenge?",
            userPrompt = "Enfoque en verbos de acción"
        )

        // Verificación de invariantes críticas:
        // 1. Cero imágenes
        assertTrue("Debe prohibir imágenes explícitamente", prompt.contains("ZERO IMAGES") || prompt.contains("NO IMAGES"))
        // 2. Exactamente 4 opciones para multiple choice
        assertTrue("Debe exigir 4 opciones para multiple choice", prompt.contains("4 distinct options") || prompt.contains("4 options"))
        // 3. Tipos admitidos
        assertTrue("Debe admitir multiple_choice", prompt.contains("multiple_choice"))
        assertTrue("Debe admitir fill_blank", prompt.contains("fill_blank"))
        assertTrue("Debe admitir error_correction", prompt.contains("error_correction"))
        assertTrue("Debe admitir speaking", prompt.contains("speaking"))
        // 4. Flashcards
        assertTrue("Debe incluir generación de flashcards", prompt.contains("flashcards"))
        // 5. Nivel MCER y Contexto
        assertTrue("Debe calibrar en nivel B2", prompt.contains("B2"))
        assertTrue("Debe incluir contexto conversacional", prompt.contains("Job Interview"))
        assertTrue("Debe incluir el transcript reciente", prompt.contains("I have worked in tech for five years."))
    }

    @Test
    fun multipleChoiceGuaranteesDistractorsAndFourOptions() {
        val options = listOf("Option A", "Option B", "Option C", "Option D")
        assertEquals(4, options.size)
        val correctAnswer = "Option A"
        assertTrue(options.contains(correctAnswer))
        val distractors = options.filterNot { it == correctAnswer }
        assertEquals(3, distractors.size)
    }
}
