package chat.mural.core

import kotlinx.serialization.Serializable

@Serializable
enum class TestItemType {
    MULTIPLE_CHOICE,
    FILL_BLANK,
    CLOZE_BANK,
    ERROR_CORRECTION,
    READING_COMPREHENSION,
    SPEAKING
}

@Serializable
enum class TestDifficulty {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
enum class TestStatus {
    GENERATING,
    READY,
    FAILED
}

@Serializable
data class TestConfig(
    val prompt: String = "",
    val topicId: String? = null,
    val languageID: String = "en",
    val mcerLevel: String = "B1",
    val allowedTypes: List<TestItemType> = TestItemType.entries,
    val perTypeCounts: Map<TestItemType, Int> = emptyMap(),
    val questionCount: Int = 8,
    val easyCount: Int = 0,
    val mediumCount: Int = 0,
    val hardCount: Int = 0,
    val timeLimitSeconds: Int? = null,
    val includeFlashcards: Boolean = false,
    val randomMode: Boolean = false
)

@Serializable
data class LearningTestItem(
    val id: String,
    val type: TestItemType,
    val prompt: String,
    val context: String = "",
    val options: List<String> = emptyList(), // Exactly 4 options for MULTIPLE_CHOICE
    val correctAnswer: String,
    val explanation: String,
    val targetSpeechText: String = "",
    val readingPassage: String = "",
    val wordBank: List<String> = emptyList(),
    val incorrectWord: String = ""
)

@Serializable
data class FlashcardItem(
    val id: String,
    val front: String,
    val back: String,
    val example: String,
    val pronunciationGuide: String = ""
)

@Serializable
data class LearningTest(
    val id: String,
    val title: String,
    val topicId: String? = null,
    val topicTitle: String,
    val mcerLevel: String, // A1, A2, B1, B2, C1, C2
    val difficulty: TestDifficulty,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<LearningTestItem> = emptyList(),
    val flashcards: List<FlashcardItem> = emptyList(),
    val languageID: String = "en",
    val status: TestStatus = TestStatus.READY,
    val config: TestConfig? = null,
    val isFavorite: Boolean = false,
    val errorMessage: String? = null
)

enum class TestTypeFilter {
    ALL,
    MULTIPLE_CHOICE,
    FILL_BLANK,
    CLOZE_BANK,
    ERROR_CORRECTION,
    READING_COMPREHENSION,
    SPEAKING,
    FLASHCARDS,
    MIXED
}

@Serializable
data class TestUserAttempt(
    val testId: String,
    val completedAt: Long = System.currentTimeMillis(),
    val score: Int,
    val totalQuestions: Int,
    val answers: Map<String, String> = emptyMap(), // itemId -> user entered/selected text
    val correctStatus: Map<String, Boolean> = emptyMap(), // itemId -> true/false
    val durationMillis: Long = 0,
    val perItemMillis: Map<String, Long> = emptyMap(),
    val unanswered: Int = 0
) {
    val accuracyPercentage: Int
        get() = if (totalQuestions > 0) ((score.toDouble() / totalQuestions) * 100).toInt() else 0
}

@Serializable
data class AttemptSummary(
    val testId: String,
    val attemptsCount: Int,
    val bestScorePercentage: Int,
    val latestScorePercentage: Int,
    val latestAttempt: TestUserAttempt? = null
)

@Serializable
data class TestSessionDraft(
    val testId: String,
    val currentIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val elapsedMillis: Long = 0,
    val savedAt: Long = System.currentTimeMillis()
)
