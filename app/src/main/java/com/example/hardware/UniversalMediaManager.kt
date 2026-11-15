package com.example.hardware

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    val isFmRadio: Boolean = false,
    val fmFrequencyMhz: String = "",
    val fmStationName: String = "",
    val fmRdsText: String = "",
)

data class FmRadioSnapshot(
    val isActive: Boolean = false,
    val frequencyMhz: String = "",
    val stationName: String = "",
    val rdsText: String = "",
    val packageName: String = "",
    val updatedAtMs: Long = 0L,
)

object UniversalMediaSessionHolder {
    private var managerRef: WeakReference<UniversalMediaManager>? = null

    fun register(manager: UniversalMediaManager) {
        managerRef = WeakReference(manager)
    }

    fun notifyListenerChanged(context: Context) {
        managerRef?.get()?.refreshAccessAndSessions()
    }

    fun updateRadioNotification(
        packageName: String,
        title: String?,
        text: String?,
        subText: String?,
    ) {
        managerRef?.get()?.onRadioNotificationUpdated(packageName, title, text, subText)
    }

    fun clearRadioNotification(packageName: String) {
        managerRef?.get()?.onRadioNotificationRemoved(packageName)
    }
}

/**
 * Pont universel MediaSessionManager + MediaControllerCompat + Récepteur Radio FM autoradio :
 * Pilote Lecture/Pause, Suivant/Précédent, SeekTo, Aléatoire, Répéter, filtre les métadonnées
 * Bluetooth invalides ("Not Provided", "Unknown", "null", etc.) et détecte la Radio FM via
 * BroadcastReceiver dédiés + notifications système.
 */
class UniversalMediaManager(private val context: Context) {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
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
    private val appLabelCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var latestFmRadio: FmRadioSnapshot? = null

    @Volatile
    private var lastNonRadioMediaPlayingAtMs: Long = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val controller = activeController ?: return
            if (!isRadioPackage(controller.packageName) && isControllerPlaying(controller)) {
                lastNonRadioMediaPlayingAtMs = SystemClock.elapsedRealtime()
            }
            publishCombinedState(controller)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishCombinedState(activeController)
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) {
            publishCombinedState(activeController)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishCombinedState(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            publishCombinedState(activeController)
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectBestController(controllers.orEmpty())
    }

    private val fmRadioReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val parsed = parseFmBroadcastIntent(intent) ?: return
            latestFmRadio = if (parsed.isActive) parsed else null
            publishCombinedState(activeController)
        }
    }

    init {
        UniversalMediaSessionHolder.register(this)
        registerFmRadioReceiver()
        refreshAccessAndSessions()
    }

    private fun registerFmRadioReceiver() {
        val filter = IntentFilter().apply {
            FM_RADIO_ACTIONS.forEach { addAction(it) }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(fmRadioReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(fmRadioReceiver, filter)
            }
        }
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
        ioScope.launch {
            val granted = isNotificationAccessGranted()
            _hasNotificationAccess.value = granted
            if (!granted) {
                mainHandler.post {
                    detachActiveController()
                    publishCombinedState(null)
                }
                return@launch
            }
            registerSessionsListenerIfNeeded()
            val sm = sessionManager
            val controllers = if (sm != null) {
                runCatching { sm.getActiveSessions(listenerComponent) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            mainHandler.post {
                selectBestController(controllers)
            }
        }
    }

    private fun registerSessionsListenerIfNeeded() {
        if (sessionsListenerRegistered) return
        val sm = sessionManager ?: return
        mainHandler.post {
            if (sessionsListenerRegistered) return@post
            runCatching {
                sm.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, mainHandler)
                sessionsListenerRegistered = true
            }
        }
    }

    fun refreshSessions() {
        val sm = sessionManager ?: return
        ioScope.launch {
            if (!isNotificationAccessGranted()) return@launch
            val controllers = runCatching {
                sm.getActiveSessions(listenerComponent)
            }.getOrDefault(emptyList())
            mainHandler.post {
                selectBestController(controllers)
            }
        }
    }

    fun onRadioNotificationUpdated(
        packageName: String,
        title: String?,
        text: String?,
        subText: String?,
    ) {
        val parsed = parseFmNotification(
            packageName = packageName,
            title = title,
            text = text,
            subText = subText,
            nowMs = SystemClock.elapsedRealtime(),
        ) ?: return
        latestFmRadio = parsed
        publishCombinedState(activeController)
    }

    fun onRadioNotificationRemoved(packageName: String) {
        val currentFm = latestFmRadio
        if (currentFm != null && currentFm.packageName == packageName) {
            latestFmRadio = null
            publishCombinedState(activeController)
        }
    }

    private fun selectBestController(controllers: List<MediaController>) {
        val externalControllers = controllers.filter { it.packageName != context.packageName }
        val chosen = externalControllers.firstOrNull { isControllerPlaying(it) }
            ?: externalControllers.firstOrNull()

        if (chosen?.sessionToken != activeController?.sessionToken) {
            detachActiveController()
            activeController = chosen
            if (chosen != null) {
                chosen.registerCallback(controllerCallback, mainHandler)
                activeCompatController = runCatching {
                    val compatToken = MediaSessionCompat.Token.fromToken(chosen.sessionToken)
                    MediaControllerCompat(context, compatToken).also { compat ->
                        compat.registerCallback(compatCallback, mainHandler)
                    }
                }.getOrNull()
            }
        }
        if (chosen != null && !isRadioPackage(chosen.packageName) && isControllerPlaying(chosen)) {
            lastNonRadioMediaPlayingAtMs = SystemClock.elapsedRealtime()
        }
        publishCombinedState(chosen)
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

    private fun publishCombinedState(controller: MediaController?) {
        val fm = latestFmRadio?.takeIf { it.isActive }
        val controllerIsRadio = controller != null && isRadioPackage(controller.packageName)
        val nonRadioPlaying = controller != null && !controllerIsRadio && isControllerPlaying(controller)

        // Priorité à la Radio FM si un flux FM récent est actif et qu'aucun autre lecteur musical n'est en cours
        val useFmAsPrimary = fm != null && (
            controller == null ||
                controllerIsRadio ||
                (!nonRadioPlaying && fm.updatedAtMs >= lastNonRadioMediaPlayingAtMs)
            )

        if (useFmAsPrimary && fm != null) {
            progressTickerJob?.cancel()
            progressTickerJob = null
            val freqFormatted = fm.frequencyMhz
            val station = fm.stationName
            val rds = fm.rdsText
            val displayTitle = when {
                station.isNotBlank() -> station
                freqFormatted.isNotBlank() -> "$freqFormatted MHz"
                else -> "Radio FM"
            }
            val displaySubtitle = buildFmSubtitle(freqFormatted, rds)

            _externalMedia.value = ExternalMediaInfo(
                hasActiveSession = true,
                packageName = fm.packageName.ifBlank { "fm.radio" },
                appName = "Radio FM",
                title = displayTitle,
                artist = displaySubtitle,
                albumArt = null,
                isPlaying = true,
                positionMs = 0L,
                durationMs = 0L,
                supportsSeek = false,
                supportsShuffle = false,
                supportsRepeat = false,
                shuffleEnabled = false,
                repeatMode = ExternalRepeatMode.NONE,
                isFmRadio = true,
                fmFrequencyMhz = freqFormatted,
                fmStationName = station,
                fmRdsText = rds,
            )
            return
        }

        if (controller == null) {
            progressTickerJob?.cancel()
            _externalMedia.value = ExternalMediaInfo()
            return
        }

        publishControllerState(controller)
    }

    private fun publishControllerState(controller: MediaController) {
        val meta = controller.metadata
        val playback = controller.playbackState
        val compat = activeCompatController
        val compatPlayback = runCatching { compat?.playbackState }.getOrNull()

        val pkg = controller.packageName
        val cachedLabel = appLabelCache[pkg]
        if (cachedLabel == null) {
            ioScope.launch {
                val resolved = resolveAppLabelSync(pkg)
                if (activeController?.packageName == pkg && resolved != pkg) {
                    _externalMedia.update { current ->
                        if (current.packageName == pkg && current.appName == pkg) {
                            current.copy(appName = resolved)
                        } else {
                            current
                        }
                    }
                }
            }
        }
        val appName = cachedLabel ?: pkg.substringAfterLast('.').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }.ifBlank { "Bluetooth / Externe" }

        val rawTitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.takeIf { sanitizeMediaText(it).isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val title = sanitizeMediaText(rawTitle).ifBlank { appName }

        val rawArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { sanitizeMediaText(it).isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val artist = sanitizeMediaText(rawArtist).ifBlank { appName }

        val isRadioSession = isRadioPackage(pkg) ||
            title.contains("MHz", ignoreCase = true) ||
            artist.contains("MHz", ignoreCase = true)
        val detectedFreq = if (isRadioSession) {
            parseFmFrequencyMhz(title).ifBlank { parseFmFrequencyMhz(artist) }
        } else {
            ""
        }

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
                appName = if (isRadioSession) "Radio FM" else appName,
                title = title,
                artist = if (isRadioSession) buildFmSubtitle(detectedFreq, artist.takeIf { it != appName }.orEmpty()) else artist,
                albumArt = compressedArt,
                isPlaying = playing,
                positionMs = livePosition,
                durationMs = duration,
                supportsSeek = supportsSeek,
                supportsShuffle = supportsShuffle,
                supportsRepeat = supportsRepeat,
                shuffleEnabled = shuffleOn,
                repeatMode = repeatMode,
                isFmRadio = isRadioSession,
                fmFrequencyMhz = detectedFreq,
                fmStationName = if (isRadioSession && detectedFreq.isBlank()) title else if (isRadioSession) stripFrequencyTokens(title).ifBlank { title } else "",
                fmRdsText = if (isRadioSession) artist.takeIf { it != appName }.orEmpty() else "",
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
        progressTickerJob = mainScope.launch {
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

    private fun resolveAppLabelSync(packageName: String): String {
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
        runCatching { context.unregisterReceiver(fmRadioReceiver) }
        if (sessionsListenerRegistered) {
            runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
            sessionsListenerRegistered = false
        }
    }

    companion object {
        val FM_RADIO_ACTIONS = listOf(
            "com.android.fmradio.metachanged",
            "com.android.music.metachanged",
            "com.ts.main.radio",
            "com.xyauto.broadcast.radio",
            "com.qf.action.radio",
            "com.tw.radio",
            "com.microntek.radio",
            "com.szchoiceway.radio",
            "com.syu.radio",
        )

        private val INVALID_METADATA_TOKENS = setOf(
            "not provided",
            "unknown",
            "<unknown>",
            "unknown artist",
            "unknown album",
            "unknown title",
            "null",
            "none",
            "n/a",
            "-",
            "--",
        )

        private val FM_FREQUENCY_REGEX = Regex(
            pattern = """\b(8[7-9]|9\d|10[0-8])(?:[.,](\d{1,2}))?\s*(?:mhz)?\b""",
            option = RegexOption.IGNORE_CASE,
        )

        fun sanitizeMediaText(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.lowercase(Locale.ROOT) in INVALID_METADATA_TOKENS) "" else trimmed
        }

        fun isRadioPackage(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val lower = packageName.lowercase(Locale.ROOT)
            return lower.contains("radio") || lower.contains(".fm") || lower.startsWith("fm.")
        }

        fun normalizeFmNumericFrequency(value: Double): String {
            if (!value.isFinite() || value <= 0.0) return ""
            val mhz = when {
                value in 87.0..108.5 -> value
                value in 870.0..1085.0 -> value / 10.0
                value in 8700.0..10850.0 -> value / 100.0
                value in 87000.0..108500.0 -> value / 1000.0
                else -> return ""
            }
            if (mhz < 87.0 || mhz > 108.5) return ""
            return String.format(Locale.US, "%.2f", mhz)
        }

        fun parseFmFrequencyMhz(raw: String?): String {
            val text = sanitizeMediaText(raw)
            if (text.isBlank()) return ""
            val directNum = text.replace(',', '.').toDoubleOrNull()
            if (directNum != null) {
                val normalized = normalizeFmNumericFrequency(directNum)
                if (normalized.isNotBlank()) return normalized
            }
            val match = FM_FREQUENCY_REGEX.find(text) ?: return ""
            val whole = match.groupValues[1]
            val decimals = match.groupValues[2]
            val combined = if (decimals.isNotEmpty()) "$whole.$decimals" else "$whole.0"
            return normalizeFmNumericFrequency(combined.toDoubleOrNull() ?: 0.0)
        }

        private fun stripFrequencyTokens(text: String): String {
            return text
                .replace(Regex("""\bFM\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(?:8[7-9]|9\d|10[0-8])(?:[.,]\d{1,2})?\s*(?:mhz)?\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*[•|\-]+\s*$"""), "")
                .replace(Regex("""^\s*[•|\-]+\s*"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        }

        fun buildFmSubtitle(frequencyMhz: String, rdsText: String): String {
            val cleanRds = sanitizeMediaText(rdsText)
            val freqPart = if (frequencyMhz.isNotBlank()) "FM $frequencyMhz MHz" else ""
            return when {
                freqPart.isNotBlank() && cleanRds.isNotBlank() -> "$freqPart • $cleanRds"
                freqPart.isNotBlank() -> freqPart
                cleanRds.isNotBlank() -> cleanRds
                else -> "Radio FM en direct"
            }
        }

        fun parseFmBroadcastIntent(
            intent: Intent,
            nowMs: Long = SystemClock.elapsedRealtime(),
        ): FmRadioSnapshot? {
            val action = intent.action.orEmpty()
            val extras = intent.extras ?: return null

            val isExplicitRadioAction = action != "com.android.music.metachanged"

            val freqKeys = listOf("frequency", "freq", "fm_freq", "frequency_mhz", "radio_freq", "fm_frequency", "band_freq")
            var extractedFreq = ""
            for (key in freqKeys) {
                if (!extras.containsKey(key)) continue
                @Suppress("DEPRECATION")
                val raw = extras.get(key)
                extractedFreq = when (raw) {
                    is Double -> normalizeFmNumericFrequency(raw)
                    is Float -> normalizeFmNumericFrequency(raw.toDouble())
                    is Int -> normalizeFmNumericFrequency(raw.toDouble())
                    is Long -> normalizeFmNumericFrequency(raw.toDouble())
                    is String -> parseFmFrequencyMhz(raw)
                    else -> ""
                }
                if (extractedFreq.isNotBlank()) break
            }

            val stationKeys = listOf("station", "station_name", "ps", "rds_ps", "program_service", "radio_name", "channel")
            var station = ""
            for (key in stationKeys) {
                val candidate = sanitizeMediaText(extras.getString(key))
                if (candidate.isNotBlank()) {
                    station = candidate
                    break
                }
            }

            val rdsKeys = listOf("rds", "rt", "rds_text", "radio_text", "track", "title", "artist")
            var rds = ""
            for (key in rdsKeys) {
                val candidate = sanitizeMediaText(extras.getString(key))
                if (candidate.isNotBlank() && !candidate.equals(station, ignoreCase = true)) {
                    rds = candidate
                    break
                }
            }

            val albumFallback = sanitizeMediaText(extras.getString("album"))
            if (extractedFreq.isBlank()) {
                extractedFreq = parseFmFrequencyMhz(station)
                    .ifBlank { parseFmFrequencyMhz(rds) }
                    .ifBlank { parseFmFrequencyMhz(albumFallback) }
            }
            if (station.isBlank()) {
                station = stripFrequencyTokens(albumFallback)
                    .ifBlank {
                        val candidateFromTrack = stripFrequencyTokens(sanitizeMediaText(extras.getString("track")))
                        if (candidateFromTrack.isNotBlank() && candidateFromTrack != rds) candidateFromTrack else ""
                    }
            }
            if (station.isNotBlank() && parseFmFrequencyMhz(station) == extractedFreq && stripFrequencyTokens(station).isNotBlank()) {
                station = stripFrequencyTokens(station)
            }

            if (!isExplicitRadioAction) {
                val hasRadioHint = freqKeys.any { extras.containsKey(it) } ||
                    stationKeys.any { extras.containsKey(it) } ||
                    extras.containsKey("rds") ||
                    extras.containsKey("rds_text") ||
                    extras.containsKey("radio_text") ||
                    extractedFreq.isNotBlank()
                if (!hasRadioHint) return null
            }

            val isPlaying = when {
                extras.containsKey("playing") -> extras.getBoolean("playing", true)
                extras.containsKey("is_playing") -> extras.getBoolean("is_playing", true)
                extras.containsKey("power") -> extras.getBoolean("power", true)
                extras.containsKey("radio_on") -> extras.getBoolean("radio_on", true)
                else -> true
            }

            if (!isPlaying) {
                return FmRadioSnapshot(isActive = false, updatedAtMs = nowMs)
            }
            if (extractedFreq.isBlank() && station.isBlank() && rds.isBlank()) {
                return null
            }

            val pkg = sanitizeMediaText(extras.getString("package")).ifBlank { action }
            return FmRadioSnapshot(
                isActive = true,
                frequencyMhz = extractedFreq,
                stationName = station,
                rdsText = if (rds.equals(station, ignoreCase = true)) "" else rds,
                packageName = pkg,
                updatedAtMs = nowMs,
            )
        }

        fun parseFmNotification(
            packageName: String,
            title: String?,
            text: String?,
            subText: String?,
            nowMs: Long = SystemClock.elapsedRealtime(),
        ): FmRadioSnapshot? {
            val cleanTitle = sanitizeMediaText(title)
            val cleanText = sanitizeMediaText(text)
            val cleanSub = sanitizeMediaText(subText)
            val combined = "$cleanTitle $cleanText $cleanSub"

            val looksLikeRadio = isRadioPackage(packageName) ||
                combined.contains("MHz", ignoreCase = true) ||
                combined.contains("Radio FM", ignoreCase = true)
            if (!looksLikeRadio) return null

            val freq = parseFmFrequencyMhz(cleanTitle)
                .ifBlank { parseFmFrequencyMhz(cleanText) }
                .ifBlank { parseFmFrequencyMhz(cleanSub) }

            val stationCandidate = stripFrequencyTokens(cleanTitle)
                .ifBlank { stripFrequencyTokens(cleanSub) }

            val rdsCandidate = when {
                cleanText.isNotBlank() &&
                    !cleanText.equals(stationCandidate, ignoreCase = true) &&
                    parseFmFrequencyMhz(cleanText) != freq -> cleanText
                cleanSub.isNotBlank() &&
                    !cleanSub.equals(stationCandidate, ignoreCase = true) &&
                    parseFmFrequencyMhz(cleanSub) != freq -> cleanSub
                else -> ""
            }

            if (freq.isBlank() && stationCandidate.isBlank() && rdsCandidate.isBlank()) return null

            return FmRadioSnapshot(
                isActive = true,
                frequencyMhz = freq,
                stationName = stationCandidate,
                rdsText = rdsCandidate,
                packageName = packageName,
                updatedAtMs = nowMs,
            )
        }

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
