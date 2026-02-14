package com.google.mediapipe.examples.llminference.settings

import android.content.Context
import android.content.SharedPreferences
import com.google.mediapipe.examples.llminference.ui.theme.AppTheme
import com.google.mediapipe.examples.llminference.ui.theme.ThemeManager

/**
 * Central preferences for all app settings.
 * Persists theme, PIN, energy mode, backend, doctor/location details.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "medgemma_prefs", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_PIN = "user_pin"
        private const val KEY_PIN_SET = "pin_is_set"
        private const val KEY_ENERGY_MODE = "energy_mode"
        private const val KEY_BACKEND = "backend_mode"
        private const val KEY_DOCTOR_NAME = "doctor_name"
        private const val KEY_DOCTOR_SPECIALTY = "doctor_specialty"
        private const val KEY_LOCATION = "location"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    // ── Theme ──
    var theme: AppTheme
        get() {
            val name = prefs.getString(KEY_THEME, AppTheme.PURPLISH_BLUE.name)
            return try { AppTheme.valueOf(name!!) } catch (_: Exception) { AppTheme.PURPLISH_BLUE }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
            ThemeManager.currentTheme = value
        }

    // ── PIN ──
    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var isPinSet: Boolean
        get() = prefs.getBoolean(KEY_PIN_SET, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_SET, value).apply()

    // ── Energy Mode ──
    var energyMode: String
        get() = prefs.getString(KEY_ENERGY_MODE, "Medium") ?: "Medium"
        set(value) = prefs.edit().putString(KEY_ENERGY_MODE, value).apply()

    // ── Backend Mode ──
    var backendMode: String
        get() = prefs.getString(KEY_BACKEND, "Auto") ?: "Auto"
        set(value) = prefs.edit().putString(KEY_BACKEND, value).apply()

    // ── Doctor Details ──
    var doctorName: String
        get() = prefs.getString(KEY_DOCTOR_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DOCTOR_NAME, value).apply()

    var doctorSpecialty: String
        get() = prefs.getString(KEY_DOCTOR_SPECIALTY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DOCTOR_SPECIALTY, value).apply()

    // ── Location ──
    var location: String
        get() = prefs.getString(KEY_LOCATION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCATION, value).apply()

    // ── First Launch ──
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    /**
     * Initialize theme from saved preference on app start
     */
    fun initTheme() {
        ThemeManager.currentTheme = theme
    }
}
