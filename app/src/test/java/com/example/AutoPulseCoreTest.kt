package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.HeadUnitSettings
import com.example.data.MAX_DOCK_APPS
import com.example.data.PreferencesRepository
import com.example.data.TripStats
import com.example.data.db.AudioTrackEntity
import com.example.data.db.AutoPulseDatabase
import com.example.hardware.AudioEngine
import com.example.hardware.AutoPulseNotificationListenerService
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
import com.example.ui.theme.AccentThemeColor
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
        )
        val json = PreferencesRepository.exportSettingsJson(original)
        assertTrue(repo.importSettingsJson(json))
        val restored = repo.settingsFlow.first()
        assertEquals("BLUE", restored.accentColorId)
        assertEquals(listOf("com.waze", "com.spotify.music"), restored.dockPackageNames)
        assertEquals(110, restored.speedLimitKmh)
        assertTrue(restored.sdvcEnabled)
        assertTrue(restored.floatingPillEnabled)
        assertFalse(restored.eqEnabled)
        assertEquals(listOf(300, -200, 100, 400, -100), restored.eqCustomBandsMb)
        assertFalse(repo.importSettingsJson("{invalid_json"))
    }
}
