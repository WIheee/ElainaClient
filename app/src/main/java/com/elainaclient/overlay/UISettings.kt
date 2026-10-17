package com.elainaclient.overlay

import android.content.Context
import androidx.core.content.edit

/**
 * Per-theme click-GUI scale, edited on the dedicated 界面 page.
 *
 * Every full-screen click GUI reads its scale here when it is created, so changing a value takes
 * effect the next time the GUI is opened (or immediately when [OverlayManager.applyTheme] rebuilds
 * the windows while the overlay service is running).
 */
object UISettings {

    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.6f

    private const val PREFS = "settings"

    private fun key(theme: GUITheme) = "ui_scale_${theme.name}"

    fun defaultScale(theme: GUITheme): Float = when (theme) {
        // The original LiquidPE frame is a 2940x1837 canvas; a small default boost makes it fill
        // more of a phone screen without cropping the outermost panels.
        GUITheme.LIQUIDPE -> 1.15f
        else -> 1f
    }

    fun scale(context: Context, theme: GUITheme): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(key(theme), defaultScale(theme))
            .coerceIn(MIN_SCALE, MAX_SCALE)

    fun setScale(context: Context, theme: GUITheme, scale: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putFloat(key(theme), scale.coerceIn(MIN_SCALE, MAX_SCALE))
        }
    }

    fun applyAll(context: Context) {
        OverlayManager.applyTheme()
    }
}
