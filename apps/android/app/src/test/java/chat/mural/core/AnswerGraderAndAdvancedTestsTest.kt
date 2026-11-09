package chat.mural.core

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import chat.mural.ui.UserAnswersMapSaver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AnswerGraderAndAdvancedTestsTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun answerGraderNormalizesPunctuationAndWhitespace() {
        val raw = "   The   Quick,  Brown Fox!  "
        val normalized = AnswerGrader.normalize(raw)
        assertEquals("the quick, brown fox", normalized)

        val rawWithPunctuation = "¿Cómo estás?..."
        val normAccents = AnswerGrader.normalize(rawWithPunctuation, stripAccents = true)
        assertEquals("como estas", normAccents)
    }

    @Test
    fun answerGraderEvaluatesMultipleChoiceCorrectly() {
        val item = LearningTestItem(
            id = "q1",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "Choose the correct preposition:",
            options = listOf("interested in", "interested at", "interested on", "interested with", "interested to"),
            correctAnswer = "interested in",
            explanation = "'Interested' is followed by 'in'."
        )

        assertTrue(AnswerGrader.isCorrect(item, "interested in"))
        assertTrue(AnswerGrader.isCorrect(item, "  INTERESTED IN.  "))
        assertFalse(AnswerGrader.isCorrect(item, "interested at"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun answerGraderEvaluatesFillBlankWithAccentAndPunctuationTolerance() {
        val item = LearningTestItem(
            id = "q2",
            type = TestItemType.FILL_BLANK,
            prompt = "Escribe el verbo:",
            correctAnswer = "está",
            explanation = "Tercera persona singular del verbo estar."
        )

        assertTrue(AnswerGrader.isCorrect(item, "está"))
        assertTrue(AnswerGrader.isCorrect(item, "esta")) // Tolerance for accent omission
        assertTrue(AnswerGrader.isCorrect(item, " está. "))
        assertFalse(AnswerGrader.isCorrect(item, "están"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun answerGraderEvaluatesSpeakingSimilarity() {
        val item = LearningTestItem(
            id = "q_speak",
            type = TestItemType.SPEAKING,
            prompt = "Di la frase en voz alta:",
            targetSpeechText = "I would like a cup of coffee please",
            correctAnswer = "I would like a cup of coffee please",
            explanation = "Pronunciación clara."
        )

        // Exact match
        assertTrue(AnswerGrader.isCorrect(item, "I would like a cup of coffee please"))
        // Minor missing word / slight recognition variation (>60% similarity)
        assertTrue(AnswerGrader.isCorrect(item, "I would like cup of coffee please"))
        // Blank speech or silence must be false!
        assertFalse(AnswerGrader.isCorrect(item, ""))
        assertFalse(AnswerGrader.isCorrect(item, "   "))
        // Completely wrong speech
        assertFalse(AnswerGrader.isCorrect(item, "yesterday was Monday"))
    }

    @Test
    fun answerGraderShufflesOptionsDeterministicallyWithoutLosingCorrectAnswer() {
        val item = LearningTestItem(
            id = "q_shuffle",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "Question",
            options = listOf("Correct", "Distractor 1", "Distractor 2", "Distractor 3", "Distractor 4"),
            correctAnswer = "Correct",
            explanation = "Expl"
        )

        val shuffled1 = AnswerGrader.shuffleOptions(item, seed = 12345L)
        val shuffled2 = AnswerGrader.shuffleOptions(item, seed = 12345L)

        assertEquals(shuffled1, shuffled2)
        assertEquals(5, shuffled1.size)
        assertTrue(shuffled1.contains("Correct"))
    }

    @Test
    fun testConfigSerializationAndDefaults() {
        val config = TestConfig(
            prompt = "Business English Vocabulary",
            languageID = "en",
            mcerLevel = "B2",
            allowedTypes = listOf(TestItemType.FILL_BLANK),
            questionCount = 10,
            timeLimitSeconds = 300,
            includeFlashcards = false
        )

        val serialized = json.encodeToString(config)
        val deserialized = json.decodeFromString<TestConfig>(serialized)

        assertEquals("Business English Vocabulary", deserialized.prompt)
        assertEquals("B2", deserialized.mcerLevel)
        assertEquals(1, deserialized.allowedTypes.size)
        assertEquals(TestItemType.FILL_BLANK, deserialized.allowedTypes.first())
        assertEquals(10, deserialized.questionCount)
        assertEquals(300, deserialized.timeLimitSeconds)
        assertFalse(deserialized.includeFlashcards)
    }

    @Test
    fun learningTestBackwardCompatibilityWithStatusAndConfig() {
        val legacyJson = """
            {
              "id": "test_legacy",
              "title": "Legacy Test",
              "topicTitle": "Topic",
              "mcerLevel": "A2",
              "difficulty": "LOW",
              "items": [],
              "flashcards": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString<LearningTest>(legacyJson)
        assertEquals(TestStatus.READY, decoded.status)
        assertNull(decoded.config)
        assertFalse(decoded.isFavorite)
        assertNull(decoded.errorMessage)
    }

    @Test
    fun teachingPolicyGenerateTestWithConfigEnforcesAllowedTypeAndCount() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Past Perfect Tense",
            languageID = "en",
            mcerLevel = "B1",
            allowedTypes = listOf(TestItemType.FILL_BLANK), // Strictly single type
            questionCount = 8,
            includeFlashcards = false
        )

        val prompt = TeachingPolicy.generateTest(
            language = module,
            config = config,
            avoidPrompts = listOf("I had already eaten before they arrived.")
        )

        // Verifications
        assertTrue(prompt.contains("EXACTLY 8 test items"))
        assertTrue(prompt.contains("fill_blank"))
        assertTrue(prompt.contains("CRITICAL: ALL generated items MUST be strictly of type \"fill_blank\""))
        assertTrue(prompt.contains("DO NOT REPEAT OR DUPLICATE"))
        assertTrue(prompt.contains("I had already eaten before they arrived."))
        assertTrue(prompt.contains("\"flashcards\": []"))
    }

    @Test
    fun learningTestStoreNoRecursionWhenAttemptsAbsent() {
        val fakePrefs = FakeSharedPreferences()
        val store = LearningTestStore(fakePrefs)

        // 1. Critical verification: testId without any stored attempts must NOT recurse or crash
        val testId = "test_non_existent"
        val attempts = store.getAttempts(testId)
        assertTrue(attempts.isEmpty())

        val latest = store.getLatestAttempt(testId)
        assertNull(latest)

        val summary = store.getAttemptSummary(testId)
        assertNull(summary)

        // 2. Add a newly generated placeholder test (as done when clicking ✨ Generar)
        val placeholder = LearningTest(
            id = "new_placeholder_test",
            title = "Prueba de Gramática",
            topicTitle = "Gramática",
            mcerLevel = "B1",
            difficulty = TestDifficulty.MEDIUM,
            status = TestStatus.GENERATING,
            items = emptyList(),
            flashcards = emptyList()
        )
        store.upsertTest(placeholder)

        // 3. Verifications called during refreshLearningTests() must execute instantaneously without StackOverflowError
        val allTests = store.getAllTests()
        assertEquals(1, allTests.size)
        assertEquals(TestStatus.GENERATING, allTests.first().status)

        val summaries = store.getAttemptSummaries()
        assertTrue(summaries.isEmpty())

        val activityDays = store.getActivityDaysThisWeek()
        assertEquals(0, activityDays)
    }

    @Test
    fun learningTestStoreLegacyAttemptMigrationFallback() {
        val fakePrefs = FakeSharedPreferences()
        val legacyAttempt = TestUserAttempt(
            testId = "legacy_test_456",
            score = 4,
            totalQuestions = 5,
            answers = mapOf("q1" to "a"),
            correctStatus = mapOf("q1" to true),
            completedAt = System.currentTimeMillis()
        )
        fakePrefs.edit()
            .putString("test_attempt_legacy_test_456", json.encodeToString(legacyAttempt))
            .apply()

        val store = LearningTestStore(fakePrefs)

        // Must read legacy attempt seamlessly
        val attempts = store.getAttempts("legacy_test_456")
        assertEquals(1, attempts.size)
        assertEquals(4, attempts.first().score)
        assertEquals(80, attempts.first().accuracyPercentage)

        val latest = store.getLatestAttempt("legacy_test_456")
        assertNotNull(latest)
        assertEquals(4, latest?.score)

        val summary = store.getAttemptSummary("legacy_test_456")
        assertNotNull(summary)
        assertEquals(1, summary?.attemptsCount)
        assertEquals(80, summary?.bestScorePercentage)
        assertEquals(80, summary?.latestScorePercentage)
    }

    @Test
    fun learningTestStoreSaveAndRetrieveAttempts() {
        val fakePrefs = FakeSharedPreferences()
        val store = LearningTestStore(fakePrefs)
        val testId = "test_eval_789"

        val attempt1 = TestUserAttempt(
            testId = testId,
            score = 3,
            totalQuestions = 5,
            answers = emptyMap(),
            correctStatus = emptyMap(),
            completedAt = 1000L
        )
        val attempt2 = TestUserAttempt(
            testId = testId,
            score = 5,
            totalQuestions = 5,
            answers = emptyMap(),
            correctStatus = emptyMap(),
            completedAt = 2000L
        )

        store.saveAttempt(attempt1)
        store.saveAttempt(attempt2)

        val attempts = store.getAttempts(testId)
        assertEquals(2, attempts.size)
        assertEquals(attempt2.completedAt, attempts[0].completedAt)
        assertEquals(attempt1.completedAt, attempts[1].completedAt)

        val latest = store.getLatestAttempt(testId)
        assertEquals(attempt2.completedAt, latest?.completedAt)

        val summary = store.getAttemptSummary(testId)
        assertNotNull(summary)
        assertEquals(2, summary?.attemptsCount)
        assertEquals(100, summary?.bestScorePercentage)
        assertEquals(100, summary?.latestScorePercentage)
    }

    @Test
    fun learningTestStoreCleanupOrphanGeneratingTests() {
        val fakePrefs = FakeSharedPreferences()
        val generatingTest = LearningTest(
            id = "orphan_1",
            title = "Test Interrumpido",
            topicTitle = "Interrumpido",
            mcerLevel = "A2",
            difficulty = TestDifficulty.MEDIUM,
            status = TestStatus.GENERATING,
            items = emptyList(),
            flashcards = emptyList(),
            languageID = "en"
        )
        fakePrefs.edit()
            .putString("learning_tests_en", json.encodeToString(listOf(generatingTest)))
            .apply()

        // When a new LearningTestStore initializes, init { cleanupOrphanGeneratingTests() } runs
        val store = LearningTestStore(fakePrefs)
        val tests = store.getTests("en")
        assertEquals(1, tests.size)
        assertEquals(TestStatus.FAILED, tests.first().status)
        assertTrue(tests.first().errorMessage?.contains("interrumpió") == true)
    }

    @Test
    fun userAnswersMapSaverSerializesAndRestoresCorrectlyWithoutCrash() {
        val original = mutableStateMapOf<String, String>()
        original["q1"] = "Option A"
        original["q2"] = "Option B"
        original["q3"] = "Custom answer text"

        // Saver.save must return a java.io.Serializable object (HashMap) acceptable by Android Bundle
        val saved = with(UserAnswersMapSaver) {
            val scope = object : androidx.compose.runtime.saveable.SaverScope {
                override fun canBeSaved(value: Any): Boolean = true
            }
            scope.save(original)
        }

        assertNotNull(saved)
        assertTrue("Saved object must be a java.util.HashMap (Serializable) for Bundle compatibility", saved is HashMap<*, *>)
        val map = saved as HashMap<*, *>
        assertEquals(3, map.size)
        assertEquals("Option A", map["q1"])
        assertEquals("Option B", map["q2"])
        assertEquals("Custom answer text", map["q3"])

        // Saver.restore must reconstruct a SnapshotStateMap with exact entries
        val restored = UserAnswersMapSaver.restore(saved)
        assertNotNull(restored)
        assertEquals(3, restored?.size)
        assertEquals("Option A", restored?.get("q1"))
        assertEquals("Option B", restored?.get("q2"))
        assertEquals("Custom answer text", restored?.get("q3"))
    }

    @Test
    fun testConfigWithTopicIdAndThemeTitle() {
        val theme = ConversationTheme(
            id = "ordering_food",
            title = "Pedir comida en un restaurante",
            subtitle = "Vocabulario de menú y pedidos",
            symbol = "food",
            category = "Comida",
            situation = "Estás en un restaurante",
            colorIndex = 1
        )

        val config = TestConfig(
            prompt = theme.title,
            topicId = theme.id,
            languageID = "es",
            mcerLevel = "B1",
            allowedTypes = listOf(TestItemType.MULTIPLE_CHOICE, TestItemType.FILL_BLANK),
            questionCount = 10,
            includeFlashcards = true,
            randomMode = false
        )

        assertEquals("Pedir comida en un restaurante", config.prompt)
        assertEquals("ordering_food", config.topicId)
        assertEquals("es", config.languageID)
        assertEquals(10, config.questionCount)
        assertFalse(config.randomMode)
    }

    @Test
    fun learningTestLanguageFilteringMatchesLanguagesProperly() {
        val testEn = LearningTest(
            id = "t_en",
            title = "English Test",
            topicTitle = "Vocabulary",
            mcerLevel = "B1",
            difficulty = TestDifficulty.MEDIUM,
            status = TestStatus.READY,
            languageID = "en"
        )
        val testEs = LearningTest(
            id = "t_es",
            title = "Prueba de Español",
            topicTitle = "Gramática",
            mcerLevel = "A2",
            difficulty = TestDifficulty.LOW,
            status = TestStatus.READY,
            languageID = "es"
        )
        val testQu = LearningTest(
            id = "t_qu",
            title = "Quechua Simi",
            topicTitle = "Rimaykuna",
            mcerLevel = "A1",
            difficulty = TestDifficulty.LOW,
            status = TestStatus.READY,
            languageID = "qu"
        )

        val allTests = listOf(testEn, testEs, testQu)

        // Empty filter matches all
        val filterEmpty = setOf<String>()
        val resultAll = allTests.filter { filterEmpty.isEmpty() || filterEmpty.contains(it.languageID.lowercase()) }
        assertEquals(3, resultAll.size)

        // Filter by English
        val filterEn = setOf("en")
        val resultEn = allTests.filter { filterEn.isEmpty() || filterEn.contains(it.languageID.lowercase()) }
        assertEquals(1, resultEn.size)
        assertEquals("t_en", resultEn.first().id)

        // Filter by Spanish and Quechua
        val filterEsQu = setOf("es", "qu")
        val resultEsQu = allTests.filter { filterEsQu.isEmpty() || filterEsQu.contains(it.languageID.lowercase()) }
        assertEquals(2, resultEsQu.size)
        assertTrue(resultEsQu.any { it.id == "t_es" })
        assertTrue(resultEsQu.any { it.id == "t_qu" })
    }

    @Test
    fun testTypeFilterAllSixTypesPresentAndMatchCorrectly() {
        // Verify all 6 filter types + ALL exist in enum
        val filters = TestTypeFilter.entries
        assertTrue(filters.contains(TestTypeFilter.ALL))
        assertTrue(filters.contains(TestTypeFilter.MULTIPLE_CHOICE))
        assertTrue(filters.contains(TestTypeFilter.FILL_BLANK))
        assertTrue(filters.contains(TestTypeFilter.ERROR_CORRECTION))
        assertTrue(filters.contains(TestTypeFilter.SPEAKING))
        assertTrue(filters.contains(TestTypeFilter.FLASHCARDS))
        assertTrue(filters.contains(TestTypeFilter.MIXED))

        // Create representative test instances
        val mcItem = LearningTestItem("1", TestItemType.MULTIPLE_CHOICE, "prompt", "", listOf("a", "b", "c", "d", "e"), "a", "exp")
        val fillItem = LearningTestItem("2", TestItemType.FILL_BLANK, "prompt", "", emptyList(), "answer", "exp")
        val errorItem = LearningTestItem("3", TestItemType.ERROR_CORRECTION, "prompt", "", emptyList(), "correct", "exp")
        val speakingItem = LearningTestItem("4", TestItemType.SPEAKING, "prompt", "", emptyList(), "target", "exp")
        val cardItem = FlashcardItem("fc1", "front", "back", "ex")

        val mcTest = LearningTest("t_mc", "MC Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = listOf(mcItem))
        val fillTest = LearningTest("t_fill", "Fill Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = listOf(fillItem))
        val errorTest = LearningTest("t_err", "Err Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = listOf(errorItem))
        val speakingTest = LearningTest("t_spk", "Spk Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = listOf(speakingItem))
        val flashcardsTest = LearningTest("t_fc", "FC Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = emptyList(), flashcards = listOf(cardItem))
        val mixedTest = LearningTest("t_mix", "Mixed Test", null, "Topic", "B1", TestDifficulty.MEDIUM, items = listOf(mcItem, fillItem))

        fun matchesType(test: LearningTest, selectedTypes: Set<String>): Boolean {
            if (selectedTypes.isEmpty()) return true
            return selectedTypes.any { typeKey ->
                when (typeKey) {
                    "multiple_choice" -> test.items.any { it.type == TestItemType.MULTIPLE_CHOICE }
                    "fill_blank" -> test.items.any { it.type == TestItemType.FILL_BLANK }
                    "error_correction" -> test.items.any { it.type == TestItemType.ERROR_CORRECTION }
                    "speaking" -> test.items.any { it.type == TestItemType.SPEAKING }
                    "flashcards" -> test.flashcards.isNotEmpty()
                    "mixed" -> test.items.map { it.type }.distinct().size > 1
                    else -> false
                }
            }
        }

        assertTrue(matchesType(mcTest, setOf("multiple_choice")))
        assertFalse(matchesType(mcTest, setOf("fill_blank")))

        assertTrue(matchesType(fillTest, setOf("fill_blank")))
        assertTrue(matchesType(errorTest, setOf("error_correction")))
        assertTrue(matchesType(speakingTest, setOf("speaking")))
        assertTrue(matchesType(flashcardsTest, setOf("flashcards")))

        // Mixed test matches both mixed and individual items present in it
        assertTrue(matchesType(mixedTest, setOf("mixed")))
        assertFalse(matchesType(mcTest, setOf("mixed")))
        assertFalse(matchesType(flashcardsTest, setOf("mixed")))
    }

    @Test
    fun testConfigFlashcardsOnlyGeneratesCorrectPrompt() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Phrasal Verbs",
            languageID = "en",
            mcerLevel = "B2",
            allowedTypes = emptyList(),
            questionCount = 0,
            includeFlashcards = true
        )

        val prompt = TeachingPolicy.generateTest(
            language = module,
            config = config
        )

        // Verifications for pure flashcards mode
        assertTrue(prompt.contains("CRITICAL: Pure flashcards deck requested. Set \"items\": [] as an empty array."))
        assertTrue(prompt.contains("Generate 0 test items (set \"items\": [] as an empty array)."))
        assertTrue(prompt.contains("Generate 8 to 12 \"flashcards\" for quick training:"))
        assertFalse(prompt.contains("CRITICAL: ALL generated items MUST be strictly of type"))
    }

    @Test
    fun flashcardsSessionAttemptScoreAndMasteryMapping() {
        val cards = listOf(
            FlashcardItem("f1", "Card 1", "Back 1", "Ex 1"),
            FlashcardItem("f2", "Card 2", "Back 2", "Ex 2"),
            FlashcardItem("f3", "Card 3", "Back 3", "Ex 3"),
            FlashcardItem("f4", "Card 4", "Back 4", "Ex 4")
        )

        // Simulate user mastered f1, f2, f3, and marked f4 for review
        val masteredIds = setOf("f1", "f2", "f3")
        val statusMap = cards.associate { it.id to masteredIds.contains(it.id) }
        val answersMap = cards.associate { it.id to if (masteredIds.contains(it.id)) "Dominada" else "Por repasar" }

        val attempt = TestUserAttempt(
            testId = "fc_test_100",
            score = masteredIds.size,
            totalQuestions = cards.size,
            answers = answersMap,
            correctStatus = statusMap,
            durationMillis = 15000L,
            unanswered = 0
        )

        assertEquals(3, attempt.score)
        assertEquals(4, attempt.totalQuestions)
        assertEquals(75, attempt.accuracyPercentage)
        assertTrue(attempt.correctStatus["f1"] == true)
        assertTrue(attempt.correctStatus["f2"] == true)
        assertTrue(attempt.correctStatus["f3"] == true)
        assertTrue(attempt.correctStatus["f4"] == false)
        assertEquals("Dominada", attempt.answers["f1"])
        assertEquals("Por repasar", attempt.answers["f4"])
    }

    @Test
    fun flashcardsLinearAdvancementOnReviewAndMaster() {
        val cards = listOf(
            FlashcardItem("f1", "Card 1", "Back 1", "Ex 1"),
            FlashcardItem("f2", "Card 2", "Back 2", "Ex 2")
        )

        var currentIndex = 0
        val masteredList = mutableListOf<String>()
        val reviewList = mutableListOf<String>()
        var isCompleted = false

        fun advance(isMaster: Boolean) {
            val card = cards[currentIndex]
            if (isMaster) {
                masteredList.add(card.id)
                reviewList.remove(card.id)
            } else {
                reviewList.add(card.id)
                masteredList.remove(card.id)
            }
            if (currentIndex + 1 < cards.size) {
                currentIndex++
            } else {
                isCompleted = true
            }
        }

        // Action on Card 1: Review
        advance(isMaster = false)
        assertEquals(1, currentIndex)
        assertFalse(isCompleted)
        assertEquals(listOf("f1"), reviewList)
        assertTrue(masteredList.isEmpty())

        // Action on Card 2 (last card): Master
        advance(isMaster = true)
        assertTrue(isCompleted)
        assertEquals(listOf("f2"), masteredList)
        assertEquals(listOf("f1"), reviewList)
    }

    @Test
    fun answerGraderFormatsTimeRemainingCorrectly() {
        assertEquals("05:00", AnswerGrader.formatTimeRemaining(300))
        assertEquals("01:05", AnswerGrader.formatTimeRemaining(65))
        assertEquals("00:59", AnswerGrader.formatTimeRemaining(59))
        assertEquals("00:09", AnswerGrader.formatTimeRemaining(9))
        assertEquals("00:00", AnswerGrader.formatTimeRemaining(0))
        assertEquals("00:00", AnswerGrader.formatTimeRemaining(-10))
    }

    @Test
    fun answerGraderEvaluatesClozeBankCorrectly() {
        val item = LearningTestItem(
            id = "cloze_1",
            type = TestItemType.CLOZE_BANK,
            prompt = "Yesterday she ___ to the supermarket.",
            correctAnswer = "went",
            wordBank = listOf("went", "goes", "go", "gone"),
            explanation = "Past simple of go."
        )

        assertTrue(AnswerGrader.isCorrect(item, "went"))
        assertTrue(AnswerGrader.isCorrect(item, " WENT "))
        assertFalse(AnswerGrader.isCorrect(item, "goes"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun answerGraderEvaluatesReadingComprehensionCorrectly() {
        val item = LearningTestItem(
            id = "reading_1",
            type = TestItemType.READING_COMPREHENSION,
            prompt = "Where did Lucas go after lunch?",
            readingPassage = "Lucas had a quick lunch at the cafe. Afterwards, he visited the nearby museum before heading to the train station.",
            options = listOf("To the library", "To the museum", "To the gym", "To his office"),
            correctAnswer = "To the museum",
            explanation = "The passage states he visited the nearby museum."
        )

        assertTrue(AnswerGrader.isCorrect(item, "To the museum"))
        assertTrue(AnswerGrader.isCorrect(item, "to the museum"))
        assertFalse(AnswerGrader.isCorrect(item, "To the library"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun answerGraderEvaluatesErrorCorrectionWithSpecificIncorrectWord() {
        val item = LearningTestItem(
            id = "error_1",
            type = TestItemType.ERROR_CORRECTION,
            prompt = "Detecta el error y escribe la forma correcta:",
            context = "She do not like spicy food.",
            incorrectWord = "do",
            correctAnswer = "does",
            explanation = "Con 'she' se usa 'does not' o 'doesn't'."
        )

        assertTrue(AnswerGrader.isCorrect(item, "does"))
        assertTrue(AnswerGrader.isCorrect(item, " DOES "))
        assertFalse(AnswerGrader.isCorrect(item, "do"))
        assertFalse(AnswerGrader.isCorrect(item, "is"))
    }

    @Test
    fun teachingPolicyPostConversationPromptConfigures25QuestionsAcross5Types() {
        val conversationText = "Learner: Hi, I want to book a table for two tonight.\nTutor: Certainly! At what time would you like your reservation?\nLearner: Around 8 pm please."
        val prompt = TeachingPolicy.generatePostConversationTest(
            language = LanguageRegistry.supported.first { it.id == "en" },
            sessionContext = conversationText,
            mcerLevel = "B1",
            themeTitle = "Restaurante"
        )

        assertTrue(prompt.contains("25") && (prompt.contains("questions") || prompt.contains("test items") || prompt.contains("EXACTLY 25")))
        assertTrue(prompt.contains("cloze_bank"))
        assertTrue(prompt.contains("reading_comprehension"))
        assertTrue(prompt.contains("multiple_choice"))
        assertTrue(prompt.contains("error_correction"))
        assertTrue(prompt.contains("speaking"))
        assertTrue(prompt.contains("readingPassage"))
        assertTrue(prompt.contains("wordBank"))
    }

    @Test
    fun testSerializationSupportsClozeBankAndReadingComprehension() {
        val original = LearningTest(
            id = "test_adv_1",
            title = "Prueba Integral",
            topicTitle = "Restaurante",
            languageID = "en",
            mcerLevel = "B1",
            difficulty = TestDifficulty.MEDIUM,
            config = TestConfig(timeLimitSeconds = 600),
            items = listOf(
                LearningTestItem(
                    id = "i1",
                    type = TestItemType.CLOZE_BANK,
                    prompt = "She ___ a glass of water.",
                    correctAnswer = "ordered",
                    explanation = "Past simple of order.",
                    wordBank = listOf("ordered", "order", "ordering")
                ),
                LearningTestItem(
                    id = "i2",
                    type = TestItemType.READING_COMPREHENSION,
                    prompt = "¿Qué pidió?",
                    readingPassage = "María ordered pasta and red wine.",
                    options = listOf("Pasta", "Pizza", "Salad"),
                    correctAnswer = "Pasta",
                    explanation = "Direct reference from text."
                )
            )
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<LearningTest>(serialized)

        assertEquals(2, deserialized.items.size)
        assertEquals(TestItemType.CLOZE_BANK, deserialized.items[0].type)
        assertEquals(listOf("ordered", "order", "ordering"), deserialized.items[0].wordBank)
        assertEquals(TestItemType.READING_COMPREHENSION, deserialized.items[1].type)
        assertEquals("María ordered pasta and red wine.", deserialized.items[1].readingPassage)
        assertEquals(600, deserialized.config?.timeLimitSeconds)
    }

    @Test
    fun readingComprehensionEvaluatesWrittenAnswerWithNormalization() {
        val item = LearningTestItem(
            id = "rc_write",
            type = TestItemType.READING_COMPREHENSION,
            prompt = "Based on the text, what did Liam say he needed?",
            readingPassage = "Liam told Maya that he urgently needed a new laptop charger.",
            options = emptyList(),
            correctAnswer = "a new laptop charger",
            explanation = "Liam explicitly stated he needed a new laptop charger."
        )

        // Exact match
        assertTrue(AnswerGrader.isCorrect(item, "a new laptop charger"))
        // Case and punctuation tolerance
        assertTrue(AnswerGrader.isCorrect(item, "  A NEW LAPTOP CHARGER. "))
        // Wrong answer
        assertFalse(AnswerGrader.isCorrect(item, "a phone"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun readingComprehensionEvaluatesMultipleChoiceWithFourOptions() {
        val item = LearningTestItem(
            id = "rc_mcq",
            type = TestItemType.READING_COMPREHENSION,
            prompt = "Why was Sophie excited?",
            readingPassage = "Sophie received a promotion at work this morning.",
            options = listOf("She got a promotion", "She bought a car", "She traveled to Rome", "She won the lottery"),
            correctAnswer = "She got a promotion",
            explanation = "Sophie was promoted."
        )

        assertEquals(4, item.options.size)
        assertTrue(AnswerGrader.isCorrect(item, "She got a promotion"))
        assertTrue(AnswerGrader.isCorrect(item, "she got a promotion"))
        assertFalse(AnswerGrader.isCorrect(item, "She bought a car"))
    }

    @Test
    fun errorCorrectionWithoutAsterisksEvaluatesCorrectly() {
        val item = LearningTestItem(
            id = "ec_clean",
            type = TestItemType.ERROR_CORRECTION,
            prompt = "Identify and write the correct word:",
            context = "Yesterday morning while we walked through the city center, my friend tell me an incredible secret.",
            correctAnswer = "told",
            incorrectWord = "tell",
            explanation = "'tell' must be in simple past 'told'."
        )

        assertFalse(item.context.contains("*"))
        assertTrue(AnswerGrader.isCorrect(item, "told"))
        assertTrue(AnswerGrader.isCorrect(item, " TOLD. "))
        assertFalse(AnswerGrader.isCorrect(item, "tell"))
    }

    @Test
    fun teachingPolicyPromptEnforcesEnglishInstructionsAndFourOptionsAndDualReading() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Environmental Science Conversation",
            languageID = "en",
            mcerLevel = "B2",
            allowedTypes = listOf(
                TestItemType.READING_COMPREHENSION,
                TestItemType.MULTIPLE_CHOICE,
                TestItemType.ERROR_CORRECTION
            ),
            questionCount = 15
        )

        val prompt = TeachingPolicy.generateTest(
            language = module,
            config = config
        )

        assertTrue(prompt.contains("4 distinct options") || prompt.contains("exactly 4 options") || prompt.contains("4 options"))
        assertTrue(prompt.contains("NO asterisks") || prompt.contains("clean, NO asterisks"))
        assertTrue(prompt.contains("Large reading") || prompt.contains("Lectura Grande"))
        assertTrue(prompt.contains("Standard reading") || prompt.contains("2 to 4 sentences"))
        assertTrue(prompt.contains("NO oral/speaking challenges in reading comprehension") || prompt.contains("NO oral"))
    }

    @Test
    fun answerGraderEvaluatesErrorCorrectionWithOptionsAsMultipleChoice() {
        val item = LearningTestItem(
            id = "err_mcq",
            type = TestItemType.ERROR_CORRECTION,
            prompt = "Which of the following sentences contains a grammatical error?",
            options = listOf(
                "She works hard every day.",
                "They goes to the cinema on Fridays.",
                "We have finished our homework.",
                "I like learning English."
            ),
            correctAnswer = "They goes to the cinema on Fridays.",
            explanation = "Subject-verb agreement error: 'they go' instead of 'they goes'."
        )

        // User marks the sentence with the error
        assertTrue(AnswerGrader.isCorrect(item, "They goes to the cinema on Fridays."))
        assertTrue(AnswerGrader.isCorrect(item, "  they goes to the cinema on fridays.  "))
        // User selects a correct sentence (wrong answer for error-spotting)
        assertFalse(AnswerGrader.isCorrect(item, "She works hard every day."))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun answerGraderEvaluatesErrorCorrectionWithoutOptionsAsTextCorrection() {
        val item = LearningTestItem(
            id = "err_inline",
            type = TestItemType.ERROR_CORRECTION,
            prompt = "Complete the sentence with the correct word to fix the mistake:",
            context = "She forgot her keys ___ the table.",
            options = emptyList(),
            correctAnswer = "on",
            explanation = "'On the table' is the correct preposition."
        )

        assertTrue(AnswerGrader.isCorrect(item, "on"))
        assertTrue(AnswerGrader.isCorrect(item, "  ON  "))
        assertFalse(AnswerGrader.isCorrect(item, "in"))
        assertFalse(AnswerGrader.isCorrect(item, ""))
    }

    @Test
    fun teachingPolicyPromptSpecificationDemandsExplicitQuestionsInTestLanguage() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Past simple vs past continuous",
            languageID = "en",
            mcerLevel = "B1",
            allowedTypes = listOf(TestItemType.ERROR_CORRECTION, TestItemType.READING_COMPREHENSION),
            questionCount = 10
        )
        val prompt = TeachingPolicy.generateTest(module, config)

        // Check error correction formats are explicitly defined
        assertTrue(prompt.contains("Format A (Marking / Opción múltiple)"))
        assertTrue(prompt.contains("Format B (Inline sentence completion"))
        // Check reading comprehension explicit question invariant
        assertTrue(prompt.contains("CRITICAL EXPLICIT QUESTION INVARIANT"))
        assertTrue(prompt.contains("NEVER use generic placeholders like \"Based on the text, choose the correct option:\""))
    }

    @Test
    fun testSessionLanguageStringsProvidesAccurateTranslationsForLocales() {
        val enStrings = chat.mural.ui.getTestSessionStrings("en")
        assertEquals("Reading passage", enStrings.readingPassageLabel)
        assertEquals("Question 5 of 10", enStrings.formatQuestionCounter(5, 10))
        assertEquals("Type your answer…", enStrings.fillBlankPlaceholder)
        assertEquals("Type the correction…", enStrings.errorCorrectionPlaceholder)

        val esStrings = chat.mural.ui.getTestSessionStrings("es")
        assertEquals("Lectura base", esStrings.readingPassageLabel)
        assertEquals("Pregunta 5 de 10", esStrings.formatQuestionCounter(5, 10))
        assertEquals("Escribe tu respuesta…", esStrings.fillBlankPlaceholder)

        val frStrings = chat.mural.ui.getTestSessionStrings("fr")
        assertEquals("Texte de lecture", frStrings.readingPassageLabel)
        assertEquals("Question 5 sur 10", frStrings.formatQuestionCounter(5, 10))
        assertEquals("Écris ta réponse…", frStrings.fillBlankPlaceholder)

        val deStrings = chat.mural.ui.getTestSessionStrings("de")
        assertEquals("Lesetext", deStrings.readingPassageLabel)
        assertEquals("Frage 5 von 10", deStrings.formatQuestionCounter(5, 10))
    }

    @Test
    fun teachingPolicyMixedPromptEnforcesExactBreakdownAndMandatoryCount() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Business English Vocabulary and Situations",
            languageID = "en",
            mcerLevel = "B2",
            allowedTypes = listOf(
                TestItemType.READING_COMPREHENSION,
                TestItemType.CLOZE_BANK,
                TestItemType.MULTIPLE_CHOICE,
                TestItemType.ERROR_CORRECTION,
                TestItemType.SPEAKING
            ),
            questionCount = 20
        )
        val prompt = TeachingPolicy.generateTest(module, config)

        assertTrue(prompt.contains("CRITICAL COMPREHENSIVE 20-QUESTION SPECIFICATION:"))
        assertTrue(prompt.contains("Exactly 4 items of type \"reading_comprehension\""))
        assertTrue(prompt.contains("Exactly 4 items of type \"cloze_bank\""))
        assertTrue(prompt.contains("Exactly 4 items of type \"multiple_choice\""))
        assertTrue(prompt.contains("Exactly 4 items of type \"error_correction\""))
        assertTrue(prompt.contains("Exactly 4 items of type \"speaking\""))
        assertTrue(prompt.contains("CRITICAL MANDATORY QUANTITY INVARIANT: You MUST generate EXACTLY 20 test items in the \"items\" array."))
        assertTrue(prompt.contains("STRICT MCER LEVEL CALIBRATION (B2):"))
    }

    @Test
    fun teachingPolicySingleTypeEnforcesExactCount() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Irregular Verbs Drill",
            languageID = "en",
            mcerLevel = "A2",
            allowedTypes = listOf(TestItemType.MULTIPLE_CHOICE),
            questionCount = 15
        )
        val prompt = TeachingPolicy.generateTest(module, config)

        assertTrue(prompt.contains("CRITICAL: ALL generated items MUST be strictly of type \"multiple_choice\"."))
        assertTrue(prompt.contains("CRITICAL MANDATORY QUANTITY INVARIANT: You MUST generate EXACTLY 15 test items in the \"items\" array."))
        assertTrue(prompt.contains("STRICT MCER LEVEL CALIBRATION (A2):"))
    }

    @Test
    fun teachingPolicyPureFlashcardsRespectsSelectedQuantity() {
        val module = LanguageRegistry.get("en")!!
        val config = TestConfig(
            prompt = "Business Vocabulary",
            languageID = "en",
            mcerLevel = "B1",
            allowedTypes = emptyList(),
            questionCount = 10,
            includeFlashcards = true
        )
        val prompt = TeachingPolicy.generateTest(module, config)

        assertTrue("Debe especificar EXACTLY 10 flashcards", prompt.contains("Generate EXACTLY 10 \"flashcards\""))
        assertTrue("No debe generar items regulares", prompt.contains("Generate 0 test items"))
    }

    @Test
    fun teachingPolicyConversationGroundingEnforcedWhenContextProvided() {
        val module = LanguageRegistry.get("en")!!
        val transcript = "USER [f1] [SPOKEN]: Can I order a croissant and an espresso?\nASSISTANT [f2] [SPOKEN]: Sure, that will be four euros."
        val prompt = TeachingPolicy.generatePostConversationTest(
            language = module,
            sessionContext = transcript,
            mcerLevel = "A2",
            themeTitle = "Ordering at a French Bakery"
        )

        assertTrue("Debe incluir la invariante de conversation grounding", prompt.contains("CRITICAL MANDATORY INVARIANT — CONVERSATION GROUNDING"))
        assertTrue("Debe mencionar el tema específico de la conversación", prompt.contains("Ordering at a French Bakery"))
        assertTrue("Debe incluir el transcript de la conversación", prompt.contains("Can I order a croissant and an espresso?"))
        assertTrue("Debe exigir 100% de preguntas derivadas de la conversación", prompt.contains("derived directly and exclusively from the specific theme"))
        assertTrue("Debe exigir 25 preguntas", prompt.contains("CRITICAL MANDATORY QUANTITY INVARIANT: You MUST generate EXACTLY 25 test items"))
    }
}

@Suppress("UNCHECKED_CAST")
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(map)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val parentMap: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val uncommitted = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) uncommitted[key] = null
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) parentMap.clear()
            for ((k, v) in uncommitted) {
                if (v == null) parentMap.remove(k)
                else parentMap[k] = v
            }
        }
    }
}
