package ua.butdetal.nespy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var disableButton: Button

    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        requestNotificationPermissionIfNeeded()
        if (preferences.getBoolean(KEY_ENABLED, false)) startKeepAwakeService()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (preferences.getBoolean(KEY_PENDING_ENABLE, false) && Settings.System.canWrite(this)) {
            preferences.edit().putBoolean(KEY_PENDING_ENABLE, false).apply()
            enableKeepAwake()
        }
        refreshStatus()
    }

    private fun buildInterface() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
        }
        val title = TextView(this).apply {
            text = "НЕ СПИ"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }
        enableButton = Button(this).apply {
            text = "УВІМКНУТИ"
            setOnClickListener { enableKeepAwake() }
        }
        disableButton = Button(this).apply {
            text = "ВИМКНУТИ"
            setOnClickListener { disableKeepAwake() }
        }
        val hint = TextView(this).apply {
            text = "Для роботи у фоні Android попросить дозвіл змінювати час вимкнення екрана. Після вимкнення режиму попереднє значення буде відновлено."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(enableButton)
        layout.addView(disableButton)
        layout.addView(hint)
        setContentView(layout)
    }

    private fun enableKeepAwake() {
        if (!Settings.System.canWrite(this)) {
            preferences.edit().putBoolean(KEY_PENDING_ENABLE, true).apply()
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        preferences.edit().putBoolean(KEY_ENABLED, true).apply()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startKeepAwakeService()
        refreshStatus()
    }

    private fun startKeepAwakeService() {
        val intent = Intent(this, KeepAwakeService::class.java).setAction(KeepAwakeService.ACTION_ENABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun disableKeepAwake() {
        preferences.edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_PENDING_ENABLE, false)
            .apply()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopService(Intent(this, KeepAwakeService::class.java))
        refreshStatus()
    }

    private fun refreshStatus() {
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        if (::status.isInitialized) {
            status.text = if (enabled) "РЕЖИМ УВІМКНЕНО\nАвтовимкнення екрана відкладено" else "РЕЖИМ ВИМКНЕНО"
        }
        if (::enableButton.isInitialized) enableButton.isEnabled = !enabled
        if (::disableButton.isInitialized) disableButton.isEnabled = enabled
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    companion object {
        private const val PREFS_NAME = "nespy_settings"
        private const val KEY_ENABLED = "keep_awake_enabled"
        private const val KEY_PENDING_ENABLE = "pending_enable"
        private const val REQUEST_NOTIFICATIONS = 100
    }
}
