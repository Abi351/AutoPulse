package com.example.hardware

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

enum class ExternalRepeatMode {
    NONE,
    ALL,
    ONE,
}

data class ExternalMediaInfo(
    val hasActiveSession: Boolean = false,
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val supportsSeek: Boolean = false,
    val supportsShuffle: Boolean = false,
    val supportsRepeat: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: ExternalRepeatMode = ExternalRepeatMode.NONE,
)

object UniversalMediaSessionHolder {
    private var managerRef: WeakReference<UniversalMediaManager>? = null

    fun register(manager: UniversalMediaManager) {
        managerRef = WeakReference(manager)
    }

    fun notifyListenerChanged(context: Context) {
        managerRef?.get()?.refreshAccessAndSessions()
    }
}

/**
 * Pont universel MediaSessionManager + MediaControllerCompat :
 * Pilote Lecture/Pause, Suivant/Précédent, SeekTo, Aléatoire, Répéter et suit le temps écoulé
 * chaque seconde pour toute app audio active (Spotify, YouTube Music, Deezer, Orpheon Beta, etc.)
 * avec compression des pochettes en RGB_565 (max 256 px).
 */
class UniversalMediaManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent = ComponentName(context, AutoPulseNotificationListenerService::class.java)
    private val pm = context.packageManager

    private val _externalMedia = MutableStateFlow(ExternalMediaInfo())
    val externalMedia: StateFlow<ExternalMediaInfo> = _externalMedia.asStateFlow()

    private val _hasNotificationAccess = MutableStateFlow(false)
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    private var activeController: MediaController? = null
    private var activeCompatController: MediaControllerCompat? = null
    private var sessionsListenerRegistered = false
    private var progressTickerJob: Job? = null
    private val appLabelCache = HashMap<String, String>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishControllerState(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishControllerState(activeController)
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) {
            publishControllerState(activeController)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishControllerState(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            publishControllerState(activeController)
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectBestController(controllers.orEmpty())
    }

    init {
        UniversalMediaSessionHolder.register(this)
        refreshAccessAndSessions()
    }

    fun isNotificationAccessGranted(): Boolean {
        return runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)
    }

    fun openNotificationListenerSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun refreshAccessAndSessions() {
        val granted = isNotificationAccessGranted()
        _hasNotificationAccess.value = granted
        if (!granted) {
            detachActiveController()
            _externalMedia.value = ExternalMediaInfo()
            return
        }
        registerSessionsListenerIfNeeded()
        refreshSessions()
    }

    private fun registerSessionsListenerIfNeeded() {
        if (sessionsListenerRegistered) return
        val sm = sessionManager ?: return
        runCatching {
            sm.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
            sessionsListenerRegistered = true
        }
    }

    fun refreshSessions() {
        val sm = sessionManager ?: return
        if (!isNotificationAccessGranted()) return
        val controllers = runCatching {
            sm.getActiveSessions(listenerComponent)
        }.getOrDefault(emptyList())
        selectBestController(controllers)
    }

    private fun selectBestController(controllers: List<MediaController>) {
        val externalControllers = controllers.filter { it.packageName != context.packageName }
        val chosen = externalControllers.firstOrNull { isControllerPlaying(it) }
            ?: externalControllers.firstOrNull()

        if (chosen?.sessionToken != activeController?.sessionToken) {
            detachActiveController()
            activeController = chosen
            if (chosen != null) {
                chosen.registerCallback(controllerCallback)
                activeCompatController = runCatching {
                    val compatToken = MediaSessionCompat.Token.fromToken(chosen.sessionToken)
                    MediaControllerCompat(context, compatToken).also { compat ->
                        compat.registerCallback(compatCallback)
                    }
                }.getOrNull()
            }
        }
        publishControllerState(chosen)
    }

    private fun detachActiveController() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        runCatching { activeCompatController?.unregisterCallback(compatCallback) }
        activeCompatController = null
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
    }

    private fun isControllerPlaying(controller: MediaController): Boolean {
        val state = controller.playbackState?.state ?: return false
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun publishControllerState(controller: MediaController?) {
        if (controller == null) {
            progressTickerJob?.cancel()
            _externalMedia.value = ExternalMediaInfo()
            return
        }

        val meta = controller.metadata
        val playback = controller.playbackState
        val compat = activeCompatController
        val compatPlayback = runCatching { compat?.playbackState }.getOrNull()

        val pkg = controller.packageName
        val appName = resolveAppLabel(pkg)

        val title = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: appName

        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: appName

        val rawArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val compressedArt = compressAlbumArtRgb565(rawArt)

        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val playing = isControllerPlaying(controller)
        val livePosition = computeCurrentPositionMs(playback, duration, playing)

        val platformActions = playback?.actions ?: 0L
        val compatActions = compatPlayback?.actions ?: 0L
        val combinedActions = platformActions or compatActions

        val supportsSeek = duration > 0L && (
            (combinedActions and PlaybackState.ACTION_SEEK_TO) != 0L ||
                (combinedActions and PlaybackStateCompat.ACTION_SEEK_TO) != 0L
            )

        val shuffleCustomAction = findCustomAction(playback, "shuffle")
        val repeatCustomAction = findCustomAction(playback, "repeat")

        val supportsShuffle = compat != null && (
            (compatActions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L ||
                compat.shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID ||
                shuffleCustomAction != null
            )

        val supportsRepeat = compat != null && (
            (compatActions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L ||
                compat.repeatMode != PlaybackStateCompat.REPEAT_MODE_INVALID ||
                repeatCustomAction != null
            )

        val shuffleOn = runCatching {
            val mode = compat?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_NONE
            mode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                mode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
        }.getOrDefault(false)

        val repeatMode = runCatching {
            when (compat?.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> ExternalRepeatMode.ONE
                PlaybackStateCompat.REPEAT_MODE_ALL,
                PlaybackStateCompat.REPEAT_MODE_GROUP -> ExternalRepeatMode.ALL
                else -> ExternalRepeatMode.NONE
            }
        }.getOrDefault(ExternalRepeatMode.NONE)

        _externalMedia.update {
            ExternalMediaInfo(
                hasActiveSession = true,
                packageName = pkg,
                appName = appName,
                title = title,
                artist = artist,
                albumArt = compressedArt,
                isPlaying = playing,
                positionMs = livePosition,
                durationMs = duration,
                supportsSeek = supportsSeek,
                supportsShuffle = supportsShuffle,
                supportsRepeat = supportsRepeat,
                shuffleEnabled = shuffleOn,
                repeatMode = repeatMode,
            )
        }

        if (playing && duration > 0L) {
            startPositionTicker()
        } else {
            progressTickerJob?.cancel()
            progressTickerJob = null
        }
    }

    private fun startPositionTicker() {
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val controller = activeController ?: break
                val playback = controller.playbackState ?: break
                if (!isControllerPlaying(controller)) break
                val duration = _externalMedia.value.durationMs
                val updatedPos = computeCurrentPositionMs(playback, duration, true)
                _externalMedia.update { it.copy(positionMs = updatedPos) }
            }
        }
    }

    private fun computeCurrentPositionMs(
        playback: PlaybackState?,
        durationMs: Long,
        isPlaying: Boolean,
    ): Long {
        if (playback == null) return 0L
        val basePos = playback.position.coerceAtLeast(0L)
        if (!isPlaying || playback.lastPositionUpdateTime <= 0L) {
            return if (durationMs > 0L) basePos.coerceAtMost(durationMs) else basePos
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime).coerceAtLeast(0L)
        val speed = if (playback.playbackSpeed > 0f) playback.playbackSpeed else 1.0f
        val computed = basePos + (elapsedMs * speed).toLong()
        return if (durationMs > 0L) computed.coerceIn(0L, durationMs) else computed.coerceAtLeast(0L)
    }

    private fun findCustomAction(playback: PlaybackState?, keyword: String): PlaybackState.CustomAction? {
        val actions = playback?.customActions ?: return null
        return actions.firstOrNull { custom ->
            custom.action.contains(keyword, ignoreCase = true) ||
                custom.name.toString().contains(keyword, ignoreCase = true)
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val resolved = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        appLabelCache[packageName] = resolved
        return resolved
    }

    fun play(): Boolean {
        val c = activeController ?: return false
        runCatching { c.transportControls.play() }
        return true
    }

    fun pause(): Boolean {
        val c = activeController ?: return false
        runCatching { c.transportControls.pause() }
        return true
    }

    fun togglePlayPause(): Boolean {
        val c = activeController ?: return false
        if (isControllerPlaying(c)) {
            runCatching { c.transportControls.pause() }
        } else {
            runCatching { c.transportControls.play() }
        }
        return true
    }

    fun skipToNext(): Boolean {
        val c = activeController ?: return false
        runCatching { c.transportControls.skipToNext() }
        return true
    }

    fun skipToPrevious(): Boolean {
        val c = activeController ?: return false
        runCatching { c.transportControls.skipToPrevious() }
        return true
    }

    fun seekTo(positionMs: Long): Boolean {
        val c = activeController ?: return false
        val maxDur = _externalMedia.value.durationMs
        val target = if (maxDur > 0L) positionMs.coerceIn(0L, maxDur) else positionMs.coerceAtLeast(0L)
        runCatching { c.transportControls.seekTo(target) }
        _externalMedia.update { it.copy(positionMs = target) }
        return true
    }

    fun toggleShuffle(): Boolean {
        val current = _externalMedia.value
        if (!current.hasActiveSession || !current.supportsShuffle) return false

        val compat = activeCompatController
        val compatActions = runCatching { compat?.playbackState?.actions ?: 0L }.getOrDefault(0L)
        if (compat != null && (compatActions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L) {
            val nextMode = if (current.shuffleEnabled) {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            }
            runCatching { compat.transportControls.setShuffleMode(nextMode) }
            _externalMedia.update { it.copy(shuffleEnabled = !current.shuffleEnabled) }
            return true
        }

        val custom = findCustomAction(activeController?.playbackState, "shuffle")
        if (custom != null) {
            runCatching { activeController?.transportControls?.sendCustomAction(custom, custom.extras) }
            _externalMedia.update { it.copy(shuffleEnabled = !current.shuffleEnabled) }
            return true
        }

        if (compat != null) {
            val nextMode = if (current.shuffleEnabled) {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            }
            runCatching { compat.transportControls.setShuffleMode(nextMode) }
            _externalMedia.update { it.copy(shuffleEnabled = !current.shuffleEnabled) }
            return true
        }
        return false
    }

    fun toggleRepeat(): Boolean {
        val current = _externalMedia.value
        if (!current.hasActiveSession || !current.supportsRepeat) return false

        val nextModeEnum = when (current.repeatMode) {
            ExternalRepeatMode.NONE -> ExternalRepeatMode.ALL
            ExternalRepeatMode.ALL -> ExternalRepeatMode.ONE
            ExternalRepeatMode.ONE -> ExternalRepeatMode.NONE
        }
        val compatRepeatConst = when (nextModeEnum) {
            ExternalRepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
            ExternalRepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
            ExternalRepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
        }

        val compat = activeCompatController
        val compatActions = runCatching { compat?.playbackState?.actions ?: 0L }.getOrDefault(0L)
        if (compat != null && (compatActions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L) {
            runCatching { compat.transportControls.setRepeatMode(compatRepeatConst) }
            _externalMedia.update { it.copy(repeatMode = nextModeEnum) }
            return true
        }

        val custom = findCustomAction(activeController?.playbackState, "repeat")
        if (custom != null) {
            runCatching { activeController?.transportControls?.sendCustomAction(custom, custom.extras) }
            _externalMedia.update { it.copy(repeatMode = nextModeEnum) }
            return true
        }

        if (compat != null) {
            runCatching { compat.transportControls.setRepeatMode(compatRepeatConst) }
            _externalMedia.update { it.copy(repeatMode = nextModeEnum) }
            return true
        }
        return false
    }

    fun release() {
        detachActiveController()
        if (sessionsListenerRegistered) {
            runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
            sessionsListenerRegistered = false
        }
    }

    companion object {
        /**
         * Réduit et convertit toute pochette externe en RGB_565 (max 256 px) pour préserver les 4 Go de RAM.
         */
        fun compressAlbumArtRgb565(source: Bitmap?): Bitmap? {
            if (source == null || source.isRecycled) return null
            val maxDim = 256
            val w = source.width.coerceAtLeast(1)
            val h = source.height.coerceAtLeast(1)
            val scale = if (w > maxDim || h > maxDim) {
                maxDim.toFloat() / maxOf(w, h).toFloat()
            } else {
                1f
            }
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)

            return runCatching {
                if (scale == 1f && source.config == Bitmap.Config.RGB_565) {
                    source
                } else {
                    val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                    val canvas = Canvas(out)
                    val srcRect = android.graphics.Rect(0, 0, w, h)
                    val dstRect = android.graphics.Rect(0, 0, targetW, targetH)
                    canvas.drawBitmap(source, srcRect, dstRect, null)
                    out
                }
            }.getOrNull()
        }
    }
}
