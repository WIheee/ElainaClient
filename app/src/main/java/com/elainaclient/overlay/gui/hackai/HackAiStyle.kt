package com.elainaclient.overlay.gui.hackai

/**
 * Colours for the HackAI click GUI.
 *
 * The original project is a light Material 3 panel; this port keeps its structure (820x500 dp
 * panel, 48 dp top bar, 200 dp sidebar, expandable module cards) but uses Elaina's dark violet
 * palette. Geometry below mirrors `HackAI/res/layout/layout_clickgui.xml` and its item layouts.
 */
object HackAiStyle {

    // Panel geometry (dp, from layout_clickgui.xml).
    const val PANEL_WIDTH_DP = 820f
    const val PANEL_HEIGHT_DP = 500f
    const val TOP_BAR_HEIGHT_DP = 48f
    const val SIDEBAR_WIDTH_DP = 200f
    const val CATEGORY_HEIGHT_DP = 44f
    const val CATEGORY_GAP_DP = 6f
    const val CARD_RADIUS_DP = 8f
    const val PANEL_RADIUS_DP = 12f

    // Palette (ClickGuiStyle.HackAi).
    val accent = 0xFFC77DFF.toInt()
    val accentSecondary = 0xFF9D4EDD.toInt()
    val accentDeep = 0xFF5A189A.toInt()
    val background = 0xFF171226.toInt()
    val sidebarBackground = 0xFF16112B.toInt()
    val card = 0xFF201A35.toInt()
    val cardActive = 0xFF2A2148.toInt()
    val chip = 0xFF2A2148.toInt()
    val searchBackground = 0xFF201A35.toInt()
    val border = 0xFF2A2140.toInt()
    val text = 0xFFF2EEFA.toInt()
    val secondary = 0xFFA99BC8.toInt()
    val muted = 0xFF7C6D9C.toInt()
    val switchOff = 0xFF3A3154.toInt()
    val onSelected = 0xFFFFFFFF.toInt()
    val separator = 0x33A99BC8
    val scrim = 0x99000000.toInt()
}
