package com.example.gigpoint

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(
        "dhwani_mitra_preferences",
        Context.MODE_PRIVATE
    )

    fun language(): String =
        prefs.getString("language", "en") ?: "en"

    fun theme(): String =
        prefs.getString("theme", "system") ?: "system"

    fun setLanguage(value: String) {
        prefs.edit().putString("language", value).apply()
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(value)
        )
    }

    fun setTheme(value: String) {
        prefs.edit().putString("theme", value).apply()

        val mode = when (value) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun apply() {
        setTheme(theme())
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language())
        )
    }
}
