package com.volumebutton

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    var idleOpacity: Float
        get() = prefs.getFloat(KEY_IDLE_OPACITY, DEFAULT_IDLE_OPACITY)
        set(value) = prefs.edit().putFloat(KEY_IDLE_OPACITY, value.coerceIn(0.2f, 1.0f)).apply()

    var autoCollapseDelaySec: Int
        get() = prefs.getInt(KEY_AUTO_COLLAPSE_SEC, DEFAULT_AUTO_COLLAPSE_SEC)
        set(value) = prefs.edit().putInt(KEY_AUTO_COLLAPSE_SEC, value.coerceIn(1, 10)).apply()

    val autoCollapseDelayMs: Long
        get() = autoCollapseDelaySec * 1000L

    var showVolumePopup: Boolean
        get() = prefs.getBoolean(KEY_SHOW_VOLUME_POPUP, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_VOLUME_POPUP, value).apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "floating_volume_prefs"
        const val KEY_START_ON_BOOT = "setting_start_on_boot"
        const val KEY_IDLE_OPACITY = "setting_idle_opacity"
        const val KEY_AUTO_COLLAPSE_SEC = "setting_auto_collapse_sec"
        const val KEY_SHOW_VOLUME_POPUP = "setting_show_volume_popup"
        const val KEY_HAPTIC_FEEDBACK = "setting_haptic_feedback"
        const val KEY_SERVICE_RUNNING = "setting_is_service_running"

        const val DEFAULT_IDLE_OPACITY = 0.85f
        const val DEFAULT_AUTO_COLLAPSE_SEC = 3
    }
}

