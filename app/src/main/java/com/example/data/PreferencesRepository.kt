package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autopulse_prefs")

const val MAX_DOCK_APPS = 10
const val DEFAULT_NIGHT_START_MINUTES = 19 * 60 + 30 // 19h30
const val DEFAULT_NIGHT_END_MINUTES = 7 * 60 + 30    // 07h30
const val SPEED_LIMIT_AUTO = -1
const val SPEED_LIMIT_OFF = 0
const val SPEED_ALERT_TOLERANCE_KMH = 3
const val DEFAULT_AUTO_DETECTION_SECONDS = 8
const val DEFAULT_SPEED_CALIBRATION_KMH = 0
const val BREAK_REMINDER_INTERVAL_SECONDS = 7_200L

enum class SpeedAlertType(val id: String, val label: String) {
    VISUAL("VISUAL", "Visuel seul"),
    BEEP("BEEP", "Visuel + Bip court"),
    DUCKING("DUCKING", "Baisse musique (-30%)");

    companion object {
        fun fromId(id: String?): SpeedAlertType {
            return entries.firstOrNull { it.id == id } ?: BEEP
        }
    }
}

data class TripStats(
    val distanceMeters: Double = 0.0,
    val drivingSeconds: Long = 0L,
    val maxSpeedKmh: Int = 0,
) {
    val distanceKm: Double
        get() = distanceMeters / 1000.0

    val averageSpeedKmh: Int
        get() = if (drivingSeconds > 0L && distanceMeters > 0.0) {
            ((distanceMeters / 1000.0) / (drivingSeconds / 3600.0)).toInt().coerceAtLeast(0)
        } else {
            0
        }
}

data class HeadUnitSettings(
    val activeAudioSource: String = "LOCAL", // "LOCAL" or "EXTERNAL"
    val eqEnabled: Boolean = true,
    val eqPresetIndex: Int = 0,
    val eqCustomBandsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val nightDimmerAlpha: Float = 0f,
    val autoNightModeEnabled: Boolean = false,
    val quickNightDimmerPaused: Boolean = false,
    val nightStartMinutes: Int = DEFAULT_NIGHT_START_MINUTES,
    val nightEndMinutes: Int = DEFAULT_NIGHT_END_MINUTES,
    val accentColorId: String = "RED",
    val autoResumeMusicEnabled: Boolean = false,
    val autoLaunchPackageName: String = "",
    val dockPackageNames: List<String> = emptyList(),
    val sdvcEnabled: Boolean = false,
    val speedLimitKmh: Int = SPEED_LIMIT_AUTO, // -1 = Auto, 0 = Off, sinon 30, 50, 70, 80, 90, 110, 130
    val floatingPillEnabled: Boolean = false,
    val autoSpeedDetectionSeconds: Int = DEFAULT_AUTO_DETECTION_SECONDS, // 5, 8 ou 12s
    val speedAlertToleranceKmh: Int = SPEED_ALERT_TOLERANCE_KMH, // +0, +3, +5 ou +10 km/h
    val speedAlertType: String = SpeedAlertType.BEEP.id, // VISUAL, BEEP, DUCKING
    val speedCalibrationOffsetKmh: Int = DEFAULT_SPEED_CALIBRATION_KMH, // +0 à +5 km/h
    val breakReminderEnabled: Boolean = false, // Pause conduite 2h
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val EQ_CUSTOM_BANDS = stringPreferencesKey("eq_custom_bands")
        val NIGHT_DIMMER = floatPreferencesKey("night_dimmer")
        val AUTO_NIGHT_ENABLED = booleanPreferencesKey("auto_night_enabled")
        val QUICK_NIGHT_PAUSED = booleanPreferencesKey("quick_night_paused")
        val NIGHT_START_MINUTES = intPreferencesKey("night_start_minutes")
        val NIGHT_END_MINUTES = intPreferencesKey("night_end_minutes")
        val ACCENT_COLOR_ID = stringPreferencesKey("accent_color_id")
        val AUTO_RESUME_MUSIC = booleanPreferencesKey("auto_resume_music")
        val AUTO_LAUNCH_PACKAGE = stringPreferencesKey("auto_launch_package")
        val DOCK_PACKAGES = stringPreferencesKey("dock_packages")
        val SDVC_ENABLED = booleanPreferencesKey("sdvc_enabled")
        val SPEED_LIMIT_KMH = intPreferencesKey("speed_limit_kmh")
        val FLOATING_PILL_ENABLED = booleanPreferencesKey("floating_pill_enabled")
        val AUTO_SPEED_DETECTION_SEC = intPreferencesKey("auto_speed_detection_sec")
        val SPEED_ALERT_TOLERANCE_KMH = intPreferencesKey("speed_alert_tolerance_kmh")
        val SPEED_ALERT_TYPE = stringPreferencesKey("speed_alert_type")
        val SPEED_CALIBRATION_OFFSET_KMH = intPreferencesKey("speed_calibration_offset_kmh")
        val BREAK_REMINDER_ENABLED = booleanPreferencesKey("break_reminder_enabled")
        val TRIP_DISTANCE_METERS = doublePreferencesKey("trip_distance_meters")
        val TRIP_DRIVING_SECONDS = longPreferencesKey("trip_driving_seconds")
        val TRIP_MAX_SPEED_KMH = intPreferencesKey("trip_max_speed_kmh")
    }

    val settingsFlow: Flow<HeadUnitSettings> = context.dataStore.data.map { prefs ->
        val rawDock = prefs[Keys.DOCK_PACKAGES].orEmpty()
        val dockList = rawDock
            .split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_DOCK_APPS)

        val customBands = parseEqBands(prefs[Keys.EQ_CUSTOM_BANDS])

        HeadUnitSettings(
            activeAudioSource = prefs[Keys.AUDIO_SOURCE] ?: "LOCAL",
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: true,
            eqPresetIndex = (prefs[Keys.EQ_PRESET] ?: 0).coerceIn(0, 5),
            eqCustomBandsMb = customBands,
            nightDimmerAlpha = prefs[Keys.NIGHT_DIMMER] ?: 0f,
            autoNightModeEnabled = prefs[Keys.AUTO_NIGHT_ENABLED] ?: false,
            quickNightDimmerPaused = prefs[Keys.QUICK_NIGHT_PAUSED] ?: false,
            nightStartMinutes = prefs[Keys.NIGHT_START_MINUTES] ?: DEFAULT_NIGHT_START_MINUTES,
            nightEndMinutes = prefs[Keys.NIGHT_END_MINUTES] ?: DEFAULT_NIGHT_END_MINUTES,
            accentColorId = prefs[Keys.ACCENT_COLOR_ID] ?: "RED",
            autoResumeMusicEnabled = prefs[Keys.AUTO_RESUME_MUSIC] ?: false,
            autoLaunchPackageName = prefs[Keys.AUTO_LAUNCH_PACKAGE] ?: "",
            dockPackageNames = dockList,
            sdvcEnabled = prefs[Keys.SDVC_ENABLED] ?: false,
            speedLimitKmh = (prefs[Keys.SPEED_LIMIT_KMH] ?: SPEED_LIMIT_AUTO).coerceAtLeast(SPEED_LIMIT_AUTO),
            floatingPillEnabled = prefs[Keys.FLOATING_PILL_ENABLED] ?: false,
            autoSpeedDetectionSeconds = normalizeAutoDetectionSeconds(prefs[Keys.AUTO_SPEED_DETECTION_SEC]),
            speedAlertToleranceKmh = normalizeAlertToleranceKmh(prefs[Keys.SPEED_ALERT_TOLERANCE_KMH]),
            speedAlertType = SpeedAlertType.fromId(prefs[Keys.SPEED_ALERT_TYPE]).id,
            speedCalibrationOffsetKmh = (prefs[Keys.SPEED_CALIBRATION_OFFSET_KMH] ?: DEFAULT_SPEED_CALIBRATION_KMH).coerceIn(0, 5),
            breakReminderEnabled = prefs[Keys.BREAK_REMINDER_ENABLED] ?: false,
        )
    }

    val tripStatsFlow: Flow<TripStats> = context.dataStore.data.map { prefs ->
        TripStats(
            distanceMeters = (prefs[Keys.TRIP_DISTANCE_METERS] ?: 0.0).coerceAtLeast(0.0),
            drivingSeconds = (prefs[Keys.TRIP_DRIVING_SECONDS] ?: 0L).coerceAtLeast(0L),
            maxSpeedKmh = (prefs[Keys.TRIP_MAX_SPEED_KMH] ?: 0).coerceAtLeast(0),
        )
    }

    suspend fun saveTripStats(stats: TripStats) {
        context.dataStore.edit {
            it[Keys.TRIP_DISTANCE_METERS] = stats.distanceMeters.coerceAtLeast(0.0)
            it[Keys.TRIP_DRIVING_SECONDS] = stats.drivingSeconds.coerceAtLeast(0L)
            it[Keys.TRIP_MAX_SPEED_KMH] = stats.maxSpeedKmh.coerceAtLeast(0)
        }
    }

    suspend fun resetTripStats() {
        saveTripStats(TripStats())
    }

    suspend fun setActiveAudioSource(source: String) {
        context.dataStore.edit { it[Keys.AUDIO_SOURCE] = source }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqPreset(index: Int) {
        context.dataStore.edit { it[Keys.EQ_PRESET] = index.coerceIn(0, 5) }
    }

    suspend fun setEqCustomBands(bandsMb: List<Int>) {
        val normalized = normalizeEqBands(bandsMb)
        context.dataStore.edit {
            it[Keys.EQ_PRESET] = 5
            it[Keys.EQ_CUSTOM_BANDS] = normalized.joinToString(",")
        }
    }

    suspend fun setNightDimmerAlpha(alpha: Float) {
        context.dataStore.edit {
            it[Keys.NIGHT_DIMMER] = alpha.coerceIn(0f, 0.85f)
            it[Keys.QUICK_NIGHT_PAUSED] = false
        }
    }

    suspend fun setAutoNightModeEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.AUTO_NIGHT_ENABLED] = enabled
            it[Keys.QUICK_NIGHT_PAUSED] = false
        }
    }

    suspend fun toggleQuickNightDimmer(nowMinutes: Int) {
        context.dataStore.edit { prefs ->
            val currentSettings = HeadUnitSettings(
                nightDimmerAlpha = prefs[Keys.NIGHT_DIMMER] ?: 0f,
                autoNightModeEnabled = prefs[Keys.AUTO_NIGHT_ENABLED] ?: false,
                quickNightDimmerPaused = prefs[Keys.QUICK_NIGHT_PAUSED] ?: false,
                nightStartMinutes = prefs[Keys.NIGHT_START_MINUTES] ?: DEFAULT_NIGHT_START_MINUTES,
                nightEndMinutes = prefs[Keys.NIGHT_END_MINUTES] ?: DEFAULT_NIGHT_END_MINUTES,
            )
            val currentlyActive = computeEffectiveDimmerAlpha(currentSettings, nowMinutes) > 0.005f
            if (currentlyActive) {
                prefs[Keys.QUICK_NIGHT_PAUSED] = true
            } else {
                prefs[Keys.QUICK_NIGHT_PAUSED] = false
                if ((prefs[Keys.NIGHT_DIMMER] ?: 0f) <= 0.005f && !currentSettings.autoNightModeEnabled) {
                    prefs[Keys.NIGHT_DIMMER] = 0.45f
                }
            }
        }
    }

    suspend fun setNightSchedule(startMinutes: Int, endMinutes: Int) {
        context.dataStore.edit {
            it[Keys.NIGHT_START_MINUTES] = startMinutes.coerceIn(0, 23 * 60 + 59)
            it[Keys.NIGHT_END_MINUTES] = endMinutes.coerceIn(0, 23 * 60 + 59)
        }
    }

    suspend fun setAccentColorId(colorId: String) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR_ID] = colorId }
    }

    suspend fun setAutoResumeMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RESUME_MUSIC] = enabled }
    }

    suspend fun setAutoLaunchPackageName(packageName: String) {
        context.dataStore.edit { it[Keys.AUTO_LAUNCH_PACKAGE] = packageName }
    }

    suspend fun setDockPackages(packages: List<String>) {
        val sanitized = packages.distinct().take(MAX_DOCK_APPS)
        context.dataStore.edit { it[Keys.DOCK_PACKAGES] = sanitized.joinToString("|") }
    }

    suspend fun setSdvcEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SDVC_ENABLED] = enabled }
    }

    suspend fun setSpeedLimitKmh(limitKmh: Int) {
        context.dataStore.edit { it[Keys.SPEED_LIMIT_KMH] = limitKmh.coerceAtLeast(SPEED_LIMIT_AUTO) }
    }

    suspend fun setFloatingPillEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLOATING_PILL_ENABLED] = enabled }
    }

    suspend fun setAutoSpeedDetectionSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.AUTO_SPEED_DETECTION_SEC] = normalizeAutoDetectionSeconds(seconds) }
    }

    suspend fun setSpeedAlertToleranceKmh(toleranceKmh: Int) {
        context.dataStore.edit { it[Keys.SPEED_ALERT_TOLERANCE_KMH] = normalizeAlertToleranceKmh(toleranceKmh) }
    }

    suspend fun setSpeedAlertType(typeId: String) {
        context.dataStore.edit { it[Keys.SPEED_ALERT_TYPE] = SpeedAlertType.fromId(typeId).id }
    }

    suspend fun setSpeedCalibrationOffsetKmh(offsetKmh: Int) {
        context.dataStore.edit { it[Keys.SPEED_CALIBRATION_OFFSET_KMH] = offsetKmh.coerceIn(0, 5) }
    }

    suspend fun setBreakReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BREAK_REMINDER_ENABLED] = enabled }
    }

    suspend fun importSettingsJson(jsonString: String): Boolean {
        return runCatching {
            val obj = JSONObject(jsonString)
            val dockArray = obj.optJSONArray("dockPackageNames")
            val dockList = buildList {
                if (dockArray != null) {
                    for (i in 0 until dockArray.length()) {
                        val pkg = dockArray.optString(i).trim()
                        if (pkg.isNotEmpty()) add(pkg)
                    }
                }
            }.distinct().take(MAX_DOCK_APPS)

            val eqArray = obj.optJSONArray("eqCustomBandsMb")
            val eqBands = if (eqArray != null) {
                List(5) { idx -> eqArray.optInt(idx, 0).coerceIn(-1500, 1500) }
            } else {
                listOf(0, 0, 0, 0, 0)
            }

            val source = obj.optString("activeAudioSource", "LOCAL")
                .takeIf { it == "LOCAL" || it == "EXTERNAL" } ?: "LOCAL"

            context.dataStore.edit { prefs ->
                prefs[Keys.AUDIO_SOURCE] = source
                prefs[Keys.EQ_ENABLED] = obj.optBoolean("eqEnabled", true)
                prefs[Keys.EQ_PRESET] = obj.optInt("eqPresetIndex", 0).coerceIn(0, 5)
                prefs[Keys.EQ_CUSTOM_BANDS] = eqBands.joinToString(",")
                prefs[Keys.NIGHT_DIMMER] = obj.optDouble("nightDimmerAlpha", 0.0).toFloat().coerceIn(0f, 0.85f)
                prefs[Keys.AUTO_NIGHT_ENABLED] = obj.optBoolean("autoNightModeEnabled", false)
                prefs[Keys.QUICK_NIGHT_PAUSED] = false
                prefs[Keys.NIGHT_START_MINUTES] = obj.optInt("nightStartMinutes", DEFAULT_NIGHT_START_MINUTES).coerceIn(0, 23 * 60 + 59)
                prefs[Keys.NIGHT_END_MINUTES] = obj.optInt("nightEndMinutes", DEFAULT_NIGHT_END_MINUTES).coerceIn(0, 23 * 60 + 59)
                prefs[Keys.ACCENT_COLOR_ID] = obj.optString("accentColorId", "RED").ifBlank { "RED" }
                prefs[Keys.AUTO_RESUME_MUSIC] = obj.optBoolean("autoResumeMusicEnabled", false)
                prefs[Keys.AUTO_LAUNCH_PACKAGE] = obj.optString("autoLaunchPackageName", "")
                prefs[Keys.DOCK_PACKAGES] = dockList.joinToString("|")
                prefs[Keys.SDVC_ENABLED] = obj.optBoolean("sdvcEnabled", false)
                prefs[Keys.SPEED_LIMIT_KMH] = obj.optInt("speedLimitKmh", SPEED_LIMIT_AUTO).coerceAtLeast(SPEED_LIMIT_AUTO)
                prefs[Keys.FLOATING_PILL_ENABLED] = obj.optBoolean("floatingPillEnabled", false)
                prefs[Keys.AUTO_SPEED_DETECTION_SEC] = normalizeAutoDetectionSeconds(
                    if (obj.has("autoSpeedDetectionSeconds")) obj.optInt("autoSpeedDetectionSeconds") else null
                )
                prefs[Keys.SPEED_ALERT_TOLERANCE_KMH] = normalizeAlertToleranceKmh(
                    if (obj.has("speedAlertToleranceKmh")) obj.optInt("speedAlertToleranceKmh") else null
                )
                prefs[Keys.SPEED_ALERT_TYPE] = SpeedAlertType.fromId(
                    obj.optString("speedAlertType", SpeedAlertType.BEEP.id)
                ).id
                prefs[Keys.SPEED_CALIBRATION_OFFSET_KMH] = obj.optInt(
                    "speedCalibrationOffsetKmh",
                    DEFAULT_SPEED_CALIBRATION_KMH,
                ).coerceIn(0, 5)
                prefs[Keys.BREAK_REMINDER_ENABLED] = obj.optBoolean("breakReminderEnabled", false)
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        fun exportSettingsJson(settings: HeadUnitSettings): String {
            return JSONObject().apply {
                put("version", 1)
                put("activeAudioSource", settings.activeAudioSource)
                put("eqEnabled", settings.eqEnabled)
                put("eqPresetIndex", settings.eqPresetIndex)
                put("eqCustomBandsMb", JSONArray(normalizeEqBands(settings.eqCustomBandsMb)))
                put("nightDimmerAlpha", settings.nightDimmerAlpha.toDouble())
                put("autoNightModeEnabled", settings.autoNightModeEnabled)
                put("nightStartMinutes", settings.nightStartMinutes)
                put("nightEndMinutes", settings.nightEndMinutes)
                put("accentColorId", settings.accentColorId)
                put("autoResumeMusicEnabled", settings.autoResumeMusicEnabled)
                put("autoLaunchPackageName", settings.autoLaunchPackageName)
                put("dockPackageNames", JSONArray(settings.dockPackageNames))
                put("sdvcEnabled", settings.sdvcEnabled)
                put("speedLimitKmh", settings.speedLimitKmh)
                put("floatingPillEnabled", settings.floatingPillEnabled)
                put("autoSpeedDetectionSeconds", settings.autoSpeedDetectionSeconds)
                put("speedAlertToleranceKmh", settings.speedAlertToleranceKmh)
                put("speedAlertType", settings.speedAlertType)
                put("speedCalibrationOffsetKmh", settings.speedCalibrationOffsetKmh)
                put("breakReminderEnabled", settings.breakReminderEnabled)
            }.toString(2)
        }

        fun parseEqBands(raw: String?): List<Int> {
            if (raw.isNullOrBlank()) return listOf(0, 0, 0, 0, 0)
            val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(-1500, 1500) }
            return normalizeEqBands(parsed)
        }

        fun normalizeEqBands(bandsMb: List<Int>): List<Int> {
            return List(5) { idx ->
                bandsMb.getOrElse(idx) { 0 }.coerceIn(-1500, 1500)
            }
        }

        fun isNightTimeNow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
            if (startMinutes == endMinutes) return false
            return if (startMinutes < endMinutes) {
                nowMinutes in startMinutes until endMinutes
            } else {
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }
        }

        fun computeEffectiveDimmerAlpha(settings: HeadUnitSettings, nowMinutes: Int): Float {
            if (settings.quickNightDimmerPaused) return 0f
            if (!settings.autoNightModeEnabled) {
                return settings.nightDimmerAlpha.coerceIn(0f, 0.85f)
            }
            return if (isNightTimeNow(nowMinutes, settings.nightStartMinutes, settings.nightEndMinutes)) {
                settings.nightDimmerAlpha.coerceAtLeast(0.35f).coerceAtMost(0.85f)
            } else {
                0f
            }
        }

        fun formatMinutesAsTime(minutes: Int): String {
            val safe = ((minutes % 1440) + 1440) % 1440
            val h = safe / 60
            val m = safe % 60
            return "%02d:%02d".format(h, m)
        }

        fun formatDrivingDuration(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val h = safe / 3600L
            val m = (safe % 3600L) / 60L
            val s = safe % 60L
            return "%02d:%02d:%02d".format(h, m, s)
        }

        fun normalizeAutoDetectionSeconds(value: Int?): Int {
            return when (value) {
                5, 8, 12 -> value
                else -> DEFAULT_AUTO_DETECTION_SECONDS
            }
        }

        fun normalizeAlertToleranceKmh(value: Int?): Int {
            return when (value) {
                0, 3, 5, 10 -> value
                else -> SPEED_ALERT_TOLERANCE_KMH
            }
        }

        fun isSpeedOverLimit(
            speedKmh: Int,
            activeLimitKmh: Int,
            toleranceKmh: Int = SPEED_ALERT_TOLERANCE_KMH,
        ): Boolean {
            return activeLimitKmh > 0 && speedKmh >= activeLimitKmh + toleranceKmh.coerceAtLeast(0)
        }
    }
}
