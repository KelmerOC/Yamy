package chat.mural.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

class LearningTestStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    init {
        cleanupOrphanGeneratingTests()
    }

    fun getTests(languageID: String): List<LearningTest> {
        val raw = prefs.getString(KEY_TESTS_PREFIX + languageID, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LearningTest>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTest(languageID: String, test: LearningTest) {
        val current = getTests(languageID).filterNot { it.id == test.id }.toMutableList()
        current.add(0, test) // Prepend so latest test is first
        val serialized = json.encodeToString(current.take(MAX_STORED_TESTS))
        prefs.edit().putString(KEY_TESTS_PREFIX + languageID, serialized).apply()
    }

    fun upsertTest(test: LearningTest) {
        val lang = test.languageID.ifBlank { "en" }
        saveTest(lang, test)
    }

    fun setFavorite(testId: String, isFavorite: Boolean): Boolean {
        var updated = false
        val allEntries = prefs.all
        val editor = prefs.edit()
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_TESTS_PREFIX) && value is String) {
                try {
                    val list = json.decodeFromString<List<LearningTest>>(value)
                    val idx = list.indexOfFirst { it.id == testId }
                    if (idx >= 0) {
                        val mutable = list.toMutableList()
                        val current = mutable[idx]
                        mutable[idx] = current.copy(isFavorite = isFavorite)
                        editor.putString(key, json.encodeToString(mutable))
                        updated = true
                    }
                } catch (_: Exception) {}
            }
        }
        if (updated) {
            editor.apply()
        }
        return updated
    }

    fun getAllTests(): List<LearningTest> {
        val allEntries = prefs.all
        val tests = mutableListOf<LearningTest>()
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_TESTS_PREFIX) && value is String) {
                try {
                    val list = json.decodeFromString<List<LearningTest>>(value)
                    tests.addAll(list)
                } catch (_: Exception) {}
            }
        }
        return tests.distinctBy { it.id }.sortedByDescending { it.createdAt }
    }

    fun deleteTest(testId: String): Boolean {
        var deleted = false
        val allEntries = prefs.all
        val editor = prefs.edit()
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_TESTS_PREFIX) && value is String) {
                try {
                    val list = json.decodeFromString<List<LearningTest>>(value).toMutableList()
                    val removed = list.removeAll { it.id == testId }
                    if (removed) {
                        editor.putString(key, json.encodeToString(list))
                        deleted = true
                    }
                } catch (_: Exception) {}
            }
        }
        if (deleted) {
            editor.remove(KEY_ATTEMPT_PREFIX + testId)
                .remove(KEY_ATTEMPTS_HISTORY_PREFIX + testId)
                .remove(KEY_DRAFT_PREFIX + testId)
                .apply()
        }
        return deleted
    }

    fun deleteTest(languageID: String, testId: String): Boolean {
        val current = getTests(languageID).toMutableList()
        val removed = current.removeAll { it.id == testId }
        if (removed) {
            val serialized = json.encodeToString(current)
            prefs.edit().putString(KEY_TESTS_PREFIX + languageID, serialized)
                .remove(KEY_ATTEMPT_PREFIX + testId)
                .remove(KEY_ATTEMPTS_HISTORY_PREFIX + testId)
                .remove(KEY_DRAFT_PREFIX + testId)
                .apply()
        }
        return removed
    }

    fun getAttempts(testId: String): List<TestUserAttempt> {
        val raw = prefs.getString(KEY_ATTEMPTS_HISTORY_PREFIX + testId, null)
        if (raw != null) {
            try {
                val list = json.decodeFromString<List<TestUserAttempt>>(raw)
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        // Fallback to legacy single attempt
        val legacy = getLegacyAttempt(testId)
        return if (legacy != null) listOf(legacy) else emptyList()
    }

    fun getLatestAttempt(testId: String): TestUserAttempt? {
        return getAttempts(testId).firstOrNull()
    }

    private fun getLegacyAttempt(testId: String): TestUserAttempt? {
        val raw = prefs.getString(KEY_ATTEMPT_PREFIX + testId, null) ?: return null
        return try {
            json.decodeFromString<TestUserAttempt>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun saveAttempt(attempt: TestUserAttempt) {
        val existing = getAttempts(attempt.testId).filterNot { it.completedAt == attempt.completedAt }.toMutableList()
        existing.add(0, attempt)
        val history = existing.take(MAX_STORED_ATTEMPTS)

        val serializedHistory = json.encodeToString(history)
        val serializedLatest = json.encodeToString(attempt)

        prefs.edit()
            .putString(KEY_ATTEMPTS_HISTORY_PREFIX + attempt.testId, serializedHistory)
            .putString(KEY_ATTEMPT_PREFIX + attempt.testId, serializedLatest)
            .remove(KEY_DRAFT_PREFIX + attempt.testId) // clear draft on completion
            .apply()
    }

    fun getAttemptSummary(testId: String): AttemptSummary? {
        return try {
            val attempts = getAttempts(testId)
            if (attempts.isEmpty()) return null
            val best = attempts.maxOf { it.accuracyPercentage }
            val latest = attempts.first()
            AttemptSummary(
                testId = testId,
                attemptsCount = attempts.size,
                bestScorePercentage = best,
                latestScorePercentage = latest.accuracyPercentage,
                latestAttempt = latest
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAttemptSummaries(): Map<String, AttemptSummary> {
        val allTests = getAllTests()
        val result = mutableMapOf<String, AttemptSummary>()
        for (t in allTests) {
            try {
                val summary = getAttemptSummary(t.id)
                if (summary != null) {
                    result[t.id] = summary
                }
            } catch (_: Exception) {}
        }
        return result
    }

    fun saveSessionDraft(draft: TestSessionDraft) {
        val serialized = json.encodeToString(draft)
        prefs.edit().putString(KEY_DRAFT_PREFIX + draft.testId, serialized).apply()
    }

    fun loadSessionDraft(testId: String): TestSessionDraft? {
        val raw = prefs.getString(KEY_DRAFT_PREFIX + testId, null) ?: return null
        return try {
            json.decodeFromString<TestSessionDraft>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clearSessionDraft(testId: String) {
        prefs.edit().remove(KEY_DRAFT_PREFIX + testId).apply()
    }

    fun getActivityDaysThisWeek(): Int {
        return try {
            val allTests = getAllTests()
            val calendar = Calendar.getInstance()
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - (7L * 24L * 60L * 60L * 1000L)

            val daysWithActivity = mutableSetOf<String>()
            for (test in allTests) {
                val attempts = getAttempts(test.id)
                for (att in attempts) {
                    if (att.completedAt >= sevenDaysAgo) {
                        calendar.timeInMillis = att.completedAt
                        val dayKey = "${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.DAY_OF_YEAR)}"
                        daysWithActivity.add(dayKey)
                    }
                }
            }
            daysWithActivity.size
        } catch (_: Exception) {
            0
        }
    }

    fun cleanupOrphanGeneratingTests() {
        val allEntries = prefs.all
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_TESTS_PREFIX) && value is String) {
                try {
                    val list = json.decodeFromString<List<LearningTest>>(value)
                    var listModified = false
                    val updatedList = list.map { test ->
                        if (test.status == TestStatus.GENERATING) {
                            listModified = true
                            test.copy(
                                status = TestStatus.FAILED,
                                errorMessage = "La generación se interrumpió. Pulsa reintentar."
                            )
                        } else {
                            test
                        }
                    }
                    if (listModified) {
                        editor.putString(key, json.encodeToString(updatedList))
                        changed = true
                    }
                } catch (_: Exception) {}
            }
        }
        if (changed) {
            editor.apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "mural_learning_tests"
        private const val KEY_TESTS_PREFIX = "learning_tests_"
        private const val KEY_ATTEMPT_PREFIX = "test_attempt_"
        private const val KEY_ATTEMPTS_HISTORY_PREFIX = "test_attempts_history_"
        private const val KEY_DRAFT_PREFIX = "test_draft_"
        private const val MAX_STORED_TESTS = 60
        private const val MAX_STORED_ATTEMPTS = 20
    }
}
