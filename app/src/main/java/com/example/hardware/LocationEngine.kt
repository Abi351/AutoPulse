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
import kotlin.math.roundToInt

data class SpeedGpsState(
    val speedKmh: Int = 0,
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val hasSignalFix: Boolean = false,
    val lastFixTimestampMs: Long = 0L,
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

    private val _tripFixes = MutableSharedFlow<GpsTripFix>(extraBufferCapacity = 16)
    val tripFixes: SharedFlow<GpsTripFix> = _tripFixes.asSharedFlow()

    private var isListening = false
    private var stalenessJob: Job? = null
    private var lastDistanceLocation: Location? = null

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            val kmh = if (location.hasSpeed()) metersPerSecondToKmh(location.speed) else 0
            val deltaMeters = computeFilteredDistanceDelta(lastDistanceLocation, location, kmh)
            if (deltaMeters > 0.0 || lastDistanceLocation == null) {
                lastDistanceLocation = Location(location)
            }
            _tripFixes.tryEmit(GpsTripFix(speedKmh = kmh, distanceDeltaMeters = deltaMeters))

            _state.update {
                it.copy(
                    speedKmh = kmh,
                    hasPermission = true,
                    isGpsEnabled = true,
                    hasSignalFix = true,
                    lastFixTimestampMs = now,
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
                    isGpsEnabled = false,
                    hasSignalFix = false,
                )
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
    }

    fun stopUpdates() {
        stalenessJob?.cancel()
        stalenessJob = null
        lastDistanceLocation = null
        if (!isListening) return
        runCatching { locationManager?.removeUpdates(gpsListener) }
        isListening = false
    }

    private fun startStalenessMonitor() {
        stalenessJob?.cancel()
        stalenessJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val current = _state.value
                val ageMs = System.currentTimeMillis() - current.lastFixTimestampMs
                if (current.hasSignalFix && current.lastFixTimestampMs > 0L && ageMs > 6_000L) {
                    _state.update { it.copy(speedKmh = 0, hasSignalFix = false) }
                }
            }
        }
    }

    fun release() {
        stopUpdates()
    }

    companion object {
        fun metersPerSecondToKmh(speedMps: Float): Int {
            if (speedMps.isNaN() || speedMps < 0.35f) return 0
            return (speedMps * 3.6f).roundToInt().coerceAtLeast(0)
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
