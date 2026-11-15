package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BREAK_REMINDER_INTERVAL_SECONDS
import com.example.data.HeadUnitSettings
import com.example.data.MAX_DOCK_APPS
import com.example.data.PreferencesRepository
import com.example.data.SPEED_LIMIT_AUTO
import com.example.data.TripStats
import com.example.data.db.AudioTrackEntity
import com.example.data.db.AutoPulseDatabase
import com.example.hardware.AudioEngine
import com.example.hardware.AudioSourceMode
import com.example.hardware.AutoSpeedLimitState
import com.example.hardware.FloatingPillService
import com.example.hardware.InstalledAppInfo
import com.example.hardware.InstalledAppsManager
import com.example.hardware.LocationEngine
import com.example.hardware.MediaPlayerState
import com.example.hardware.MetadataFetcher
import com.example.hardware.ScreenDimmerService
import com.example.hardware.SdvcController
import com.example.hardware.SpeedGpsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoverSyncUiState(
    val isRunning: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val updatedCount: Int = 0,
    val statusMessage: String? = null,
    val isOfflineError: Boolean = false,
)

class AutoPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AutoPulseDatabase.getInstance(application)
    private val audioTrackDao = db.audioTrackDao()
    private val prefsRepo = PreferencesRepository(application)

    val audioEngine = AudioEngine(application)
    val appsManager = InstalledAppsManager(application)
    val locationEngine = LocationEngine(application)
    private val sdvcController = SdvcController()

    val mediaState: StateFlow<MediaPlayerState> = audioEngine.state
    val speedState: StateFlow<SpeedGpsState> = locationEngine.state
    val autoSpeedLimitState: StateFlow<AutoSpeedLimitState> = locationEngine.autoSpeedLimitState
    val installedApps: StateFlow<List<InstalledAppInfo>> = appsManager.apps

    val settings: StateFlow<HeadUnitSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeadUnitSettings())

    val dockApps: StateFlow<List<InstalledAppInfo>> = combine(installedApps, settings) { allApps, prefs ->
        val appMap = allApps.associateBy { it.packageName }
        prefs.dockPackageNames.mapNotNull { pkg -> appMap[pkg] }.take(MAX_DOCK_APPS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<AudioTrackEntity>> = audioTrackDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats.asStateFlow()

    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _coverSyncState = MutableStateFlow(CoverSyncUiState())
    val coverSyncState: StateFlow<CoverSyncUiState> = _coverSyncState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _showBreakReminder = MutableStateFlow(false)
    val showBreakReminder: StateFlow<Boolean> = _showBreakReminder.asStateFlow()

    private var wakeResumeJob: Job? = null
    private var eqBandSaveJob: Job? = null
    private var coverSyncJob: Job? = null
    private var tripLoaded = false
    private var nextBreakReminderAtSeconds = BREAK_REMINDER_INTERVAL_SECONDS

    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                val appCtx = getApplication<Application>()
                ScreenDimmerService.syncFromSettings(appCtx, settings.value)
                FloatingPillService.sync(appCtx, settings.value.floatingPillEnabled)
                if (!settings.value.autoResumeMusicEnabled) return
                wakeResumeJob?.cancel()
                wakeResumeJob = viewModelScope.launch {
                    delay(3_000L)
                    audioEngine.playActive()
                }
            }
        }
    }

    init {
        refreshInstalledApps()
        refreshLauncherStatus()
        refreshOverlayPermission()

        tracks.onEach { list ->
            audioEngine.setPlaylist(list)
        }.launchIn(viewModelScope)

        prefsRepo.settingsFlow
            .map { Triple(it.eqEnabled, it.eqPresetIndex, it.eqCustomBandsMb) }
            .distinctUntilChanged()
            .onEach { (enabled, presetIdx, customBands) ->
                audioEngine.syncEqSettings(enabled, presetIdx, customBands)
            }
            .launchIn(viewModelScope)

        prefsRepo.settingsFlow
            .map { it.speedLimitKmh == SPEED_LIMIT_AUTO }
            .distinctUntilChanged()
            .onEach { isAuto ->
                locationEngine.setAutoSpeedLimitActive(isAuto)
            }
            .launchIn(viewModelScope)

        prefsRepo.settingsFlow
            .map { it.autoSpeedDetectionSeconds }
            .distinctUntilChanged()
            .onEach { seconds ->
                locationEngine.setAutoDetectionSeconds(seconds)
            }
            .launchIn(viewModelScope)

        prefsRepo.settingsFlow
            .map { it.speedCalibrationOffsetKmh }
            .distinctUntilChanged()
            .onEach { offset ->
                locationEngine.setSpeedCalibrationOffset(offset)
            }
            .launchIn(viewModelScope)

        locationEngine.tripFixes
            .onEach { fix ->
                _tripStats.update { cur ->
                    cur.copy(
                        distanceMeters = cur.distanceMeters + fix.distanceDeltaMeters,
                        maxSpeedKmh = maxOf(cur.maxSpeedKmh, fix.speedKmh),
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val savedTrip = prefsRepo.tripStatsFlow.first()
            _tripStats.value = savedTrip
            nextBreakReminderAtSeconds =
                ((savedTrip.drivingSeconds / BREAK_REMINDER_INTERVAL_SECONDS) + 1L) * BREAK_REMINDER_INTERVAL_SECONDS
            tripLoaded = true

            var secondsSincePersist = 0
            while (isActive) {
                delay(1_000L)
                _tripStats.update { cur -> cur.copy(drivingSeconds = cur.drivingSeconds + 1L) }

                val currentTrip = _tripStats.value
                val currentSettings = settings.value
                if (!currentSettings.breakReminderEnabled) {
                    if (_showBreakReminder.value) {
                        _showBreakReminder.value = false
                    }
                } else if (currentTrip.drivingSeconds >= nextBreakReminderAtSeconds) {
                    _showBreakReminder.value = true
                    nextBreakReminderAtSeconds += BREAK_REMINDER_INTERVAL_SECONDS
                }

                val targetVol = sdvcController.evaluate(
                    enabled = currentSettings.sdvcEnabled,
                    speedKmh = speedState.value.speedKmh,
                    currentVolume = mediaState.value.mediaVolume,
                    maxVolume = mediaState.value.maxVolume,
                    nowMs = SystemClock.elapsedRealtime(),
                )
                if (targetVol != null) {
                    audioEngine.setVolume(targetVol)
                }

                secondsSincePersist++
                if (secondsSincePersist >= 10) {
                    secondsSincePersist = 0
                    prefsRepo.saveTripStats(_tripStats.value)
                }
            }
        }

        val wakeFilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        val appCtx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(screenWakeReceiver, wakeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(screenWakeReceiver, wakeFilter)
        }
    }

    fun onPermissionsUpdated() {
        audioEngine.refreshBluetoothDevices()
        audioEngine.refreshExternalMediaAccess()
        locationEngine.startUpdates()
        refreshLauncherStatus()
        refreshOverlayPermission()
    }

    fun onResumeScreen() {
        refreshLauncherStatus()
        refreshOverlayPermission()
        audioEngine.refreshVolume()
        audioEngine.refreshExternalMediaAccess()
        locationEngine.startUpdates()
        val appCtx = getApplication<Application>()
        ScreenDimmerService.syncFromSettings(appCtx, settings.value)
        FloatingPillService.sync(appCtx, settings.value.floatingPillEnabled)
    }

    fun onPauseScreen() {
        locationEngine.stopUpdates()
        if (tripLoaded) {
            viewModelScope.launch {
                prefsRepo.saveTripStats(_tripStats.value)
            }
        }
    }

    fun resetTrip() {
        locationEngine.resetTripAnchor()
        _tripStats.value = TripStats()
        _showBreakReminder.value = false
        nextBreakReminderAtSeconds = BREAK_REMINDER_INTERVAL_SECONDS
        viewModelScope.launch {
            prefsRepo.resetTripStats()
            postFeedback("Nouveau trajet démarré (compteurs remis à zéro)")
        }
    }

    fun dismissBreakReminder() {
        _showBreakReminder.value = false
    }

    fun refreshOverlayPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasOverlayPermission.value = ScreenDimmerService.isOverlayPermissionGranted(getApplication())
        }
    }

    fun openOverlayPermissionSettings() {
        ScreenDimmerService.openOverlayPermissionSettings(getApplication())
    }

    fun refreshLauncherStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDefaultLauncher.value = appsManager.isDefaultHomeLauncher()
        }
    }

    fun openLauncherSettings() {
        appsManager.openHomeLauncherSettings()
    }

    fun openNotificationListenerSettings() {
        audioEngine.openNotificationListenerSettings()
    }

    fun openSystemDspEqualizer() {
        if (!audioEngine.openSystemDspEqualizer()) {
            postFeedback("Aucun égaliseur DSP système détecté — utilisez les bandes intégrées ci-dessus")
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    private fun postFeedback(msg: String) {
        _feedbackMessage.value = msg
    }

    // --- Commandes au volant (SWC) ---
    fun handleSteeringWheelKey(keyCode: Int): Boolean {
        return audioEngine.handleSteeringWheelKey(keyCode)
    }

    // --- Applications & Dock (max 10 apps) ---
    fun refreshInstalledApps() {
        viewModelScope.launch {
            appsManager.loadInstalledApps()
        }
    }

    fun getCachedAppIcon(packageName: String): ImageBitmap? = appsManager.getCachedIcon(packageName)

    suspend fun loadAppIcon(packageName: String): ImageBitmap? = appsManager.getOrLoadIcon(packageName)

    fun toggleDockApp(packageName: String) {
        val current = settings.value.dockPackageNames.toMutableList()
        if (packageName in current) {
            current.remove(packageName)
            viewModelScope.launch {
                prefsRepo.setDockPackages(current)
                postFeedback("Application retirée du dock")
            }
        } else {
            if (current.size >= MAX_DOCK_APPS) {
                postFeedback("Maximum $MAX_DOCK_APPS applications sur le dock — retirez-en une d'abord")
                return
            }
            current.add(packageName)
            viewModelScope.launch {
                prefsRepo.setDockPackages(current)
                postFeedback("Application ajoutée au dock (${current.size}/$MAX_DOCK_APPS)")
            }
        }
    }

    fun removeFromDock(packageName: String) {
        val current = settings.value.dockPackageNames.filter { it != packageName }
        viewModelScope.launch {
            prefsRepo.setDockPackages(current)
        }
    }

    fun launchApp(packageName: String) {
        if (!appsManager.launchApp(packageName)) {
            postFeedback("Impossible de lancer cette application")
        }
    }

    // --- Audio Local, Bluetooth & MediaSession Externe ---
    fun importAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val extracted = audioEngine.extractTracksFromUris(uris)
            val newOnes = deduplicateTracksAgainstDb(extracted, audioTrackDao.getAllOnce())
            if (newOnes.isNotEmpty()) {
                audioTrackDao.insertAll(newOnes)
                postFeedback("${newOnes.size} piste(s) ajoutée(s) à la bibliothèque locale")
            } else {
                postFeedback("Piste(s) déjà présente(s) dans la bibliothèque")
            }
        }
    }

    fun importAudioFolder(treeUri: Uri) {
        viewModelScope.launch {
            val found = audioEngine.scanFolderTree(treeUri)
            val newOnes = deduplicateTracksAgainstDb(found, audioTrackDao.getAllOnce())
            if (newOnes.isNotEmpty()) {
                audioTrackDao.insertAll(newOnes)
                postFeedback("${newOnes.size} piste(s) importée(s) depuis le dossier")
            } else if (found.isEmpty()) {
                postFeedback("Aucun fichier audio détecté dans ce dossier")
            } else {
                postFeedback("Toutes les pistes de ce dossier sont déjà importées")
            }
        }
    }

    fun deleteTrack(id: Int) {
        viewModelScope.launch { audioTrackDao.deleteById(id) }
    }

    fun setAudioSource(mode: AudioSourceMode) {
        audioEngine.setSourceMode(mode)
        viewModelScope.launch {
            prefsRepo.setActiveAudioSource(mode.name)
        }
    }

    // --- Égaliseur 5 bandes ---
    fun setEqEnabled(enabled: Boolean) {
        audioEngine.setEqEnabled(enabled)
        viewModelScope.launch { prefsRepo.setEqEnabled(enabled) }
    }

    fun applyEqPreset(index: Int) {
        audioEngine.applyEqPreset(index)
        viewModelScope.launch { prefsRepo.setEqPreset(index) }
    }

    fun setEqBandLevel(bandIndex: Short, levelMb: Short) {
        val updatedBands = audioEngine.setEqBandLevel(bandIndex, levelMb)
        eqBandSaveJob?.cancel()
        eqBandSaveJob = viewModelScope.launch {
            delay(180L)
            prefsRepo.setEqCustomBands(updatedBands)
        }
    }

    // --- Téléchargement en ligne des pochettes et métadonnées (iTunes Search) ---
    fun fetchOnlineCoversAndMetadata(forceRedownload: Boolean) {
        if (coverSyncJob?.isActive == true) return
        val appCtx = getApplication<Application>()

        if (!MetadataFetcher.isNetworkAvailable(appCtx)) {
            _coverSyncState.value = CoverSyncUiState(
                isRunning = false,
                statusMessage = "Autoradio hors ligne — connectez-vous en Wi-Fi ou partage de connexion pour récupérer les pochettes.",
                isOfflineError = true,
            )
            return
        }

        coverSyncJob = viewModelScope.launch {
            val allTracks = audioTrackDao.getAllOnce()
            if (allTracks.isEmpty()) {
                _coverSyncState.value = CoverSyncUiState(
                    isRunning = false,
                    statusMessage = "Aucune piste locale importée à enrichir.",
                )
                return@launch
            }

            var updated = 0
            _coverSyncState.value = CoverSyncUiState(
                isRunning = true,
                processedCount = 0,
                totalCount = allTracks.size,
                updatedCount = 0,
                statusMessage = "Analyse en cours...",
            )

            allTracks.forEachIndexed { idx, track ->
                if (!MetadataFetcher.isNetworkAvailable(appCtx)) {
                    _coverSyncState.value = CoverSyncUiState(
                        isRunning = false,
                        processedCount = idx,
                        totalCount = allTracks.size,
                        updatedCount = updated,
                        statusMessage = "Connexion Internet perdue après $idx / ${allTracks.size} morceaux ($updated mis à jour).",
                        isOfflineError = true,
                    )
                    return@launch
                }

                val result = runCatching {
                    MetadataFetcher.fetchOnlineMetadataForTrack(
                        context = appCtx,
                        track = track,
                        forceRedownload = forceRedownload,
                    )
                }.getOrNull()
                if (result != null && result.wasUpdated) {
                    audioTrackDao.updateTrack(result.track)
                    updated++
                }
                _coverSyncState.value = CoverSyncUiState(
                    isRunning = true,
                    processedCount = idx + 1,
                    totalCount = allTracks.size,
                    updatedCount = updated,
                    statusMessage = "Analyse : ${idx + 1} / ${allTracks.size} morceaux ($updated mis à jour)",
                )
                if (idx < allTracks.lastIndex) {
                    delay(MetadataFetcher.REQUEST_THROTTLE_MS)
                }
            }

            _coverSyncState.value = CoverSyncUiState(
                isRunning = false,
                processedCount = allTracks.size,
                totalCount = allTracks.size,
                updatedCount = updated,
                statusMessage = "Terminé : $updated morceau(x) mis à jour sur ${allTracks.size} analysés.",
            )
        }
    }

    // --- Export / Import JSON de configuration ---
    fun exportConfigToUri(uri: Uri) {
        val appCtx = getApplication<Application>()
        val snapshot = settings.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = PreferencesRepository.exportSettingsJson(snapshot)
                    appCtx.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("OutputStream null")
                    true
                }.getOrDefault(false)
            }
            postFeedback(
                if (ok) "Réglages exportés au format JSON avec succès"
                else "Impossible d'exporter les réglages",
            )
        }
    }

    fun importConfigFromUri(uri: Uri) {
        val appCtx = getApplication<Application>()
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    appCtx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val imported = if (!json.isNullOrBlank()) prefsRepo.importSettingsJson(json) else false
            postFeedback(
                if (imported) "Réglages restaurés avec succès"
                else "Fichier de sauvegarde JSON invalide ou illisible",
            )
        }
    }

    // --- Thème, SDVC, Limiteur visuel, Pastille & ACC ---
    fun setAccentColorId(colorId: String) {
        viewModelScope.launch { prefsRepo.setAccentColorId(colorId) }
    }

    fun setNightDimmerAlpha(alpha: Float) {
        viewModelScope.launch { prefsRepo.setNightDimmerAlpha(alpha) }
    }

    fun setAutoNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoNightModeEnabled(enabled) }
    }

    fun setNightSchedule(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch { prefsRepo.setNightSchedule(startMinutes, endMinutes) }
    }

    fun setAutoResumeMusicEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoResumeMusicEnabled(enabled) }
    }

    fun setAutoLaunchPackageName(packageName: String) {
        viewModelScope.launch { prefsRepo.setAutoLaunchPackageName(packageName) }
    }

    fun setSdvcEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSdvcEnabled(enabled) }
    }

    fun setSpeedLimitKmh(limitKmh: Int) {
        if (limitKmh == SPEED_LIMIT_AUTO) {
            locationEngine.resetAutoSpeedLimit()
        }
        viewModelScope.launch { prefsRepo.setSpeedLimitKmh(limitKmh) }
    }

    fun setAutoSpeedDetectionSeconds(seconds: Int) {
        locationEngine.setAutoDetectionSeconds(seconds)
        viewModelScope.launch { prefsRepo.setAutoSpeedDetectionSeconds(seconds) }
    }

    fun setSpeedAlertToleranceKmh(toleranceKmh: Int) {
        viewModelScope.launch { prefsRepo.setSpeedAlertToleranceKmh(toleranceKmh) }
    }

    fun setSpeedAlertType(typeId: String) {
        viewModelScope.launch { prefsRepo.setSpeedAlertType(typeId) }
    }

    fun setSpeedCalibrationOffsetKmh(offsetKmh: Int) {
        locationEngine.setSpeedCalibrationOffset(offsetKmh)
        viewModelScope.launch { prefsRepo.setSpeedCalibrationOffsetKmh(offsetKmh) }
    }

    fun setBreakReminderEnabled(enabled: Boolean) {
        if (!enabled) {
            _showBreakReminder.value = false
        }
        viewModelScope.launch { prefsRepo.setBreakReminderEnabled(enabled) }
    }

    fun triggerSpeedAlertDucking() {
        audioEngine.triggerOverspeedAudioDucking()
    }

    fun setFloatingPillEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setFloatingPillEnabled(enabled)
            FloatingPillService.sync(getApplication(), enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        wakeResumeJob?.cancel()
        eqBandSaveJob?.cancel()
        coverSyncJob?.cancel()
        runCatching { getApplication<Application>().unregisterReceiver(screenWakeReceiver) }
        audioEngine.release()
        appsManager.release()
        locationEngine.release()
    }

    companion object {
        fun extractNormalizedFileKey(uriString: String): String {
            val decoded = runCatching { Uri.decode(uriString) }.getOrDefault(uriString)
            val docPath = decoded.substringAfterLast("/document/", missingDelimiterValue = "")
            if (docPath.isNotBlank()) return docPath.lowercase()
            return decoded.substringAfterLast('/').lowercase()
        }

        fun deduplicateTracksAgainstDb(
            candidates: List<AudioTrackEntity>,
            existing: List<AudioTrackEntity>,
        ): List<AudioTrackEntity> {
            val existingUris = existing.map { it.uriString }.toHashSet()
            val existingPaths = existing.map { extractNormalizedFileKey(it.uriString) }.toHashSet()
            val result = mutableListOf<AudioTrackEntity>()

            for (track in candidates) {
                val pathKey = extractNormalizedFileKey(track.uriString)
                if (track.uriString in existingUris || pathKey in existingPaths) {
                    continue
                }
                existingUris.add(track.uriString)
                existingPaths.add(pathKey)
                result.add(track)
            }
            return result
        }
    }
}
