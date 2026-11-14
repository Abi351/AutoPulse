package com.example.hardware

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Service NotificationListener requis par Android pour accéder à MediaSessionManager
 * et détecter Spotify, YouTube Music, Deezer, Poweramp, Radio, etc.
 */
class AutoPulseNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        UniversalMediaSessionHolder.notifyListenerChanged(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        UniversalMediaSessionHolder.notifyListenerChanged(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Aucun traitement lourd des notifications ; les sessions média sont écoutées via MediaSessionManager
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Aucun traitement lourd requis
    }
}
