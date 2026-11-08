package chat.mural.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsianLanguagesAndAppLanguageTest {

    @Test
    fun languageRegistryPreservesEightBaseLanguagesAndExposesNewLanguagesInSupported() {
        // Strict regression invariant: LanguageRegistry.all must contain exactly the 8 base languages
        assertEquals(
            listOf("nb", "es", "en", "fr", "de", "it", "pt", "zh"),
            LanguageRegistry.all.map { it.id }
        )

        // Additional languages must include Korean, Japanese, Hindi
        val additionalIds = LanguageRegistry.additionalLanguages.map { it.id }
        assertTrue(additionalIds.containsAll(listOf("ko", "ja", "hi")))

        // Supported must include base + originarias + additional
        val supportedIds = LanguageRegistry.supported.map { it.id }
        assertTrue(supportedIds.containsAll(listOf("nb", "es", "en", "fr", "de", "it", "pt", "zh", "qu", "ko", "ja", "hi")))
    }

    @Test
    fun newLanguagesAreRetrievableWithCorrectMetadata() {
        val ko = LanguageRegistry.get("ko")
        assertNotNull(ko)
        assertEquals("Korean", ko?.name)
        assertEquals("한국어", ko?.nativeName)
        assertEquals("ko-KR", ko?.locale)
        assertEquals("안녕하세요!", ko?.greeting)
        assertEquals(6, ko?.teachingFocus?.size)
        assertEquals(5, ko?.themeOverrides?.size)

        val ja = LanguageRegistry.get("ja")
        assertNotNull(ja)
        assertEquals("Japanese", ja?.name)
        assertEquals("日本語", ja?.nativeName)
        assertEquals("ja-JP", ja?.locale)
        assertEquals("こんにちは！", ja?.greeting)
        assertEquals(6, ja?.teachingFocus?.size)
        assertEquals(5, ja?.themeOverrides?.size)

        val hi = LanguageRegistry.get("hi")
        assertNotNull(hi)
        assertEquals("Hindi", hi?.name)
        assertEquals("हिन्दी", hi?.nativeName)
        assertEquals("hi-IN", hi?.locale)
        assertEquals("नमस्ते!", hi?.greeting)
        assertEquals(6, hi?.teachingFocus?.size)
        assertEquals(5, hi?.themeOverrides?.size)
    }

    @Test
    fun meaningLanguagesSupportNewLanguages() {
        assertTrue(MeaningLanguages.all.containsAll(listOf("Korean", "Japanese", "Hindi", "Quechua Colla")))
        assertEquals("안녕하세요!", MeaningLanguages.greeting("Korean"))
        assertEquals("こんにちは！", MeaningLanguages.greeting("Japanese"))
        assertEquals("नमस्ते!", MeaningLanguages.greeting("Hindi"))
        assertEquals("¡Allillanchu!", MeaningLanguages.greeting("Quechua Colla"))
    }

    @Test
    fun appLanguagesCatalogProvidesPredefinedLanguages() {
        val supportedAppLanguages = listOf("es", "en", "ko", "ja", "hi", "fr", "de", "it", "pt", "zh", "nb", "qu")
        val appLanguageIds = AppLanguages.all.map { it.id }
        assertEquals(12, AppLanguages.all.size)
        assertTrue(appLanguageIds.containsAll(supportedAppLanguages))

        assertEquals("Spanish", AppLanguages.get("es").name)
        assertEquals("Korean", AppLanguages.get("ko").name)
        assertEquals("한국어", AppLanguages.get("ko").nativeName)
        assertEquals("Japanese", AppLanguages.get("ja").name)
        assertEquals("日本語", AppLanguages.get("ja").nativeName)
        assertEquals("Hindi", AppLanguages.get("hi").name)
        assertEquals("हिन्दी", AppLanguages.get("hi").nativeName)
        assertEquals("Quechua Colla", AppLanguages.get("qu").name)
        assertEquals("Runa Simi", AppLanguages.get("qu").nativeName)

        // Verify Quechua Colla displayName and settingsTitle in LanguageRegistry
        val qu = LanguageRegistry.get("qu")
        assertNotNull(qu)
        assertEquals("Quechua", qu?.name)
        assertEquals("Quechua Colla", qu?.displayName)
        assertEquals("Quechua Colla · Cusco-Collao", qu?.settingsTitle)
        assertTrue(qu!!.speechGuidance.contains("Quechua Colla"))
        assertTrue(qu.writingGuidance.contains("Quechua Colla"))

        // Fallback for unknown language must default to Spanish
        assertEquals("es", AppLanguages.get("unknown_code").id)
        assertEquals("es", AppLanguages.get(null).id)
    }

    @Test
    fun answerGraderHandlesAsianScriptsAndPunctuation() {
        val itemJa = LearningTestItem(
            id = "ja1",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "猫",
            correctAnswer = "私は学生です",
            explanation = "Japanese sample explanation"
        )
        // Japanese unspaced script exact and punctuation trimmed
        assertTrue(AnswerGrader.isCorrect(itemJa, "私は学生です。"))
        assertTrue(AnswerGrader.isCorrect(itemJa, "私は学生です"))
        assertEquals("私は学生です", AnswerGrader.normalize("私は学生です。"))

        val itemKo = LearningTestItem(
            id = "ko1",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "사과",
            correctAnswer = "사과를 먹어요",
            explanation = "Korean sample explanation"
        )
        // Korean spacing and punctuation trimmed
        assertTrue(AnswerGrader.isCorrect(itemKo, "사과를 먹어요!"))
        assertTrue(AnswerGrader.isCorrect(itemKo, "사과를 먹어요"))
        assertEquals("사과를 먹어요", AnswerGrader.normalize("사과를 먹어요!"))

        val itemHi = LearningTestItem(
            id = "hi1",
            type = TestItemType.MULTIPLE_CHOICE,
            prompt = "नाम",
            correctAnswer = "मेरा नाम राज है",
            explanation = "Hindi sample explanation"
        )
        // Hindi Devanagari purna viram (।) trimmed
        assertTrue(AnswerGrader.isCorrect(itemHi, "मेरा नाम राज है।"))
        assertTrue(AnswerGrader.isCorrect(itemHi, "मेरा नाम राज है"))
        assertEquals("मेरा नाम राज है", AnswerGrader.normalize("मेरा नाम राज है।"))
    }

    @Test
    fun captionWordsSegmentsJapaneseWithBreakIterator() {
        val text = "猫が好きです"
        val segments = CaptionWords.segments(text, "ja")
        assertTrue("Should segment into multiple Japanese tokens", segments.size >= 3)
        val tokens = segments.map { it.text }
        assertTrue("Tokens should include Kanji/Kana tokens", tokens.contains("猫") || tokens.contains("好き"))
    }

    @Test
    fun teachingPolicyPromptInstructionsIncludeAsianGuidance() {
        val ko = LanguageRegistry.get("ko")!!
        val ja = LanguageRegistry.get("ja")!!
        val hi = LanguageRegistry.get("hi")!!

        val koVoice = TeachingPolicy.voice(
            language = ko,
            learner = LearningEngine.project(emptyList(), ko.id),
            theme = ko.themes.first(),
            interests = "K-Pop",
            meaningLanguage = "Korean",
            pedagogicalMode = TopicEcosystemStore.MODE_EDUCATION
        )
        assertTrue(koVoice.contains("해요체") || koVoice.contains("Korean"))

        val jaVoice = TeachingPolicy.voice(
            language = ja,
            learner = LearningEngine.project(emptyList(), ja.id),
            theme = ja.themes.first(),
            interests = "Anime",
            meaningLanguage = "Japanese",
            pedagogicalMode = TopicEcosystemStore.MODE_CONVERSATION
        )
        assertTrue(jaVoice.contains("です・ます") || jaVoice.contains("Japanese"))

        val hiVoice = TeachingPolicy.voice(
            language = hi,
            learner = LearningEngine.project(emptyList(), hi.id),
            theme = hi.themes.first(),
            interests = "Yoga",
            meaningLanguage = "Hindi",
            pedagogicalMode = TopicEcosystemStore.MODE_EDUCATION
        )
        assertTrue(hiVoice.contains("आप") || hiVoice.contains("Hindi"))
    }

    @Test
    fun testGenerationInstructionsIncludeExplicitScriptRules() {
        val koTestPrompt = TeachingPolicy.generateTest(
            language = LanguageRegistry.get("ko")!!,
            config = TestConfig(mcerLevel = "A1", questionCount = 5, prompt = "Greetings")
        )
        assertTrue(koTestPrompt.contains("Hangul") || koTestPrompt.contains("Korean"))

        val jaTestPrompt = TeachingPolicy.generateTest(
            language = LanguageRegistry.get("ja")!!,
            config = TestConfig(mcerLevel = "A2", questionCount = 5, prompt = "Food")
        )
        assertTrue(jaTestPrompt.contains("Kanji") || jaTestPrompt.contains("Japanese"))

        val hiTestPrompt = TeachingPolicy.generateTest(
            language = LanguageRegistry.get("hi")!!,
            config = TestConfig(mcerLevel = "B1", questionCount = 5, prompt = "Travel")
        )
        assertTrue(hiTestPrompt.contains("Devanagari") || hiTestPrompt.contains("Hindi"))
    }

    @Test
    fun localizedThemesProvidesFullTranslationsAcrossPredefinedLanguages() {
        val baseTheme = Themes.shared.first { it.id == "coffee" }

        // English baseline
        val enTheme = baseTheme.localized("en")
        assertEquals("A coffee?", enTheme.title)

        // Spanish
        val esTheme = baseTheme.localized("es")
        assertEquals("Un café", esTheme.title)
        assertEquals("Algo caliente, por favor", esTheme.subtitle)

        // Korean
        val koTheme = baseTheme.localized("ko")
        assertEquals("커피 한 잔?", koTheme.title)
        assertEquals("따뜻한 음료 한 잔", koTheme.subtitle)

        // Japanese
        val jaTheme = baseTheme.localized("ja")
        assertEquals("コーヒーでも？", jaTheme.title)
        assertEquals("温かいものをどうぞ", jaTheme.subtitle)

        // Hindi
        val hiTheme = baseTheme.localized("hi")
        assertEquals("एक कप कॉफ़ी?", hiTheme.title)
        assertEquals("कुछ गरमा-गरम", hiTheme.subtitle)

        // Portuguese
        val ptTheme = baseTheme.localized("pt")
        assertEquals("Um cafezinho?", ptTheme.title)
        assertEquals("Algo quentinho, por favor", ptTheme.subtitle)

        // French
        val frTheme = baseTheme.localized("fr")
        assertEquals("Un café ?", frTheme.title)
        assertEquals("Quelque chose de chaud, s\'il vous plaît", frTheme.subtitle)

        // German
        val deTheme = baseTheme.localized("de")
        assertEquals("Ein Kaffee?", deTheme.title)
        assertEquals("Etwas Warmes, bitte", deTheme.subtitle)

        // Italian
        val itTheme = baseTheme.localized("it")
        assertEquals("Un caffè?", itTheme.title)
        assertEquals("Qualcosa di caldo, per favore", itTheme.subtitle)

        // Chinese
        val zhTheme = baseTheme.localized("zh")
        assertEquals("喝杯咖啡？", zhTheme.title)
        assertEquals("来杯温热的饮品", zhTheme.subtitle)

        // Norwegian
        val nbTheme = baseTheme.localized("nb")
        assertEquals("En kaffe?", nbTheme.title)
        assertEquals("Noe varmt, takk", nbTheme.subtitle)

        // Quechua Colla
        val quTheme = baseTheme.localized("qu")
        assertEquals("¿Aqha icha kaphichu?", quTheme.title)
        assertEquals("Q'uñi upyana, ama hina kaychu", quTheme.subtitle)

        // Verify English ("en") appLanguage overrides target-language themeOverrides (e.g. Japanese coffee theme)
        val jaOverrideTheme = LanguageRegistry.japanese.themes.first { it.id == "coffee" }
        assertEquals("カフェで一杯？", jaOverrideTheme.title)
        assertEquals("A coffee?", jaOverrideTheme.localized("en").title)

        // Verify all 24 themes are covered and non-empty for all 12 predefined languages
        for (lang in AppLanguages.all) {
            for (t in Themes.shared) {
                val loc = t.localized(lang.id)
                assertTrue("Theme ${t.id} title in ${lang.id} must not be blank", loc.title.isNotBlank())
                assertTrue("Theme ${t.id} subtitle in ${lang.id} must not be blank", loc.subtitle.isNotBlank())
                assertTrue("Theme ${t.id} situation in ${lang.id} must not be blank", loc.situation.isNotBlank())
            }
        }
    }

    @Test
    fun customThemesPreserveOriginalContentWhenLocalized() {
        val customTheme = ConversationTheme(
            id = "custom_test_123",
            title = "My Custom Interview",
            subtitle = "Special Scenario",
            symbol = "sparkles",
            category = "Custom",
            situation = "Practice a custom interview role.",
            colorIndex = 1
        )
        val localized = customTheme.localized("ko")
        assertEquals("My Custom Interview", localized.title)
        assertEquals("Special Scenario", localized.subtitle)
        assertEquals("Practice a custom interview role.", localized.situation)
    }
}

