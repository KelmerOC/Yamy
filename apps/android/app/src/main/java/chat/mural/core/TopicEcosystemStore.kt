package chat.mural.core

import android.content.Context

class TopicEcosystemStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var favoriteThemeIDs: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_FAVORITES, value).apply()
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
            prefs.edit().putString(KEY_GLOBAL_MCER, normalized).apply()
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
            prefs.edit().putString(KEY_PREFIX_MCER + themeID, normalized).apply()
        }
    }

    fun getPersonality(themeID: String): String {
        return prefs.getString(KEY_PREFIX_PERSONALITY + themeID, DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY
    }

    fun setPersonality(themeID: String, personality: String) {
        val normalized = personality.lowercase().trim().takeIf { it in PERSONALITIES } ?: DEFAULT_PERSONALITY
        prefs.edit().putString(KEY_PREFIX_PERSONALITY + themeID, normalized).apply()
    }

    fun getThemeMemory(themeID: String): String? {
        return prefs.getString(KEY_PREFIX_MEMORY + themeID, null)
    }

    fun setThemeMemory(themeID: String, memory: String) {
        prefs.edit().putString(KEY_PREFIX_MEMORY + themeID, memory.trim()).apply()
    }

    fun getCustomThemes(languageID: String): List<ConversationTheme> {
        val jsonStr = prefs.getString(KEY_PREFIX_CUSTOM_THEMES + languageID, null) ?: return emptyList()
        return try {
            val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr) as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            jsonArray.mapNotNull { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                val title = (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val subtitle = (obj["subtitle"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val symbol = (obj["symbol"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "sparkles"
                val category = (obj["category"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Personalizados"
                val situation = (obj["situation"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                val colorIndex = ((obj["colorIndex"] as? kotlinx.serialization.json.JsonPrimitive)?.content)?.toIntOrNull() ?: 1
                ConversationTheme(id, title, subtitle, symbol, category, situation, colorIndex)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomTheme(languageID: String, theme: ConversationTheme) {
        val current = getCustomThemes(languageID).filterNot { it.id == theme.id }.toMutableList()
        current.add(0, theme)
        val jsonArray = kotlinx.serialization.json.buildJsonArray {
            for (t in current.take(50)) {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("id", kotlinx.serialization.json.JsonPrimitive(t.id))
                    put("title", kotlinx.serialization.json.JsonPrimitive(t.title))
                    put("subtitle", kotlinx.serialization.json.JsonPrimitive(t.subtitle))
                    put("symbol", kotlinx.serialization.json.JsonPrimitive(t.symbol))
                    put("category", kotlinx.serialization.json.JsonPrimitive(t.category))
                    put("situation", kotlinx.serialization.json.JsonPrimitive(t.situation))
                    put("colorIndex", kotlinx.serialization.json.JsonPrimitive(t.colorIndex))
                })
            }
        }
        prefs.edit().putString(KEY_PREFIX_CUSTOM_THEMES + languageID, jsonArray.toString()).apply()
    }

    var globalPedagogicalMode: String
        get() = prefs.getString(KEY_GLOBAL_PEDAGOGICAL_MODE, DEFAULT_MODE) ?: DEFAULT_MODE
        set(value) {
            val normalized = value.lowercase().trim().takeIf { it in PEDAGOGICAL_MODES } ?: DEFAULT_MODE
            prefs.edit().putString(KEY_GLOBAL_PEDAGOGICAL_MODE, normalized).apply()
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
            prefs.edit().putString(KEY_PREFIX_MODE + themeID, normalized).apply()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "mural_topic_ecosystem"
        private const val KEY_FAVORITES = "favorite_theme_ids"
        private const val KEY_PREFIX_MCER = "mcer_level_"
        private const val KEY_PREFIX_PERSONALITY = "personality_"
        private const val KEY_PREFIX_MEMORY = "theme_memory_"
        private const val KEY_PREFIX_CUSTOM_THEMES = "custom_themes_"
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
