package chat.mural

import android.app.Application
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import chat.mural.core.*
import chat.mural.media.YamyLiveSessionSnapshot
import chat.mural.media.YamySessionCoordinator
import chat.mural.media.YamyVoiceForegroundService
import chat.mural.network.*
import java.util.UUID
import kotlin.math.round
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Maps a network/credential failure reason to its user-facing string resource, or 0 if unmapped. */
@StringRes
internal fun errorMessageRes(e: Throwable): Int = when (e) {
    is HostedFailure -> hostedErrorMessageRes(e)
    is AccountFailure.Http -> when (e.status) {
        401 -> R.string.hosted_sign_in_again
        429 -> R.string.hosted_rate_limit
        else -> R.string.hosted_request_failed
    }
    AccountFailure.Unavailable -> R.string.account_error_connection
    is APIClient.APIException.MissingKey -> R.string.error_missing_key
    is APIClient.APIException.Refused -> R.string.error_request_refused
    is APIClient.APIException.InvalidResponse, is APIClient.APIException.Incomplete -> R.string.error_incomplete_response
    is APIClient.APIException.Http -> when (e.kind) {
        ProviderFailureKind.authentication -> R.string.error_http_401
        ProviderFailureKind.modelAccess -> R.string.error_http_403_404
        ProviderFailureKind.quota -> R.string.error_provider_quota
        ProviderFailureKind.rateLimit -> R.string.error_http_429
        ProviderFailureKind.unavailable -> R.string.error_provider_unavailable
        ProviderFailureKind.invalidRequest -> R.string.error_provider_request
        ProviderFailureKind.unknown -> R.string.error_http_generic
    }
    is java.net.SocketTimeoutException -> R.string.error_request_timeout
    is java.net.UnknownHostException, is java.net.ConnectException -> R.string.error_request_connection
    is CredentialStore.CredentialException.Invalid -> R.string.error_key_invalid
    is CredentialStore.CredentialException.Save -> R.string.error_key_save
    is CredentialStore.CredentialException.Remove -> R.string.error_key_remove
    else -> 0
}

@StringRes
internal fun hostedErrorMessageRes(failure: HostedFailure): Int = when (failure) {
    HostedFailure.SignInRequired -> R.string.hosted_sign_in_again
    HostedFailure.Unconfirmed -> R.string.hosted_unconfirmed
    HostedFailure.Unavailable -> R.string.hosted_unavailable
    is HostedFailure.Http -> when {
        needsAccountRecovery(failure) -> R.string.hosted_sign_in_again
        failure.code in setOf("insufficient_minutes", "insufficient_credit") -> R.string.hosted_no_minutes
        failure.code == "helper_session_limit" && failure.retryable == true -> R.string.hosted_help_busy
        failure.code == "helper_concurrency_limit" -> R.string.hosted_help_busy
        failure.code in setOf("helper_budget_exhausted", "helper_session_limit") -> R.string.hosted_extra_help_limit
        failure.code == "helper_output_refused" -> R.string.error_request_refused
        failure.code == "helper_output_incomplete" -> R.string.error_incomplete_response
        failure.code == "provider_connection_lost" -> R.string.error_transport_network_lost
        failure.code == "helper_session_funding_unavailable" -> R.string.hosted_help_funding
        failure.code in setOf("provider_reconciliation_required", "minute_balance_reconciliation_required", "minute_purchase_reconciliation_required", "helper_provider_reconciliation_required") -> R.string.hosted_balance_checking
        failure.code == "helper_session_window_closed" -> R.string.hosted_window_closed
        failure.code in setOf("helper_request_already_attempted", "helper_response_uncertain", "live_request_already_created", "provider_session_unconfirmed") -> R.string.hosted_unconfirmed
        failure.code == "live_session_unresolved" -> R.string.hosted_checking_previous
        failure.code == "provider_create_rejected" -> R.string.hosted_start_rejected
        failure.status == 429 -> R.string.hosted_rate_limit
        failure.code in setOf("hosted_voice_not_ready", "hosted_helpers_not_ready", "hosted_paid_not_ready", "hosted_funding_cap_reached") -> R.string.hosted_unavailable
        else -> R.string.hosted_request_failed
    }
    else -> R.string.hosted_request_failed
}

internal fun needsAccountRecovery(error: Throwable): Boolean = error == HostedFailure.SignInRequired ||
    (error is HostedFailure.Http && (error.status == 401 || error.code == "sign_in_to_continue")) ||
    (error is AccountFailure.Http && error.status == 401)

internal fun requestErrorReference(error: Throwable): String? = safeRequestErrorReference(when (error) {
    is HostedFailure.Http -> error.reference
    is AccountFailure.Http -> error.reference
    else -> null
})

/** Whether the failure means the learner needs to add or fix their API key in Settings. */
internal fun errorNeedsKeySetup(e: Throwable): Boolean =
    e is APIClient.APIException.MissingKey || (e is APIClient.APIException.Http && e.status == 401)

/** Imported partial conversations are history, not local sessions awaiting cloud recovery. */
internal fun prepareImportedArchive(data: String, importedAt: Double = nowSeconds()): Archive =
    ArchiveCodec.decode(data).also { imported ->
        imported.sessions.filter { it.endedAt == null }.forEach {
            it.endedAt = importedAt
            it.endReason = "Imported unfinished conversation"
        }
    }

class MuralViewModel(application: Application) : AndroidViewModel(application) {
    // Keep intent, not an Activity-capturing callback, while the learner reviews consent.
    var pendingCloudAction: CloudAction? = null
    var archive by mutableStateOf(Archive()); private set
    var loadingHistory by mutableStateOf(true); private set
    var session by mutableStateOf<SessionRecord?>(null); private set
    var state by mutableStateOf("idle"); private set
    var error by mutableStateOf<String?>(null); private set
    var errorNeedsKeySetup by mutableStateOf(false); private set
    var errorNeedsAccountSignIn by mutableStateOf(false); private set
    var typedReplyError by mutableStateOf<String?>(null); private set
    var typedRepliesSent by mutableStateOf(0); private set
    fun clearTypedReplyError() { typedReplyError = null }
    var notice by mutableStateOf<String?>(null); private set
    var meaning by mutableStateOf(""); private set
    var translating by mutableStateOf(false); private set
    var meaningFailed by mutableStateOf(false); private set
    var meaningLimitReached by mutableStateOf(false); private set
    var working by mutableStateOf(false); private set
    var isMuted by mutableStateOf(false); private set
    var isMediaPaused by mutableStateOf(false); private set
    var inputLevel by mutableStateOf(0.0); private set
    var outputLevel by mutableStateOf(0.0); private set
    var selectedTheme by mutableStateOf<ConversationTheme?>(null); private set
    var lookupResult by mutableStateOf<String?>(null); private set
    var lookupError by mutableStateOf<String?>(null); private set
    var lookupLoading by mutableStateOf(false); private set
    private var lookupGeneration = 0L
    private var lookupJob: Job? = null
    var topicResult by mutableStateOf<TopicBrief?>(null); private set
    var hasKey by mutableStateOf(false); private set
    var credentialProvider by mutableStateOf<CredentialStore.ProviderType?>(null); private set
    val language get() = LanguageRegistry.get(archive.preferences.learningLanguageID)!!
    val learner get() = LearningEngine.project(archive.sessions, language.id, archive.preferences.hiddenWords)
    val isRunning get() = state in listOf("connecting", "active", "closing")
    val isVoiceSession get() = voiceSession

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val repository = LearningRepository(application)
    private val credentials = CredentialStore(application)
    private val api = APIClient(credentials)
    private val transport = LiveTransport(application, viewModelScope)
    private val providerStore = ConversationProviderStore(application)
    private val voiceSettingsStore = VoiceSettingsStore(application)
    private val topicEcosystemStore = TopicEcosystemStore(application)
    private val learningTestStore = LearningTestStore(application)
    private val hostedConfiguration = HostedConfiguration.parse(BuildConfig.MANAGED_API_ORIGIN)
    private val accountConfiguration = ManagedAccountConfiguration.parse(BuildConfig.MANAGED_API_ORIGIN, BuildConfig.GOOGLE_SERVER_CLIENT_ID)
    private val memberSessions = accountConfiguration?.let { AccountSessionStore(application, it.origin.toString()) }
    private val guests = hostedConfiguration?.let { config ->
        GuestMinuteController(GuestInstallationStore(application, config.origin.toString()), GuestMinuteClient(config), {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(java.security.SecureRandom()::nextBytes))
        })
    }
    var guestState by mutableStateOf(GuestMinuteState()); private set
    var showMinuteAccess by mutableStateOf(false); private set
    private var selectedAccount = AccountState(busy = true)
    private var accessJob: Job? = null
    private val hostedBindings = HostedConversationBindings(viewModelScope)
    private var hostedSessionIDs = emptySet<String>()
    private var pendingHostedOwnerID: String? = null
    /** Reauthentication may renew this account only; another account cannot replace an unresolved owner. */
    suspend fun pendingMemberForSignIn(): String? {
        clearAcknowledgedGuestMarker()
        guests?.expectedMemberID()?.let { return it }
        val pending = pendingHostedOwnerID ?: return null
        if (guests?.owns(pending) != true) return pending
        // Renewal retains the same guest identity, allowing an interrupted conversation to settle
        // before Google login. A guest UUID is never sent as Google's expected member identity.
        if (guests.session(pending) == null) guests.acquire()
        return null
    }
    private var reconciliationJob: Job? = null
    var conversationProvider by mutableStateOf(ConversationProvider.PERSONAL_KEY); private set
    var hostedReadiness by mutableStateOf(HostedReadiness()); private set
    private val readiness = HostedReadinessController(viewModelScope,
        readSession = { availableHostedOwner() },
        fetch = { owner ->
            val enabled = hostedClient(owner.accountID).available()
            val balance = if (enabled) hostedBalance(owner) else null
            HostedReadiness(owner.accountID, balance?.readinessMilliseconds ?: 0, enabled)
        }, changed = { hostedReadiness = it })
    var accountChangeBlocked by mutableStateOf(true); private set
    var voiceSpeed by mutableStateOf(voiceSettingsStore.voiceSpeed); private set
    var cleanVoiceMode by mutableStateOf(voiceSettingsStore.cleanVoiceMode); private set
    var voiceTimbre by mutableStateOf(voiceSettingsStore.voiceTimbre); private set
    var appLanguage by mutableStateOf(voiceSettingsStore.appLanguage); private set
    var darkTheme by mutableStateOf(voiceSettingsStore.darkTheme); private set
    private var _backgroundVoiceEnabled by mutableStateOf(voiceSettingsStore.backgroundVoiceEnabled)
    val backgroundVoiceEnabled: Boolean get() = _backgroundVoiceEnabled
    private var _mediaOutputEnabled by mutableStateOf(voiceSettingsStore.mediaOutputEnabled)
    val mediaOutputEnabled: Boolean get() = _mediaOutputEnabled
    private var _appVolume by mutableStateOf(voiceSettingsStore.appVolume)
    val appVolume: Float get() = _appVolume
    var hasSeenApiKeyGuide by mutableStateOf(voiceSettingsStore.hasSeenApiKeyGuide); private set
    fun markApiKeyGuideSeen() {
        voiceSettingsStore.hasSeenApiKeyGuide = true
        hasSeenApiKeyGuide = true
    }
    var favoriteThemeIDs by mutableStateOf(topicEcosystemStore.favoriteThemeIDs); private set
    var currentPedagogicalMode by mutableStateOf(topicEcosystemStore.getPedagogicalMode(selectedTheme?.id)); private set
    var currentMcerLevel by mutableStateOf(topicEcosystemStore.getMcerLevel(selectedTheme?.id)); private set

    var learningTests by mutableStateOf<List<LearningTest>>(emptyList()); private set
    var activeTest by mutableStateOf<LearningTest?>(null); private set
    var activeFlashcards by mutableStateOf<List<FlashcardItem>?>(null); private set
    var generatingTestIds by mutableStateOf<Set<String>>(emptySet()); private set
    val testGenerating: Boolean get() = generatingTestIds.isNotEmpty()
    var attemptSummaries by mutableStateOf<Map<String, AttemptSummary>>(emptyMap()); private set
    var activityDaysThisWeek by mutableStateOf(0); private set
    var guardrailStep by mutableStateOf(0); private set
    var endedSessionSnapshot by mutableStateOf<SessionRecord?>(null); private set
    var postSessionThemeDraft by mutableStateOf<GeneratedCustomTheme?>(null); private set
    var showPostSessionThemeModal by mutableStateOf(false); private set
    var showPostSessionTestModal by mutableStateOf(false); private set
    var postSessionGeneratingTheme by mutableStateOf(false); private set
    var showGuardrailDialog by mutableStateOf(false); private set
    var guardrailPendingAction by mutableStateOf<(() -> Unit)?>(null); private set

    var themeTranslationsVersion by mutableStateOf(0); private set

    fun selectAppLanguage(languageID: String) {
        val validated = AppLanguages.get(languageID).id
        voiceSettingsStore.appLanguage = validated
        appLanguage = validated
        ensureCustomThemesTranslated(customThemes, validated)
    }

    fun selectVoiceSpeed(speed: String) {
        voiceSettingsStore.voiceSpeed = speed
        voiceSpeed = speed
        if (state == "active" && voiceSession) {
            command("instructions", TeachingPolicy.voiceCadenceGuidance(speed))
        }
    }

    fun selectVoiceTimbre(timbre: String) {
        voiceSettingsStore.voiceTimbre = timbre
        voiceTimbre = timbre
    }

    fun toggleCleanVoice() {
        val next = !cleanVoiceMode
        voiceSettingsStore.cleanVoiceMode = next
        cleanVoiceMode = next
    }

    fun updateDarkTheme(enabled: Boolean) {
        voiceSettingsStore.darkTheme = enabled
        darkTheme = enabled
    }

    fun shouldStayAliveInBackground(): Boolean = backgroundVoiceEnabled && isRunning && voiceSession

    fun setBackgroundVoiceEnabled(enabled: Boolean) {
        voiceSettingsStore.backgroundVoiceEnabled = enabled
        _backgroundVoiceEnabled = enabled
        if (isRunning && voiceSession) {
            if (enabled) {
                startBackgroundVoiceService()
            } else {
                stopBackgroundVoiceService()
            }
        }
    }

    fun setMediaOutputEnabled(enabled: Boolean) {
        voiceSettingsStore.mediaOutputEnabled = enabled
        _mediaOutputEnabled = enabled
        if (isRunning && voiceSession) {
            notice = getApplication<Application>().getString(R.string.notice_media_output_next_session)
        }
    }

    fun setAppVolume(volume: Float) {
        val clamped = if (volume.isNaN()) VoiceSettingsStore.DEFAULT_APP_VOLUME else volume.coerceIn(0f, 1f)
        voiceSettingsStore.appVolume = clamped
        _appVolume = clamped
        transport.setAppVolume(clamped)
        if (isRunning && voiceSession) {
            publishLiveSessionSnapshot()
        }
    }

    fun adjustAppVolume(delta: Float) {
        val raw = (appVolume + delta).coerceIn(0f, 1f)
        val rounded = (round(raw * 20f) / 20f).coerceIn(0f, 1f)
        setAppVolume(rounded)
    }

    fun refreshLiveSessionNotification() {
        if (shouldStayAliveInBackground()) {
            publishLiveSessionSnapshot()
            YamySessionCoordinator.requestNotificationRefresh()
        }
    }

    private val mediaActionHandler = object : YamySessionCoordinator.ActionHandler {
        override fun onToggleMute() = toggleMute()
        override fun onTogglePause() = toggleMediaPause()
        override fun onVolumeDelta(delta: Float) = adjustAppVolume(delta)
        override fun onHelp() = help()
        override fun onEndCall() = end("Ended from media controls")
    }

    private fun publishLiveSessionSnapshot() {
        if (!backgroundVoiceEnabled || !isRunning || !voiceSession) return
        val app = getApplication<Application>()
        val currentSession = session
        val title = selectedTheme?.title?.takeIf { it.isNotBlank() }
            ?: currentSession?.title?.takeIf { it.isNotBlank() }
            ?: language.defaultTitle.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.notif_default_title)
        val statusLabel = when {
            state == "connecting" -> app.getString(R.string.notif_status_connecting)
            isMediaPaused -> app.getString(R.string.notif_status_paused)
            isMuted -> app.getString(R.string.notif_status_muted)
            else -> app.getString(R.string.notif_status_listening)
        }
        val albumLabel = app.getString(R.string.notif_album_format, language.name, currentMcerLevel)
        val elapsedMs = currentSession?.let { ((nowSeconds() - it.startedAt) * 1000.0).toLong().coerceAtLeast(0L) } ?: 0L
        val startedAtMs = System.currentTimeMillis() - elapsedMs
        val totalDurationMs = archive.preferences.sessionMinutes * 60_000L
        YamySessionCoordinator.publish(
            YamyLiveSessionSnapshot(
                title = title,
                subtitleStatus = statusLabel,
                albumLabel = albumLabel,
                isRunning = isRunning,
                isConnecting = state == "connecting",
                isMuted = isMuted,
                isPaused = isMediaPaused,
                appVolume = appVolume,
                startedAtMs = startedAtMs,
                totalDurationMs = totalDurationMs,
                mediaOutputEnabled = mediaOutputEnabled,
            )
        )
    }

    private fun startBackgroundVoiceService() {
        YamySessionCoordinator.registerHandler(mediaActionHandler)
        publishLiveSessionSnapshot()
        runCatching {
            val app = getApplication<Application>()
            val intent = Intent(app, YamyVoiceForegroundService::class.java).apply {
                action = YamyVoiceForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(app, intent)
        }
    }

    private fun stopBackgroundVoiceService() {
        YamySessionCoordinator.unregisterHandler(mediaActionHandler)
        YamySessionCoordinator.clear()
        runCatching {
            val app = getApplication<Application>()
            app.stopService(Intent(app, YamyVoiceForegroundService::class.java))
        }
    }

    fun toggleFavoriteTheme(themeID: String): Boolean {
        val isNowFav = topicEcosystemStore.toggleFavorite(themeID)
        favoriteThemeIDs = topicEcosystemStore.favoriteThemeIDs
        return isNowFav
    }

    fun isThemeFavorite(themeID: String): Boolean = favoriteThemeIDs.contains(themeID)

    fun getThemeMcerLevel(themeID: String?): String = topicEcosystemStore.getMcerLevel(themeID)

    fun setThemeMcerLevel(themeID: String?, level: String) {
        val normalized = level.uppercase().trim().takeIf { it in TopicEcosystemStore.MCER_LEVELS } ?: TopicEcosystemStore.DEFAULT_MCER
        topicEcosystemStore.setMcerLevel(themeID, normalized)
        val isCurrentContext = themeID == null || themeID == "general" || selectedTheme?.id == themeID || (selectedTheme == null && themeID == "general")
        if (isCurrentContext) {
            currentMcerLevel = normalized
        }
        if (state == "active" && voiceSession && isCurrentContext) {
            command("instructions", TeachingPolicy.mcerGuidance(normalized))
        }
    }

    fun getThemePersonality(themeID: String): String = topicEcosystemStore.getPersonality(themeID)

    fun setThemePersonality(themeID: String, personality: String) {
        topicEcosystemStore.setPersonality(themeID, personality)
        if (state == "active" && voiceSession && (selectedTheme?.id == themeID || (selectedTheme == null && themeID == "general"))) {
            command("instructions", TeachingPolicy.personalityGuidance(personality))
        }
    }

    fun getThemeMemory(themeID: String): String? = topicEcosystemStore.getThemeMemory(themeID)

    fun getStructuredThemeMemory(themeID: String): ThemePersistentMemory = topicEcosystemStore.getStructuredThemeMemory(themeID)

    fun setThemeMemory(themeID: String, memory: String) {
        topicEcosystemStore.setThemeMemory(themeID, memory)
    }

    fun getThemePedagogicalMode(themeID: String?): String = topicEcosystemStore.getPedagogicalMode(themeID)

    fun setThemePedagogicalMode(themeID: String?, mode: String) {
        val normalized = mode.lowercase().trim().takeIf { it in TopicEcosystemStore.PEDAGOGICAL_MODES } ?: TopicEcosystemStore.DEFAULT_MODE
        topicEcosystemStore.setPedagogicalMode(themeID, normalized)
        val isCurrentContext = themeID == null || themeID == "general" || selectedTheme?.id == themeID || (selectedTheme == null && themeID == "general")
        if (isCurrentContext) {
            currentPedagogicalMode = normalized
        }
        if (state == "active" && voiceSession && isCurrentContext) {
            command("instructions", TeachingPolicy.pedagogicalModeGuidance(normalized, language))
        }
    }

    fun getSessionsForTheme(themeID: String): List<SessionRecord> {
        return archive.sessions.filter { it.themeID == themeID }.sortedByDescending { it.startedAt }
    }

    data class GeneratedCustomTheme(
        val theme: ConversationTheme,
        val mcerLevel: String,
        val personality: String,
        val systemPromptRole: String
    )

    var customThemes by mutableStateOf<List<ConversationTheme>>(emptyList()); private set

    val allThemes: List<ConversationTheme>
        get() = (customThemes + language.themes.filterNot { it.id == "today" }).distinctBy { it.id }

    fun refreshCustomThemes() {
        customThemes = topicEcosystemStore.getAllCustomThemes()
        ensureCustomThemesTranslated(customThemes, appLanguage)
    }

    fun saveCustomTheme(theme: ConversationTheme) {
        topicEcosystemStore.saveCustomTheme(language.id, theme)
        topicEcosystemStore.saveCachedThemeTranslation(
            theme.id,
            appLanguage,
            CachedThemeTranslation(theme.title, theme.subtitle, theme.situation)
        )
        refreshCustomThemes()
    }

    fun ensureCustomThemesTranslated(themes: List<ConversationTheme>, targetLang: String) {
        val customOnly = themes.filter { it.id.startsWith("custom_") }
        if (customOnly.isEmpty() || !cloudReady()) return

        for (theme in customOnly) {
            val cached = topicEcosystemStore.getCachedThemeTranslation(theme.id, targetLang)
            if (cached != null) continue

            viewModelScope.launch {
                try {
                    val targetName = AppLanguages.get(targetLang).name
                    val instructions = TeachingPolicy.themeTranslationPrompt(targetLang, targetName)
                    val input = "Title: ${theme.title}\nSubtitle: ${theme.subtitle}\nSituation: ${theme.situation}"
                    val result = teaching(
                        localID = null,
                        purpose = HelperPurpose.TOPIC,
                        logicalID = UUID.randomUUID().toString(),
                        instructions = instructions,
                        input = input
                    )
                    val raw = result.text.trim()
                    val firstBrace = raw.indexOf('{')
                    val lastBrace = raw.lastIndexOf('}')
                    val clean = if (firstBrace in 0 until lastBrace) raw.substring(firstBrace, lastBrace + 1) else raw
                    val json = Json.parseToJsonElement(clean).jsonObject
                    val title = json["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: theme.title
                    val subtitle = json["subtitle"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: theme.subtitle
                    val situation = json["situation"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: theme.situation

                    val translation = CachedThemeTranslation(title, subtitle, situation)
                    topicEcosystemStore.saveCachedThemeTranslation(theme.id, targetLang, translation)
                    themeTranslationsVersion++
                } catch (e: Exception) {
                    android.util.Log.w("MuralThemeTrans", "Error translating custom theme ${theme.id} to $targetLang", e)
                }
            }
        }
    }

    fun createCustomTheme(prompt: String, onDone: ((GeneratedCustomTheme) -> Unit)? = null) {
        if (prompt.isBlank() || working || !cloudReady()) return
        val token = generation
        val module = language
        working = true
        actionJob = viewModelScope.launch {
            try {
                val instructions = TeachingPolicy.createTheme(module)
                val result = teaching(
                    localID = session?.id,
                    purpose = HelperPurpose.TOPIC,
                    logicalID = UUID.randomUUID().toString(),
                    instructions = instructions,
                    input = prompt.trim().take(500),
                    schema = null,
                    search = false
                )
                if (token != generation) return@launch

                val raw = result.text.trim()
                val firstBrace = raw.indexOf('{')
                val lastBrace = raw.lastIndexOf('}')
                val clean = if (firstBrace in 0 until lastBrace) {
                    raw.substring(firstBrace, lastBrace + 1)
                } else if (raw.startsWith("```")) {
                    raw.substringAfter("\n").substringBeforeLast("```").trim()
                } else raw

                val json = try {
                    Json.parseToJsonElement(clean) as? JsonObject
                } catch (_: Exception) {
                    null
                }

                val title = (json?.get("title") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: prompt.take(24)
                val situation = (json?.get("situation") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: prompt
                val mcer = (json?.get("defaultMcerLevel") as? JsonPrimitive)?.contentOrNull?.uppercase()?.trim()
                    ?.takeIf { it in TopicEcosystemStore.MCER_LEVELS } ?: TopicEcosystemStore.DEFAULT_MCER
                val personality = (json?.get("tutorPersonality") as? JsonPrimitive)?.contentOrNull?.lowercase()?.trim()
                    ?.takeIf { it in TopicEcosystemStore.PERSONALITIES } ?: TopicEcosystemStore.DEFAULT_PERSONALITY
                val systemPromptRole = (json?.get("systemPromptRole") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: situation

                val existingMatch = customThemes.firstOrNull {
                    it.title.trim().equals(title.trim(), ignoreCase = true)
                }
                val themeID = existingMatch?.id ?: ("custom_" + UUID.randomUUID().toString().take(8))
                val theme = ConversationTheme(
                    id = themeID,
                    title = title,
                    subtitle = situation.take(45),
                    symbol = "sparkles",
                    category = "Personalizados",
                    situation = systemPromptRole,
                    colorIndex = 1
                )
                topicEcosystemStore.setMcerLevel(themeID, mcer)
                topicEcosystemStore.setPersonality(themeID, personality)
                saveCustomTheme(theme)
                selectedTheme = theme
                currentMcerLevel = mcer
                currentPedagogicalMode = topicEcosystemStore.getPedagogicalMode(themeID)

                val draft = GeneratedCustomTheme(theme, mcer, personality, systemPromptRole)
                onDone?.invoke(draft)
            } catch (e: Exception) {
                postSessionGeneratingTheme = false
                if (token == generation) presentError(e, R.string.error_find_topic_failed)
            } finally {
                if (token == generation) working = false
            }
        }
    }

    fun refreshLearningTests() {
        learningTests = learningTestStore.getAllTests()
        attemptSummaries = learningTestStore.getAttemptSummaries()
        activityDaysThisWeek = learningTestStore.getActivityDaysThisWeek()
    }

    fun selectActiveTest(test: LearningTest?) {
        activeTest = test
        activeFlashcards = test?.flashcards
    }

    fun selectActiveFlashcards(cards: List<FlashcardItem>?) {
        activeFlashcards = cards
    }

    fun deleteLearningTest(testId: String) {
        learningTestStore.deleteTest(testId)
        refreshLearningTests()
        if (activeTest?.id == testId) {
            activeTest = null
        }
    }

    fun toggleTestFavorite(testId: String) {
        val test = learningTests.find { it.id == testId } ?: return
        learningTestStore.setFavorite(testId, !test.isFavorite)
        refreshLearningTests()
    }

    fun saveTestAttempt(attempt: TestUserAttempt) {
        learningTestStore.saveAttempt(attempt)
        refreshLearningTests()
        val test = learningTests.find { it.id == attempt.testId } ?: activeTest?.takeIf { it.id == attempt.testId }
        val tid = test?.topicId ?: selectedTheme?.id ?: "general"
        val missed = test?.items?.filter { attempt.correctStatus[it.id] == false }?.map { "${it.prompt.take(45)} (${it.correctAnswer.take(30)})" }?.take(6) ?: emptyList()
        topicEcosystemStore.recordTestAttemptMemory(tid, attempt.accuracyPercentage, missed)
    }

    fun getLatestAttempt(testId: String): TestUserAttempt? {
        return learningTestStore.getLatestAttempt(testId)
    }

    fun getAttemptSummary(testId: String): AttemptSummary? {
        return attemptSummaries[testId] ?: learningTestStore.getAttemptSummary(testId)
    }

    fun requestEndSession(onDirectEnd: (() -> Unit)? = null) {
        if (isRunning || session != null) {
            endedSessionSnapshot = session?.let(::clone)
            guardrailPendingAction = onDirectEnd
            guardrailStep = 1
            showGuardrailDialog = true
        } else {
            onDirectEnd?.invoke()
        }
    }

    fun confirmEndSessionStep1() {
        endedSessionSnapshot = session?.let(::clone) ?: endedSessionSnapshot
        if (isRunning) end("Ended by you")
        val activeTheme = selectedTheme ?: endedSessionSnapshot?.themeID?.let { tid ->
            allThemes.firstOrNull { it.id == tid } ?: customThemes.firstOrNull { it.id == tid }
        }
        val hasActiveTheme = activeTheme != null && activeTheme.id != "general" && activeTheme.id != "today"
        if (hasActiveTheme) {
            endedSessionSnapshot?.let { snap ->
                updateThemeCheckpoint(activeTheme.id, snap)
            }
            guardrailStep = 3
        } else {
            guardrailStep = 2
        }
        showGuardrailDialog = true
    }

    fun quickCloseGuardrail() {
        if (isRunning) end("Ended by user quick close")
        dismissGuardrail()
        val pending = guardrailPendingAction
        guardrailPendingAction = null
        pending?.invoke()
    }

    fun updateThemeCheckpoint(themeID: String, snapshot: SessionRecord) {
        val passages = snapshot.passages
        if (passages.isEmpty()) return

        val transcriptSummary = passages.takeLast(10).joinToString("\n") { p ->
            val role = if (p.speaker == Speaker.user) "Learner" else "Tutor"
            "$role: ${p.text}"
        }
        val contextErrors = snapshot.assessments.mapNotNull { it.nextGoal.takeIf { g -> g.isNotBlank() } }
        val contextWords = snapshot.assessments.flatMap { it.words }.map { it.lemma }

        if (!cloudReady() || working) {
            topicEcosystemStore.recordSessionMemory(
                themeID = themeID,
                sessionSummary = transcriptSummary.take(200).ifBlank { snapshot.title },
                detectedErrors = contextErrors,
                practicedLemmas = contextWords
            )
            return
        }

        viewModelScope.launch {
            try {
                val existing = topicEcosystemStore.getStructuredThemeMemory(themeID)
                val instructions = TeachingPolicy.themeCheckpointPrompt(language)
                val input = """
THEME ID: $themeID
PRIOR CHECKPOINT: ${existing.checkpointSummary}
PREVIOUS TOPICS: ${existing.keyTopicsDiscussed.joinToString(", ")}
PREVIOUS ERRORS: ${existing.frequentErrors.joinToString(", ")}
RECENT SESSION TRANSCRIPT:
$transcriptSummary
""".trimIndent()
                val result = teaching(
                    localID = snapshot.id,
                    purpose = HelperPurpose.TOPIC,
                    logicalID = UUID.randomUUID().toString(),
                    instructions = instructions,
                    input = input
                )
                val raw = result.text.trim()
                val firstBrace = raw.indexOf('{')
                val lastBrace = raw.lastIndexOf('}')
                val clean = if (firstBrace in 0 until lastBrace) raw.substring(firstBrace, lastBrace + 1) else raw
                val json = Json.parseToJsonElement(clean).jsonObject

                val checkpointSummary = json["checkpointSummary"]?.jsonPrimitive?.contentOrNull ?: ""
                val lastSessionSummary = json["lastSessionSummary"]?.jsonPrimitive?.contentOrNull ?: ""
                val keyTopics = json["keyTopicsDiscussed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val errors = (json["frequentErrors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()) + contextErrors
                val words = (json["practicedWords"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()) + contextWords

                topicEcosystemStore.recordCheckpointMemory(
                    themeID = themeID,
                    checkpointSummary = checkpointSummary,
                    lastSessionSummary = lastSessionSummary,
                    keyTopics = keyTopics,
                    detectedErrors = errors,
                    practicedWords = words
                )
            } catch (e: Exception) {
                android.util.Log.w("MuralCheckpoint", "Failed to update theme checkpoint via master, using fallback", e)
                topicEcosystemStore.recordSessionMemory(
                    themeID = themeID,
                    sessionSummary = transcriptSummary.take(200).ifBlank { snapshot.title },
                    detectedErrors = contextErrors,
                    practicedLemmas = contextWords
                )
            }
        }
    }

    fun dismissGuardrail() {
        guardrailStep = 0
        showGuardrailDialog = false
        guardrailPendingAction = null
    }

    fun confirmGenerateThemeStep2() {
        if (postSessionGeneratingTheme) return
        val snap = endedSessionSnapshot ?: session
        val transcriptSummary = snap?.passages?.takeLast(8)?.joinToString(" | ") { "${it.speaker.name}: ${it.text.take(60)}" } ?: ""
        val contextErrors = snap?.assessments?.mapNotNull { it.nextGoal.takeIf { g -> g.isNotBlank() } } ?: emptyList()
        val contextWords = snap?.assessments?.flatMap { it.words }?.map { it.lemma } ?: emptyList()

        val activeCustomTheme = selectedTheme?.takeIf { it.id.startsWith("custom_") }
            ?: snap?.themeID?.let { tid -> customThemes.firstOrNull { it.id == tid } }

        if (activeCustomTheme != null) {
            topicEcosystemStore.recordSessionMemory(
                activeCustomTheme.id,
                transcriptSummary.ifBlank { snap?.title ?: "Sesión previa" }.take(200),
                contextErrors,
                contextWords
            )
            saveCustomTheme(activeCustomTheme)
            selectedTheme = activeCustomTheme
            val draft = GeneratedCustomTheme(
                theme = activeCustomTheme,
                mcerLevel = topicEcosystemStore.getMcerLevel(activeCustomTheme.id),
                personality = topicEcosystemStore.getPersonality(activeCustomTheme.id),
                systemPromptRole = activeCustomTheme.situation
            )
            postSessionThemeDraft = draft
            postSessionGeneratingTheme = false
            guardrailStep = 0
            showGuardrailDialog = false
            showPostSessionThemeModal = true
            return
        }

        if (!cloudReady() || working) {
            val title = snap?.title?.takeIf { it.isNotBlank() } ?: (if (transcriptSummary.isNotBlank()) "Sesión de práctica" else language.defaultTitle)
            val existing = customThemes.firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
            val themeID = existing?.id ?: ("custom_" + UUID.randomUUID().toString().take(8))
            val theme = ConversationTheme(
                id = themeID,
                title = title,
                subtitle = transcriptSummary.take(45).ifBlank { "Práctica personalizada" },
                symbol = "sparkles",
                category = "Personalizados",
                situation = transcriptSummary.ifBlank { title },
                colorIndex = 1
            )
            val draft = GeneratedCustomTheme(theme, currentMcerLevel, getThemePersonality(selectedTheme?.id ?: "general"), transcriptSummary.ifBlank { title })
            saveCustomTheme(draft.theme)
            selectedTheme = draft.theme
            topicEcosystemStore.recordSessionMemory(
                draft.theme.id,
                transcriptSummary.ifBlank { snap?.title ?: "Sesión previa" }.take(200),
                contextErrors,
                contextWords
            )
            postSessionThemeDraft = draft
            postSessionGeneratingTheme = false
            guardrailStep = 0
            showGuardrailDialog = false
            showPostSessionThemeModal = true
            return
        }

        postSessionGeneratingTheme = true
        val prompt = if (transcriptSummary.isNotBlank()) {
            "Conversación sobre: ${snap?.title ?: language.defaultTitle}. Contexto: $transcriptSummary"
        } else {
            snap?.title ?: language.defaultTitle
        }

        createCustomTheme(prompt = prompt) { draft ->
            topicEcosystemStore.recordSessionMemory(
                draft.theme.id,
                transcriptSummary.ifBlank { snap?.title ?: "Sesión previa" }.take(200),
                contextErrors,
                contextWords
            )
            postSessionThemeDraft = draft
            postSessionGeneratingTheme = false
            guardrailStep = 0
            showGuardrailDialog = false
            showPostSessionThemeModal = true
        }
    }

    fun onPostSessionThemeModalClosed(savedTheme: ConversationTheme? = null) {
        showPostSessionThemeModal = false
        postSessionThemeDraft = null
        postSessionGeneratingTheme = false
        if (savedTheme != null) {
            saveCustomTheme(savedTheme)
            selectedTheme = savedTheme
        }
        guardrailStep = 3
        showGuardrailDialog = true
    }

    fun skipGenerateThemeStep2() {
        guardrailStep = 3
        showGuardrailDialog = true
    }

    fun confirmOpenTestCreatorStep3() {
        guardrailStep = 0
        showGuardrailDialog = false
        showPostSessionTestModal = true
    }

    fun onPostSessionTestGenerated(config: TestConfig, onNavigateToTests: () -> Unit) {
        showPostSessionTestModal = false
        val ctx = endedSessionSnapshot?.let { TeachingPolicy.context(it) } ?: ""
        val finalConfig = if (config.topicId == null && selectedTheme != null) config.copy(topicId = selectedTheme?.id) else config
        generateTest(config = finalConfig, conversationContext = ctx, sessionID = endedSessionSnapshot?.id, onDone = onNavigateToTests)
        val pending = guardrailPendingAction
        guardrailPendingAction = null
        pending?.invoke()
        onNavigateToTests()
    }

    fun dismissPostSessionTestModal() {
        showPostSessionTestModal = false
        val pending = guardrailPendingAction
        guardrailPendingAction = null
        pending?.invoke()
    }

    fun confirmGuardrailFinish() {
        guardrailStep = 0
        showGuardrailDialog = false
        val pending = guardrailPendingAction
        guardrailPendingAction = null
        pending?.invoke()
    }

    fun confirmGuardrailGenerate(onOpenTest: () -> Unit) {
        guardrailStep = 0
        showGuardrailDialog = false
        val pending = guardrailPendingAction
        guardrailPendingAction = null
        if (isRunning) end()
        pending?.invoke()

        val snap = endedSessionSnapshot ?: session
        val currentPassages = snap?.passages ?: emptyList()
        val topicTitle = snap?.title ?: selectedTheme?.title ?: language.defaultTitle
        val mcerLevel = currentMcerLevel
        val transcriptContext = currentPassages.takeLast(12).joinToString("\n") { p ->
            "${p.speaker.name.uppercase()}: ${p.text}"
        }

        val config = TestConfig(
            prompt = topicTitle,
            topicId = selectedTheme?.id,
            languageID = language.id,
            mcerLevel = mcerLevel,
            allowedTypes = TestItemType.entries,
            questionCount = 8,
            mediumCount = 8,
            includeFlashcards = true
        )

        generateTest(
            config = config,
            conversationContext = transcriptContext,
            sessionID = snap?.id,
            onDone = onOpenTest
        )
    }

    fun requestChooseTheme(theme: ConversationTheme?, onDone: () -> Unit) {
        chooseTheme(theme)
        onDone()
    }

    fun generateTest(
        config: TestConfig,
        conversationContext: String = "",
        avoidPrompts: List<String> = emptyList(),
        existingTestId: String? = null,
        sessionID: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (generatingTestIds.size >= 2 || !cloudReady()) return

        val testId = existingTestId ?: ("test_" + UUID.randomUUID().toString().take(8))
        val targetModule = LanguageRegistry.get(config.languageID) ?: language

        val placeholder = LearningTest(
            id = testId,
            title = if (config.prompt.isNotBlank()) config.prompt.take(45) else "Evaluación: ${targetModule.name}",
            topicId = config.topicId,
            topicTitle = if (config.prompt.isNotBlank()) config.prompt else "Práctica general",
            mcerLevel = config.mcerLevel,
            difficulty = TestDifficulty.MEDIUM,
            status = TestStatus.GENERATING,
            config = config,
            languageID = targetModule.id
        )
        learningTestStore.upsertTest(placeholder)
        generatingTestIds = generatingTestIds + testId
        refreshLearningTests()

        viewModelScope.launch {
            try {
                val instructions = TeachingPolicy.generateTest(
                    language = targetModule,
                    config = config,
                    conversationContext = conversationContext,
                    avoidPrompts = avoidPrompts
                )

                val result = teaching(
                    localID = sessionID ?: session?.id,
                    purpose = HelperPurpose.TEST,
                    logicalID = UUID.randomUUID().toString(),
                    instructions = instructions,
                    input = if (config.prompt.isNotBlank()) config.prompt.take(500) else "Práctica de ${targetModule.name}",
                    schema = null,
                    search = false
                )

                val raw = result.text.trim()
                val parsed = parseLearningTest(raw, config, targetModule.id, testId, createdAt = placeholder.createdAt)
                if (parsed != null && (parsed.items.isNotEmpty() || parsed.flashcards.isNotEmpty())) {
                    val ready = parsed.copy(status = TestStatus.READY, createdAt = placeholder.createdAt)
                    learningTestStore.upsertTest(ready)
                    refreshLearningTests()
                    if (activeTest == null || activeTest?.id == testId || onDone != null) {
                        activeTest = ready
                        activeFlashcards = ready.flashcards
                    }
                    onDone?.invoke()
                } else {
                    val failed = placeholder.copy(
                        status = TestStatus.FAILED,
                        errorMessage = "La respuesta de la IA no pudo interpretarse."
                    )
                    learningTestStore.upsertTest(failed)
                    refreshLearningTests()
                }
            } catch (e: Exception) {
                val failed = placeholder.copy(
                    status = TestStatus.FAILED,
                    errorMessage = e.message?.take(90) ?: "Error de red al generar el test."
                )
                learningTestStore.upsertTest(failed)
                refreshLearningTests()
            } finally {
                generatingTestIds = generatingTestIds - testId
                refreshLearningTests()
            }
        }
    }

    fun generateTest(
        topicTitle: String,
        mcerLevel: String,
        difficulty: TestDifficulty,
        targetLanguage: LanguageModule = language,
        conversationContext: String = "",
        userPrompt: String = "",
        questionCount: Int = 10,
        onDone: (() -> Unit)? = null
    ) {
        val config = TestConfig(
            prompt = userPrompt.ifBlank { topicTitle },
            topicId = null,
            languageID = targetLanguage.id,
            mcerLevel = mcerLevel,
            allowedTypes = TestItemType.entries,
            questionCount = questionCount.coerceIn(5, 30),
            includeFlashcards = true
        )
        generateTest(config, conversationContext = conversationContext, onDone = onDone)
    }

    fun retryFailedTest(testId: String) {
        val test = learningTests.find { it.id == testId } ?: return
        val cfg = test.config ?: TestConfig(
            prompt = test.topicTitle,
            topicId = test.topicId,
            languageID = test.languageID,
            mcerLevel = test.mcerLevel,
            allowedTypes = TestItemType.entries,
            questionCount = 10
        )
        generateTest(config = cfg, existingTestId = testId)
    }

    fun refreshTestQuestions(testId: String) {
        val test = learningTests.find { it.id == testId } ?: return
        val cfg = test.config ?: TestConfig(
            prompt = test.topicTitle,
            topicId = test.topicId,
            languageID = test.languageID,
            mcerLevel = test.mcerLevel,
            allowedTypes = test.items.map { it.type }.distinct().ifEmpty { TestItemType.entries },
            questionCount = test.items.size.coerceIn(5, 15)
        )
        val avoid = test.items.map { it.prompt }
        generateTest(config = cfg, avoidPrompts = avoid)
    }

    fun triggerPostConversationTest(sessionRecord: SessionRecord) {
        val userPassages = sessionRecord.passages.filter { it.speaker == Speaker.user && it.text.isNotBlank() }
        if (userPassages.isEmpty()) return

        val themeTitle = selectedTheme?.title ?: sessionRecord.title.ifBlank { "Recent conversation" }
        val activeMcer = currentMcerLevel
        val targetLanguage = language
        val contextString = TeachingPolicy.context(sessionRecord)

        val config = TestConfig(
            prompt = "Comprehensive 25-question evaluation based on conversation: $themeTitle",
            topicId = selectedTheme?.id,
            languageID = targetLanguage.id,
            mcerLevel = activeMcer,
            allowedTypes = listOf(
                TestItemType.READING_COMPREHENSION,
                TestItemType.CLOZE_BANK,
                TestItemType.MULTIPLE_CHOICE,
                TestItemType.ERROR_CORRECTION,
                TestItemType.SPEAKING
            ),
            questionCount = 25,
            includeFlashcards = false
        )

        notice = getApplication<Application>().getString(R.string.tests_post_conversation_notice)
        generateTest(config = config, conversationContext = contextString, sessionID = sessionRecord.id)
    }

    suspend fun translatePromptOnly(prompt: String, sourceLanguageId: String = language.id): String {
        if (!cloudReady() || prompt.isBlank()) return prompt
        val module = LanguageRegistry.get(sourceLanguageId) ?: language
        val meaningLang = archive.preferences.meaningLanguage
        return try {
            val result = teaching(
                localID = session?.id,
                purpose = HelperPurpose.MEANING,
                logicalID = UUID.randomUUID().toString(),
                instructions = TeachingPolicy.translation(module, meaningLang),
                input = prompt.take(500)
            )
            result.text.trim()
        } catch (e: Exception) {
            android.util.Log.w("MuralTranslatePrompt", "Error translating prompt", e)
            prompt
        }
    }

    private fun parseLearningTest(
        raw: String,
        config: TestConfig,
        languageID: String = "en",
        testId: String = "test_" + java.util.UUID.randomUUID().toString().take(8),
        createdAt: Long = System.currentTimeMillis()
    ): LearningTest? {
        return try {
            val firstBrace = raw.indexOf('{')
            val lastBrace = raw.lastIndexOf('}')
            val cleanCandidate = if (firstBrace in 0 until lastBrace) {
                raw.substring(firstBrace, lastBrace + 1)
            } else if (raw.startsWith("```")) {
                raw.substringAfter("\n").substringBeforeLast("```").trim()
            } else raw.trim()

            val root = try {
                Json.parseToJsonElement(cleanCandidate).jsonObject
            } catch (initialErr: Exception) {
                val itemsIdx = raw.indexOf("\"items\"")
                if (itemsIdx >= 0) {
                    val afterItems = raw.substring(itemsIdx)
                    val lastItemEnd = afterItems.lastIndexOf('}')
                    if (lastItemEnd > 0) {
                        val repaired = raw.substring(0, itemsIdx + lastItemEnd + 1) + "\n], \"flashcards\": []}"
                        Json.parseToJsonElement(repaired).jsonObject
                    } else throw initialErr
                } else throw initialErr
            }

            val title = (root["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: if (config.prompt.isNotBlank()) config.prompt.take(45) else "Evaluación ${config.mcerLevel}"
            val topicTitle = (root["topicTitle"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: if (config.prompt.isNotBlank()) config.prompt else "Práctica general"
            val mcer = (root["mcerLevel"] as? JsonPrimitive)?.contentOrNull?.uppercase()?.trim()
                ?.takeIf { it in TopicEcosystemStore.MCER_LEVELS } ?: config.mcerLevel

            val rawItems = (root["items"] as? JsonArray) ?: JsonArray(emptyList())
            val parsedItems = rawItems.mapIndexedNotNull { index, el ->
                val obj = el as? JsonObject ?: return@mapIndexedNotNull null
                val typeStr = (obj["type"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: "multiple_choice"
                val type = when {
                    typeStr.contains("cloze") || typeStr.contains("bank") -> TestItemType.CLOZE_BANK
                    typeStr.contains("fill") -> TestItemType.FILL_BLANK
                    typeStr.contains("error") || typeStr.contains("correct") -> TestItemType.ERROR_CORRECTION
                    typeStr.contains("reading") || typeStr.contains("comprehension") -> TestItemType.READING_COMPREHENSION
                    typeStr.contains("speak") || typeStr.contains("oral") -> TestItemType.SPEAKING
                    else -> TestItemType.MULTIPLE_CHOICE
                }

                // If only 1 type was requested, enforce it
                val finalType = if (config.allowedTypes.size == 1) config.allowedTypes.first() else type

                val prompt = (obj["prompt"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val context = (obj["context"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val readingPassage = (obj["readingPassage"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val rawWordBank = (obj["wordBank"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.replace("*", "") } ?: emptyList()
                val incorrectWord = (obj["incorrectWord"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val rawOptions = (obj["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.replace("*", "") } ?: emptyList()
                val correctAnswer = (obj["correctAnswer"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val explanation = (obj["explanation"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""
                val targetSpeech = (obj["targetSpeechText"] as? JsonPrimitive)?.contentOrNull?.replace("*", "") ?: ""

                val finalOptions = if (finalType == TestItemType.MULTIPLE_CHOICE ||
                    (finalType == TestItemType.ERROR_CORRECTION && rawOptions.isNotEmpty()) ||
                    (finalType == TestItemType.READING_COMPREHENSION && rawOptions.isNotEmpty())) {
                    val opts = rawOptions.map { it.trim() }.filter {
                        it.isNotBlank() && !it.startsWith("Opción", ignoreCase = true) && !it.startsWith("Option", ignoreCase = true)
                    }.toMutableList()
                    if (correctAnswer.isNotBlank() && !opts.any { it.equals(correctAnswer.trim(), ignoreCase = true) }) {
                        opts.add(correctAnswer.trim())
                    }
                    val distinctOpts = opts.distinct()
                    if (distinctOpts.size > 4) {
                        val correct = distinctOpts.firstOrNull { it.equals(correctAnswer.trim(), ignoreCase = true) }
                        val rest = distinctOpts.filterNot { it.equals(correctAnswer.trim(), ignoreCase = true) }.shuffled().take(3)
                        (listOfNotNull(correct) + rest).shuffled()
                    } else {
                        distinctOpts
                    }
                } else emptyList()

                val finalWordBank = if (finalType == TestItemType.CLOZE_BANK) {
                    val wb = rawWordBank.map { it.trim() }.toMutableList()
                    if (correctAnswer.isNotBlank() && !wb.any { it.equals(correctAnswer.trim(), ignoreCase = true) }) {
                        wb.add(correctAnswer.trim())
                    }
                    wb.distinct().shuffled()
                } else emptyList()

                val rawId = (obj["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "${index + 1}"
                val id = "q_${index + 1}_$rawId"
                val item = LearningTestItem(
                    id = id,
                    type = finalType,
                    prompt = prompt.ifBlank { "Select the correct option:" },
                    context = context,
                    options = finalOptions,
                    correctAnswer = correctAnswer,
                    explanation = explanation,
                    targetSpeechText = targetSpeech,
                    readingPassage = readingPassage,
                    wordBank = finalWordBank,
                    incorrectWord = incorrectWord
                )
                if (finalType == TestItemType.MULTIPLE_CHOICE ||
                    (finalType == TestItemType.ERROR_CORRECTION && finalOptions.isNotEmpty()) ||
                    (finalType == TestItemType.READING_COMPREHENSION && finalOptions.isNotEmpty())) {
                    item.copy(options = AnswerGrader.shuffleOptions(item))
                } else item
            }

            val targetCount = if (config.allowedTypes.isEmpty() && config.includeFlashcards) 0 else config.questionCount.coerceIn(0, 30)
            val countLimited = parsedItems.take(targetCount)
            val finalItems = if (config.allowedTypes.size > 1 && countLimited.size > 1) {
                interleaveTestItems(countLimited)
            } else {
                countLimited
            }

            val rawFlashcards = if (config.includeFlashcards) {
                (root["flashcards"] as? JsonArray) ?: JsonArray(emptyList())
            } else JsonArray(emptyList())

            val parsedFlashcards = rawFlashcards.mapIndexedNotNull { index, el ->
                val obj = el as? JsonObject ?: return@mapIndexedNotNull null
                val front = (obj["front"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                val back = (obj["back"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                val example = (obj["example"] as? JsonPrimitive)?.contentOrNull ?: ""
                val pronunciation = (obj["pronunciationGuide"] as? JsonPrimitive)?.contentOrNull ?: ""
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "f_${index + 1}"
                FlashcardItem(id, front, back, example, pronunciation)
            }
            val flashcards = if (config.allowedTypes.isEmpty() && config.includeFlashcards && config.questionCount > 0) {
                parsedFlashcards.take(config.questionCount)
            } else {
                parsedFlashcards
            }

            LearningTest(
                id = testId,
                title = title,
                topicId = config.topicId,
                topicTitle = topicTitle,
                mcerLevel = mcer,
                difficulty = TestDifficulty.MEDIUM,
                status = TestStatus.READY,
                config = config,
                createdAt = createdAt,
                items = finalItems,
                flashcards = flashcards,
                languageID = languageID
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun interleaveTestItems(items: List<LearningTestItem>): List<LearningTestItem> {
        val readingGroups = items.filter { it.type == TestItemType.READING_COMPREHENSION }
            .groupBy { it.readingPassage.ifBlank { it.id } }
            .values.toList()
        val nonReading = items.filterNot { it.type == TestItemType.READING_COMPREHENSION }
        val byType = nonReading.groupBy { it.type }.values.map { it.toMutableList() }.toMutableList()

        val result = mutableListOf<LearningTestItem>()
        var rIdx = 0
        var addedAny = true
        while (addedAny) {
            addedAny = false
            for (typeList in byType) {
                if (typeList.isNotEmpty()) {
                    result.add(typeList.removeAt(0))
                    addedAny = true
                }
            }
            if (rIdx < readingGroups.size) {
                result.addAll(readingGroups[rIdx])
                rIdx++
                addedAny = true
            }
        }
        return if (result.size == items.size) result else items
    }

    private val reports = ReportController(viewModelScope,
        ReportConfiguration.parse(BuildConfig.MANAGED_API_ORIGIN)?.let(::ReportClient))
    val reportState = reports.state

    fun reportUtterance(sessionID: String, passageID: String) {
        val selectedSession = session?.takeIf { it.id == sessionID }
            ?: archive.sessions.firstOrNull { it.id == sessionID } ?: return
        ReportSelection.from(selectedSession, passageID)?.let(reports::open)
    }
    fun submitReport(report: AIReportSubmission) = reports.submit(report)
    fun dismissReport() = reports.dismiss()

    private data class SaveRequest(val snapshot: LearningSnapshot, val completion: CompletableDeferred<Unit>? = null)
    private val writes = Channel<SaveRequest>(Channel.UNLIMITED)
    private var storageReady = false
    private var finalAssessmentTickets: List<FinalAssessmentTicket> = emptyList()
    private val recoveredAssessmentIDs = mutableSetOf<String>()
    private val hostedFinalAssessmentJobs = mutableMapOf<String, Job>()
    private var connectionJob: Job? = null
    private var durationJob: Job? = null
    private var closeJob: Job? = null
    private var resetJob: Job? = null
    private var assessmentJob: Job? = null
    private var actionJob: Job? = null
    private val meanings = MeaningController(
        viewModelScope,
        delayMillis = 100,
        incompleteDelayMillis = 350,
        minimumSpacingMillis = 500,
        canRetryFailure = HostedHelperRetry::canRetryAtBoundary,
        retryDelay = HostedHelperRetry::automaticDelay,
        stream = { request, onText -> translateMeaning(request, onText) }
    ) { request -> translateMeaning(request) }
    private suspend fun translateMeaning(request: MeaningRequest, onText: ((String) -> Unit)? = null): MeaningResult {
        if (archive.preferences.aiConsentVersion != 1) throw IllegalStateException("AI processing consent is required.")
        val module = LanguageRegistry.get(request.learningLanguageID) ?: throw IllegalStateException("Unsupported language.")
        if (request.sessionID in hostedSessionIDs && request.translationInput.toByteArray(Charsets.UTF_8).size > 24_576) throw MeaningInputLimitException()
        android.util.Log.d("MuralMeaning", "translateMeaning input: '${request.translationInput}', target: ${request.meaningLanguage}")
        val result = teaching(request.sessionID, HelperPurpose.MEANING, request.cacheKey,
            TeachingPolicy.translation(module, request.meaningLanguage), request.translationInput, onText = onText)
        return MeaningResult(result.text, result.usage.input, result.usage.output)
    }
    private val finalAssessments = FinalAssessmentQueue(viewModelScope) { snapshot, passage -> requestAssessment(snapshot, passage) }
    private val languageDetector = LanguageDetector(application)
    private var languageCheckJob: Job? = null
    private var lastLanguageRedirect: String? = null
    private val delegations = mutableMapOf<String, Job>()
    private var voiceSession = false
    private var activity = ConversationActivity(activityNow())
    private var conversationPace = ConversationPace()
    var inactivitySeconds by mutableStateOf<Int?>(null); private set
    private fun activityNow() = System.nanoTime() / 1_000_000_000.0
    fun noteTypingActivity() { if (state == "active" && voiceSession) { activity.typing(activityNow()); inactivitySeconds = null } }
    fun setTyping(typing: Boolean) {
        if (state == "active" && voiceSession) {
            if (typing) {
                activity.typing(activityNow())
                inactivitySeconds = null
            }
            transport.send(buildJsonObject {
                put("type", if (typing) "session.typing.start" else "session.typing.stop")
                put("event_id", UUID.randomUUID().toString())
            })
        }
    }
    private var generation = 0

    init {
        LocalizedThemes.storeProvider = { topicEcosystemStore }
        guests?.let { controller -> viewModelScope.launch { controller.state.collect { guestState = it } } }
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { repository.load() to credentials.hasKey }
                archive = loaded.first.archive
                val providers = providerStore.read(ConversationProvider.PERSONAL_KEY)
                hostedSessionIDs = providers.hostedIDs
                pendingHostedOwnerID = providers.pendingOwnerID
                accountChangeBlocked = providers.pendingOwnerID != null
                guests?.recoverAcknowledgedOwnerAtStartup(pendingHostedOwnerID,
                    clear = { owner -> clearAcknowledgedGuestMarker(owner) },
                    onFailure = { presentError(getApplication<Application>().getString(R.string.error_guest_secure_storage_unavailable)) })
                conversationProvider = providers.selection
                finalAssessmentTickets = ConversationProviderPolicy.recoveryTickets(loaded.first.finalAssessments, hostedSessionIDs)
                hasKey = loaded.second
                credentialProvider = credentials.provider
                storageReady = true
                refreshCustomThemes()
                refreshLearningTests()
                recoverFinalAssessments()
                refreshHostedReadiness()
                if (accountChangeBlocked) reconcileHostedSessions()
            } catch (_: CancellationException) { }
            catch (_: Exception) {
                presentError(getApplication<Application>().getString(R.string.error_local_history_unavailable))
            } finally { loadingHistory = false }
        }
        viewModelScope.launch {
            for (first in writes) {
                var latest = first
                val completions = mutableListOf<CompletableDeferred<Unit>>()
                first.completion?.let(completions::add)
                // Coalesce transcript deltas while preserving every requested durability barrier.
                while (true) {
                    val next = writes.tryReceive().getOrNull() ?: break
                    latest = next
                    next.completion?.let(completions::add)
                }
                try {
                    withContext(Dispatchers.IO) { repository.save(latest.snapshot) }
                    completions.forEach { it.complete(Unit) }
                } catch (error: Exception) {
                    completions.forEach { it.completeExceptionally(error) }
                    if (error is CancellationException) throw error
                    presentError(getApplication<Application>().getString(R.string.error_save_progress_failed))
                }
            }
        }
        transport.onEvent = { event ->
            try { handle(event) }
            catch (_: IllegalArgumentException) { notice = getApplication<Application>().getString(R.string.notice_invalid_voice_update) }
            catch (_: IllegalStateException) { notice = getApplication<Application>().getString(R.string.notice_invalid_voice_update) }
        }
        transport.onFailure = { fail(it) }
        transport.onLevels = { input, output ->
            inputLevel = input; outputLevel = output
            if (state == "active" && voiceSession) {
                if (input > 0.03) {
                    activity.inputActive(activityNow())
                    if (input > 0.05) {
                        activity.learnerEngaged(activityNow())
                        inactivitySeconds = null
                    }
                }
                if (output > 0.03) activity.assistantActive(activityNow())
            }
        }
        meanings.onChange = { meaning = meanings.text; translating = meanings.isLoading; meaningFailed = meanings.error != null; meaningLimitReached = meanings.error is MeaningInputLimitException }
        meanings.onResult = { request, result ->
            if (session?.id == request.sessionID) updateSession {
                it.translations[request.cacheKey] = result.text
                addUsage(it, APIUsage(result.inputTokens, result.outputTokens))
            }
        }
        finalAssessments.beforeAssessment = { snapshot, passage ->
            val current = archive.sessions.firstOrNull { it.id == snapshot.id }
            val ticket = finalAssessmentTickets.firstOrNull { it.matches(snapshot, passage) }
            if (snapshot.id in hostedSessionIDs) false
            else if (!storageReady || !hasKey || archive.preferences.aiConsentVersion != 1 || current == null ||
                ticket == null || !FinalAssessmentRecovery.canAttempt(ticket, current, nowSeconds())) false
            else {
                finalAssessmentTickets = finalAssessmentTickets.map {
                    if (it == ticket) it.copy(attempts = it.attempts + 1, lastAttemptAt = nowSeconds()) else it
                }
                persistAndWait()
                true
            }
        }
        finalAssessments.onResult = { result ->
            result.applying(archive.sessions.firstOrNull { it.id == result.sessionID })?.let { updated ->
                save(updated)
                if (session?.id == updated.id) session = clone(updated)
                persistAndWait()
            }
        }
    }

    private fun recoverFinalAssessments() {
        if (!storageReady || !hasKey || archive.preferences.aiConsentVersion != 1) return
        val remaining = FinalAssessmentRecovery.MAX_RECOVERED_PER_LAUNCH - recoveredAssessmentIDs.size
        if (remaining <= 0) return
        FinalAssessmentRecovery.recover(archive.sessions, finalAssessmentTickets, nowSeconds())
            .filterNot { it.id in recoveredAssessmentIDs || it.id in hostedSessionIDs }.take(remaining)
            .forEach { if (finalAssessments.submit(clone(it))) recoveredAssessmentIDs += it.id }
    }

    private fun clone(s: SessionRecord): SessionRecord = s.copy(
        fragments = s.fragments.map { it.copy(previousTexts = it.previousTexts.toList()) }.toMutableList(),
        assessments = s.assessments.map { it.copy(words = it.words.toList()) }.toMutableList(),
        translations = s.translations.toMutableMap(),
        topics = s.topics.map { it.copy(sources = it.sources.toList()) }.toMutableList(),
    )
    private fun persistenceSnapshot() = LearningSnapshot(
        archive.copy(sessions = archive.sessions.toMutableList(), preferences = archive.preferences.copy()),
        finalAssessmentTickets.toList(),
    )
    private fun persist() {
        if (storageReady) writes.trySend(SaveRequest(persistenceSnapshot()))
    }
    private suspend fun persistAndWait() {
        check(storageReady)
        val completion = CompletableDeferred<Unit>()
        writes.send(SaveRequest(persistenceSnapshot(), completion))
        completion.await()
    }
    private fun save(s: SessionRecord) {
        finalAssessmentTickets = ConversationProviderPolicy.enqueueRecovery(s, finalAssessmentTickets, hostedSessionIDs)
        archive = archive.copy(sessions = (archive.sessions.filterNot { it.id == s.id } + clone(s)).toMutableList())
        persist()
        val tid = s.themeID ?: "general"
        if (s.passages.isNotEmpty()) {
            val words = s.assessments.flatMap { it.words }.map { it.lemma }.distinct().take(8)
            val errors = s.assessments.mapNotNull { it.nextGoal.takeIf { g -> g.isNotBlank() } }.distinct().take(6)
            val lastExchange = s.passages.takeLast(2).joinToString(" | ") { "${it.speaker.name}: ${it.text.take(70)}" }
            val summary = if (words.isNotEmpty()) "Practiced ${words.take(4).joinToString(", ")}: $lastExchange"
                          else lastExchange.takeIf { it.isNotBlank() } ?: s.title
            if (s.endedAt != null) {
                topicEcosystemStore.recordSessionMemory(tid, summary.take(200), errors, words)
            } else if (s.passages.size >= 2) {
                topicEcosystemStore.setThemeMemory(tid, summary.take(200))
            }
        }
    }
    private fun updateSession(change: (SessionRecord) -> Unit) {
        val current = session ?: return
        val next = clone(current); change(next); session = next; save(next)
    }
    private fun resolveMessage(e: Throwable, @StringRes fallback: Int): String {
        val app = getApplication<Application>()
        val res = errorMessageRes(e)
        val message = when {
            res == R.string.error_http_generic && e is APIClient.APIException.Http -> app.getString(res, e.status)
            res != 0 -> app.getString(res)
            e is LiveTransport.TransportException -> e.message ?: app.getString(fallback)
            else -> app.getString(fallback)
        }
        if (e is APIClient.APIException.Http && e.reference != null)
            return message + "\n\n" + app.getString(R.string.error_provider_reference, e.reference)
        return requestErrorReference(e)?.let { message + "\n\n" + app.getString(R.string.hosted_error_reference, it) } ?: message
    }
    private fun presentError(message: String, needsKeySetup: Boolean = false) {
        error = message; errorNeedsKeySetup = needsKeySetup; errorNeedsAccountSignIn = false
    }
    private fun presentError(e: Throwable, @StringRes fallback: Int) {
        presentError(resolveMessage(e, fallback), errorNeedsKeySetup(e))
        errorNeedsAccountSignIn = needsAccountRecovery(e)
    }
    private fun cloudReady(): Boolean {
        if (!storageReady) { presentError(getApplication<Application>().getString(R.string.error_resolve_local_history_first)); return false }
        if (archive.preferences.aiConsentVersion != 1) {
            presentError(getApplication<Application>().getString(R.string.error_accept_ai_consent)); return false
        }
        val currentHosted = session?.id in hostedSessionIDs
        if (!currentHosted && conversationProvider == ConversationProvider.PERSONAL_KEY && !hasKey) {
            presentError(getApplication<Application>().getString(R.string.error_missing_key), needsKeySetup = true); return false
        }
        return true
    }
    /** Called by the minutes/account UI after an explicit provider choice. No failure changes it. */
    fun selectConversationProvider(provider: ConversationProvider) {
        if (isRunning || !storageReady) return
        conversationProvider = provider
        viewModelScope.launch {
            try { providerStore.select(provider) }
            catch (_: Exception) { presentError(getApplication<Application>().getString(R.string.provider_preference_save_failed)) }
        }
        if (provider == ConversationProvider.HOSTED_MINUTES) refreshHostedReadiness()
    }

    fun onAccountChanged(account: AccountState) {
        selectedAccount = account
        // Invalidate immediately. Slow guest or member reads may not restore access during a transition.
        readiness.selectAccount(null, true)
        accessJob?.cancel()
        if (!account.busy) refreshHostedReadiness()
    }
    fun refreshHostedReadiness() {
        if (selectedAccount.busy || !storageReady) return
        accessJob?.cancel()
        accessJob = viewModelScope.launch {
            try {
                val member = memberSessions?.read()?.takeIf { it.isValid(System.currentTimeMillis()) }
                if (member != null) {
                    clearAcknowledgedGuestMarker()
                    if (guests?.needsLink() == true) {
                        readiness.selectAccount(null, true)
                        if (!completeGuestSignIn() && guests.memberMaySpend(member.accountID) != true) return@launch
                    }
                    if (selectedAccount.busy) return@launch
                    readiness.selectAccount(member.accountID, false)
                    readiness.refresh()
                } else {
                    if (archive.preferences.aiConsentVersion != 1 || conversationProvider != ConversationProvider.HOSTED_MINUTES) {
                        readiness.selectAccount(null, false); return@launch
                    }
                    guests?.acquire()
                    if (selectedAccount.busy) return@launch
                    val guest = guests?.availableSession()
                    readiness.selectAccount(guest?.accountID, false)
                    readiness.refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readiness.selectAccount(null, false) }
        }
    }
    fun dismissMinuteAccess() { showMinuteAccess = false }
    fun openMinuteAccess() { showMinuteAccess = true }
    fun needsMinuteAccess(): Boolean {
        if (!hasKey) { showMinuteAccess = true; return true }
        return false


    }
    suspend fun prepareGuestCustodyForDeletion(memberID: String) {
        try { guests?.retireAcknowledgedLinkForDeletion(memberID) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw AccountFailure.SecureStorage }
    }

    /** Two encrypted stores are not crash-atomic. The durable guest receipt repairs only its marker. */
    private suspend fun clearAcknowledgedGuestMarker() {
        guests?.recoverAcknowledgedOwner(pendingHostedOwnerID) { owner -> clearAcknowledgedGuestMarker(owner) }
    }
    private suspend fun clearAcknowledgedGuestMarker(owner: String) {
        providerStore.clearPending()
        pendingHostedOwnerID = null; accountChangeBlocked = false
        hostedBindings.delegateOwnerRecovery(owner)
    }

    /** Member spending requires a durable server acknowledgment; guest transfer may finish later. */
    suspend fun completeGuestSignIn(): Boolean {
        val member = memberSessions?.read()?.takeIf { it.isValid(System.currentTimeMillis()) } ?: return false
        val guestOwner = guests?.retainedOwnerID()
        if (guestOwner != null && pendingHostedOwnerID == guestOwner) {
            if (isRunning) return false
            // Finish pending local provenance writes before handing remote recovery to the server.
            connectionJob?.join()
            reconciliationJob?.cancelAndJoin()
        }
        val accepted = guests?.linkTo(member, allowDeferred = true) ?: true
        val safe = accepted || guests?.memberMaySpend(member.accountID) == true
        if (safe && guestOwner != null && pendingHostedOwnerID == guestOwner) {
            // The retained GuestInstallation still owns its bearer and acknowledged member binding.
            // Server recovery owns the old lease; never close it with the new member bearer.
            withContext(NonCancellable) {
                providerStore.clearPending()
                pendingHostedOwnerID = null; accountChangeBlocked = false
                hostedBindings.delegateOwnerRecovery(guestOwner)
            }
        }
        if (!safe) showMinuteAccess = true
        return safe
    }
    private suspend fun availableHostedOwner(): AccountSession? {
        val member = memberSessions?.read()?.takeIf { it.isValid(System.currentTimeMillis()) }
        if (member != null) return member.takeIf { guests?.needsLink() != true || guests?.memberMaySpend(member.accountID) == true }
        return guests?.availableSession()
    }
    private suspend fun requireHostedOwner(ownerID: String? = null): AccountSession {
        // The pending guest lease must still be closable after Google has stored a member bearer.
        val guest = if (ownerID != null) guests?.sessionForSettlement(ownerID) else null
        return guest ?: availableHostedOwner()?.takeIf { ownerID == null || it.accountID == ownerID }
            ?: throw HostedFailure.SignInRequired
    }
    private suspend fun hostedBalance(owner: AccountSession): MinuteBalance =
        GuestMinuteClient(hostedConfiguration ?: throw HostedFailure.Unavailable).balance(owner)

    private fun hostedClient(ownerID: String): HostedAPIClient {
        val config = hostedConfiguration ?: throw HostedFailure.Unavailable
        return HostedAPIClient(config.origin, { requireHostedOwner(ownerID) }, okhttp3.OkHttpClient())
    }
    private fun binding(lease: HostedAPIClient.HostedLease) = HostedConversationBindings.Lease(
        lease.sessionID, lease.teaching, lease::requestClose, lease::status, lease.deadlineMilliseconds)

    /** The creating session, rather than the currently selected settings option, chooses every helper. */
    private suspend fun teaching(localID: String?, purpose: HelperPurpose, logicalID: String,
        instructions: String, input: String, schema: JsonObject? = null, search: Boolean = false, onText: ((String) -> Unit)? = null): APIResult {
        if (archive.preferences.aiConsentVersion != 1) throw HostedFailure.Unavailable
        if (localID != null && localID in hostedSessionIDs) {
            return hostedBindings.respond(localID, purpose, logicalID, instructions, input, schema, search, onText)
        }
        if (localID == null && conversationProvider == ConversationProvider.HOSTED_MINUTES) throw HostedFailure.Unavailable
        return if (onText != null && purpose == HelperPurpose.MEANING) api.streamMeaning(instructions, input, onText)
        else api.respond(instructions, input, schema, search, purpose)
    }
    private fun helperContext(snapshot: SessionRecord, passage: Passage? = null): String =
        if (snapshot.id in hostedSessionIDs) ConversationHistory.helperContext(snapshot, passage)
        else TeachingPolicy.context(snapshot, passage)

    /** Renewing a member token must not turn a retained guest transfer back into an OAuth gate. */
    suspend fun settleRenewedMember(): Boolean {
        val pending = pendingHostedOwnerID ?: return true
        if (guests?.owns(pending) == true) return true
        return prepareForAccountChange()
    }

    /** Google identity can be added while guest accounting finishes; guest credentials stay separate. */
    suspend fun prepareForSignIn(): Boolean = AccountSignInPreparation(
        finishLocalWork = {
            if (!storageReady) {
                presentError(getApplication<Application>().getString(R.string.error_resolve_local_history_first))
                false
            } else {
                hostedBindings.disableHelpers(); meanings.reset()
                if (isRunning && (session?.id in hostedSessionIDs || conversationProvider == ConversationProvider.HOSTED_MINUTES)) {
                    updateSession { it.endReason = "Signing in" }; finish(false)
                }
                hostedFinalAssessmentJobs.values.toList().forEach { it.cancel() }; hostedFinalAssessmentJobs.clear()
                // Wait only for local startup/provenance persistence, never remote guest settlement.
                awaitLocalSignInStartup(connectionJob) {
                    presentError(getApplication<Application>().getString(R.string.error_sign_in_finishing_startup))
                }
            }
        },
        pendingOwner = { pendingHostedOwnerID },
        guestOwns = { guests?.owns(it) == true },
        prepareAccountChange = { prepareForAccountChange() },
    ).prepare()

    /** Root UI wiring must use this before sign-out, deletion, account switching, or session revocation. */
    suspend fun prepareForAccountChange(): Boolean {
        if (!storageReady) {
            presentError(getApplication<Application>().getString(R.string.error_resolve_local_history_first)); return false
        }
        hostedBindings.disableHelpers(); meanings.reset()
        hostedFinalAssessmentJobs.values.toList().forEach { it.cancel() }; hostedFinalAssessmentJobs.clear()
        if (!accountChangeBlocked) {
            val member = memberSessions?.read()?.takeIf { it.isValid(System.currentTimeMillis()) }
            if (member != null && guests?.needsLink() == true && !completeGuestSignIn()) return false
            return true
        }
        if (isRunning && (session?.id in hostedSessionIDs || conversationProvider == ConversationProvider.HOSTED_MINUTES)) {
            updateSession { it.endReason = "Account change" }; finish(false)
        }
        val finished = withTimeoutOrNull(10_000) { connectionJob?.join(); reconciliationJob?.join(); true } ?: false
        val ready = finished && settleHostedSessions()
        if (!ready) presentError(getApplication<Application>().getString(R.string.hosted_checking_previous))
        return ready
    }

    private fun reconcileHostedSessions() {
        if (reconciliationJob?.isActive == true) return
        reconciliationJob = viewModelScope.launch {
            // Wait for cancelled creation/provenance writes before clearing the durable pending marker.
            connectionJob?.join()
            if (settleHostedSessions()) refreshHostedReadiness()
        }
    }

    private suspend fun settleHostedSessions(): Boolean {
        val ownerID = pendingHostedOwnerID
        if (ownerID == null) { accountChangeBlocked = false; return true }
        accountChangeBlocked = true
        return try {
            val owner = requireHostedOwner(ownerID)
            if (owner.accountID != ownerID) return false
            for (id in hostedBindings.openSessionIDs.filter { hostedBindings.owner(it) == ownerID })
                if (!hostedBindings.closeAndConfirm(id)) return false
            // An interrupted create may have committed remotely without returning its lease locally.
            val client = hostedClient(ownerID)
            val current = client.currentSession()
            if (current != null) {
                try { current.requestClose() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                val closed = withTimeoutOrNull(8_000) {
                    while (current.status().state != "closed") delay(500)
                    true
                } ?: false
                if (!closed) return false
            }
            val balance = hostedBalance(owner)
            if (balance.reservedMilliseconds != 0L) return false
            guests?.recordSettledBalance(owner, balance)
            providerStore.clearPending()
            pendingHostedOwnerID = null; accountChangeBlocked = false
            readiness.refresh()
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    fun dismissError() { error = null; errorNeedsKeySetup = false; errorNeedsAccountSignIn = false }
    fun clearLookup() {
        lookupGeneration++
        lookupJob?.cancel()
        lookupJob = null; lookupResult = null; lookupError = null; lookupLoading = false
    }
    fun saveKey(key: String) {
        if (isRunning) return
        try { credentials.save(key); hasKey = credentials.hasKey; credentialProvider = credentials.provider; selectConversationProvider(ConversationProvider.PERSONAL_KEY); recoverFinalAssessments(); notice = getApplication<Application>().getString(R.string.notice_key_saved) }
        catch (e: Exception) { presentError(e, R.string.error_key_save_failed) }
    }
    fun deleteKey() {
        if (isRunning) return
        try { credentials.delete(); hasKey = false; credentialProvider = null }
        catch (e: Exception) { presentError(e, R.string.error_key_delete_failed) }
        finally { hasKey = credentials.hasKey; credentialProvider = credentials.provider }
    }
    fun updatePreferences(preferences: Preferences) {
        if (isRunning || !storageReady) return
        if (LanguageRegistry.get(preferences.learningLanguageID) == null || preferences.meaningLanguage !in MeaningLanguages.all || preferences.sessionMinutes !in 1..60) return
        val languageChanged = preferences.learningLanguageID != language.id
        actionJob?.cancel(); clearLookup(); working = false
        if (preferences.aiConsentVersion != 1) {
            finalAssessments.cancelAll(); hostedBindings.disableHelpers()
            hostedFinalAssessmentJobs.values.toList().forEach { it.cancel() }; hostedFinalAssessmentJobs.clear()
        }
        generation++; meanings.reset()
        if (languageChanged) resetConversation()
        archive = archive.copy(preferences = preferences.copy(interests = preferences.interests.take(500)))
        persist(); scheduleTranslation(); recoverFinalAssessments(); refreshHostedReadiness()
    }
    fun selectLanguage(id: String) {
        if (!isRunning && LanguageRegistry.get(id) != null) {
            updatePreferences(archive.preferences.copy(learningLanguageID = id))
            refreshCustomThemes()
            refreshLearningTests()
        }
    }
    fun selectSessionMinutes(minutes: Int) {
        if (!storageReady) return
        val clamped = minutes.coerceIn(1, 60)
        if (archive.preferences.sessionMinutes == clamped) return
        if (isRunning && conversationProvider == ConversationProvider.HOSTED_MINUTES) return
        archive = archive.copy(preferences = archive.preferences.copy(sessionMinutes = clamped))
        persist()
        if (isRunning && voiceSession) {
            publishLiveSessionSnapshot()
        }
    }
    fun chooseTheme(theme: ConversationTheme?) {
        if (!isRunning && session != null) resetConversation()
        selectedTheme = theme
        currentPedagogicalMode = topicEcosystemStore.getPedagogicalMode(theme?.id)
        currentMcerLevel = topicEcosystemStore.getMcerLevel(theme?.id)
        if (state == "active") {
            updateSession { it.themeID = theme?.id; it.title = theme?.title ?: language.defaultTitle }
            command("instructions", TeachingPolicy.theme(theme, language))
            if (voiceSession) publishLiveSessionSnapshot()
        }
    }
    fun toggleMeaning() {
        archive = archive.copy(preferences = archive.preferences.copy(meaningVisible = !archive.preferences.meaningVisible)); persist()
        meanings.reset()
        if (archive.preferences.meaningVisible) scheduleTranslation()
    }
    fun toggleMute() {
        if (state == "active" && voiceSession) {
            if (isMediaPaused) {
                isMediaPaused = false
                transport.setPlaybackPaused(false)
                isMuted = false
                transport.mute(false)
            } else {
                isMuted = !isMuted
                transport.mute(isMuted)
            }
            publishLiveSessionSnapshot()
        }
    }

    fun toggleMediaPause() {
        if (state == "active" && voiceSession) {
            val paused = !isMediaPaused
            isMediaPaused = paused
            isMuted = paused
            transport.mute(paused)
            transport.setPlaybackPaused(paused)
            publishLiveSessionSnapshot()
        }
    }
    fun help() {
        if (state != "active") return
        if (voiceSession) { activity.learnerEngaged(activityNow()); inactivitySeconds = null; conversationPace.askForHelp(session?.passages?.lastOrNull { it.speaker == Speaker.user }); command("instructions", conversationPace.instruction); command("instructions", TeachingPolicy.help(language)); notice = getApplication<Application>().getString(R.string.notice_help_simpler) }
        else {
            if (working || !cloudReady()) return
            val snapshot = session ?: return
            val token = generation; working = true
            actionJob = viewModelScope.launch {
                try {
                    val result = teaching(snapshot.id, HelperPurpose.HELP, UUID.randomUUID().toString(),
                        TeachingPolicy.help(language) + " Return only your brief explanation.", helperContext(snapshot))
                    if (token != generation || session?.id != snapshot.id || state != "active") return@launch
                    val offset = ((nowSeconds() - snapshot.startedAt) * 1000).toInt().coerceAtLeast(0)
                    updateSession {
                        it.append(Fragment(speaker = Speaker.assistant, text = result.text, startMS = offset, endMS = offset + 1))
                        addUsage(it, result.usage)
                    }
                    scheduleTranslation()
                } catch (_: CancellationException) { }
                catch (e: Exception) { if (token == generation) presentError(e, R.string.error_help_failed) }
                finally { if (token == generation) working = false }
            }
        }
    }

    private fun newSession(voice: Boolean, id: String = UUID.randomUUID().toString()) {
        generation++; resetJob?.cancel(); assessmentJob?.cancel(); actionJob?.cancel(); clearLookup(); working = false; meanings.reset()
        dismissError(); notice = null; isMuted = false; isMediaPaused = false
        voiceSession = voice
        val record = SessionRecord(id = id, languageID = language.id, themeID = selectedTheme?.id, title = selectedTheme?.title ?: language.defaultTitle)
        topicResult?.takeIf { it.languageID == language.id && selectedTheme?.id == "current" }?.let { record.topics += it }
        session = record; save(record)
    }
    fun startWritten(theme: ConversationTheme? = null) {
        if (isRunning || !cloudReady()) return
        theme?.let {
            selectedTheme = it
            currentPedagogicalMode = topicEcosystemStore.getPedagogicalMode(it.id)
            currentMcerLevel = topicEcosystemStore.getMcerLevel(it.id)
        }
        val choice = conversationProvider
        if (!ConversationProviderPolicy.canStart(choice, hasKey, hostedReadiness)) {
            if (choice == ConversationProvider.HOSTED_MINUTES) {
                showMinuteAccess = true; refreshHostedReadiness()
            } else presentError(getApplication<Application>().getString(R.string.error_missing_key), true)
            return
        }
        newSession(false); state = "active"; startDurationChecks()
        triggerWrittenGreeting()
    }
    fun startWrittenSession(theme: ConversationTheme? = null) = startWritten(theme)
    fun triggerWrittenGreeting() {
        val current = session ?: return
        if (current.passages.isNotEmpty() || working) return
        val id = current.id
        val token = generation
        working = true
        actionJob = viewModelScope.launch {
            try {
                val activeThemeId = selectedTheme?.id ?: "general"
                val instructions = TeachingPolicy.typedReply(
                    language = language,
                    theme = selectedTheme,
                    mcerLevel = currentMcerLevel,
                    personality = getThemePersonality(activeThemeId),
                    themeMemory = getThemeMemory(activeThemeId),
                    pedagogicalMode = currentPedagogicalMode,
                    interests = archive.preferences.interests,
                    meaningLanguage = archive.preferences.meaningLanguage,
                    voiceSpeed = voiceSpeed,
                    learner = learner
                ) + "\n" + TeachingPolicy.greeting(language)

                val contextStr = "TARGET LANGUAGE: ${language.id}\n${selectedTheme?.let { "ACTIVE THEME: ${it.title} (ID: ${it.id})\n" } ?: ""}NEW CONVERSATION GREETING"
                val result = teaching(id, HelperPurpose.TYPED_REPLY, UUID.randomUUID().toString(), instructions, contextStr)
                if (token != generation || session?.id != id || state != "active") return@launch
                updateSession { addUsage(it, result.usage) }
                updateSession {
                    it.append(Fragment(speaker = Speaker.assistant, text = result.text, startMS = 0, endMS = 1000))
                }
                scheduleTranslation()
            } catch (_: CancellationException) { }
            catch (e: Exception) {
                android.util.Log.w("MuralWritten", "triggerWrittenGreeting fallback engaged", e)
                val fallback = selectedTheme?.situation ?: language.greeting
                updateSession {
                    it.append(Fragment(speaker = Speaker.assistant, text = fallback, startMS = 0, endMS = 1000))
                }
                scheduleTranslation()
            } finally {
                if (token == generation) working = false
            }
        }
    }
    fun getPassageTranslation(passage: Passage): String? {
        val sess = session ?: return null
        val key = MeaningRequest.cacheKey(passage.revisionKey, archive.preferences.meaningLanguage)
        return sess.translations[key]
    }
    fun start() {
        if (isRunning || !cloudReady()) return
        val choice = conversationProvider
        if (choice == ConversationProvider.HOSTED_MINUTES && accountChangeBlocked) {
            presentError(getApplication<Application>().getString(R.string.hosted_checking_previous))
            reconcileHostedSessions(); return
        }
        if (!ConversationProviderPolicy.canStart(choice, hasKey, hostedReadiness)) {
            if (choice == ConversationProvider.HOSTED_MINUTES) {
                showMinuteAccess = true; refreshHostedReadiness()
            } else presentError(getApplication<Application>().getString(R.string.error_missing_key), true)
            return
        }
        newSession(true); state = "connecting"
        if (backgroundVoiceEnabled) {
            startBackgroundVoiceService()
        }
        val id = session!!.id
        val module = language
        val activeThemeId = selectedTheme?.id ?: "general"
        val instructions = TeachingPolicy.voice(
            language = module,
            learner = learner,
            theme = selectedTheme,
            interests = archive.preferences.interests,
            meaningLanguage = archive.preferences.meaningLanguage,
            voiceSpeed = voiceSpeed,
            mcerLevel = currentMcerLevel,
            personality = getThemePersonality(activeThemeId),
            themeMemory = getThemeMemory(activeThemeId),
            pedagogicalMode = currentPedagogicalMode
        )
        val history = ConversationHistory.messages(session)
        if (choice == ConversationProvider.HOSTED_MINUTES) accountChangeBlocked = true
        connectionJob = viewModelScope.launch {
            try {
                val provider: LiveSessionProvider = if (choice == ConversationProvider.PERSONAL_KEY) api else {
                    val owner = requireHostedOwner()
                    if (selectedAccount.busy || owner.accountID != hostedReadiness.accountID) throw HostedFailure.SignInRequired
                    val hosted = hostedClient(owner.accountID)
                    val balance = hostedBalance(owner)
                    if (!balance.canStartConversation || !hosted.available()) throw HostedFailure.Unavailable
                    // Commit provider provenance and the unresolved-owner marker before making a paid create.
                    hostedSessionIDs = hostedSessionIDs + id
                    pendingHostedOwnerID = owner.accountID
                    withContext(NonCancellable) { providerStore.markHosted(id, owner.accountID) }
                    object : LiveSessionProvider {
                        override suspend fun createLiveSession(request: LiveSessionRequest): LiveSessionConnection {
                            val result = hosted.createLiveSession(request.copy(requestedMilliseconds = archive.preferences.sessionMinutes * 60_000L))
                            val lease = result.lease as? HostedAPIClient.HostedLease ?: throw HostedFailure.InvalidResponse
                            withContext(NonCancellable + Dispatchers.Main.immediate) {
                                hostedBindings.bind(id, owner.accountID, binding(lease))
                                if (session?.id != id || state != "connecting") {
                                    hostedBindings.ended(id); reconcileHostedSessions()
                                }
                            }
                            return result
                        }
                    }
                }
                transport.connect(
                    provider,
                    instructions,
                    history,
                    module.locale,
                    voiceName = voiceTimbre,
                    useMediaOutput = mediaOutputEnabled,
                    initialAppVolume = appVolume,
                )
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) {
                    if (session?.id == id && isRunning) fail(cancelled, R.string.error_voice_connect_failed)
                }
                if (choice == ConversationProvider.HOSTED_MINUTES) reconcileHostedSessions()
                throw cancelled
            } catch (e: Exception) {
                if (session?.id == id && isRunning) fail(e, R.string.error_voice_connect_failed)
                if (choice == ConversationProvider.HOSTED_MINUTES) reconcileHostedSessions()
            }
        }
    }
    fun end(reason: String = "Ended by you") {
        if (state !in listOf("active", "connecting")) return
        val connecting = state == "connecting"
        state = "closing"; isMuted = true
        connectionJob?.cancel(); durationJob?.cancel(); assessmentJob?.cancel(); actionJob?.cancel(); clearLookup()
        delegations.values.toList().forEach { it.cancel() }; delegations.clear(); working = false
        updateSession { it.endReason = reason }
        if (!voiceSession || connecting) { finish(false); return }
        publishLiveSessionSnapshot()
        transport.close()
        closeJob = viewModelScope.launch { delay(5000); if (state == "closing") finish(false) }
    }
    fun background() {
        // Leaving the foreground ends the conversation and releases the microphone; its final assessment still completes.
        generation++; actionJob?.cancel(); clearLookup(); meanings.reset(); working = false
        if (isRunning) { updateSession { it.endReason = "App moved to background" }; finish(false) }
    }
    private fun finish(final: Boolean) {
        if (!isRunning) return
        connectionJob?.cancel(); durationJob?.cancel(); closeJob?.cancel(); assessmentJob?.cancel()
        actionJob?.cancel(); clearLookup(); languageCheckJob?.cancel()
        delegations.values.toList().forEach { it.cancel() }; delegations.clear()
        transport.disconnect(); inputLevel = 0.0; outputLevel = 0.0; working = false; isMuted = false; isMediaPaused = false
        stopBackgroundVoiceService()
        updateSession { it.endedAt = nowSeconds(); it.usageFinal = final }
        state = "ended"
        notice = when (session?.endReason) {
            "Inactivity" -> getApplication<Application>().getString(R.string.notice_ended_inactivity)
            "Time limit" -> getApplication<Application>().getString(R.string.notice_time_limit_reached)
            else -> null
        }
        session?.let {
            if (it.id in hostedSessionIDs) {
                hostedBindings.ended(it.id); reconcileHostedSessions(); finishHostedAssessment(clone(it))
            } else finalAssessments.submit(clone(it))
            val tid = it.themeID ?: selectedTheme?.id
            if (tid != null && tid != "general" && tid != "today" && it.passages.isNotEmpty()) {
                updateThemeCheckpoint(tid, clone(it))
            }
        }
        scheduleTranslation(utteranceComplete = true)
        resetJob = viewModelScope.launch { delay(15000); if (state == "ended") resetConversation() }
    }
    private fun fail(message: String, needsKeySetup: Boolean = false) { finish(false); resetJob?.cancel(); state = "failed"; presentError(message, needsKeySetup) }
    private fun fail(e: Throwable, @StringRes fallback: Int) {
        fail(resolveMessage(e, fallback), errorNeedsKeySetup(e))
        errorNeedsAccountSignIn = needsAccountRecovery(e)
    }
    fun resetConversation() {
        if (isRunning) return
        generation++; resetJob?.cancel(); actionJob?.cancel(); clearLookup(); assessmentJob?.cancel(); languageCheckJob?.cancel(); meanings.reset()
        session = null; selectedTheme = null; topicResult = null
        currentPedagogicalMode = topicEcosystemStore.getPedagogicalMode(null)
        currentMcerLevel = topicEcosystemStore.getMcerLevel(null)
        notice = null; working = false; state = "idle"; voiceSession = false
    }
    private fun command(kind: String, content: String, delegationID: String? = null): Boolean {
        if (state != "active" || !voiceSession) return false
        return transport.send(buildJsonObject {
            put("type", "session.$kind.append"); put("event_id", UUID.randomUUID().toString())
            put("delegation_id", delegationID?.let(::JsonPrimitive) ?: JsonNull); put("content", content.take(1000))
        }).also { if (!it) notice = getApplication<Application>().getString(R.string.notice_update_send_failed) }
    }
    private fun handle(event: JsonObject) {
        if (session == null || !isRunning) return
        when (event["type"]?.jsonPrimitive?.content) {
            "mural.session.created" -> updateSession { it.providerID = (event["session"] as? JsonObject)?.get("id")?.jsonPrimitive?.content; it.voiceSeconds = 15.0 }
            "session.started" -> if (state == "connecting") {
                state = "active"; activity = ConversationActivity(activityNow()); conversationPace = ConversationPace(); inactivitySeconds = null
                updateSession { it.providerID = (event["session"] as? JsonObject)?.get("id")?.jsonPrimitive?.content ?: it.providerID }
                publishLiveSessionSnapshot()
                command("instructions", TeachingPolicy.greeting(language)); startDurationChecks()
            }
            "session.input_transcript.delta", "session.output_transcript.delta" -> {
                val text = event["delta"]?.jsonPrimitive?.contentOrNull ?: return
                val start = event["start_ms"]?.jsonPrimitive?.intOrNull ?: return
                val end = event["end_ms"]?.jsonPrimitive?.intOrNull ?: return
                if (start < 0 || end < start || text.length > 50000) return
                val speaker = if (event["type"]?.jsonPrimitive?.content == "session.input_transcript.delta") Speaker.user else Speaker.assistant
                if (speaker == Speaker.user && isSpuriousInputTranscript(text, language.id, archive.preferences.meaningLanguage)) {
                    return
                }
                updateSession { it.append(Fragment(id = event["event_id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(), speaker = speaker, text = text, startMS = start, endMS = end, meaningVisible = archive.preferences.meaningVisible)) }
                if (text.isNotBlank()) {
                    if (speaker == Speaker.user) { activity.learnerEngaged(activityNow()); inactivitySeconds = null }
                    else activity.assistantActive(activityNow())
                }
                if (speaker == Speaker.assistant) { scheduleTranslation(); if (state == "active") checkLanguage() } else scheduleAssessment()
            }
            "session.delegation.created" -> {
                val d = event["delegation"] as? JsonObject ?: return
                if (d["target"]?.jsonPrimitive?.content == "client") d["id"]?.jsonPrimitive?.content?.let(::delegate)
            }
            "session.usage.updated", "session.closed" -> {
                val seconds = (event["usage"] as? JsonObject)?.get("seconds")?.jsonPrimitive?.doubleOrNull
                if (seconds != null && seconds.isFinite() && seconds in 0.0..31536000.0) updateSession { it.voiceSeconds = seconds }
                if (event["type"]?.jsonPrimitive?.content == "session.closed") finish(true)
            }
            "error" -> { notice = getApplication<Application>().getString(R.string.notice_voice_update_rejected) }
        }
    }
    private fun isSpuriousInputTranscript(text: String, targetLanguageID: String, meaningLanguage: String): Boolean {
        val target = targetLanguageID.lowercase()
        val meaning = meaningLanguage.lowercase()
        val isGermanContext = target == "de" || meaning.contains("deutsch") || meaning.contains("german") || meaning.contains("alemán")
        if (!isGermanContext) {
            val lower = text.lowercase()
            val spuriousPatterns = listOf(
                "sehr technisch", "diskret und infidel", "alle gleich wie",
                "sehr diskret", "und infidel", "technisch diskret"
            )
            if (spuriousPatterns.any { lower.contains(it) }) return true
        }
        return false
    }
    private fun startDurationChecks() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (state == "active") {
                delay(1000)
                val current = session ?: break
                if (current.id in hostedSessionIDs && hostedBindings.reachedDeadline(current.id)) {
                    updateSession { it.endReason = "Reserved conversation time ended" }; finish(false); break
                }
                if (nowSeconds() - current.startedAt > archive.preferences.sessionMinutes * 60) {
                    notice = getApplication<Application>().getString(R.string.notice_time_limit_reached); end("Time limit"); break
                }
                inactivitySeconds = null
                if (voiceSession) when (val next = activity.tick(activityNow(), isMuted, working || delegations.isNotEmpty())) {
                    ConversationActivity.Action.CheckIn -> command("instructions", TeachingPolicy.checkIn(language))
                    is ConversationActivity.Action.Warning -> inactivitySeconds = next.seconds
                    ConversationActivity.Action.End -> { notice = getApplication<Application>().getString(R.string.notice_ended_inactivity); end("Inactivity"); break }
                    ConversationActivity.Action.Wait -> Unit
                }
            }
        }
    }

    private fun addUsage(s: SessionRecord, usage: APIUsage) {
        s.inputTokens = (s.inputTokens.toLong() + usage.input).coerceAtMost(1_000_000_000).toInt()
        s.outputTokens = (s.outputTokens.toLong() + usage.output).coerceAtMost(1_000_000_000).toInt()
        s.searchCalls = (s.searchCalls.toLong() + usage.searches).coerceAtMost(1_000_000_000).toInt()
    }
    private fun scheduleTranslation(utteranceComplete: Boolean = state == "ended") {
        if (!archive.preferences.meaningVisible || archive.preferences.aiConsentVersion != 1) return
        val current = session ?: return
        val passage = current.passages.lastOrNull { it.speaker == Speaker.assistant } ?: return
        val request = MeaningRequest(current.id, passage, current.languageID, archive.preferences.meaningLanguage)
        meanings.update(request, current.translations[request.cacheKey], utteranceComplete, conversationEnded = state == "ended")
    }
    fun retryMeaning() { scheduleTranslation(); meanings.retry() }
    private fun checkLanguage() {
        val passage = session?.passages?.lastOrNull { it.speaker == Speaker.assistant }
        if (!LanguageDetector.shouldCheck(passage, lastLanguageRedirect) || languageCheckJob?.isActive == true) return
        val module = language; val passageID = passage!!.id; val text = passage.text
        languageCheckJob = viewModelScope.launch {
            val detected = languageDetector.detect(text) ?: return@launch
            if (state == "active" && lastLanguageRedirect != passageID &&
                TeachingPolicy.shouldRedirectSpeech(module, detected.languageID, detected.confidence)) {
                lastLanguageRedirect = passageID
                command("instructions", TeachingPolicy.redirect(module))
            }
        }
    }
    @Serializable private data class AssessmentResponse(val outcome: Outcome, val suggestedLevel: Int, val nextGoal: String, val capability: String, val words: List<WordProposal>)
    private fun assessmentSchema(id: String): JsonObject {
        fun string() = buildJsonObject { put("type", "string") }
        fun obj(fields: Map<String, JsonElement>) = buildJsonObject {
            put("type", "object"); put("properties", JsonObject(fields)); put("required", JsonArray(fields.keys.map(::JsonPrimitive))); put("additionalProperties", false)
        }
        fun enumeration(values: List<String>) = buildJsonObject { put("type", "string"); put("enum", JsonArray(values.map(::JsonPrimitive))) }
        return obj(mapOf(
            "outcome" to enumeration(Outcome.entries.map { it.name }),
            "suggestedLevel" to buildJsonObject { put("type", "integer"); put("minimum", 0); put("maximum", 5) },
            "nextGoal" to string(), "capability" to string(),
            "words" to buildJsonObject {
                put("type", "array"); put("maxItems", 12)
                put("items", obj(mapOf("lemma" to string(), "meaning" to string(), "form" to string(), "quote" to string(),
                    "language" to enumeration(listOf(id, "en", "mixed", "uncertain").distinct()),
                    "kind" to enumeration(EvidenceKind.entries.map { it.name }),
                    "confidence" to buildJsonObject { put("type", "number"); put("minimum", 0); put("maximum", 1) },
                    "sourceIDs" to buildJsonObject { put("type", "array"); put("items", string()) }
                )))
            }
        ))
    }
    private suspend fun requestAssessment(snapshot: SessionRecord, passage: Passage): FinalAssessmentResult {
        if (archive.preferences.aiConsentVersion != 1) throw IllegalStateException("AI processing consent is required.")
        val module = LanguageRegistry.get(snapshot.languageID) ?: throw IllegalStateException("Unsupported language.")
        val result = teaching(snapshot.id, HelperPurpose.ASSESSMENT, passage.revisionKey,
            TeachingPolicy.assessment(module), helperContext(snapshot, passage), assessmentSchema(module.id))
        val rawText = result.text.trim()
        val firstBrace = rawText.indexOf('{')
        val lastBrace = rawText.lastIndexOf('}')
        val cleanText = if (firstBrace in 0 until lastBrace) {
            rawText.substring(firstBrace, lastBrace + 1)
        } else if (rawText.startsWith("```")) {
            rawText.substringAfter("\n").substringBeforeLast("```").trim()
        } else rawText
        val decoded = json.decodeFromString<AssessmentResponse>(cleanText)
        android.util.Log.d("MuralAssessment", "requestAssessment decoded outcome=${decoded.outcome}, words=${decoded.words.size}")
        val proposal = Assessment(passage.id, passage.revisionKey, decoded.outcome, decoded.suggestedLevel, decoded.nextGoal, decoded.capability, decoded.words, context = snapshot.themeID ?: "free", textAssemblyVersion = 2)
        return FinalAssessmentResult(snapshot.id, snapshot.languageID, proposal, result.usage.input, result.usage.output, result.usage.searches)
    }
    /** Hosted finalization awaits the original helper for its remaining lease window, without entering BYOK recovery. */
    private fun finishHostedAssessment(snapshot: SessionRecord) {
        val passage = FinalAssessmentRecovery.passage(snapshot) ?: return
        if (hostedFinalAssessmentJobs.containsKey(snapshot.id) || !hostedBindings.canAssess(snapshot.id)) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(HostedConversationBindings.POST_END_MILLIS) {
                    val result = requestAssessment(snapshot, passage)
                    if (archive.preferences.aiConsentVersion != 1) return@withTimeout
                    result.applying(archive.sessions.firstOrNull { it.id == result.sessionID })?.let { updated ->
                        save(updated)
                        if (session?.id == updated.id) session = clone(updated)
                        persistAndWait()
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep uncertain evidence unassessed; never submit a replacement request. */ }
            finally { hostedFinalAssessmentJobs.remove(snapshot.id) }
        }
        hostedFinalAssessmentJobs[snapshot.id] = job
        job.start()
    }

    private fun scheduleAssessment() {
        assessmentJob?.cancel()
        assessmentJob = viewModelScope.launch {
            delay(6000)
            val snapshot = session?.let(::clone) ?: return@launch
            val passage = snapshot.passages.lastOrNull { it.speaker == Speaker.user && it.text.trim().length >= 2 } ?: return@launch
            if (snapshot.assessments.any { it.passageID == passage.id && it.revisionKey == passage.revisionKey }) return@launch
            try {
                android.util.Log.d("MuralAssessment", "Running assessment for passage: ${passage.text}")
                val result = requestAssessment(snapshot, passage)
                val current = archive.sessions.firstOrNull { it.id == snapshot.id && it.languageID == snapshot.languageID } ?: return@launch
                val valid = LearningEngine.validate(result.assessment, current)
                if (valid == null) {
                    android.util.Log.w("MuralAssessment", "LearningEngine.validate rejected assessment for passage: ${passage.text}")
                    return@launch
                }
                android.util.Log.d("MuralAssessment", "Assessment validated! Words recorded: ${valid.words.map { it.lemma }}")
                val updated = clone(current)
                updated.assessments.removeAll { it.passageID == valid.passageID }; updated.assessments += valid
                addUsage(updated, APIUsage(result.inputTokens, result.outputTokens, result.searchCalls)); save(updated)
                if (session?.id == updated.id) session = clone(updated)
                if (state == "active" && session?.id == snapshot.id &&
                    session?.passages?.lastOrNull { it.speaker == Speaker.user }?.revisionKey == passage.revisionKey) {
                    // Keep assessment notes in learning records; injecting them during speech can make the voice read them aloud.
                    if (voiceSession && conversationPace.observe(valid, passage, snapshot.languageID)) {
                        command("instructions", conversationPace.instruction)
                    }
                }
            } catch (_: CancellationException) { }
            catch (e: Exception) {
                android.util.Log.w("MuralAssessment", "Assessment scheduling failed", e)
            }
        }
    }
    private fun delegate(id: String) {
        if (state != "active" || delegations.containsKey(id)) return
        val sessionID = session?.id ?: return
        delegations[id] = viewModelScope.launch {
            try {
                delay(500)
                val snapshot = session ?: return@launch
                val result = teaching(snapshot.id, HelperPurpose.DELEGATION, id,
                    TeachingPolicy.delegation(language), helperContext(snapshot), search = snapshot.searchCalls < 3)
                if (state != "active" || session?.id != sessionID) return@launch
                updateSession { addUsage(it, result.usage); if (result.sources.isNotEmpty()) it.topics += TopicBrief(languageID = it.languageID, query = getApplication<Application>().getString(R.string.topics_from_conversation), text = result.text, sources = result.sources) }
                command("commentary", result.text, id)
            } catch (_: CancellationException) { }
            catch (_: Exception) { if (session?.id == sessionID) command("commentary", language.lookupUnavailableReply, id) }
            finally { delegations.remove(id) }
        }
    }
    fun sendTyped(text: String) {
        typedReplyError = null
        val clean = text.trim().take(2000)
        if (clean.isEmpty() || working || state in listOf("connecting", "closing") || !cloudReady()) return
        if (state != "active") {
            if (conversationProvider == ConversationProvider.HOSTED_MINUTES) {
                presentError(getApplication<Application>().getString(R.string.hosted_typed_start)); return
            }
            newSession(false); state = "active"; startDurationChecks()
        }
        val id = session!!.id; val token = generation
        val offset = if (voiceSession) session!!.nextTypedVoiceOffsetMS
            else ((nowSeconds() - session!!.startedAt) * 1000).toInt().coerceAtLeast(0)
        val fragment = Fragment(speaker = Speaker.user, text = clean, startMS = offset, endMS = offset + 1, meaningVisible = archive.preferences.meaningVisible, typed = true)
        val draft = clone(session!!).also { it.append(fragment) }
        if (!voiceSession) {
            updateSession { it.append(fragment) }
        }
        if (voiceSession) { activity.learnerEngaged(activityNow()); inactivitySeconds = null }; working = true
        actionJob = viewModelScope.launch {
            try {
                val activeThemeId = selectedTheme?.id ?: "general"
                val instructions = TeachingPolicy.typedReply(
                    language = language,
                    theme = selectedTheme,
                    mcerLevel = currentMcerLevel,
                    personality = getThemePersonality(activeThemeId),
                    themeMemory = getThemeMemory(activeThemeId),
                    pedagogicalMode = currentPedagogicalMode,
                    interests = archive.preferences.interests,
                    meaningLanguage = archive.preferences.meaningLanguage,
                    voiceSpeed = voiceSpeed,
                    learner = learner
                )
                val result = teaching(id, HelperPurpose.TYPED_REPLY, UUID.randomUUID().toString(), instructions, helperContext(draft))
                if (token != generation || session?.id != id || state != "active") return@launch
                updateSession { addUsage(it, result.usage) }
                if (voiceSession) {
                    if (!command("thinking", "The learner typed (data): ${clean.take(650)}") || !command("commentary", result.text)) {
                        typedReplyError = getApplication<Application>().getString(R.string.error_send_message_failed)
                        return@launch
                    }
                    updateSession { it.append(fragment) }
                } else {
                    val end = ((nowSeconds() - session!!.startedAt) * 1000).toInt().coerceAtLeast(offset + 2)
                    updateSession { it.append(Fragment(speaker = Speaker.assistant, text = result.text, startMS = end, endMS = end + 1)) }
                    scheduleTranslation()
                }
                typedRepliesSent++
                scheduleAssessment()
            } catch (_: CancellationException) { }
            catch (e: Exception) {
                android.util.Log.e("MuralTypedReply", "sendTypedReply error", e)
                if (session?.id == id) {
                    typedReplyError = resolveMessage(e, R.string.error_send_message_failed)
                    if (errorNeedsKeySetup(e) || needsAccountRecovery(e) || !voiceSession) presentError(e, R.string.error_send_message_failed)
                }
            }
            finally { if (token == generation) working = false }
        }
    }
    fun lookup(word: String, sentence: String) {
        if (!cloudReady() || word.isBlank()) return
        clearLookup()
        val token = generation; val id = session?.id; val request = lookupGeneration
        lookupLoading = true
        lookupJob = viewModelScope.launch {
            try {
                val result = teaching(id, HelperPurpose.LOOKUP, UUID.randomUUID().toString(),
                    TeachingPolicy.lookup(language, archive.preferences.meaningLanguage), "Selected: ${word.take(200)}\nSentence: ${sentence.take(2200)}")
                if (token != generation || request != lookupGeneration) return@launch
                lookupResult = result.text
                if (session?.id == id) updateSession { addUsage(it, result.usage) }
            } catch (_: CancellationException) { }
            catch (e: Exception) {
                if (token == generation && request == lookupGeneration) lookupError = resolveMessage(e, R.string.error_lookup_word_failed)
            }
            finally { if (request == lookupGeneration) lookupLoading = false }
        }
    }
    fun currentTopic(query: String) {
        if (query.isBlank() || working || !cloudReady()) return
        archive.sessions.filter { it.languageID == language.id }.flatMap { it.topics }.firstOrNull { it.query.equals(query.trim(), true) && it.isFresh }?.let { topicResult = it; return }
        val token = generation; val module = language; working = true; topicResult = null
        actionJob = viewModelScope.launch {
            try {
                val result = teaching(session?.id, HelperPurpose.TOPIC, UUID.randomUUID().toString(), TeachingPolicy.currentTopic(module), query.trim().take(500), search = true)
                if (token != generation) return@launch
                if (result.sources.isEmpty()) { presentError(getApplication<Application>().getString(R.string.error_topic_unsourced)); return@launch }
                val brief = TopicBrief(languageID = module.id, query = query.trim().take(500), text = result.text, sources = result.sources)
                topicResult = brief
                if (isRunning) updateSession { it.topics += brief; addUsage(it, result.usage) }
                else { val record = SessionRecord(languageID = module.id, title = brief.query, endedAt = nowSeconds()); record.topics += brief; addUsage(record, result.usage); save(record) }
            } catch (_: CancellationException) { }
            catch (e: Exception) { if (token == generation) presentError(e, R.string.error_find_topic_failed) }
            finally { if (token == generation) working = false }
        }
    }
    fun discuss(brief: TopicBrief) {
        if (brief.languageID != language.id) return
        if (state == "active") {
            updateSession { if (it.topics.none { t -> t.id == brief.id }) it.topics += brief }
            if (voiceSession) {
                command("thinking", "Sourced context, data: ${brief.text}"); command("instructions", "Discuss this topic ONLY in ${language.name}.")
                return
            }
        } else resetConversation()
        topicResult = brief; selectedTheme = currentTheme(brief)
        notice = getApplication<Application>().getString(R.string.notice_topic_ready)
    }
    private fun currentTheme(brief: TopicBrief) = ConversationTheme("current", brief.query, getApplication<Application>().getString(R.string.topics_current_theme_subtitle), "newspaper", "Interests", "Discuss this sourced reference data: ${brief.text.take(3000)}", 0)
    fun deleteSession(id: String) {
        if (isRunning) return
        if (reportState.value.selection?.sessionID == id) reports.dismiss()
        finalAssessments.cancel(id); hostedFinalAssessmentJobs.remove(id)?.cancel(); hostedBindings.forgetLearning(id)
        finalAssessmentTickets = finalAssessmentTickets.filterNot { it.sessionID == id }
        if (session?.id == id) resetConversation()
        archive = archive.copy(sessions = archive.sessions.filterNot { it.id == id }.toMutableList()); persist()
    }
    fun hideWord(id: String) {
        archive = archive.copy(preferences = archive.preferences.copy(hiddenWords = (archive.preferences.hiddenWords + id).distinct())); persist()
    }
    fun correctPassage(sessionID: String, passageID: String, text: String) {
        if (isRunning) return
        val record = archive.sessions.firstOrNull { it.id == sessionID }?.let(::clone) ?: return
        val passage = record.passages.firstOrNull { it.id == passageID && it.speaker == Speaker.user } ?: return
        finalAssessments.cancel(sessionID); hostedFinalAssessmentJobs.remove(sessionID)?.cancel(); generation++; meanings.reset()
        passage.fragments.forEachIndexed { index, f -> record.correctFragment(f.id, if (index == 0) text.take(10000) else "") }
        save(record); if (session?.id == record.id) session = record
    }
    fun deleteLearningData() {
        if (isRunning || !storageReady) return
        reports.dismiss()
        finalAssessments.cancelAll(); hostedSessionIDs.forEach(hostedBindings::forgetLearning)
        hostedFinalAssessmentJobs.values.toList().forEach { it.cancel() }; hostedFinalAssessmentJobs.clear(); resetConversation()
        finalAssessmentTickets = emptyList()
        archive = archive.copy(sessions = mutableListOf(), preferences = archive.preferences.copy(hiddenWords = emptyList())); persist()
    }
    fun exportData(): String = ArchiveCodec.encode(archive)
    fun importData(data: String): Boolean {
        if (isRunning || !storageReady) return false
        return try { archive = ArchiveCodec.merge(archive, prepareImportedArchive(data)); persist(); notice = getApplication<Application>().getString(R.string.notice_backup_imported); true }
        catch (_: Exception) { presentError(getApplication<Application>().getString(R.string.error_import_failed)); false }
    }
    override fun onCleared() {
        hostedBindings.disableHelpers()
        if (!shouldStayAliveInBackground() || !YamyVoiceForegroundService.isRunning) {
            transport.disconnect()
            stopBackgroundVoiceService()
        }
        super.onCleared()
    }
}
