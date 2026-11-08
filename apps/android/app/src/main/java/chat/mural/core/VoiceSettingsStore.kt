package chat.mural.core

import android.content.Context

class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var voiceSpeed: String
        get() = prefs.getString(KEY_VOICE_SPEED, SPEED_NORMAL) ?: SPEED_NORMAL
        set(value) {
            prefs.edit().putString(KEY_VOICE_SPEED, value).apply()
        }

    var cleanVoiceMode: Boolean
        get() = prefs.getBoolean(KEY_CLEAN_VOICE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CLEAN_VOICE, value).apply()
        }

    var voiceTimbre: String
        get() = prefs.getString(KEY_VOICE_TIMBRE, VOICE_PUCK) ?: VOICE_PUCK
        set(value) {
            prefs.edit().putString(KEY_VOICE_TIMBRE, value).apply()
        }

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, null) ?: AppLanguages.defaultForSystem().id
        set(value) {
            prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()
        }

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
        }

    companion object {
        private const val PREFERENCES_NAME = "mural_voice_settings"
        const val KEY_VOICE_SPEED = "voice_speed"
        const val KEY_CLEAN_VOICE = "clean_voice_mode"
        const val KEY_VOICE_TIMBRE = "voice_timbre"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_DARK_THEME = "dark_theme"

        const val SPEED_BEGINNER = "beginner"
        const val SPEED_NORMAL = "normal"
        const val SPEED_ADVANCED = "advanced"

        const val VOICE_AOEDE = "Aoede"
        const val VOICE_KORE = "Kore"
        const val VOICE_PUCK = "Puck"
        const val VOICE_FENRIR = "Fenrir"
        const val VOICE_CHARON = "Charon"

        val ALL_VOICE_TIMBRES = listOf(VOICE_AOEDE, VOICE_KORE, VOICE_PUCK, VOICE_FENRIR, VOICE_CHARON)
    }
}
