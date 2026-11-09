package ua.butdetal.nespy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager

class KeepAwakeService : Service() {
    private var screenLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE -> {
                releaseScreenLock()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, false).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ENABLE, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                acquireScreenLock()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, true).apply()
            }
        }
        return START_STICKY
    }

    private fun acquireScreenLock() {
        if (screenLock?.isHeld == true) return

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        screenLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
            "NeSpy:ScreenAwake"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseScreenLock() {
        screenLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        screenLock = null
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
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KeepAwakeService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableAction = Notification.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Вимкнути",
            disableIntent
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Не спи увімкнено")
            .setContentText("Екран утримується активним")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(disableAction)
            .build()
    }

    override fun onDestroy() {
        releaseScreenLock()
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
    }
}
