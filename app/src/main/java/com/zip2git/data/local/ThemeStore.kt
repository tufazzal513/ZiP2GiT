package com.zip2git.data.local

import android.content.Context

/** Persists the light/dark preference under `zip2git_theme_prefs`. */
class ThemeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("zip2git_theme_prefs", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    fun toggle(): Boolean {
        darkMode = !darkMode
        return darkMode
    }

    private companion object {
        const val KEY_DARK = "dark_mode"
    }
}
