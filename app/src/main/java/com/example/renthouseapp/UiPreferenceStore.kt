package com.example.renthouseapp

import android.content.Context

class UiPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isSeniorModeEnabled(): Boolean = preferences.getBoolean(KEY_SENIOR_MODE, false)

    fun setSeniorModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SENIOR_MODE, enabled).apply()
    }

    private companion object {
        const val PREF_NAME = "rent_house_ui_prefs"
        const val KEY_SENIOR_MODE = "senior_mode_enabled"
    }
}
