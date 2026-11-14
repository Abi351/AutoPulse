package com.example.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Récepteur BOOT_COMPLETED / QUICKBOOT_POWERON (ACC) :
 * - Relance immédiatement ScreenDimmerService si le mode nuit ou une atténuation > 0 % est active.
 * - Attend 3 secondes après le démarrage pour relancer la musique et/ou ouvrir l'application favorite.
 */
class BootAndWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = PreferencesRepository(appContext).settingsFlow.first()

                // Relance automatique du filtre nocturne global et de la pastille au démarrage si actifs
                ScreenDimmerService.syncFromSettings(appContext, settings)
                FloatingPillService.sync(appContext, settings.floatingPillEnabled)

                if (!settings.autoResumeMusicEnabled && settings.autoLaunchPackageName.isBlank()) {
                    return@launch
                }

                delay(3_000L)

                if (settings.autoResumeMusicEnabled) {
                    dispatchMediaPlay(appContext)
                }

                val targetPkg = settings.autoLaunchPackageName
                if (targetPkg.isNotBlank()) {
                    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        runCatching { appContext.startActivity(launchIntent) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun dispatchMediaPlay(context: Context) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            runCatching {
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        }
    }
}
