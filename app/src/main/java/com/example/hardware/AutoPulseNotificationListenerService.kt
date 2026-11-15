package com.example.hardware

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Service NotificationListener requis par Android pour accéder à MediaSessionManager
 * et détecter Spotify, YouTube Music, Deezer, Poweramp ou les applications Radio FM autoradio.
 */
class AutoPulseNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        UniversalMediaSessionHolder.notifyListenerChanged(applicationContext)
        runCatching {
            activeNotifications?.forEach { inspectNotification(it) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        UniversalMediaSessionHolder.notifyListenerChanged(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        inspectNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != applicationContext.packageName &&
            UniversalMediaManager.isRadioPackage(sbn.packageName)
        ) {
            UniversalMediaSessionHolder.clearRadioNotification(sbn.packageName)
        }
    }

    private fun inspectNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val looksLikeRadio = UniversalMediaManager.isRadioPackage(pkg) ||
            title?.contains("MHz", ignoreCase = true) == true ||
            text?.contains("MHz", ignoreCase = true) == true ||
            title?.contains("Radio FM", ignoreCase = true) == true
        if (!looksLikeRadio) return

        UniversalMediaSessionHolder.updateRadioNotification(
            packageName = pkg,
            title = title,
            text = text,
            subText = subText,
        )
    }
}
