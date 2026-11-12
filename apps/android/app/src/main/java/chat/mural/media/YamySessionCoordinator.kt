package chat.mural.media

import android.os.Handler
import android.os.Looper
import chat.mural.core.VoiceSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class YamyLiveSessionSnapshot(
    val title: String = "",
    val subtitleStatus: String = "",
    val albumLabel: String = "",
    val isRunning: Boolean = false,
    val isConnecting: Boolean = false,
    val isMuted: Boolean = false,
    val appVolume: Float = VoiceSettingsStore.DEFAULT_APP_VOLUME,
    val startedAtMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val mediaOutputEnabled: Boolean = true,
    val isPaused: Boolean = false,
)

/**
 * Process-wide bridge between the active voice ViewModel and [YamyVoiceForegroundService] /
 * [YamyMediaSessionController]. Designed to be safe in JVM unit tests when Android Looper / Context
 * are unavailable.
 */
object YamySessionCoordinator {
    const val VOLUME_STEP = 0.10f

    interface ActionHandler {
        fun onToggleMute()
        fun onTogglePause() = onToggleMute()
        fun onVolumeDelta(delta: Float)
        fun onHelp()
        fun onEndCall()
    }

    private val _snapshots = MutableStateFlow(YamyLiveSessionSnapshot())
    val snapshots: StateFlow<YamyLiveSessionSnapshot> = _snapshots.asStateFlow()

    private val _refreshTriggers = MutableStateFlow(0L)
    val refreshTriggers: StateFlow<Long> = _refreshTriggers.asStateFlow()

    val currentSnapshot: YamyLiveSessionSnapshot
        get() = _snapshots.value

    @Volatile
    private var actionHandler: ActionHandler? = null

    fun registerHandler(handler: ActionHandler) {
        actionHandler = handler
    }

    fun unregisterHandler(handler: ActionHandler? = null) {
        if (handler == null || actionHandler === handler) {
            actionHandler = null
        }
    }

    fun publish(snapshot: YamyLiveSessionSnapshot) {
        _snapshots.value = snapshot
    }

    fun update(transform: (YamyLiveSessionSnapshot) -> YamyLiveSessionSnapshot) {
        _snapshots.value = transform(_snapshots.value)
    }

    fun requestNotificationRefresh() {
        _refreshTriggers.value = _refreshTriggers.value + 1L
    }

    fun clear() {
        actionHandler = null
        _snapshots.value = YamyLiveSessionSnapshot()
    }

    fun dispatchToggleMute() {
        val handler = actionHandler ?: return
        runOnMain { handler.onToggleMute() }
    }

    fun dispatchTogglePause() {
        val handler = actionHandler ?: return
        runOnMain { handler.onTogglePause() }
    }

    fun dispatchVolumeDelta(delta: Float) {
        val handler = actionHandler ?: return
        runOnMain { handler.onVolumeDelta(delta) }
    }

    fun dispatchHelp() {
        val handler = actionHandler ?: return
        runOnMain { handler.onHelp() }
    }

    fun dispatchEnd() {
        val handler = actionHandler ?: return
        runOnMain { handler.onEndCall() }
    }

    private fun runOnMain(block: () -> Unit) {
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        if (mainLooper == null || runCatching { Looper.myLooper() == mainLooper }.getOrDefault(false)) {
            block()
        } else {
            Handler(mainLooper).post(block)
        }
    }
}
