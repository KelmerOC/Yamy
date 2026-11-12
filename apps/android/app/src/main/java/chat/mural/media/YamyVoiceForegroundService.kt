package chat.mural.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import chat.mural.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service keeping Yamy's live voice conversation alive when the app moves to the
 * background or the screen locks, paired with a universal MediaStyle notification.
 */
class YamyVoiceForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaController: YamyMediaSessionController? = null
    private var foregroundStarted = false
    private var hasSeenRunningSession = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannel()
        mediaController = YamyMediaSessionController(this)
        observeSessionSnapshots()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) {
            promoteToForeground(YamySessionCoordinator.currentSnapshot)
        }
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> YamySessionCoordinator.dispatchTogglePause()
            ACTION_TOGGLE_MUTE -> YamySessionCoordinator.dispatchToggleMute()
            ACTION_END_CALL -> {
                YamySessionCoordinator.dispatchEnd()
                if (!YamySessionCoordinator.currentSnapshot.isRunning) {
                    stopForegroundAndSelf()
                }
            }
            ACTION_STOP -> stopForegroundAndSelf()
            else -> {
                val snapshot = YamySessionCoordinator.currentSnapshot
                if (snapshot.isRunning) {
                    hasSeenRunningSession = true
                    updateNotification(snapshot)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        YamySessionCoordinator.dispatchEnd()
        stopForegroundAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        foregroundStarted = false
        serviceScope.cancel()
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }

    private fun observeSessionSnapshots() {
        serviceScope.launch {
            combine(
                YamySessionCoordinator.snapshots,
                YamySessionCoordinator.refreshTriggers,
            ) { snapshot, _ -> snapshot }.collect { snapshot ->
                if (snapshot.isRunning) {
                    hasSeenRunningSession = true
                    if (!foregroundStarted) {
                        promoteToForeground(snapshot)
                    } else {
                        updateNotification(snapshot)
                    }
                } else if (hasSeenRunningSession) {
                    stopForegroundAndSelf()
                }
            }
        }
    }

    private fun promoteToForeground(snapshot: YamyLiveSessionSnapshot) {
        val controller = mediaController ?: return
        val notification = controller.updateAndBuildNotification(snapshot, CHANNEL_ID)
        val serviceTypes = foregroundServiceTypes()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceTypes)
            foregroundStarted = true
        } catch (_: SecurityException) {
            val fallbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fallbackType)
                foregroundStarted = true
            }.onFailure {
                stopSelf()
            }
        }
    }

    private fun updateNotification(snapshot: YamyLiveSessionSnapshot) {
        val controller = mediaController ?: return
        val notification = controller.updateAndBuildNotification(snapshot, CHANNEL_ID)
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        isRunning = false
        foregroundStarted = false
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun foregroundServiceTypes(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_live_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_live_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "yamy_live_voice"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START = "chat.mural.media.ACTION_START"
        const val ACTION_STOP = "chat.mural.media.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "chat.mural.media.ACTION_TOGGLE_PAUSE"
        const val ACTION_TOGGLE_MUTE = "chat.mural.media.ACTION_TOGGLE_MUTE"
        const val ACTION_END_CALL = "chat.mural.media.ACTION_END_CALL"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
