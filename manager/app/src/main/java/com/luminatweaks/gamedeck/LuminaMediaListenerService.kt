package com.luminatweaks.gamedeck

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

data class LiveMediaInfo(
    val title: String = "",
    val artist: String = "",
    val packageName: String? = null,
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class LuminaMediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncSessions(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detachController()
    }

    companion object {
        @Volatile
        var currentMedia: LiveMediaInfo = LiveMediaInfo()
            private set

        @Volatile
        private var activeController: MediaController? = null

        @Volatile
        private var onChangedCallback: (() -> Unit)? = null

        private var sessionListenerRegistered = false
        private var lastArtSource: Bitmap? = null
        private var lastArtScaled: Bitmap? = null

        private val controllerCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val ctrl = activeController ?: return
                publishFromController(ctrl)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val ctrl = activeController ?: return
                publishFromController(ctrl)
            }

            override fun onSessionDestroyed() {
                detachController()
                currentMedia = LiveMediaInfo()
                onChangedCallback?.invoke()
            }
        }

        private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            selectBestController(controllers.orEmpty())
        }

        fun bind(context: Context, onChanged: () -> Unit) {
            onChangedCallback = onChanged
            syncSessions(context)
        }

        fun unbind() {
            onChangedCallback = null
            detachController()
        }

        fun syncSessions(context: Context) {
            val appCtx = context.applicationContext
            val msm = appCtx.getSystemService(MediaSessionManager::class.java) ?: return
            val component = ComponentName(appCtx, LuminaMediaListenerService::class.java)
            runCatching {
                if (!sessionListenerRegistered) {
                    msm.addOnActiveSessionsChangedListener(sessionsListener, component)
                    sessionListenerRegistered = true
                }
                val controllers = msm.getActiveSessions(component)
                selectBestController(controllers)
            }
        }

        private fun selectBestController(controllers: List<MediaController>) {
            if (controllers.isEmpty()) {
                detachController()
                if (currentMedia.title.isNotEmpty() || currentMedia.isPlaying) {
                    currentMedia = LiveMediaInfo()
                    onChangedCallback?.invoke()
                }
                return
            }

            val chosen = controllers.firstOrNull { ctrl ->
                val st = ctrl.playbackState?.state
                st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
            } ?: controllers.first()

            if (activeController?.sessionToken != chosen.sessionToken) {
                detachController()
                activeController = chosen
                runCatching { chosen.registerCallback(controllerCallback) }
            }
            publishFromController(chosen)
        }

        private fun detachController() {
            activeController?.let { ctrl ->
                runCatching { ctrl.unregisterCallback(controllerCallback) }
            }
            activeController = null
        }

        private fun publishFromController(controller: MediaController) {
            val meta = controller.metadata
            val pb = controller.playbackState

            val rawTitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: ""
            val rawArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: ""

            val rawArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val scaledArt = if (rawArt != null && !rawArt.isRecycled) {
                if (rawArt === lastArtSource && lastArtScaled != null && !lastArtScaled!!.isRecycled) {
                    lastArtScaled
                } else {
                    val bmp = runCatching {
                        Bitmap.createScaledBitmap(rawArt, 128, 128, true)
                    }.getOrNull()
                    lastArtSource = rawArt
                    lastArtScaled = bmp
                    bmp
                }
            } else {
                lastArtSource = null
                lastArtScaled = null
                null
            }

            val duration = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
            val stateCode = pb?.state ?: PlaybackState.STATE_NONE
            val playing = stateCode == PlaybackState.STATE_PLAYING || stateCode == PlaybackState.STATE_BUFFERING

            val basePos = (pb?.position ?: 0L).coerceAtLeast(0L)
            val updatedAt = pb?.lastPositionUpdateTime ?: 0L
            val speed = pb?.playbackSpeed ?: 1f
            val livePos = if (playing && updatedAt > 0L) {
                val elapsed = (SystemClock.elapsedRealtime() - updatedAt).coerceAtLeast(0L)
                (basePos + (elapsed * speed).toLong()).coerceAtLeast(0L)
            } else {
                basePos
            }.let { pos ->
                if (duration > 0L) pos.coerceIn(0L, duration) else pos
            }

            currentMedia = LiveMediaInfo(
                title = rawTitle.trim(),
                artist = rawArtist.trim(),
                packageName = controller.packageName,
                albumArt = scaledArt,
                isPlaying = playing,
                positionMs = livePos,
                durationMs = duration
            )
            onChangedCallback?.invoke()
        }

        fun playPause(context: Context) {
            val ctrl = activeController
            if (ctrl != null) {
                val st = ctrl.playbackState?.state
                if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) {
                    ctrl.transportControls.pause()
                } else {
                    ctrl.transportControls.play()
                }
            } else {
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }

        fun next(context: Context) {
            val ctrl = activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToNext()
            } else {
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }

        fun previous(context: Context) {
            val ctrl = activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToPrevious()
            } else {
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }

        fun seekTo(positionMs: Long) {
            val ctrl = activeController ?: return
            val target = positionMs.coerceAtLeast(0L)
            ctrl.transportControls.seekTo(target)
            currentMedia = currentMedia.copy(positionMs = target)
            onChangedCallback?.invoke()
        }

        private fun dispatchMediaKey(context: Context, keyCode: Int) {
            val am = context.getSystemService(AudioManager::class.java) ?: return
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }
}
