package com.elainaclient.overlay

import android.content.Context
import com.elainaclient.game.ClickGuiStyle

/**
 * Selectable click-GUI themes. The pref key/names are unchanged from the original single-theme
 * build (`CLASSIC`), so existing installs keep their choice.
 */
enum class GUITheme(
    val displayName: String,
    val style: ClickGuiStyle,
    /**
     * False while a theme still runs on the shared Compose approximation. Ported themes are listed
     * first in the pickers; pending ones stay at the end and cannot be selected yet.
     */
    val ported: Boolean = true,
) {
    CLASSIC("经典", ClickGuiStyle.Classic),
    KITSU("Kitsu", ClickGuiStyle.Kitsu),
    GRACE("Grace", ClickGuiStyle.Grace),
    PROTOHAX("Protohax", ClickGuiStyle.Protohax),

    // Native Kotlin ports of the OpenClickGUI themes. DREAM/HACKAI are custom rewrites;
    // LIQUIDPE deliberately keeps its original LiquidBounce colours and Minecraft font.
    DREAM("Dream", ClickGuiStyle.Dream),
    HACKAI("HackAI", ClickGuiStyle.HackAi),
    LIQUIDPE("LiquidPE", ClickGuiStyle.LiquidPe),

    // Still on the shared OpenGuiWindow Compose shell; native ports are pending.
    PIANAIXEL("Pianaixel", ClickGuiStyle.Pianaixel, ported = false),
    PIXELSIGHT("PixelSight", ClickGuiStyle.PixelSight, ported = false);

    companion object {
        const val PREF_KEY = "gui_theme"

        fun fromPref(context: Context): GUITheme {
            val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(PREF_KEY, CLASSIC.name)
            return runCatching { valueOf(raw!!) }.getOrDefault(CLASSIC)
        }
    }
}
