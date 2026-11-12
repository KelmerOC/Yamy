package chat.mural.media

import android.support.v4.media.session.PlaybackStateCompat
import chat.mural.core.VoiceSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BackgroundMediaSessionTest {

    private fun appMainDir(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val direct = File(dir, "src/main")
            if (direct.isDirectory) return direct
            val nested = File(dir, "apps/android/app/src/main")
            if (nested.isDirectory) return nested
            dir = dir.parentFile
        }
        error("Could not locate apps/android/app/src/main from ${File(".").canonicalPath}")
    }

    @Before
    fun setUp() {
        YamySessionCoordinator.clear()
    }

    @After
    fun tearDown() {
        YamySessionCoordinator.clear()
    }

    @Test
    fun coordinatorInitialStateIsInactiveAndClearResetsSnapshot() {
        val initial = YamySessionCoordinator.currentSnapshot
        assertFalse(initial.isRunning)
        assertFalse(initial.isConnecting)
        assertFalse(initial.isMuted)
        assertFalse(initial.isPaused)
        assertEquals(VoiceSettingsStore.DEFAULT_APP_VOLUME, initial.appVolume, 0.0001f)
        assertEquals(initial, YamySessionCoordinator.snapshots.value)

        val active = YamyLiveSessionSnapshot(
            title = "Café en Madrid",
            subtitleStatus = "En vivo",
            albumLabel = "Yamy",
            isRunning = true,
            isConnecting = false,
            isMuted = true,
            appVolume = 0.6f,
            startedAtMs = 1234L,
            totalDurationMs = 5000L,
            mediaOutputEnabled = true,
        )
        YamySessionCoordinator.publish(active)
        assertEquals(active, YamySessionCoordinator.currentSnapshot)
        assertTrue(YamySessionCoordinator.snapshots.value.isRunning)

        YamySessionCoordinator.update { it.copy(isMuted = false, appVolume = 0.8f) }
        assertFalse(YamySessionCoordinator.currentSnapshot.isMuted)
        assertEquals(0.8f, YamySessionCoordinator.currentSnapshot.appVolume, 0.0001f)

        YamySessionCoordinator.clear()
        val cleared = YamySessionCoordinator.currentSnapshot
        assertFalse(cleared.isRunning)
        assertEquals("", cleared.title)
        assertEquals(VoiceSettingsStore.DEFAULT_APP_VOLUME, cleared.appVolume, 0.0001f)
    }

    @Test
    fun volumeStepConstantAndActionDispatchAreSafeWithAndWithoutHandler() {
        assertEquals(0.10f, YamySessionCoordinator.VOLUME_STEP, 0.0001f)

        // Dispatching without a registered handler must be a safe no-op
        YamySessionCoordinator.dispatchToggleMute()
        YamySessionCoordinator.dispatchVolumeDelta(YamySessionCoordinator.VOLUME_STEP)
        YamySessionCoordinator.dispatchVolumeDelta(-YamySessionCoordinator.VOLUME_STEP)
        YamySessionCoordinator.dispatchHelp()
        YamySessionCoordinator.dispatchEnd()

        var toggleCount = 0
        var helpCount = 0
        var endCount = 0
        var simulatedVolume = 0.95f

        val handler = object : YamySessionCoordinator.ActionHandler {
            override fun onToggleMute() {
                toggleCount++
            }

            override fun onVolumeDelta(delta: Float) {
                simulatedVolume = (simulatedVolume + delta).coerceIn(0f, 1f)
            }

            override fun onHelp() {
                helpCount++
            }

            override fun onEndCall() {
                endCount++
            }
        }

        YamySessionCoordinator.registerHandler(handler)
        YamySessionCoordinator.dispatchToggleMute()
        YamySessionCoordinator.dispatchHelp()
        YamySessionCoordinator.dispatchVolumeDelta(YamySessionCoordinator.VOLUME_STEP)
        assertEquals(1.0f, simulatedVolume, 0.0001f)

        simulatedVolume = 0.05f
        YamySessionCoordinator.dispatchVolumeDelta(-YamySessionCoordinator.VOLUME_STEP)
        assertEquals(0.0f, simulatedVolume, 0.0001f)

        YamySessionCoordinator.dispatchEnd()
        assertEquals(1, toggleCount)
        assertEquals(1, helpCount)
        assertEquals(1, endCount)

        YamySessionCoordinator.unregisterHandler(handler)
        YamySessionCoordinator.dispatchToggleMute()
        assertEquals(1, toggleCount)
    }

    @Test
    fun manifestServiceControllerAndMainActivityPreserveBackgroundMediaContract() {
        val mainDir = appMainDir()

        val manifest = File(mainDir, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android:launchMode=\"singleTop\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|mediaPlayback\""))

        val notifIcon = File(mainDir, "res/drawable/ic_yamy_notif.xml")
        assertTrue("Monochrome notification icon ic_yamy_notif.xml must exist", notifIcon.isFile)
        val iconXml = notifIcon.readText()
        assertTrue(iconXml.contains("<vector"))
        assertTrue(iconXml.contains("#FFFFFF"))

        val controllerSource = File(mainDir, "java/chat/mural/media/YamyMediaSessionController.kt").readText()
        assertTrue(controllerSource.contains("import android.support.v4.media.session.MediaSessionCompat"))
        assertTrue(controllerSource.contains("R.drawable.ic_yamy_notif"))
        assertFalse(
            "Must use standard AndroidX MediaSessionCompat without proprietary Samsung APIs",
            controllerSource.contains("com.samsung", ignoreCase = true)
        )

        val serviceSource = File(mainDir, "java/chat/mural/media/YamyVoiceForegroundService.kt").readText()
        assertTrue(serviceSource.contains("YamyMediaSessionController"))
        assertTrue(serviceSource.contains("YamySessionCoordinator"))

        val mainActivitySource = File(mainDir, "java/chat/mural/MainActivity.kt").readText()
        val onStopIdx = mainActivitySource.indexOf("override fun onStop()")
        assertTrue("MainActivity must override onStop()", onStopIdx >= 0)
        val onStopBlock = mainActivitySource.substring(onStopIdx, (onStopIdx + 250).coerceAtMost(mainActivitySource.length))
        assertTrue(
            "MainActivity.onStop() must guard vm.background() with shouldStayAliveInBackground()",
            onStopBlock.contains("shouldStayAliveInBackground()")
        )
    }

    @Test
    fun togglePauseDispatchesCleanlyWithDefaultFallbackAndExplicitHandler() {
        // Safe no-op without handler
        YamySessionCoordinator.dispatchTogglePause()

        // Default interface method forwards onTogglePause -> onToggleMute when omitted
        var fallbackMuteCount = 0
        val defaultHandler = object : YamySessionCoordinator.ActionHandler {
            override fun onToggleMute() {
                fallbackMuteCount++
            }
            override fun onVolumeDelta(delta: Float) = Unit
            override fun onHelp() = Unit
            override fun onEndCall() = Unit
        }
        YamySessionCoordinator.registerHandler(defaultHandler)
        YamySessionCoordinator.dispatchTogglePause()
        assertEquals(1, fallbackMuteCount)

        // Explicit onTogglePause handler receives dedicated pause dispatches
        var explicitPauseCount = 0
        val pauseHandler = object : YamySessionCoordinator.ActionHandler {
            override fun onToggleMute() = Unit
            override fun onTogglePause() {
                explicitPauseCount++
            }
            override fun onVolumeDelta(delta: Float) = Unit
            override fun onHelp() = Unit
            override fun onEndCall() = Unit
        }
        YamySessionCoordinator.registerHandler(pauseHandler)
        YamySessionCoordinator.dispatchTogglePause()
        assertEquals(1, explicitPauseCount)
        assertEquals(1, fallbackMuteCount)
    }

    @Test
    fun notificationControlsExposeStrictlyTwoActionsPauseAndEndCall() {
        assertEquals(2, YamyMediaSessionController.MAX_NOTIFICATION_ACTIONS)

        val supported = YamyMediaSessionController.supportedPlaybackActions()
        assertTrue((supported and PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0L)
        assertTrue((supported and PlaybackStateCompat.ACTION_PLAY) != 0L)
        assertTrue((supported and PlaybackStateCompat.ACTION_PAUSE) != 0L)
        assertTrue((supported and PlaybackStateCompat.ACTION_STOP) != 0L)
        assertEquals(0L, supported and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        assertEquals(0L, supported and PlaybackStateCompat.ACTION_SKIP_TO_NEXT)

        val mainDir = appMainDir()
        val controllerSource = File(mainDir, "java/chat/mural/media/YamyMediaSessionController.kt").readText()
        assertFalse("Help custom action must be removed from media notification", controllerSource.contains("CUSTOM_ACTION_HELP"))
        assertFalse("Skip previous action must be absent", controllerSource.contains("ACTION_SKIP_TO_PREVIOUS"))
        assertFalse("Skip next action must be absent", controllerSource.contains("ACTION_SKIP_TO_NEXT"))
        assertEquals(2, Regex("""\.addAction\(""").findAll(controllerSource).count())
        assertTrue(controllerSource.contains(".setShowActionsInCompactView(0, 1)"))

        val serviceSource = File(mainDir, "java/chat/mural/media/YamyVoiceForegroundService.kt").readText()
        assertTrue(serviceSource.contains("ACTION_TOGGLE_PAUSE -> YamySessionCoordinator.dispatchTogglePause()"))
        assertTrue(serviceSource.contains("ACTION_END_CALL ->"))

        listOf("ic_notif_pause.xml", "ic_notif_play.xml", "ic_notif_end_call.xml").forEach { name ->
            val icon = File(mainDir, "res/drawable/$name")
            assertTrue("$name must exist as monochrome vector drawable", icon.isFile)
            val xml = icon.readText()
            assertTrue(xml.contains("<vector"))
            assertTrue(xml.contains("#FFFFFF"))
        }
        listOf("ic_notif_help.xml", "ic_notif_vol_down.xml", "ic_notif_vol_up.xml").forEach { removed ->
            assertFalse("$removed must be deleted", File(mainDir, "res/drawable/$removed").exists())
        }
    }

    @Test
    fun flatVectorUiPreservesZeroDropShadowsDurationSelectorBarOnlyVolumeAndClearGuardrail() {
        val mainDir = appMainDir()
        val flatUiFiles = listOf(
            "ui/TalkScreen.kt",
            "ui/SoftControls.kt",
            "ui/VolumeController.kt",
            "ui/McerLevelSelector.kt",
            "ui/PedagogicalModeSelector.kt",
            "ui/SessionDurationSelector.kt",
            "ui/LearningTestsUi.kt",
        )
        val nonZeroShadowElevation = Regex("""shadowElevation\s*=\s*(?!0(?:\.0+)?\.dp)\d""")
        flatUiFiles.forEach { rel ->
            val content = File(mainDir, "java/chat/mural/$rel").readText()
            assertFalse("$rel must not use Compose .shadow(", Regex("""\.shadow\(""").containsMatchIn(content))
            assertFalse("$rel must not use non-zero shadowElevation", nonZeroShadowElevation.containsMatchIn(content))
        }

        val talkSource = File(mainDir, "java/chat/mural/ui/TalkScreen.kt").readText()
        assertTrue("TalkScreen must render SessionDurationSelector inside FlowRow", talkSource.contains("FlowRow("))
        assertTrue("TalkScreen must render SessionDurationSelector", talkSource.contains("SessionDurationSelector("))
        assertTrue("TalkScreen must wire selectSessionMinutes", talkSource.contains("vm::selectSessionMinutes"))

        val durationSource = File(mainDir, "java/chat/mural/ui/SessionDurationSelector.kt").readText()
        assertTrue(durationSource.contains("MuralSymbol.Clock"))
        assertTrue(durationSource.contains("tnum"))
        assertTrue(durationSource.contains("session-duration-selector"))

        val volumeSource = File(mainDir, "java/chat/mural/ui/VolumeController.kt").readText()
        assertFalse("Volume bar must not render explanatory hint text", volumeSource.contains("volume_yamy_independent_hint"))
        val cardIdx = volumeSource.indexOf("yamy-volume-card")
        assertTrue(cardIdx >= 0)
        val expandedCardSource = volumeSource.substring(cardIdx)
        assertFalse("Expanded volume bar must not contain any Text composables", expandedCardSource.contains("Text("))
        assertTrue(volumeSource.contains("yamy-volume-mute-toggle"))
        assertTrue(volumeSource.contains("yamy-volume-slider"))

        val guardrailSource = File(mainDir, "java/chat/mural/ui/LearningTestsUi.kt").readText()
        assertTrue(guardrailSource.contains("R.string.guardrail_skip_and_end_all"))
        assertTrue(guardrailSource.contains("guardrail-quick-close"))
    }
}
