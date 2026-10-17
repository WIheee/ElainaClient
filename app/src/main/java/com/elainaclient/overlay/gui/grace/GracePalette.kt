package com.elainaclient.overlay.gui.grace

import androidx.compose.ui.graphics.Color

/**
 * Grace palette. The layout is ported from LuminaCN-2.1.0, but the colours are re-skinned to
 * Elaina's violet/lavender identity (matching `ClickGuiStyle.Grace`) instead of the source's
 * blue/cyan/pink.
 */
internal object GracePalette {
    // elevatedCard
    val Card = Color(0xFF171226)
    val BorderA = Color(0xFF9D4EDD)
    val BorderB = Color(0xFFE0C3FC)
    val ModuleEnabledA = Color(0xFF9D4EDD)
    val ModuleEnabledB = Color(0xFFC77DFF)
    val ModuleDisabled = Color(0xFF221A33)

    // moduleSettingScreen
    val Screen = Color(0xFF1E1730)
    val ScreenText = Color(0xFFEDE7F6)

    // navigationRailItem
    val NavSelectedA = Color(0xFF7B2CBF)
    val NavSelectedB = Color(0xFFC77DFF)
    val NavSelectedC = Color(0xFFE0C3FC)
    val NavRail = Color(0xFF0E0A14)

    val Text = Color(0xFFEDE7F6)
}
