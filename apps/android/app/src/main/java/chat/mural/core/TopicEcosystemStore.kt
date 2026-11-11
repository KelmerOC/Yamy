package chat.mural.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class CachedThemeTranslation(
    val title: String,
    val subtitle: String,
    val situation: String = ""
)

@Serializable
data class ThemePersistentMemory(
    val themeId: String,
    val checkpointSummary: String = "",
    val keyTopicsDiscussed: List<String> = emptyList(),
    val lastSessionSummary: String = "",
    val contextHistory: List<String> = emptyList(),
    val frequentErrors: List<String> = emptyList(),
    val practicedWords: List<String> = emptyList(),
    val sessionsCount: Int = 0,
    val completedTestsCount: Int = 0,
    val bestTestScore: Int = 0,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

class TopicEcosystemStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var favoriteThemeIDs: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_FAVORITES, value).commit()
        }

    fun isFavorite(themeID: String): Boolean = favoriteThemeIDs.contains(themeID)

    fun toggleFavorite(themeID: String): Boolean {
        val current = favoriteThemeIDs.toMutableSet()
        val isNowFavorite = if (current.contains(themeID)) {
            current.remove(themeID)
            false
        } else {
            current.add(themeID)
            true
        }
        favoriteThemeIDs = current
        return isNowFavorite
    }

    var globalMcerLevel: String
        get() = prefs.getString(KEY_GLOBAL_MCER, DEFAULT_MCER) ?: DEFAULT_MCER
        set(value) {
            val normalized = value.uppercase().trim().takeIf { it in MCER_LEVELS } ?: DEFAULT_MCER
            prefs.edit().putString(KEY_GLOBAL_MCER, normalized).commit()
        }

    fun getMcerLevel(themeID: String?): String {
        if (themeID == null || themeID == "general") return globalMcerLevel
        return prefs.getString(KEY_PREFIX_MCER + themeID, globalMcerLevel) ?: globalMcerLevel
    }

    fun setMcerLevel(themeID: String?, level: String) {
        val normalized = level.uppercase().trim().takeIf { it in MCER_LEVELS } ?: DEFAULT_MCER
        if (themeID == null || themeID == "general") {
            globalMcerLevel = normalized
        } else {
            prefs.edit().putString(KEY_PREFIX_MCER + themeID, normalized).commit()
        }
    }

    fun getPersonality(themeID: String): String {
        return prefs.getString(KEY_PREFIX_PERSONALITY + themeID, DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY
    }

    fun setPersonality(themeID: String, personality: String) {
        val normalized = personality.lowercase().trim().takeIf { it in PERSONALITIES } ?: DEFAULT_PERSONALITY
        prefs.edit().putString(KEY_PREFIX_PERSONALITY + themeID, normalized).commit()
    }

    fun getThemeMemory(themeID: String): String? {
        val direct = prefs.getString(KEY_PREFIX_MEMORY + themeID, null)
        if (!direct.isNullOrBlank()) return direct
        val structured = getStructuredThemeMemory(themeID)
        return formatSummary(structured).takeIf { it.isNotBlank() }
    }

    fun setThemeMemory(themeID: String, memory: String) {
        val trimmed = memory.trim()
        val current = getStructuredThemeMemory(themeID)
        val updatedHistory = if (trimmed.isNotEmpty()) {
            (listOf(trimmed) + current.contextHistory).distinct().take(8)
        } else {
            current.contextHistory
        }
        val updated = current.copy(
            contextHistory = updatedHistory,
            lastUpdatedAt = System.currentTimeMillis()
        )
        prefs.edit()
            .putString(KEY_PREFIX_MEMORY + themeID, trimmed)
            .putString(KEY_PREFIX_STRUCTURED_MEMORY + themeID, json.encodeToString(updated))
            .commit()
    }

    fun getStructuredThemeMemory(themeID: String): ThemePersistentMemory {
        val rawJson = prefs.getString(KEY_PREFIX_STRUCTURED_MEMORY + themeID, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                return json.decodeFromString<ThemePersistentMemory>(rawJson)
            } catch (_: Exception) {
                // Fallback to legacy if JSON is corrupted
            }
        }
        val legacy = prefs.getString(KEY_PREFIX_MEMORY + themeID, null)?.trim()
        val initialHistory = if (!legacy.isNullOrBlank()) listOf(legacy) else emptyList()
        return ThemePersistentMemory(
            themeId = themeID,
            contextHistory = initialHistory
        )
    }

    fun saveStructuredThemeMemory(memory: ThemePersistentMemory) {
        val summary = formatSummary(memory)
        prefs.edit()
            .putString(KEY_PREFIX_STRUCTURED_MEMORY + memory.themeId, json.encodeToString(memory))
            .putString(KEY_PREFIX_MEMORY + memory.themeId, summary)
            .commit()
    }

    fun recordCheckpointMemory(
        themeID: String,
        checkpointSummary: String,
        lastSessionSummary: String = "",
        keyTopics: List<String> = emptyList(),
        detectedErrors: List<String> = emptyList(),
        practicedWords: List<String> = emptyList()
    ) {
        val current = getStructuredThemeMemory(themeID)
        val cleanSummary = checkpointSummary.trim()
        val cleanLast = lastSessionSummary.trim()

        val newEntries = listOfNotNull(
            cleanLast.takeIf { it.isNotBlank() },
            cleanSummary.takeIf { it.isNotBlank() && it != cleanLast }
        )
        val updatedHistory = (newEntries + current.contextHistory)
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        val cleanTopics = (keyTopics.map { it.trim() }.filter { it.isNotEmpty() } + current.keyTopicsDiscussed)
            .distinct()
            .take(10)

        val cleanErrors = (detectedErrors.map { it.trim() }.filter { it.isNotEmpty() } + current.frequentErrors)
            .distinct()
            .take(12)

        val cleanWords = (practicedWords.map { it.trim() }.filter { it.isNotEmpty() } + current.practicedWords)
            .distinct()
            .take(25)

        val updated = current.copy(
            checkpointSummary = cleanSummary.ifBlank { current.checkpointSummary },
            lastSessionSummary = cleanLast.ifBlank { current.lastSessionSummary },
            keyTopicsDiscussed = cleanTopics,
            contextHistory = updatedHistory,
            frequentErrors = cleanErrors,
            practicedWords = cleanWords,
            sessionsCount = current.sessionsCount + 1,
            lastUpdatedAt = System.currentTimeMillis()
        )
        saveStructuredThemeMemory(updated)
    }

    fun recordSessionMemory(
        themeID: String,
        sessionSummary: String,
        detectedErrors: List<String> = emptyList(),
        practicedLemmas: List<String> = emptyList()
    ) {
        val current = getStructuredThemeMemory(themeID)
        val trimmedSummary = sessionSummary.trim()
        val updatedHistory = (if (trimmedSummary.isNotEmpty()) listOf(trimmedSummary) + current.contextHistory else current.contextHistory)
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        val cleanErrors = detectedErrors.map { it.trim() }.filter { it.isNotEmpty() }
        val updatedErrors = (cleanErrors + current.frequentErrors)
            .distinct()
            .take(12)

        val cleanLemmas = practicedLemmas.map { it.trim() }.filter { it.isNotEmpty() }
        val updatedWords = (cleanLemmas + current.practicedWords)
            .distinct()
            .take(25)

        val updated = current.copy(
            contextHistory = updatedHistory,
            frequentErrors = updatedErrors,
            practicedWords = updatedWords,
            sessionsCount = current.sessionsCount + 1,
            lastUpdatedAt = System.currentTimeMillis()
        )
        saveStructuredThemeMemory(updated)
    }

    fun recordTestAttemptMemory(
        themeID: String,
        scorePercentage: Int,
        missedExpressions: List<String> = emptyList()
    ) {
        val current = getStructuredThemeMemory(themeID)
        val cleanMissed = missedExpressions.map { it.trim() }.filter { it.isNotEmpty() }
        val updatedErrors = (cleanMissed + current.frequentErrors)
            .distinct()
            .take(12)

        val updated = current.copy(
            frequentErrors = updatedErrors,
            completedTestsCount = current.completedTestsCount + 1,
            bestTestScore = maxOf(current.bestTestScore, scorePercentage),
            lastUpdatedAt = System.currentTimeMillis()
        )
        saveStructuredThemeMemory(updated)
    }

    private fun formatSummary(memory: ThemePersistentMemory): String {
        val parts = mutableListOf<String>()
        val checkpoint = memory.checkpointSummary.ifBlank { memory.lastSessionSummary }
        if (checkpoint.isNotBlank()) {
            parts.add("Punto de control previo: $checkpoint")
        }
        if (memory.keyTopicsDiscussed.isNotEmpty()) {
            parts.add("Temas tratados: " + memory.keyTopicsDiscussed.take(5).joinToString(", "))
        }
        if (memory.contextHistory.isNotEmpty() && checkpoint.isBlank()) {
            parts.add("Historial reciente: " + memory.contextHistory.take(3).joinToString(" | "))
        }
        if (memory.frequentErrors.isNotEmpty()) {
            parts.add("Errores frecuentes a reforzar: " + memory.frequentErrors.take(5).joinToString(", "))
        }
        if (memory.practicedWords.isNotEmpty()) {
            parts.add("Vocabulario practicado: " + memory.practicedWords.take(10).joinToString(", "))
        }
        val progress = mutableListOf<String>()
        if (memory.sessionsCount > 0) {
            progress.add("Sesiones: ${memory.sessionsCount}")
        }
        if (memory.completedTestsCount > 0) {
            progress.add("Tests completados: ${memory.completedTestsCount} (Mejor: ${memory.bestTestScore}%)")
        }
        if (progress.isNotEmpty()) {
            parts.add("Progreso: " + progress.joinToString(" · "))
        }
        return parts.joinToString("\n")
    }

    private fun parseCustomThemesJson(jsonStr: String): List<ConversationTheme> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val jsonArray = json.parseToJsonElement(jsonStr) as? JsonArray ?: return emptyList()
            jsonArray.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val title = (obj["title"] as? JsonPrimitive)?.content ?: ""
                val subtitle = (obj["subtitle"] as? JsonPrimitive)?.content ?: ""
                val symbol = (obj["symbol"] as? JsonPrimitive)?.content ?: "sparkles"
                val category = (obj["category"] as? JsonPrimitive)?.content ?: "Personalizados"
                val situation = (obj["situation"] as? JsonPrimitive)?.content ?: ""
                val colorIndex = ((obj["colorIndex"] as? JsonPrimitive)?.content)?.toIntOrNull() ?: 1
                ConversationTheme(id, title, subtitle, symbol, category, situation, colorIndex)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeThemes(themes: List<ConversationTheme>): String {
        val jsonArray = buildJsonArray {
            for (t in themes.take(100)) {
                add(buildJsonObject {
                    put("id", JsonPrimitive(t.id))
                    put("title", JsonPrimitive(t.title))
                    put("subtitle", JsonPrimitive(t.subtitle))
                    put("symbol", JsonPrimitive(t.symbol))
                    put("category", JsonPrimitive(t.category))
                    put("situation", JsonPrimitive(t.situation))
                    put("colorIndex", JsonPrimitive(t.colorIndex))
                })
            }
        }
        return jsonArray.toString()
    }

    fun getCustomThemes(languageID: String): List<ConversationTheme> {
        val jsonStr = prefs.getString(KEY_PREFIX_CUSTOM_THEMES + languageID, null) ?: return emptyList()
        return parseCustomThemesJson(jsonStr)
    }

    fun getAllCustomThemes(): List<ConversationTheme> {
        val allCustom = mutableListOf<ConversationTheme>()
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        fun addIfNew(theme: ConversationTheme) {
            val normTitle = theme.title.trim().lowercase()
            if (seenIds.add(theme.id) && (normTitle.isEmpty() || seenTitles.add(normTitle))) {
                allCustom.add(theme)
            }
        }

        try {
            val globalJson = prefs.getString(KEY_GLOBAL_CUSTOM_THEMES, null)
            if (!globalJson.isNullOrBlank()) {
                for (theme in parseCustomThemesJson(globalJson)) {
                    addIfNew(theme)
                }
            }
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX_CUSTOM_THEMES) && key != KEY_GLOBAL_CUSTOM_THEMES && value is String) {
                    val parsed = parseCustomThemesJson(value)
                    for (theme in parsed) {
                        addIfNew(theme)
                    }
                }
            }
        } catch (_: Exception) {}
        return allCustom
    }

    fun saveCustomTheme(languageID: String, theme: ConversationTheme) {
        val currentLang = getCustomThemes(languageID).filterNot {
            it.id == theme.id || it.title.trim().equals(theme.title.trim(), ignoreCase = true)
        }.toMutableList()
        currentLang.add(0, theme)
        prefs.edit().putString(KEY_PREFIX_CUSTOM_THEMES + languageID, serializeThemes(currentLang)).commit()

        val currentGlobal = getAllCustomThemes().filterNot {
            it.id == theme.id || it.title.trim().equals(theme.title.trim(), ignoreCase = true)
        }.toMutableList()
        currentGlobal.add(0, theme)
        prefs.edit().putString(KEY_GLOBAL_CUSTOM_THEMES, serializeThemes(currentGlobal)).commit()
    }

    fun getCachedThemeTranslation(themeID: String, languageID: String): CachedThemeTranslation? {
        val key = KEY_PREFIX_THEME_TRANSLATION + languageID.lowercase() + "_" + themeID
        val raw = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString<CachedThemeTranslation>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCachedThemeTranslation(themeID: String, languageID: String, translation: CachedThemeTranslation) {
        val key = KEY_PREFIX_THEME_TRANSLATION + languageID.lowercase() + "_" + themeID
        try {
            prefs.edit().putString(key, json.encodeToString(translation)).commit()
        } catch (_: Exception) {}
    }

    var globalPedagogicalMode: String
        get() = prefs.getString(KEY_GLOBAL_PEDAGOGICAL_MODE, DEFAULT_MODE) ?: DEFAULT_MODE
        set(value) {
            val normalized = value.lowercase().trim().takeIf { it in PEDAGOGICAL_MODES } ?: DEFAULT_MODE
            prefs.edit().putString(KEY_GLOBAL_PEDAGOGICAL_MODE, normalized).commit()
        }

    fun getPedagogicalMode(themeID: String?): String {
        if (themeID == null || themeID == "general") return globalPedagogicalMode
        return prefs.getString(KEY_PREFIX_MODE + themeID, globalPedagogicalMode) ?: globalPedagogicalMode
    }

    fun setPedagogicalMode(themeID: String?, mode: String) {
        val normalized = mode.lowercase().trim().takeIf { it in PEDAGOGICAL_MODES } ?: DEFAULT_MODE
        if (themeID == null || themeID == "general") {
            globalPedagogicalMode = normalized
        } else {
            prefs.edit().putString(KEY_PREFIX_MODE + themeID, normalized).commit()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "mural_topic_ecosystem"
        private const val KEY_FAVORITES = "favorite_theme_ids"
        private const val KEY_PREFIX_MCER = "mcer_level_"
        private const val KEY_PREFIX_PERSONALITY = "personality_"
        private const val KEY_PREFIX_MEMORY = "theme_memory_"
        private const val KEY_PREFIX_STRUCTURED_MEMORY = "structured_theme_memory_"
        private const val KEY_PREFIX_CUSTOM_THEMES = "custom_themes_"
        private const val KEY_GLOBAL_CUSTOM_THEMES = "global_custom_themes"
        private const val KEY_PREFIX_THEME_TRANSLATION = "theme_trans_"
        private const val KEY_PREFIX_MODE = "pedagogical_mode_"
        private const val KEY_GLOBAL_PEDAGOGICAL_MODE = "global_pedagogical_mode"
        private const val KEY_GLOBAL_MCER = "global_mcer_level"

        const val DEFAULT_MCER = "B1"
        val MCER_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

        const val PERSONALITY_FRIENDLY = "friendly"
        const val PERSONALITY_PROFESSIONAL = "professional"
        const val PERSONALITY_CASUAL = "casual"
        const val PERSONALITY_BUSY = "busy"

        const val DEFAULT_PERSONALITY = PERSONALITY_FRIENDLY
        val PERSONALITIES = listOf(
            PERSONALITY_FRIENDLY,
            PERSONALITY_PROFESSIONAL,
            PERSONALITY_CASUAL,
            PERSONALITY_BUSY
        )

        const val MODE_EDUCATION = "education"
        const val MODE_CONVERSATION = "conversation"
        const val DEFAULT_MODE = MODE_EDUCATION
        const val DEFAULT_PEDAGOGICAL_MODE = DEFAULT_MODE
        val PEDAGOGICAL_MODES = listOf(MODE_EDUCATION, MODE_CONVERSATION)
    }
}
