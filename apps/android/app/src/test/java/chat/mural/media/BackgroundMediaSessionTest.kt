package chat.mural.media

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
}
