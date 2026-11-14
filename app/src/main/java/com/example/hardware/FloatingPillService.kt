package com.example.hardware

import android.annotation.SuppressLint
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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/**
 * Pastille flottante déplaçable par-dessus les autres applications (compatible Android Auto / Waze) :
 * - Appui simple : bascule le filtre d'atténuation nocturne global.
 * - Appui long : ramène directement sur AutoPulse.
 */
class FloatingPillService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var pillView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var observerJob: Job? = null
    private var isNightActive: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ensureNotificationChannel()
        startForegroundCompat()
        observeNightState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ScreenDimmerService.isOverlayPermissionGranted(this) || intent?.action == ACTION_STOP) {
            removePillView()
            stopSelf()
            return START_NOT_STICKY
        }
        ensurePillView()
        return START_STICKY
    }

    private fun observeNightState() {
        observerJob?.cancel()
        val repo = PreferencesRepository(applicationContext)
        observerJob = serviceScope.launch {
            repo.settingsFlow.collect { settings ->
                if (!settings.floatingPillEnabled) {
                    removePillView()
                    stopSelf()
                    return@collect
                }
                isNightActive = PreferencesRepository.computeEffectiveDimmerAlpha(
                    settings = settings,
                    nowMinutes = currentTimeMinutes(),
                ) > 0.005f
                updatePillAppearance()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensurePillView() {
        if (pillView != null) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val sizePx = (52f * density).toInt().coerceAtLeast(96)
        val padPx = (12f * density).toInt()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        val params = buildPillLayoutParams(sizePx, density)
        val view = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setPadding(padPx, padPx, padPx, padPx)
            contentDescription = "Pastille rapide AutoPulse"
            elevation = 8f * density
        }

        var initialX = params.x
        var initialY = params.y
        var downRawX = 0f
        var downRawY = 0f
        var moved = false
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openAutoPulseHome()
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                    longPressTriggered = false
                    mainHandler.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (moved) {
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = (initialY + dy).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered) {
                        toggleNightFilter()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }

                else -> false
            }
        }

        runCatching {
            wm.addView(view, params)
            pillView = view
            layoutParams = params
            updatePillAppearance()
        }
    }

    private fun updatePillAppearance() {
        val view = pillView ?: return
        val density = resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E614171D"))
            setStroke(
                (2f * density).toInt().coerceAtLeast(2),
                if (isNightActive) Color.parseColor("#FFFF2A3C") else Color.parseColor("#FF4A5160"),
            )
        }
        view.background = bg
        view.setColorFilter(
            if (isNightActive) Color.parseColor("#FFFF6B7A") else Color.WHITE,
        )
    }

    private fun toggleNightFilter() {
        val repo = PreferencesRepository(applicationContext)
        serviceScope.launch {
            repo.toggleQuickNightDimmer(currentTimeMinutes())
        }
    }

    private fun openAutoPulseHome() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun buildPillLayoutParams(sizePx: Int, density: Float): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24f * density).toInt()
            y = (110f * density).toInt()
        }
    }

    private fun removePillView() {
        mainHandler.removeCallbacksAndMessages(null)
        val view = pillView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        pillView = null
        layoutParams = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pastille flottante rapide",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Raccourci flottant AutoPulse au-dessus d'Android Auto"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun startForegroundCompat() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AutoPulse • Pastille rapide")
            .setContentText("Appui : mode nuit • Appui long : retour AutoPulse")
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
        observerJob?.cancel()
        removePillView()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "autopulse_floating_pill"
        private const val NOTIFICATION_ID = 4202
        private const val ACTION_STOP = "com.example.hardware.ACTION_STOP_PILL"

        fun sync(context: Context, enabled: Boolean) {
            if (!enabled || !ScreenDimmerService.isOverlayPermissionGranted(context)) {
                runCatching { context.stopService(Intent(context, FloatingPillService::class.java)) }
                return
            }
            val intent = Intent(context, FloatingPillService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        private fun currentTimeMinutes(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }
}
