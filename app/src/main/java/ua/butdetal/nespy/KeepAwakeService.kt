package ua.butdetal.nespy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.provider.Settings

class KeepAwakeService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE -> {
                restoreScreenTimeout()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, false).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ENABLE, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                applyLongScreenTimeout()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, true).apply()
            }
        }
        return START_STICKY
    }

    private fun applyLongScreenTimeout() {
        if (!Settings.System.canWrite(this)) return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        try {
            if (!prefs.getBoolean(KEY_TIMEOUT_SAVED, false)) {
                val previous = Settings.System.getInt(
                    contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    DEFAULT_TIMEOUT
                )
                prefs.edit().putInt(KEY_PREVIOUS_TIMEOUT, previous)
                    .putBoolean(KEY_TIMEOUT_SAVED, true).apply()
            }
            // Extend the inactivity timeout while the user has enabled this mode.
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                LONG_TIMEOUT
            )
        } catch (_: Exception) {
            // The app remains active; the system setting may be restricted by the device.
        }
    }

    private fun restoreScreenTimeout() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_TIMEOUT_SAVED, false)) return
        if (Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    prefs.getInt(KEY_PREVIOUS_TIMEOUT, DEFAULT_TIMEOUT)
                )
            } catch (_: Exception) {
                // Leave the saved value available so it can be restored after permission is returned.
                return
            }
        }
        prefs.edit().remove(KEY_PREVIOUS_TIMEOUT).putBoolean(KEY_TIMEOUT_SAVED, false).apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Не спи — активний режим",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Сповіщення про активний режим утримання екрана"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = PendingIntent.getService(
            this, 1, Intent(this, KeepAwakeService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause),
            "Вимкнути",
            disableIntent
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Не спи увімкнено")
            .setContentText("Час автовимкнення екрана тимчасово збільшено")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(disableAction)
            .build()
    }

    override fun onDestroy() {
        // Do not restore here: Android may recreate a sticky foreground service.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ENABLE = "ua.butdetal.nespy.action.ENABLE"
        const val ACTION_DISABLE = "ua.butdetal.nespy.action.DISABLE"
        private const val CHANNEL_ID = "nespy_awake"
        private const val NOTIFICATION_ID = 101
        private const val PREFS_NAME = "nespy_settings"
        private const val KEY_ENABLED = "keep_awake_enabled"
        private const val KEY_TIMEOUT_SAVED = "timeout_saved"
        private const val KEY_PREVIOUS_TIMEOUT = "previous_screen_timeout"
        private const val DEFAULT_TIMEOUT = 30000
        private const val LONG_TIMEOUT = 86400000
    }
}
