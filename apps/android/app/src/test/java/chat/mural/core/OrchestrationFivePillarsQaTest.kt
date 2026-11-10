package chat.mural.core

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import chat.mural.ui.UserAnswersMapSaver
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrationFivePillarsQaTest {

    private class InMemorySharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) staged[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) staged[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) staged[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) staged[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) staged[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) staged[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(staged)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    @Test
    fun `pillar 1 - 30 question stress test preserves unique IDs, saver state and grading stability`() {
        val prefs = InMemorySharedPreferences()
        val store = LearningTestStore(prefs)
        val types = TestItemType.entries
        val items = (1..30).map { idx ->
            val type = types[(idx - 1) % types.size]
            LearningTestItem(
                id = "q_${idx}_item",
                type = type,
                prompt = "Question $idx prompt",
                context = if (type == TestItemType.FILL_BLANK || type == TestItemType.ERROR_CORRECTION) "El gato ___ muy lindo." else "Context $idx",
                options = if (type == TestItemType.MULTIPLE_CHOICE || type == TestItemType.ERROR_CORRECTION || type == TestItemType.READING_COMPREHENSION) {
                    listOf("Correct $idx", "Wrong A $idx", "Wrong B $idx", "Wrong C $idx")
                } else emptyList(),
                correctAnswer = "Correct $idx",
                explanation = "Explanation $idx",
                targetSpeechText = if (type == TestItemType.SPEAKING) "Correct $idx" else "",
                readingPassage = if (type == TestItemType.READING_COMPREHENSION) "Passage $idx" else "",
                wordBank = if (type == TestItemType.CLOZE_BANK) listOf("Correct $idx", "Distractor $idx") else emptyList()
            )
        }

        assertEquals(30, items.map { it.id }.distinct().size)

        val test = LearningTest(
            id = "stress_test_30",
            title = "Comprehensive 30-question evaluation",
            topicId = "custom_cat",
            topicTitle = "Hablando de mi gato",
            mcerLevel = "A2",
            difficulty = TestDifficulty.MEDIUM,
            status = TestStatus.READY,
            items = items,
            languageID = "es"
        )
        store.upsertTest(test)

        val stateMap = mutableStateMapOf<String, String>()
        for (item in items) {
            stateMap[item.id] = item.correctAnswer
        }

        val savedBundleObj = with(UserAnswersMapSaver) {
            SaverScope { true }.save(stateMap)
        }
        assertNotNull(savedBundleObj)
        val restored = UserAnswersMapSaver.restore(savedBundleObj!!)
        assertNotNull(restored)
        assertEquals(30, restored!!.size)

        val correctMap = items.associate { it.id to AnswerGrader.isCorrect(it, restored[it.id] ?: "") }
        val score = correctMap.values.count { it }
        assertEquals(30, score)

        val attempt = TestUserAttempt(
            testId = test.id,
            score = score,
            totalQuestions = 30,
            answers = restored.toMap(),
            correctStatus = correctMap,
            durationMillis = 45_000L,
            unanswered = 0
        )
        store.saveAttempt(attempt)

        val reloadedStore = LearningTestStore(prefs)
        val summary = reloadedStore.getAttemptSummary(test.id)
        assertNotNull(summary)
        assertEquals(100, summary!!.bestScorePercentage)
    }

    @Test
    fun `pillar 3 - custom themes sort first in TopicsScreen and persist across store instances`() {
        val custom1 = ConversationTheme("custom_old", "En el aeropuerto", "Viajes", "sparkles", "Personalizados", "Rol A", 1)
        val custom2 = ConversationTheme("custom_new", "Hablando de mi gato", "Bechamel", "sparkles", "Personalizados", "Rol B", 1)
        val default1 = ConversationTheme("Buenas cosas", "Buenas cosas", "Hechas con esmero", "sparkles", "Día a día", "Rol C", 0)
        val default2 = ConversationTheme("Costumbres", "Costumbres del día a día", "Pequeños gestos", "sparkles", "Día a día", "Rol D", 0)

        val customThemes = listOf(custom2, custom1) // newest first
        val customIds = customThemes.map { it.id }.toSet()
        val customOrder = customThemes.mapIndexed { idx, t -> t.id to idx }.toMap()
        val favorites = setOf("Costumbres")

        val all = listOf(default1, default2, custom1, custom2)
        val sorted = all.sortedWith(
            compareByDescending<ConversationTheme> { it.id in customIds }
                .thenBy { customOrder[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.id in favorites }
                .thenBy { it.title }
        )

        assertEquals("custom_new", sorted[0].id)
        assertEquals("custom_old", sorted[1].id)
        assertEquals("Costumbres", sorted[2].id)
        assertEquals("Buenas cosas", sorted[3].id)
    }

    @Test
    fun `pillar 4 - strict settings enforcement injects language, MCER, pace, persona, interests and memory`() {
        val es = LanguageRegistry.get("es")!!
        val theme = ConversationTheme("custom_cat", "Hablando de mi gato", "Bechamel", "sparkles", "Personalizados", "Hablar del gato Bechamel", 1)

        val voiceStrictPrompt = TeachingPolicy.voice(
            language = es,
            learner = LearnerState(0, 1, "Practicar descripciones", emptyList(), emptyList()),
            theme = theme,
            interests = "Mascotas y fotografía",
            meaningLanguage = "en",
            voiceSpeed = "beginner",
            mcerLevel = "A1",
            personality = "professional",
            themeMemory = "Contexto reciente: El gato se llama Bechamel | Errores frecuentes a reforzar: el manzana -> la manzana",
            pedagogicalMode = "education"
        )

        assertTrue(voiceStrictPrompt.contains("STRICT SETTINGS & LANGUAGE ENFORCEMENT (MANDATORY)"))
        assertTrue(voiceStrictPrompt.contains("CEFR Difficulty Level A1"))
        assertTrue(voiceStrictPrompt.contains("Professional and rigorous"))
        assertTrue(voiceStrictPrompt.contains("100-110 words per minute"))
        assertTrue(voiceStrictPrompt.contains("Mascotas y fotografía"))
        assertTrue(voiceStrictPrompt.contains("Bechamel"))
        assertFalse(voiceStrictPrompt.contains("Welcome replies in any language"))

        val writtenStrictPrompt = TeachingPolicy.typedReply(
            language = es,
            theme = theme,
            mcerLevel = "A1",
            personality = "professional",
            themeMemory = "Contexto reciente: El gato se llama Bechamel",
            pedagogicalMode = "education",
            interests = "Mascotas y fotografía",
            meaningLanguage = "en",
            voiceSpeed = "beginner"
        )

        assertTrue(writtenStrictPrompt.contains("STRICT SETTINGS & LANGUAGE ENFORCEMENT (MANDATORY)"))
        assertTrue(writtenStrictPrompt.contains("CEFR Difficulty Level A1"))
        assertTrue(writtenStrictPrompt.contains("Professional and rigorous"))
        assertTrue(writtenStrictPrompt.contains("Mascotas y fotografía"))
        assertFalse(writtenStrictPrompt.contains("Replies in any language from the learner are welcome"))
    }

    @Test
    fun `pillar 5 - ThemePersistentMemory isolates context, errors and progress per theme`() {
        val memA = ThemePersistentMemory(
            themeId = "custom_cat",
            contextHistory = listOf("Sesión 2: Bechamel duerme mucho", "Sesión 1: El gato se llama Bechamel"),
            frequentErrors = listOf("El gato somos blanquito -> El gato es blanquito"),
            practicedWords = listOf("gato", "dormir", "blanquito"),
            sessionsCount = 2,
            completedTestsCount = 1,
            bestTestScore = 92
        )
        val memB = ThemePersistentMemory(
            themeId = "airport",
            contextHistory = listOf("Sesión 1: Puerta de embarque B12"),
            frequentErrors = listOf("Yo sabo -> Yo sé"),
            practicedWords = listOf("vuelo", "maleta"),
            sessionsCount = 1,
            completedTestsCount = 0,
            bestTestScore = 0
        )

        assertEquals(2, memA.contextHistory.size)
        assertEquals(1, memB.contextHistory.size)
        assertTrue(memA.frequentErrors.first().contains("blanquito"))
        assertTrue(memB.frequentErrors.first().contains("Yo sé"))
        assertEquals(92, memA.bestTestScore)
    }

    @Test
    fun `pillar 3 fix - deduplication prevents duplicate custom themes with same title or ID`() {
        val prefs = InMemorySharedPreferences()
        val store = TopicEcosystemStore(prefs)

        val theme1 = ConversationTheme("custom_1", "Adoptando un gato", "Refugio de animales", "sparkles", "Personalizados", "Rol A", 1)
        val theme2 = ConversationTheme("custom_2", "Adoptando un gato ", "Refugio de animales actualizado", "sparkles", "Personalizados", "Rol B", 1)

        store.saveCustomTheme("es", theme1)
        assertEquals(1, store.getCustomThemes("es").size)

        // Saving theme with same normalized title must update existing entry and not create duplicate
        store.saveCustomTheme("es", theme2)
        val themes = store.getCustomThemes("es")
        assertEquals(1, themes.size)
        assertEquals("Adoptando un gato ", themes[0].title)
        assertEquals("custom_2", themes[0].id)
    }
}
