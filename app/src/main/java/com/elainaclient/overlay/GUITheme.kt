package com.elainaclient.overlay

import android.content.Context
import com.elainaclient.game.ClickGuiStyle

/**
 * Selectable click-GUI themes. The pref key/names are unchanged from the original single-theme
 * build (`CLASSIC`), so existing installs keep their choice.
 */
enum class GUITheme(
    val displayName: String,
    val style: ClickGuiStyle
) {
    CLASSIC("经典", ClickGuiStyle.Classic),
    KITSU("Kitsu", ClickGuiStyle.Kitsu),
    GRACE("Grace", ClickGuiStyle.Grace),
    PROTOHAX("Protohax", ClickGuiStyle.Protohax);

    companion object {
        const val PREF_KEY = "gui_theme"

        fun fromPref(context: Context): GUITheme {
            val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(PREF_KEY, CLASSIC.name)
            return runCatching { valueOf(raw!!) }.getOrDefault(CLASSIC)
        }
    }
}
