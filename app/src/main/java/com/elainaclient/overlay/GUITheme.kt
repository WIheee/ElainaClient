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
    PROTOHAX("Protohax", ClickGuiStyle.Protohax),

    // Ported from OpenMoxel/OpenClickGUI. The first four are re-skinned with Elaina's palette;
    // LIQUIDPE deliberately keeps its original LiquidBounce colours and Minecraft font.
    DREAM("Dream", ClickGuiStyle.Dream),
    HACKAI("HackAI", ClickGuiStyle.HackAi),
    PIANAIXEL("Pianaixel", ClickGuiStyle.Pianaixel),
    PIXELSIGHT("PixelSight", ClickGuiStyle.PixelSight),
    LIQUIDPE("LiquidPE", ClickGuiStyle.LiquidPe);

    companion object {
        const val PREF_KEY = "gui_theme"

        fun fromPref(context: Context): GUITheme {
            val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(PREF_KEY, CLASSIC.name)
            return runCatching { valueOf(raw!!) }.getOrDefault(CLASSIC)
        }
    }
}
