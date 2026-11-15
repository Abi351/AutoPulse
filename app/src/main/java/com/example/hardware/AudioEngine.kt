package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.data.db.AudioTrackEntity
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
import kotlinx.coroutines.withContext

enum class AudioSourceMode {
    LOCAL,
    EXTERNAL,
}

enum class EqPreset(
    val index: Int,
    val label: String,
    val gainsMb: List<Int>,
) {
    FLAT(0, "Plat", listOf(0, 0, 0, 0, 0)),
    ROCK(1, "Rock", listOf(450, 200, -150, 250, 450)),
    JAZZ(2, "Jazz", listOf(300, 150, -100, 250, 350)),
    POP(3, "Pop", listOf(-150, 250, 450, 200, -100)),
    ELECTRO(4, "Électro / Basses", listOf(600, 400, 0, 200, 450)),
    CUSTOM(5, "Personnalisé", listOf(0, 0, 0, 0, 0));

    companion object {
        fun fromIndex(index: Int): EqPreset {
            return entries.firstOrNull { it.index == index } ?: FLAT
        }
    }
}

data class BluetoothAudioDeviceInfo(
    val name: String,
    val address: String,
    val isConnectedA2dp: Boolean,
    val batteryPercent: Int? = null,
)

data class EqBandState(
    val index: Short,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minMb: Short,
    val maxMb: Short,
)

private val DEFAULT_BAND_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)

private fun buildDefaultEqBands(gainsMb: List<Int> = listOf(0, 0, 0, 0, 0)): List<EqBandState> {
    return DEFAULT_BAND_FREQS_HZ.mapIndexed { idx, freqHz ->
        EqBandState(
            index = idx.toShort(),
            centerFreqHz = freqHz,
            levelMb = gainsMb.getOrElse(idx) { 0 }.coerceIn(-1200, 1200).toShort(),
            minMb = (-1200).toShort(),
            maxMb = 1200.toShort(),
        )
    }
}

/**
 * Contrôle automatique du volume selon la vitesse (SDVC) :
 * +2 crans au-dessus de 90 km/h, retour au niveau initial en dessous de 55 km/h,
 * avec délai de stabilisation de 5 secondes pour éviter les variations brusques.
 */
class SdvcController(
    private val stabilizationDelayMs: Long = 5_000L,
) {
    var isBoostActive: Boolean = false
        private set
    private var baseVolume: Int? = null
    private var pendingBoostState: Boolean? = null
    private var pendingStartMs: Long = 0L

    fun evaluate(
        enabled: Boolean,
        speedKmh: Int,
        currentVolume: Int,
        maxVolume: Int,
        nowMs: Long,
    ): Int? {
        if (!enabled) {
            pendingBoostState = null
            if (isBoostActive) {
                val restore = (baseVolume ?: (currentVolume - 2)).coerceIn(0, maxVolume)
                isBoostActive = false
                baseVolume = null
                return if (restore != currentVolume) restore else null
            }
            return null
        }

        if (!isBoostActive) {
            if (speedKmh > 90) {
                if (pendingBoostState != true) {
                    pendingBoostState = true
                    pendingStartMs = nowMs
                    return null
                }
                if (nowMs - pendingStartMs >= stabilizationDelayMs) {
                    pendingBoostState = null
                    baseVolume = currentVolume
                    isBoostActive = true
                    val boosted = (currentVolume + 2).coerceAtMost(maxVolume)
                    return if (boosted != currentVolume) boosted else null
                }
            } else {
                pendingBoostState = null
            }
        } else {
            if (speedKmh < 55) {
                if (pendingBoostState != false) {
                    pendingBoostState = false
                    pendingStartMs = nowMs
                    return null
                }
                if (nowMs - pendingStartMs >= stabilizationDelayMs) {
                    pendingBoostState = null
                    val restore = (baseVolume ?: (currentVolume - 2)).coerceIn(0, maxVolume)
                    isBoostActive = false
                    baseVolume = null
                    return if (restore != currentVolume) restore else null
                }
            } else {
                pendingBoostState = null
            }
        }
        return null
    }
}

data class MediaPlayerState(
    val sourceMode: AudioSourceMode = AudioSourceMode.LOCAL,
    val currentTrack: AudioTrackEntity? = null,
    val currentAlbumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatOne: Boolean = false,
    val eqEnabled: Boolean = true,
    val eqPresetIndex: Int = 0,
    val eqPresetName: String = EqPreset.FLAT.label,
    val eqBands: List<EqBandState> = buildDefaultEqBands(),
    val isScanningFolder: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val connectedBtDevice: BluetoothAudioDeviceInfo? = null,
    val pairedBtDevices: List<BluetoothAudioDeviceInfo> = emptyList(),
    val btAudioActive: Boolean = false,
    val externalMedia: ExternalMediaInfo = ExternalMediaInfo(),
    val hasNotificationListenerAccess: Boolean = false,
    val mediaVolume: Int = 8,
    val maxVolume: Int = 15,
    val isMuted: Boolean = false,
)

class AudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val universalMedia = UniversalMediaManager(context)

    private val _state = MutableStateFlow(MediaPlayerState())
    val state: StateFlow<MediaPlayerState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var progressJob: Job? = null
    private var duckingJob: Job? = null
    private var playlist: List<AudioTrackEntity> = emptyList()
    private var a2dpProxy: BluetoothA2dp? = null
    private var focusRequest: AudioFocusRequest? = null

    private var currentEqEnabled: Boolean = true
    private var currentEqPresetIndex: Int = EqPreset.FLAT.index
    private var currentEqGainsMb: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0)
    private var savedCustomBandsMb: List<Int> = listOf(0, 0, 0, 0, 0)

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refreshBluetoothDevices()
            }
        }
    }

    private val a2dpServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = proxy as? BluetoothA2dp
                refreshBluetoothDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
                refreshBluetoothDevices()
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_VOLUME_CHANGED) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                    refreshVolume()
                }
            }
        }
    }

    private val volumeContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshVolume()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(btReceiver, filter)
        }

        val volFilter = IntentFilter(ACTION_VOLUME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(volumeReceiver, volFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(volumeReceiver, volFilter)
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeContentObserver,
            )
        }

        runCatching {
            bluetoothAdapter?.getProfileProxy(context, a2dpServiceListener, BluetoothProfile.A2DP)
        }

        scope.launch {
            universalMedia.hasNotificationAccess.collect { granted ->
                _state.update { it.copy(hasNotificationListenerAccess = granted) }
            }
        }
        scope.launch {
            universalMedia.externalMedia.collect { ext ->
                _state.update { current ->
                    val shouldSwitchToExternal =
                        ext.hasActiveSession && ext.isPlaying && !current.isPlaying
                    current.copy(
                        externalMedia = ext,
                        sourceMode = if (shouldSwitchToExternal) AudioSourceMode.EXTERNAL else current.sourceMode,
                    )
                }
            }
        }

        refreshVolume()
        refreshBluetoothDevices()
    }

    fun refreshExternalMediaAccess() {
        universalMedia.refreshAccessAndSessions()
    }

    fun openNotificationListenerSettings() {
        universalMedia.openNotificationListenerSettings()
    }

    fun setPlaylist(tracks: List<AudioTrackEntity>) {
        playlist = tracks
        val cur = _state.value.currentTrack
        if (cur == null && tracks.isNotEmpty()) {
            val first = tracks.first()
            _state.update { it.copy(currentTrack = first, durationMs = first.durationMs) }
            loadTrackArtAsync(first)
        } else if (cur != null) {
            val updated = tracks.firstOrNull { it.id == cur.id || it.uriString == cur.uriString }
            if (updated != null && updated != cur) {
                _state.update { it.copy(currentTrack = updated) }
                if (updated.coverPath != cur.coverPath || _state.value.currentAlbumArt == null) {
                    loadTrackArtAsync(updated)
                }
            }
        }
    }

    fun setSourceMode(mode: AudioSourceMode) {
        if (mode == AudioSourceMode.EXTERNAL && _state.value.isPlaying) {
            pauseLocal()
        } else if (mode == AudioSourceMode.LOCAL && _state.value.externalMedia.isPlaying) {
            universalMedia.pause()
        }
        _state.update { it.copy(sourceMode = mode) }
        refreshBluetoothDevices()
        universalMedia.refreshSessions()
    }

    fun playTrack(track: AudioTrackEntity) {
        _state.update {
            it.copy(
                sourceMode = AudioSourceMode.LOCAL,
                currentTrack = track,
                currentAlbumArt = null,
                positionMs = 0L,
                durationMs = track.durationMs,
            )
        }
        requestAudioFocus()
        releasePlayerOnly()
        loadTrackArtAsync(track)

        val uri = Uri.parse(track.uriString)
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(context, uri)
            player.setOnPreparedListener { mp ->
                mp.start()
                setupEqualizer(mp.audioSessionId)
                _state.update {
                    it.copy(
                        currentTrack = track,
                        isPlaying = true,
                        durationMs = mp.duration.toLong().coerceAtLeast(track.durationMs),
                        positionMs = 0L,
                    )
                }
                startProgressLoop()
            }
            player.setOnCompletionListener {
                if (_state.value.repeatOne) {
                    playTrack(track)
                } else {
                    nextTrack()
                }
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (_: Exception) {
            player.release()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun loadTrackArtAsync(track: AudioTrackEntity) {
        val uri = Uri.parse(track.uriString)
        scope.launch(Dispatchers.IO) {
            val art = MetadataFetcher.loadCoverBitmapRgb565(track.coverPath) ?: extractAlbumArt(uri)
            withContext(Dispatchers.Main) {
                if (_state.value.currentTrack?.uriString == track.uriString) {
                    _state.update { it.copy(currentAlbumArt = art) }
                }
            }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.sourceMode == AudioSourceMode.EXTERNAL ||
            (current.currentTrack == null && playlist.isEmpty() && current.externalMedia.hasActiveSession)
        ) {
            if (!universalMedia.togglePlayPause()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                _state.update { it.copy(btAudioActive = !it.btAudioActive) }
            }
            return
        }

        val mp = mediaPlayer
        if (mp != null) {
            if (mp.isPlaying) {
                pauseLocal()
            } else {
                requestAudioFocus()
                mp.start()
                _state.update { it.copy(isPlaying = true) }
                startProgressLoop()
            }
        } else {
            val target = current.currentTrack ?: playlist.firstOrNull()
            if (target != null) playTrack(target)
        }
    }

    fun playActive() {
        val current = _state.value
        if (current.sourceMode == AudioSourceMode.EXTERNAL ||
            (playlist.isEmpty() && current.externalMedia.hasActiveSession)
        ) {
            if (!universalMedia.play()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                _state.update { it.copy(btAudioActive = true) }
            }
            return
        }
        val mp = mediaPlayer
        if (mp != null) {
            if (!mp.isPlaying) {
                requestAudioFocus()
                mp.start()
                _state.update { it.copy(isPlaying = true) }
                startProgressLoop()
            }
        } else {
            val target = current.currentTrack ?: playlist.firstOrNull()
            if (target != null) {
                playTrack(target)
            } else if (!universalMedia.play()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }
    }

    fun pauseActive() {
        val current = _state.value
        if (current.sourceMode == AudioSourceMode.EXTERNAL) {
            if (!universalMedia.pause()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                _state.update { it.copy(btAudioActive = false) }
            }
            return
        }
        pauseLocal()
    }

    fun handleSteeringWheelKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playActive()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pauseActive()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                nextTrack()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                previousTrack()
                true
            }
            else -> false
        }
    }

    private fun pauseLocal() {
        runCatching { mediaPlayer?.pause() }
        progressJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    fun nextTrack() {
        val current = _state.value
        if (current.sourceMode == AudioSourceMode.EXTERNAL ||
            (playlist.isEmpty() && current.externalMedia.hasActiveSession)
        ) {
            if (!universalMedia.skipToNext()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            return
        }
        if (playlist.isEmpty()) return
        val cur = current.currentTrack
        val next = if (current.shuffleEnabled && playlist.size > 1) {
            playlist.filter { it.id != cur?.id }.random()
        } else {
            val idx = playlist.indexOfFirst { it.id == cur?.id }
            playlist[(idx + 1) % playlist.size]
        }
        playTrack(next)
    }

    fun previousTrack() {
        val current = _state.value
        if (current.sourceMode == AudioSourceMode.EXTERNAL ||
            (playlist.isEmpty() && current.externalMedia.hasActiveSession)
        ) {
            if (!universalMedia.skipToPrevious()) {
                dispatchAvrcpMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            return
        }
        if (playlist.isEmpty()) return
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3500) {
            mp.seekTo(0)
            _state.update { it.copy(positionMs = 0L) }
            return
        }
        val cur = current.currentTrack
        val idx = playlist.indexOfFirst { it.id == cur?.id }
        val prevIdx = if (idx <= 0) playlist.lastIndex else idx - 1
        playTrack(playlist[prevIdx])
    }

    fun seekTo(positionMs: Long) {
        if (_state.value.sourceMode == AudioSourceMode.EXTERNAL) {
            universalMedia.seekTo(positionMs)
            return
        }
        val mp = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(1L))
        runCatching { mp.seekTo(clamped.toInt()) }
        _state.update { it.copy(positionMs = clamped) }
    }

    fun toggleShuffle() {
        if (_state.value.sourceMode == AudioSourceMode.EXTERNAL) {
            universalMedia.toggleShuffle()
            return
        }
        _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun toggleRepeatOne() {
        if (_state.value.sourceMode == AudioSourceMode.EXTERNAL) {
            universalMedia.toggleRepeat()
            return
        }
        _state.update { it.copy(repeatOne = !it.repeatOne) }
    }

    // --- Égaliseur 5 bandes (android.media.audiofx.Equalizer) ---
    fun syncEqSettings(enabled: Boolean, presetIndex: Int, customBandsMb: List<Int>) {
        savedCustomBandsMb = List(5) { idx -> customBandsMb.getOrElse(idx) { 0 }.coerceIn(-1200, 1200) }
        currentEqEnabled = enabled
        val preset = EqPreset.fromIndex(presetIndex)
        currentEqPresetIndex = preset.index
        val targetGains = if (preset == EqPreset.CUSTOM) savedCustomBandsMb else preset.gainsMb
        currentEqGainsMb = MutableList(5) { idx -> targetGains.getOrElse(idx) { 0 }.coerceIn(-1200, 1200) }

        applyEqToHardware()
        publishEqState()
    }

    fun setEqEnabled(enabled: Boolean) {
        currentEqEnabled = enabled
        runCatching { equalizer?.enabled = enabled }
        publishEqState()
    }

    fun applyEqPreset(presetIndex: Int): EqPreset {
        val preset = EqPreset.fromIndex(presetIndex)
        currentEqPresetIndex = preset.index
        val gains = if (preset == EqPreset.CUSTOM) savedCustomBandsMb else preset.gainsMb
        currentEqGainsMb = MutableList(5) { idx -> gains.getOrElse(idx) { 0 }.coerceIn(-1200, 1200) }
        applyEqToHardware()
        publishEqState()
        return preset
    }

    fun setEqBandLevel(bandIndex: Short, levelMb: Short): List<Int> {
        val idx = bandIndex.toInt().coerceIn(0, 4)
        val clamped = levelMb.toInt().coerceIn(-1200, 1200)
        currentEqGainsMb[idx] = clamped
        savedCustomBandsMb = currentEqGainsMb.toList()
        currentEqPresetIndex = EqPreset.CUSTOM.index
        applyEqToHardware()
        publishEqState()
        return savedCustomBandsMb
    }

    private fun setupEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        runCatching {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            applyEqToHardware()
            publishEqState()
        }.onFailure {
            equalizer = null
        }
    }

    private fun applyEqToHardware() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = currentEqEnabled
            val count = eq.numberOfBands.toInt().coerceAtMost(5)
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            for (i in 0 until count) {
                val target = currentEqGainsMb.getOrElse(i) { 0 }.coerceIn(minMb, maxMb).toShort()
                eq.setBandLevel(i.toShort(), target)
            }
        }
    }

    private fun publishEqState() {
        val eq = equalizer
        val presetLabel = EqPreset.fromIndex(currentEqPresetIndex).label
        val bands = if (eq != null) {
            runCatching {
                val range = eq.bandLevelRange
                val minUi = maxOf(range[0].toInt(), -1200).toShort()
                val maxUi = minOf(range[1].toInt(), 1200).toShort()
                val count = eq.numberOfBands.toInt().coerceAtMost(5)
                (0 until count).map { idx ->
                    val sIdx = idx.toShort()
                    val level = currentEqGainsMb.getOrElse(idx) { eq.getBandLevel(sIdx).toInt() }
                        .coerceIn(minUi.toInt(), maxUi.toInt())
                        .toShort()
                    EqBandState(
                        index = sIdx,
                        centerFreqHz = (eq.getCenterFreq(sIdx) / 1000).coerceAtLeast(20),
                        levelMb = level,
                        minMb = minUi,
                        maxMb = maxUi,
                    )
                }
            }.getOrElse { buildDefaultEqBands(currentEqGainsMb) }
        } else {
            buildDefaultEqBands(currentEqGainsMb)
        }

        _state.update {
            it.copy(
                eqEnabled = currentEqEnabled,
                eqPresetIndex = currentEqPresetIndex,
                eqPresetName = presetLabel,
                eqBands = bands,
            )
        }
    }

    fun setVolume(volume: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clamped = volume.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        _state.update { it.copy(mediaVolume = clamped, maxVolume = max, isMuted = clamped == 0) }
    }

    fun toggleMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            setVolume(0)
        } else {
            setVolume((_state.value.maxVolume * 0.5f).toInt().coerceAtLeast(4))
        }
    }

    fun refreshVolume() {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        _state.update { it.copy(mediaVolume = cur, maxVolume = max, isMuted = cur == 0) }
    }

    fun triggerOverspeedAudioDucking() {
        if (duckingJob?.isActive == true) return
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (originalVolume <= 1) return

        val duckedVolume = kotlin.math.round(originalVolume * 0.70f)
            .toInt()
            .coerceIn(1, originalVolume - 1)

        duckingJob = scope.launch {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, duckedVolume, 0)
                refreshVolume()
                delay(2_000L)
            } finally {
                runCatching {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    refreshVolume()
                }
                duckingJob = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothDevices() {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        val btEnabled = bluetoothAdapter?.isEnabled == true
        if (!hasPerm || !btEnabled) {
            _state.update {
                it.copy(
                    hasBluetoothPermission = hasPerm,
                    bluetoothEnabled = btEnabled,
                    connectedBtDevice = null,
                    pairedBtDevices = emptyList(),
                    btAudioActive = audioManager.isBluetoothA2dpOn,
                )
            }
            return
        }

        val connectedA2dp = runCatching { a2dpProxy?.connectedDevices ?: emptyList() }.getOrDefault(emptyList())
        val connectedAddresses = connectedA2dp.map { it.address }.toSet()

        val paired = runCatching {
            bluetoothAdapter?.bondedDevices?.map { dev ->
                val isConn = dev.address in connectedAddresses
                BluetoothAudioDeviceInfo(
                    name = dev.name ?: "Appareil Bluetooth",
                    address = dev.address,
                    isConnectedA2dp = isConn,
                    batteryPercent = readDeviceBattery(dev),
                )
            }.orEmpty()
        }.getOrDefault(emptyList())

        val activeDevice = paired.firstOrNull { it.isConnectedA2dp }
        _state.update {
            it.copy(
                hasBluetoothPermission = true,
                bluetoothEnabled = true,
                connectedBtDevice = activeDevice,
                pairedBtDevices = paired,
                btAudioActive = audioManager.isMusicActive && activeDevice != null,
            )
        }
    }

    private fun readDeviceBattery(device: BluetoothDevice): Int? {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            if (level != null && level in 0..100) level else null
        }.getOrNull()
    }

    private fun dispatchAvrcpMediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    suspend fun extractTracksFromUris(uris: List<Uri>): List<AudioTrackEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AudioTrackEntity>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            extractSingleTrack(uri, "Fichiers sélectionnés")?.let { result.add(it) }
        }
        result
    }

    suspend fun scanFolderTree(treeUri: Uri): List<AudioTrackEntity> = withContext(Dispatchers.IO) {
        _state.update { it.copy(isScanningFolder = true) }
        val result = mutableListOf<AudioTrackEntity>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDirectoryRecursive(treeUri, rootDocId, "Dossier Audio", result, depth = 0)
        } finally {
            _state.update { it.copy(isScanningFolder = false) }
        }
        result
    }

    private fun scanDirectoryRecursive(
        treeUri: Uri,
        parentDocId: String,
        folderLabel: String,
        out: MutableList<AudioTrackEntity>,
        depth: Int,
    ) {
        if (depth > 4 || out.size > 300) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: "Audio"
                val mime = cursor.getString(mimeIdx) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanDirectoryRecursive(treeUri, docId, name, out, depth + 1)
                } else if (mime.startsWith("audio/") || name.endsWith(".mp3", true) ||
                    name.endsWith(".flac", true) || name.endsWith(".m4a", true) ||
                    name.endsWith(".ogg", true) || name.endsWith(".wav", true)
                ) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    extractSingleTrack(fileUri, folderLabel, name)?.let { out.add(it) }
                }
            }
        }
    }

    private fun extractSingleTrack(
        uri: Uri,
        folderName: String,
        fallbackName: String? = null,
    ): AudioTrackEntity? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val rawFileName = fallbackName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Piste audio"
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: MetadataFetcher.cleanTrackName(rawFileName)
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artiste inconnu"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: folderName
            val genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() } ?: "Inconnu"
            val rawYear = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = MetadataFetcher.extractYear(rawYear)
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val embeddedBytes = runCatching { mmr.embeddedPicture }.getOrNull()
            val coverPath = embeddedBytes?.let { bytes ->
                MetadataFetcher.compressAndSaveCoverRgb565(
                    context = context,
                    rawBytes = bytes,
                    cacheKey = uri.toString(),
                )
            }

            AudioTrackEntity(
                uriString = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                year = year,
                coverPath = coverPath,
                durationMs = durationMs,
                folderName = folderName,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun extractAlbumArt(uri: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val artBytes = mmr.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, bounds)
            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 256) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val pos = runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
                _state.update { it.copy(positionMs = pos) }
                delay(500L)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun releasePlayerOnly() {
        progressJob?.cancel()
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    fun release() {
        duckingJob?.cancel()
        releasePlayerOnly()
        universalMedia.release()
        runCatching { context.unregisterReceiver(btReceiver) }
        runCatching { context.unregisterReceiver(volumeReceiver) }
        runCatching { context.contentResolver.unregisterContentObserver(volumeContentObserver) }
        runCatching {
            a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
    }

    companion object {
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        fun formatDuration(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return "%d:%02d".format(min, sec)
        }
    }
}
