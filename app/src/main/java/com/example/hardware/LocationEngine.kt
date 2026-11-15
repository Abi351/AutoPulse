package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val FRENCH_ROAD_SPEED_LIMITS = listOf(30, 50, 70, 80, 90, 110, 130)

data class AutoSpeedLimitState(
    val detectedLimitKmh: Int? = null,
    val isObserving: Boolean = false,
    val isCountdownVisible: Boolean = false,
    val countdownSeconds: Int = 0, // 1..countdownTotalSeconds pendant la phase 2
    val progressFraction: Float = 0f, // 0f..1f pendant la phase 2
    val stableDurationMs: Long = 0L,
    val referenceSpeedKmh: Int? = null,
    val countdownTotalSeconds: Int = 5,
)

/**
 * Détecteur automatique de limitation de vitesse (cycle configurable 5s, 8s ou 12s) :
 * - Actif lorsque vitesse > 10 km/h
 * - Tolérance de stabilité : ±2 km/h autour de la vitesse de référence
 * - Phase 1 (0 à 3s) : observation silencieuse
 * - Phase 2 (3s à durée totale) : jauge fine et décompte seconde par seconde
 * - Validation : arrondi vers le palier [30, 50, 70, 80, 90, 110, 130] le plus proche (inférieur si équidistance)
 */
class AutoSpeedLimitDetector {

    private var lastTickMs: Long = -1L
    private var referenceSpeedKmh: Int? = null
    private var stableDurationMs: Long = 0L
    private var detectedLimitKmh: Int? = null
    private var lockedReferenceSpeedKmh: Int? = null
    private var awaitingPaceChange: Boolean = false

    fun reset(): AutoSpeedLimitState {
        lastTickMs = -1L
        referenceSpeedKmh = null
        stableDurationMs = 0L
        detectedLimitKmh = null
        lockedReferenceSpeedKmh = null
        awaitingPaceChange = false
        return AutoSpeedLimitState()
    }

    fun pauseKeepingDetected(): AutoSpeedLimitState {
        lastTickMs = -1L
        referenceSpeedKmh = null
        stableDurationMs = 0L
        return AutoSpeedLimitState(detectedLimitKmh = detectedLimitKmh)
    }

    fun evaluate(
        speedKmh: Int,
        nowMs: Long,
        totalLockDurationMs: Long = TOTAL_LOCK_MS,
    ): AutoSpeedLimitState {
        val safeTotalMs = totalLockDurationMs.coerceIn(4_000L, 15_000L)
        val countdownPhaseMs = (safeTotalMs - SILENT_PHASE_MS).coerceAtLeast(1_000L)
        val countdownTotalSec = (countdownPhaseMs / 1_000L).toInt().coerceAtLeast(1)

        val dtMs = if (lastTickMs < 0L) 0L else (nowMs - lastTickMs).coerceIn(0L, 1_000L)
        lastTickMs = nowMs

        if (speedKmh <= MIN_MOVING_SPEED_KMH) {
            referenceSpeedKmh = null
            stableDurationMs = 0L
            return AutoSpeedLimitState(
                detectedLimitKmh = detectedLimitKmh,
                countdownTotalSeconds = countdownTotalSec,
            )
        }

        if (awaitingPaceChange) {
            val lockedRef = lockedReferenceSpeedKmh
            val lockedLimit = detectedLimitKmh
            if (lockedRef != null && lockedLimit != null) {
                val outsideTolerance = abs(speedKmh - lockedRef) > STABILITY_TOLERANCE_KMH
                val differentTier = snapToNearestRoadLimit(speedKmh) != lockedLimit
                if (!(outsideTolerance && differentTier)) {
                    return AutoSpeedLimitState(
                        detectedLimitKmh = detectedLimitKmh,
                        countdownTotalSeconds = countdownTotalSec,
                    )
                }
                awaitingPaceChange = false
                lockedReferenceSpeedKmh = null
                referenceSpeedKmh = speedKmh
                stableDurationMs = 0L
                return AutoSpeedLimitState(
                    detectedLimitKmh = detectedLimitKmh,
                    isObserving = true,
                    referenceSpeedKmh = speedKmh,
                    countdownTotalSeconds = countdownTotalSec,
                )
            } else {
                awaitingPaceChange = false
            }
        }

        val ref = referenceSpeedKmh
        if (ref == null) {
            referenceSpeedKmh = speedKmh
            stableDurationMs = 0L
            return AutoSpeedLimitState(
                detectedLimitKmh = detectedLimitKmh,
                isObserving = true,
                referenceSpeedKmh = speedKmh,
                countdownTotalSeconds = countdownTotalSec,
            )
        }

        if (abs(speedKmh - ref) > STABILITY_TOLERANCE_KMH) {
            referenceSpeedKmh = speedKmh
            stableDurationMs = 0L
            return AutoSpeedLimitState(
                detectedLimitKmh = detectedLimitKmh,
                isObserving = true,
                referenceSpeedKmh = speedKmh,
                countdownTotalSeconds = countdownTotalSec,
            )
        }

        stableDurationMs += dtMs

        if (stableDurationMs >= safeTotalMs) {
            val snapped = snapToNearestRoadLimit(speedKmh)
            detectedLimitKmh = snapped
            lockedReferenceSpeedKmh = speedKmh
            awaitingPaceChange = true
            referenceSpeedKmh = null
            stableDurationMs = 0L
            return AutoSpeedLimitState(
                detectedLimitKmh = snapped,
                isObserving = false,
                isCountdownVisible = false,
                countdownSeconds = 0,
                progressFraction = 0f,
                stableDurationMs = 0L,
                referenceSpeedKmh = null,
                countdownTotalSeconds = countdownTotalSec,
            )
        }

        if (stableDurationMs >= SILENT_PHASE_MS) {
            val phase2ElapsedMs = stableDurationMs - SILENT_PHASE_MS
            val countdownSec = ((phase2ElapsedMs / 1_000L).toInt() + 1).coerceIn(1, countdownTotalSec)
            val progress = (phase2ElapsedMs.toFloat() / countdownPhaseMs.toFloat()).coerceIn(0f, 1f)
            return AutoSpeedLimitState(
                detectedLimitKmh = detectedLimitKmh,
                isObserving = true,
                isCountdownVisible = true,
                countdownSeconds = countdownSec,
                progressFraction = progress,
                stableDurationMs = stableDurationMs,
                referenceSpeedKmh = ref,
                countdownTotalSeconds = countdownTotalSec,
            )
        }

        return AutoSpeedLimitState(
            detectedLimitKmh = detectedLimitKmh,
            isObserving = true,
            isCountdownVisible = false,
            countdownSeconds = 0,
            progressFraction = 0f,
            stableDurationMs = stableDurationMs,
            referenceSpeedKmh = ref,
            countdownTotalSeconds = countdownTotalSec,
        )
    }

    companion object {
        const val MIN_MOVING_SPEED_KMH = 10
        const val STABILITY_TOLERANCE_KMH = 2
        const val SILENT_PHASE_MS = 3_000L
        const val COUNTDOWN_PHASE_MS = 5_000L
        const val TOTAL_LOCK_MS = 8_000L
        const val TICK_INTERVAL_MS = 200L

        fun snapToNearestRoadLimit(speedKmh: Int): Int {
            // FRENCH_ROAD_SPEED_LIMITS est trié croissant : minByOrNull retourne le palier inférieur en cas d'équidistance exacte.
            return FRENCH_ROAD_SPEED_LIMITS.minByOrNull { abs(speedKmh - it) } ?: 50
        }
    }
}

data class SpeedGpsState(
    val speedKmh: Int = 0,
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val hasSignalFix: Boolean = false,
    val lastFixTimestampMs: Long = 0L,
    val rawSpeedKmh: Int = 0,
)

data class GpsTripFix(
    val speedKmh: Int,
    val distanceDeltaMeters: Double,
)

/**
 * Moteur de vitesse GPS numérique léger (LocationManager natif sans aucune carte chargée).
 * Calcule aussi les incréments de distance de trajet en filtrant les micro-variations GPS à l'arrêt.
 */
class LocationEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SpeedGpsState())
    val state: StateFlow<SpeedGpsState> = _state.asStateFlow()

    private val autoLimitDetector = AutoSpeedLimitDetector()
    private val _autoSpeedLimitState = MutableStateFlow(AutoSpeedLimitState())
    val autoSpeedLimitState: StateFlow<AutoSpeedLimitState> = _autoSpeedLimitState.asStateFlow()

    private val _tripFixes = MutableSharedFlow<GpsTripFix>(extraBufferCapacity = 16)
    val tripFixes: SharedFlow<GpsTripFix> = _tripFixes.asSharedFlow()

    private var isListening = false
    private var autoLimitEnabled = false
    @Volatile
    private var speedCalibrationOffsetKmh: Int = 0
    @Volatile
    private var autoDetectionTotalMs: Long = AutoSpeedLimitDetector.TOTAL_LOCK_MS
    private var stalenessJob: Job? = null
    private var autoLimitJob: Job? = null
    private var lastDistanceLocation: Location? = null

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            val rawKmh = if (location.hasSpeed()) metersPerSecondToKmh(location.speed) else 0
            val calibratedKmh = applySpeedCalibration(rawKmh, speedCalibrationOffsetKmh)
            val deltaMeters = computeFilteredDistanceDelta(lastDistanceLocation, location, rawKmh)
            if (deltaMeters > 0.0 || lastDistanceLocation == null) {
                lastDistanceLocation = Location(location)
            }
            _tripFixes.tryEmit(GpsTripFix(speedKmh = calibratedKmh, distanceDeltaMeters = deltaMeters))

            _state.update {
                it.copy(
                    speedKmh = calibratedKmh,
                    rawSpeedKmh = rawKmh,
                    hasPermission = true,
                    isGpsEnabled = true,
                    hasSignalFix = true,
                    lastFixTimestampMs = now,
                )
            }
            if (autoLimitEnabled) {
                _autoSpeedLimitState.value = autoLimitDetector.evaluate(
                    speedKmh = calibratedKmh,
                    nowMs = SystemClock.elapsedRealtime(),
                    totalLockDurationMs = autoDetectionTotalMs,
                )
            }
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            refreshState()
        }

        override fun onProviderEnabled(provider: String) {
            refreshState()
        }

        override fun onProviderDisabled(provider: String) {
            lastDistanceLocation = null
            _state.update {
                it.copy(
                    speedKmh = 0,
                    rawSpeedKmh = 0,
                    isGpsEnabled = false,
                    hasSignalFix = false,
                )
            }
            if (autoLimitEnabled) {
                _autoSpeedLimitState.value = autoLimitDetector.pauseKeepingDetected()
            }
        }
    }

    init {
        refreshState()
    }

    fun resetTripAnchor() {
        lastDistanceLocation = null
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun refreshState() {
        val hasPerm = hasLocationPermission()
        val gpsEnabled = runCatching {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }.getOrDefault(false)

        _state.update {
            it.copy(
                hasPermission = hasPerm,
                isGpsEnabled = gpsEnabled,
                speedKmh = if (hasPerm && gpsEnabled) it.speedKmh else 0,
                rawSpeedKmh = if (hasPerm && gpsEnabled) it.rawSpeedKmh else 0,
                hasSignalFix = if (hasPerm && gpsEnabled) it.hasSignalFix else false,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        refreshState()
        if (isListening || !hasLocationPermission()) return
        val lm = locationManager ?: return

        val gpsSupported = runCatching {
            lm.allProviders.contains(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        if (gpsSupported) {
            runCatching {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    0f,
                    gpsListener,
                    Looper.getMainLooper(),
                )
                isListening = true
            }
        }

        startStalenessMonitor()
        if (autoLimitEnabled) {
            startAutoSpeedLimitLoop()
        }
    }

    fun stopUpdates() {
        stalenessJob?.cancel()
        stalenessJob = null
        autoLimitJob?.cancel()
        autoLimitJob = null
        lastDistanceLocation = null
        if (!isListening) return
        runCatching { locationManager?.removeUpdates(gpsListener) }
        isListening = false
    }

    fun setAutoSpeedLimitActive(active: Boolean) {
        autoLimitEnabled = active
        if (active) {
            startAutoSpeedLimitLoop()
        } else {
            autoLimitJob?.cancel()
            autoLimitJob = null
            _autoSpeedLimitState.value = autoLimitDetector.pauseKeepingDetected()
        }
    }

    fun setAutoDetectionSeconds(seconds: Int) {
        val safeSec = seconds.coerceIn(4, 15)
        autoDetectionTotalMs = safeSec * 1_000L
    }

    fun setSpeedCalibrationOffset(offsetKmh: Int) {
        speedCalibrationOffsetKmh = offsetKmh.coerceIn(0, 5)
        _state.update { cur ->
            val updatedSpeed = if (cur.hasPermission && cur.isGpsEnabled && cur.hasSignalFix) {
                applySpeedCalibration(cur.rawSpeedKmh, speedCalibrationOffsetKmh)
            } else {
                0
            }
            cur.copy(speedKmh = updatedSpeed)
        }
    }

    fun resetAutoSpeedLimit() {
        _autoSpeedLimitState.value = autoLimitDetector.reset()
        if (autoLimitEnabled) {
            startAutoSpeedLimitLoop()
        }
    }

    private fun startAutoSpeedLimitLoop() {
        if (autoLimitJob?.isActive == true) return
        autoLimitJob = scope.launch {
            while (isActive && autoLimitEnabled) {
                _autoSpeedLimitState.value = autoLimitDetector.evaluate(
                    speedKmh = _state.value.speedKmh,
                    nowMs = SystemClock.elapsedRealtime(),
                    totalLockDurationMs = autoDetectionTotalMs,
                )
                delay(AutoSpeedLimitDetector.TICK_INTERVAL_MS)
            }
        }
    }

    private fun startStalenessMonitor() {
        stalenessJob?.cancel()
        stalenessJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val current = _state.value
                val ageMs = System.currentTimeMillis() - current.lastFixTimestampMs
                if (current.hasSignalFix && current.lastFixTimestampMs > 0L && ageMs > 6_000L) {
                    _state.update { it.copy(speedKmh = 0, rawSpeedKmh = 0, hasSignalFix = false) }
                    if (autoLimitEnabled) {
                        _autoSpeedLimitState.value = autoLimitDetector.pauseKeepingDetected()
                    }
                }
            }
        }
    }

    fun release() {
        autoLimitEnabled = false
        stopUpdates()
    }

    companion object {
        fun metersPerSecondToKmh(speedMps: Float): Int {
            if (speedMps.isNaN() || speedMps < 0.35f) return 0
            return (speedMps * 3.6f).roundToInt().coerceAtLeast(0)
        }

        fun applySpeedCalibration(rawGpsSpeedKmh: Int, offsetKmh: Int): Int {
            if (rawGpsSpeedKmh <= 2) return 0
            return rawGpsSpeedKmh + offsetKmh.coerceIn(0, 5)
        }

        /**
         * Filtre les micro-variations GPS à l'arrêt :
         * - Ignore si précision > 35 m
         * - Ignore si vitesse < 3 km/h (sauf si le fournisseur ne reporte pas de vitesse et déplacement >= 8 m)
         * - Ignore les dérives < 3 m et les sauts aberrants > 250 m par tick
         */
        fun computeFilteredDistanceDelta(
            previous: Location?,
            current: Location,
            speedKmh: Int,
        ): Double {
            if (previous == null) return 0.0
            if (current.hasAccuracy() && current.accuracy > 35f) return 0.0
            val delta = previous.distanceTo(current)
            if (delta < 3.0f || delta > 250.0f) return 0.0
            if (current.hasSpeed() && speedKmh < 3) return 0.0
            if (!current.hasSpeed() && delta < 8.0f) return 0.0
            return delta.toDouble()
        }
    }
}
