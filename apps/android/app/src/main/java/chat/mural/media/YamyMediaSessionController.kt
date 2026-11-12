package chat.mural.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import chat.mural.MainActivity
import chat.mural.R
import kotlin.math.roundToInt

/**
 * Manages a universal AndroidX [MediaSessionCompat] and Spotify-style [androidx.media.app.NotificationCompat.MediaStyle]
 * notification without any OEM-specific APIs, supporting Pixel, Samsung One UI, Xiaomi HyperOS,
 * Motorola, OPPO, and standard Android 8-16 (API 26-36).
 */
class YamyMediaSessionController(
    private val context: Context,
) {
    private val appContext = context.applicationContext

    private val mediaSession = MediaSessionCompat(appContext, MEDIA_SESSION_TAG).apply {
        setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    val snapshot = YamySessionCoordinator.currentSnapshot
                    if (snapshot.isPaused) {
                        YamySessionCoordinator.dispatchTogglePause()
                    } else if (snapshot.isMuted) {
                        YamySessionCoordinator.dispatchToggleMute()
                    }
                }

                override fun onPause() {
                    if (!YamySessionCoordinator.currentSnapshot.isPaused) {
                        YamySessionCoordinator.dispatchTogglePause()
                    }
                }

                override fun onStop() {
                    YamySessionCoordinator.dispatchEnd()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        CUSTOM_ACTION_END -> YamySessionCoordinator.dispatchEnd()
                    }
                }
            },
        )
        isActive = true
    }

    fun updateAndBuildNotification(
        snapshot: YamyLiveSessionSnapshot,
        channelId: String,
    ): Notification {
        val title = snapshot.title.ifBlank { appContext.getString(R.string.notif_default_title) }
        val statusLabel = when {
            snapshot.subtitleStatus.isNotBlank() -> snapshot.subtitleStatus
            snapshot.isConnecting -> appContext.getString(R.string.notif_status_connecting)
            snapshot.isPaused -> appContext.getString(R.string.notif_status_paused)
            snapshot.isMuted -> appContext.getString(R.string.notif_status_muted)
            else -> appContext.getString(R.string.notif_status_listening)
        }
        val volumePercent = (snapshot.appVolume.coerceIn(0f, 1f) * 100f).roundToInt()
        val artistSubtitle = appContext.getString(R.string.notif_subtitle_format, statusLabel, volumePercent)
        val albumLabel = snapshot.albumLabel.ifBlank { appContext.getString(R.string.notif_default_title) }
        val durationMs = snapshot.totalDurationMs.coerceAtLeast(0L)
        val positionMs = if (snapshot.startedAtMs > 0L) {
            (System.currentTimeMillis() - snapshot.startedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val orbBitmap = YamyOrbArtworkFactory.createArtworkBitmap()

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistSubtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumLabel)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, orbBitmap)
            .build()
        mediaSession.setMetadata(metadata)

        val playbackStateCode = when {
            snapshot.isConnecting -> PlaybackStateCompat.STATE_BUFFERING
            snapshot.isPaused -> PlaybackStateCompat.STATE_PAUSED
            snapshot.isRunning -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        val playbackSpeed = if (snapshot.isRunning && !snapshot.isConnecting && !snapshot.isPaused) 1f else 0f
        val supportedActions = supportedPlaybackActions()

        val endCustomAction = PlaybackStateCompat.CustomAction.Builder(
            CUSTOM_ACTION_END,
            appContext.getString(R.string.notif_action_end),
            R.drawable.ic_notif_end_call,
        ).build()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(supportedActions)
            .setState(playbackStateCode, positionMs, playbackSpeed)
            .addCustomAction(endCustomAction)
            .build()
        mediaSession.setPlaybackState(playbackState)

        val contentIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseIcon = if (snapshot.isPaused) R.drawable.ic_notif_play else R.drawable.ic_notif_pause
        val pauseTitle = appContext.getString(
            if (snapshot.isPaused) R.string.notif_action_resume else R.string.notif_action_pause,
        )

        return NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_yamy_notif)
            .setLargeIcon(orbBitmap)
            .setContentTitle(title)
            .setContentText(artistSubtitle)
            .setSubText(albumLabel)
            .setContentIntent(contentIntent)
            .setColorized(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action.Builder(
                    pauseIcon,
                    pauseTitle,
                    serviceActionPendingIntent(YamyVoiceForegroundService.ACTION_TOGGLE_PAUSE, REQUEST_CODE_PAUSE),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notif_end_call,
                    appContext.getString(R.string.notif_action_end),
                    serviceActionPendingIntent(YamyVoiceForegroundService.ACTION_END_CALL, REQUEST_CODE_END),
                ).build(),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    fun release() {
        runCatching {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, YamyVoiceForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val MEDIA_SESSION_TAG = "YamyVoiceSession"
        const val CUSTOM_ACTION_END = "chat.mural.media.CUSTOM_ACTION_END"
        const val MAX_NOTIFICATION_ACTIONS = 2

        internal fun supportedPlaybackActions(): Long =
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP

        private const val REQUEST_CODE_OPEN_APP = 100
        private const val REQUEST_CODE_PAUSE = 103
        private const val REQUEST_CODE_END = 105
    }
}
