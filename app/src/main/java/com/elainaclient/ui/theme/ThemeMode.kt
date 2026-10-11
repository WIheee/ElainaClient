package com.elainaclient.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User-selectable appearance mode. Persisted in `SharedPreferences("settings")`. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME = "settings"
private const val KEY_THEME_MODE = "theme_mode"

/**
 * Holds the current [ThemeMode] as Compose state so the whole app recomposes when it changes.
 * `MainActivity` loads it on start; the settings page updates it via [set].
 */
object AppTheme {

    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        mode = readMode(context)
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, newMode.name)
            .apply()
    }

    private fun readMode(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    /** Resolves whether the dark scheme should be used, honouring the manual choice. */
    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
