package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.media.AudioManager
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.HeadUnitSettings
import com.example.data.MAX_DOCK_APPS
import com.example.data.PreferencesRepository
import com.example.data.SPEED_LIMIT_AUTO
import com.example.data.SpeedAlertType
import com.example.data.TripStats
import com.example.data.db.AudioTrackEntity
import com.example.data.db.AutoPulseDatabase
import com.example.hardware.AudioEngine
import com.example.hardware.AutoPulseNotificationListenerService
import com.example.hardware.AutoSpeedLimitDetector
import com.example.hardware.BootAndWakeReceiver
import com.example.hardware.EqPreset
import com.example.hardware.ExternalMediaInfo
import com.example.hardware.ExternalRepeatMode
import com.example.hardware.FloatingPillService
import com.example.hardware.LocationEngine
import com.example.hardware.MetadataFetcher
import com.example.hardware.ScreenDimmerService
import com.example.hardware.SdvcController
import com.example.hardware.UniversalMediaManager
import com.example.ui.screens.formatShortFrenchDate
import com.example.ui.theme.AccentThemeColor
import com.example.viewmodel.AutoPulseViewModel
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class AutoPulseCoreTest {

    @Test
    fun mainActivityDeclaresHomeLauncherIntentFilter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.queryIntentActivities(homeIntent, 0)
        assertTrue(
            "MainActivity should resolve CATEGORY_HOME so user can set AutoPulse as launcher",
            resolved.any { it.activityInfo.packageName == context.packageName },
        )
    }

    @Test
    fun manifestDeclaresNotificationListenerAndBootReceiver() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val serviceInfo = pm.getServiceInfo(
            ComponentName(context, AutoPulseNotificationListenerService::class.java),
            0,
        )
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", serviceInfo.permission)

        val receiverInfo = pm.getReceiverInfo(
            ComponentName(context, BootAndWakeReceiver::class.java),
            0,
        )
        assertTrue(receiverInfo.exported)

        val dimmerServiceInfo = pm.getServiceInfo(
            ComponentName(context, ScreenDimmerService::class.java),
            0,
        )
        assertFalse(dimmerServiceInfo.exported)

        val pillServiceInfo = pm.getServiceInfo(
            ComponentName(context, FloatingPillService::class.java),
            0,
        )
        assertFalse(pillServiceInfo.exported)
    }

    @Test
    fun audioDurationFormattingWorks() {
        assertEquals("0:00", AudioEngine.formatDuration(0L))
        assertEquals("3:45", AudioEngine.formatDuration(225_000L))
    }

    @Test
    fun dockMaxAppsIsExpanded() {
        assertEquals(10, MAX_DOCK_APPS)
    }

    @Test
    fun rainbowThemeHasAllSevenExactColors() {
        assertEquals(7, AccentThemeColor.entries.size)
        assertEquals(Color(0xFFFF2A3C), AccentThemeColor.RED.primary)
        assertEquals(Color(0xFFFF7A00), AccentThemeColor.ORANGE.primary)
        assertEquals(Color(0xFFFFD600), AccentThemeColor.YELLOW.primary)
        assertEquals(Color(0xFF00E676), AccentThemeColor.GREEN.primary)
        assertEquals(Color(0xFF2979FF), AccentThemeColor.BLUE.primary)
        assertEquals(Color(0xFF536DFE), AccentThemeColor.INDIGO.primary)
        assertEquals(Color(0xFFD500F9), AccentThemeColor.VIOLET.primary)
    }

    @Test
    fun locationSpeedConversionToKmhWorks() {
        assertEquals(0, LocationEngine.metersPerSecondToKmh(0f))
        assertEquals(0, LocationEngine.metersPerSecondToKmh(0.2f))
        assertEquals(50, LocationEngine.metersPerSecondToKmh(13.89f))
        assertEquals(90, LocationEngine.metersPerSecondToKmh(25.0f))
    }

    @Test
    fun autoNightModeScheduleEvaluatesOvernightWindow() {
        val start = 19 * 60 + 30
        val end = 7 * 60 + 30
        assertTrue(PreferencesRepository.isNightTimeNow(21 * 60, start, end))
        assertTrue(PreferencesRepository.isNightTimeNow(2 * 60, start, end))
        assertFalse(PreferencesRepository.isNightTimeNow(12 * 60, start, end))

        val settings = HeadUnitSettings(
            nightDimmerAlpha = 0.45f,
            autoNightModeEnabled = true,
            nightStartMinutes = start,
            nightEndMinutes = end,
        )
        assertEquals(0.45f, PreferencesRepository.computeEffectiveDimmerAlpha(settings, 22 * 60), 0.01f)
        assertEquals(0f, PreferencesRepository.computeEffectiveDimmerAlpha(settings, 14 * 60), 0.01f)
    }

    @Test
    fun externalAlbumArtCompressesToRgb565Max256() {
        val oversized = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val compressed = UniversalMediaManager.compressAlbumArtRgb565(oversized)
        assertNotNull(compressed)
        assertEquals(Bitmap.Config.RGB_565, compressed!!.config)
        assertEquals(256, compressed.width)
        assertEquals(128, compressed.height)
    }

    @Test
    fun dimmerAlphaClampsUpTo85PercentAndExternalMediaDefaultsWork() {
        val highManual = HeadUnitSettings(
            nightDimmerAlpha = 0.95f,
            autoNightModeEnabled = false,
        )
        assertEquals(0.85f, PreferencesRepository.computeEffectiveDimmerAlpha(highManual, 12 * 60), 0.001f)

        val disabledZero = HeadUnitSettings(
            nightDimmerAlpha = 0f,
            autoNightModeEnabled = false,
        )
        assertEquals(0f, PreferencesRepository.computeEffectiveDimmerAlpha(disabledZero, 23 * 60), 0.001f)

        val defaultExternal = ExternalMediaInfo()
        assertFalse(defaultExternal.hasActiveSession)
        assertFalse(defaultExternal.shuffleEnabled)
        assertEquals(ExternalRepeatMode.NONE, defaultExternal.repeatMode)
    }

    @Test
    fun metadataFetcherCleansTrackNamesExtractsYearAndSavesRgb565Cover() {
        assertEquals(
            "Instant Crush",
            MetadataFetcher.cleanTrackName("05 - Instant Crush (feat. Julian Casablancas) [Official Audio].mp3"),
        )
        assertEquals(
            "Daft Punk Instant Crush",
            MetadataFetcher.buildSearchQuery("05. Instant Crush [320kbps].flac", "Daft Punk"),
        )
        assertEquals("2013", MetadataFetcher.extractYear("2013-05-17T07:00:00Z"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val path = MetadataFetcher.compressAndSaveCoverRgb565(context, baos.toByteArray(), "test_cover_key")
        assertNotNull(path)

        val loaded = MetadataFetcher.loadCoverBitmapRgb565(path)
        assertNotNull(loaded)
        assertEquals(Bitmap.Config.RGB_565, loaded!!.config)
        assertTrue(loaded.width <= 256 && loaded.height <= 256)
    }

    @Test
    fun equalizerPresetsAndRoomV4MetadataWork() = runBlocking {
        assertEquals(6, EqPreset.entries.size)
        assertEquals("Plat", EqPreset.fromIndex(0).label)
        assertEquals("Personnalisé", EqPreset.fromIndex(5).label)
        assertEquals(listOf(400, -1200, 0, 1200, 0), PreferencesRepository.parseEqBands("400,-1500,0,2000,0"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AutoPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.audioTrackDao()
            dao.insertAll(
                listOf(
                    AudioTrackEntity(
                        uriString = "content://usb/01.mp3",
                        title = "Midnight City",
                        artist = "M83",
                        album = "Hurry Up, We're Dreaming",
                        genre = "Electronic",
                        year = "2011",
                        coverPath = "/tmp/cover.jpg",
                        durationMs = 243_000L,
                        folderName = "USB",
                    )
                )
            )
            val all = dao.getAllOnce()
            assertEquals(1, all.size)
            assertEquals("Electronic", all[0].genre)
            assertEquals("2011", all[0].year)
            assertEquals("/tmp/cover.jpg", all[0].coverPath)
        } finally {
            db.close()
        }
    }

    @Test
    fun tripFilteringAndStatsCalculationsWork() {
        val loc1 = Location("gps").apply {
            latitude = 48.8566
            longitude = 2.3522
            accuracy = 8f
            speed = 15f
        }
        val microJitter = Location("gps").apply {
            latitude = 48.85661
            longitude = 2.35221
            accuracy = 8f
            speed = 0.5f // < 3 km/h à l'arrêt
        }
        assertEquals(0.0, LocationEngine.computeFilteredDistanceDelta(loc1, microJitter, 2), 0.001)

        val drivingLoc = Location("gps").apply {
            latitude = 48.8576
            longitude = 2.3522
            accuracy = 8f
            speed = 15f
        }
        val delta = LocationEngine.computeFilteredDistanceDelta(loc1, drivingLoc, 54)
        assertTrue(delta > 100.0 && delta < 130.0)

        val stats = TripStats(distanceMeters = 15_000.0, drivingSeconds = 900L, maxSpeedKmh = 112)
        assertEquals(15.0, stats.distanceKm, 0.01)
        assertEquals(60, stats.averageSpeedKmh)
        assertEquals("00:15:00", PreferencesRepository.formatDrivingDuration(stats.drivingSeconds))
    }

    @Test
    fun sdvcControllerEnforcesFiveSecondStabilization() {
        val sdvc = SdvcController(stabilizationDelayMs = 5_000L)
        assertEquals(null, sdvc.evaluate(enabled = true, speedKmh = 95, currentVolume = 8, maxVolume = 15, nowMs = 1_000L))
        assertEquals(null, sdvc.evaluate(enabled = true, speedKmh = 98, currentVolume = 8, maxVolume = 15, nowMs = 4_000L))
        assertEquals(10, sdvc.evaluate(enabled = true, speedKmh = 100, currentVolume = 8, maxVolume = 15, nowMs = 6_000L))
        assertTrue(sdvc.isBoostActive)

        // Aucun retour brusque à 70 km/h ou avant 5s sous 55 km/h
        assertEquals(null, sdvc.evaluate(enabled = true, speedKmh = 70, currentVolume = 10, maxVolume = 15, nowMs = 8_000L))
        assertEquals(null, sdvc.evaluate(enabled = true, speedKmh = 45, currentVolume = 10, maxVolume = 15, nowMs = 9_000L))
        assertEquals(null, sdvc.evaluate(enabled = true, speedKmh = 45, currentVolume = 10, maxVolume = 15, nowMs = 12_000L))
        assertEquals(8, sdvc.evaluate(enabled = true, speedKmh = 40, currentVolume = 10, maxVolume = 15, nowMs = 14_100L))
        assertFalse(sdvc.isBoostActive)
    }

    @Test
    fun settingsJsonExportAndImportRoundtripWorks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        val original = HeadUnitSettings(
            activeAudioSource = "EXTERNAL",
            eqEnabled = false,
            eqPresetIndex = 5,
            eqCustomBandsMb = listOf(300, -200, 100, 400, -100),
            nightDimmerAlpha = 0.55f,
            autoNightModeEnabled = true,
            nightStartMinutes = 20 * 60,
            nightEndMinutes = 6 * 60 + 30,
            accentColorId = "BLUE",
            autoResumeMusicEnabled = true,
            autoLaunchPackageName = "com.waze",
            dockPackageNames = listOf("com.waze", "com.spotify.music"),
            sdvcEnabled = true,
            speedLimitKmh = 110,
            floatingPillEnabled = true,
            autoSpeedDetectionSeconds = 12,
            speedAlertToleranceKmh = 5,
            speedAlertType = SpeedAlertType.DUCKING.id,
            speedCalibrationOffsetKmh = 4,
            breakReminderEnabled = true,
        )
        val json = PreferencesRepository.exportSettingsJson(original)
        assertTrue(repo.importSettingsJson(json))
        val restored = repo.settingsFlow.first()
        assertEquals("BLUE", restored.accentColorId)
        assertEquals(listOf("com.waze", "com.spotify.music"), restored.dockPackageNames)
        assertEquals(110, restored.speedLimitKmh)
        assertTrue(restored.sdvcEnabled)
        assertTrue(restored.floatingPillEnabled)
        assertEquals(12, restored.autoSpeedDetectionSeconds)
        assertEquals(5, restored.speedAlertToleranceKmh)
        assertEquals(SpeedAlertType.DUCKING.id, restored.speedAlertType)
        assertEquals(4, restored.speedCalibrationOffsetKmh)
        assertTrue(restored.breakReminderEnabled)
        assertFalse(restored.eqEnabled)
        assertEquals(listOf(300, -200, 100, 400, -100), restored.eqCustomBandsMb)
        assertFalse(repo.importSettingsJson("{invalid_json"))
    }

    @Test
    fun metadataFetcherStripsNoiseKeywordsAndBuildsSimplifiedQuery() {
        assertEquals(
            "Starboy",
            MetadataFetcher.cleanTrackName("01 - Starboy (Official Video) [4K HQ] Full Song Lyrical.mp3"),
        )
        assertEquals(
            "Midnight City",
            MetadataFetcher.cleanTrackName("Midnight City - Lyric Video Video Song HD Official Audio.flac"),
        )
        assertEquals(
            "Never Gonna Give You",
            MetadataFetcher.buildSimplifiedQuery("03. Never Gonna Give You Up (Official Video) [4K].mp3"),
        )
    }

    @Test
    fun deduplicateTracksFiltersExistingUrisNormalizedSafPathsAndBatchDuplicates() {
        val existing = listOf(
            AudioTrackEntity(
                id = 1,
                uriString = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FRoad%2Ftrack1.mp3",
                title = "Track 1",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L,
                folderName = "Road",
            )
        )
        val candidates = listOf(
            // Même fichier via URI OpenDocumentTree (préfixe tree différent, même /document/)
            AudioTrackEntity(
                uriString = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FRoad%2Ftrack1.mp3",
                title = "Track 1 Tree",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L,
                folderName = "Road",
            ),
            // Nouvelle piste valide
            AudioTrackEntity(
                uriString = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FRoad%2Ftrack2.mp3",
                title = "Track 2",
                artist = "Artist",
                album = "Album",
                durationMs = 200_000L,
                folderName = "Road",
            ),
            // Doublon dans le même lot d'import
            AudioTrackEntity(
                uriString = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FRoad%2Ftrack2.mp3",
                title = "Track 2 Duplicate",
                artist = "Artist",
                album = "Album",
                durationMs = 200_000L,
                folderName = "Road",
            ),
        )

        val inserted = AutoPulseViewModel.deduplicateTracksAgainstDb(candidates, existing)
        assertEquals(1, inserted.size)
        assertEquals("Track 2", inserted[0].title)
    }

    @Test
    fun audioEngineSyncsMediaVolumeOnHardwareBroadcast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val engine = AudioEngine(context)
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol / 2).coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            context.sendBroadcast(
                Intent(AudioEngine.ACTION_VOLUME_CHANGED).apply {
                    putExtra(AudioEngine.EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                }
            )
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(targetVol, engine.state.value.mediaVolume)
        } finally {
            engine.release()
        }
    }

    @Test
    fun autoSpeedLimitSnappingAndOverspeedToleranceMatchRules() {
        assertEquals(SPEED_LIMIT_AUTO, HeadUnitSettings().speedLimitKmh)

        assertEquals(30, AutoSpeedLimitDetector.snapToNearestRoadLimit(32))
        assertEquals(70, AutoSpeedLimitDetector.snapToNearestRoadLimit(67))
        assertEquals(80, AutoSpeedLimitDetector.snapToNearestRoadLimit(83))
        assertEquals(90, AutoSpeedLimitDetector.snapToNearestRoadLimit(87))
        // Équidistance exacte -> palier inférieur
        assertEquals(30, AutoSpeedLimitDetector.snapToNearestRoadLimit(40))
        assertEquals(50, AutoSpeedLimitDetector.snapToNearestRoadLimit(60))
        assertEquals(70, AutoSpeedLimitDetector.snapToNearestRoadLimit(75))
        assertEquals(80, AutoSpeedLimitDetector.snapToNearestRoadLimit(85))

        // Seuil d'alerte : limite + 3 km/h
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 30, activeLimitKmh = 30))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 32, activeLimitKmh = 30))
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 33, activeLimitKmh = 30))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 52, activeLimitKmh = 50))
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 53, activeLimitKmh = 50))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 92, activeLimitKmh = 90))
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 93, activeLimitKmh = 90))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 150, activeLimitKmh = 0))
    }

    @Test
    fun autoSpeedLimitDetectorRunsEightSecondCycleWithResetAndReEvaluation() {
        val detector = AutoSpeedLimitDetector()

        // Vitesse <= 10 km/h ignorée (feux rouges / bouchons)
        var state = detector.evaluate(speedKmh = 8, nowMs = 0L)
        assertFalse(state.isObserving)
        assertFalse(state.isCountdownVisible)
        assertEquals(null, state.detectedLimitKmh)

        // Phase 1 (0 à 3s) à 83 km/h ±2 km/h : observation silencieuse
        var now = 1_000L
        state = detector.evaluate(speedKmh = 83, nowMs = now)
        assertTrue(state.isObserving)
        assertFalse(state.isCountdownVisible)

        repeat(14) { idx ->
            now += 200L
            val jitterSpeed = if (idx % 2 == 0) 84 else 81 // dans ±2 km/h de 83
            state = detector.evaluate(speedKmh = jitterSpeed, nowMs = now)
            assertFalse("Phase 1 (<3s) must remain silent at tick $idx", state.isCountdownVisible)
        }

        // À 3.0s : Phase 2 démarre (chrono 1s)
        now += 200L
        state = detector.evaluate(speedKmh = 83, nowMs = now)
        assertTrue(state.isCountdownVisible)
        assertEquals(1, state.countdownSeconds)

        // Sortie de ±2 km/h pendant la Phase 2 -> annulation immédiate et nouvelle référence
        now += 200L
        state = detector.evaluate(speedKmh = 87, nowMs = now)
        assertFalse(state.isCountdownVisible)
        assertEquals(0, state.countdownSeconds)
        assertEquals(0L, state.stableDurationMs)

        // Nouveau cycle complet de 8s autour de 87 km/h -> verrouille 90 km/h
        repeat(15) {
            now += 200L
            state = detector.evaluate(speedKmh = 87, nowMs = now)
        }
        assertTrue(state.isCountdownVisible) // t = 3.0s
        assertEquals(1, state.countdownSeconds)

        repeat(24) {
            now += 200L
            state = detector.evaluate(speedKmh = 88, nowMs = now) // dans ±2 km/h de 87
        }
        assertTrue(state.isCountdownVisible)
        assertEquals(5, state.countdownSeconds)

        now += 200L
        state = detector.evaluate(speedKmh = 87, nowMs = now) // t = 8.0s -> validation
        assertFalse(state.isCountdownVisible)
        assertEquals(90, state.detectedLimitKmh)

        // Changement d'allure vers 32 km/h -> nouveau cycle de 8s -> 30 km/h
        now += 200L
        state = detector.evaluate(speedKmh = 32, nowMs = now)
        assertTrue(state.isObserving)
        assertEquals(90, state.detectedLimitKmh)

        repeat(40) {
            now += 200L
            state = detector.evaluate(speedKmh = 32, nowMs = now)
        }
        assertFalse(state.isCountdownVisible)
        assertEquals(30, state.detectedLimitKmh)
    }

    @Test
    fun metadataFetcherRejectsHomonymFromDifferentArtist() {
        assertTrue(
            MetadataFetcher.doesItunesResultMatch(
                knownArtist = "Mylène Farmer",
                trackTitle = "Libertine",
                apiArtistName = "Mylène Farmer",
                apiTrackName = "Libertine (Live)",
            )
        )
        assertFalse(
            MetadataFetcher.doesItunesResultMatch(
                knownArtist = "Daft Punk",
                trackTitle = "Instant Crush",
                apiArtistName = "Natalie Imbruglia",
                apiTrackName = "Instant Crush",
            )
        )
        assertTrue(
            MetadataFetcher.doesItunesResultMatch(
                knownArtist = "Artiste inconnu",
                trackTitle = "Starboy",
                apiArtistName = "The Weeknd",
                apiTrackName = "Starboy",
            )
        )
    }

    @Test
    fun speedCalibrationAlertTolerancesAndCustomAutoDurationWork() {
        // Étalonnage : 0 quand à l'arrêt (<= 2 km/h), offset appliqué sinon
        assertEquals(0, LocationEngine.applySpeedCalibration(0, 4))
        assertEquals(0, LocationEngine.applySpeedCalibration(2, 5))
        assertEquals(54, LocationEngine.applySpeedCalibration(50, 4))
        assertEquals(115, LocationEngine.applySpeedCalibration(110, 8)) // borné à +5 max

        // Tolérances d'alerte : +0, +3, +5, +10 km/h
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 80, activeLimitKmh = 80, toleranceKmh = 0))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 84, activeLimitKmh = 80, toleranceKmh = 5))
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 85, activeLimitKmh = 80, toleranceKmh = 5))
        assertFalse(PreferencesRepository.isSpeedOverLimit(speedKmh = 139, activeLimitKmh = 130, toleranceKmh = 10))
        assertTrue(PreferencesRepository.isSpeedOverLimit(speedKmh = 140, activeLimitKmh = 130, toleranceKmh = 10))

        // Cycle Auto configurable à 5s (3s silencieuses + 2s jauge)
        val detector = AutoSpeedLimitDetector()
        var now = 1_000L
        var state = detector.evaluate(speedKmh = 72, nowMs = now, totalLockDurationMs = 5_000L)
        assertTrue(state.isObserving)
        assertEquals(2, state.countdownTotalSeconds)

        repeat(24) {
            now += 200L
            state = detector.evaluate(speedKmh = 72, nowMs = now, totalLockDurationMs = 5_000L)
        }
        assertTrue(state.isCountdownVisible)
        assertEquals(2, state.countdownSeconds)

        now += 200L
        state = detector.evaluate(speedKmh = 72, nowMs = now, totalLockDurationMs = 5_000L)
        assertFalse(state.isCountdownVisible)
        assertEquals(70, state.detectedLimitKmh)
    }

    @Test
    fun shortFrenchDateFormatsThreeLetterAbbreviationsWithoutDots() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 10, 30, 0)
        }
        assertEquals("Dim 15 nov 2026", formatShortFrenchDate(cal.time))
    }
}
