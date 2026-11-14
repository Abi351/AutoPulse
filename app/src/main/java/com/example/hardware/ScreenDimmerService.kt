package com.example.hardware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.HeadUnitSettings
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Service système global affichant un filtre d'atténuation nocturne non-bloquant
 * par-dessus toutes les applications (Android Auto, Waze, Google Maps, etc.).
 * Se retire automatiquement du WindowManager et s'arrête à 0 % pour consommer 0 ressource.
 */
class ScreenDimmerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var autoObserverJob: Job? = null
    private var currentAlpha: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ensureNotificationChannel()
        startForegroundCompat()
        observeSettingsAndSchedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isOverlayPermissionGranted(this)) {
            removeOverlayView()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            removeOverlayView()
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedAlpha = intent?.getFloatExtra(EXTRA_ALPHA, currentAlpha) ?: currentAlpha
        applyAlpha(requestedAlpha)
        return START_STICKY
    }

    private fun observeSettingsAndSchedule() {
        autoObserverJob?.cancel()
        val repo = PreferencesRepository(applicationContext)
        autoObserverJob = serviceScope.launch {
            launch {
                repo.settingsFlow.collect { settings ->
                    val effective = PreferencesRepository.computeEffectiveDimmerAlpha(
                        settings = settings,
                        nowMinutes = currentTimeMinutes(),
                    )
                    applyAlpha(effective)
                }
            }
            while (isActive) {
                delay(30_000L)
                val settings = repo.settingsFlow.first()
                if (settings.autoNightModeEnabled) {
                    val effective = PreferencesRepository.computeEffectiveDimmerAlpha(
                        settings = settings,
                        nowMinutes = currentTimeMinutes(),
                    )
                    applyAlpha(effective)
                }
            }
        }
    }

    private fun applyAlpha(rawAlpha: Float) {
        val clamped = rawAlpha.coerceIn(0.0f, MAX_DIMMER_ALPHA)
        currentAlpha = clamped

        if (clamped <= 0.005f || !isOverlayPermissionGranted(this)) {
            removeOverlayView()
            stopSelf()
            return
        }

        val wm = windowManager ?: return
        val existingView = overlayView
        if (existingView == null) {
            val view = View(this).apply {
                setBackgroundColor(Color.BLACK)
                alpha = clamped
            }
            val params = buildOverlayLayoutParams()
            runCatching {
                wm.addView(view, params)
                overlayView = view
                layoutParams = params
            }
        } else {
            existingView.alpha = clamped
        }
    }

    private fun buildOverlayLayoutParams(): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun removeOverlayView() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        overlayView = null
        layoutParams = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Filtre nocturne global",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Maintient le filtre d'atténuation nocturne par-dessus les applications autoradio"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun startForegroundCompat() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AutoPulse • Mode nuit actif")
            .setContentText("Filtre d'atténuation global actif (tactile libre)")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        autoObserverJob?.cancel()
        removeOverlayView()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val MAX_DIMMER_ALPHA = 0.85f
        private const val CHANNEL_ID = "autopulse_night_dimmer"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_ALPHA = "extra_alpha"
        private const val ACTION_STOP = "com.example.hardware.ACTION_STOP_DIMMER"

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        }

        fun openOverlayPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        fun syncDimmer(context: Context, alpha: Float) {
            val clamped = alpha.coerceIn(0f, MAX_DIMMER_ALPHA)
            if (clamped <= 0.005f || !isOverlayPermissionGranted(context)) {
                runCatching { context.stopService(Intent(context, ScreenDimmerService::class.java)) }
                return
            }

            val intent = Intent(context, ScreenDimmerService::class.java).apply {
                putExtra(EXTRA_ALPHA, clamped)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun syncFromSettings(context: Context, settings: HeadUnitSettings) {
            val alpha = PreferencesRepository.computeEffectiveDimmerAlpha(
                settings = settings,
                nowMinutes = currentTimeMinutes(),
            )
            syncDimmer(context, alpha)
        }

        private fun currentTimeMinutes(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }
}
