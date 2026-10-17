package com.elainaclient.overlay.gui.dream

/**
 * Dream click GUI style.
 *
 * Geometry is copied verbatim from OpenMoxel/OpenClickGUI's `Dream/.../ClickGuiView.java`, which
 * expresses the whole panel in a 1411x820 reference frame. Colours are Elaina's violet palette
 * instead of the original blue-grey/magenta scheme (the user's chosen direction for this port).
 */
object DreamStyle {

    const val REFERENCE_WIDTH = 1411f
    const val REFERENCE_HEIGHT = 820f

    // --- Geometry (reference px) ---
    const val SIDE_WIDTH = 343f
    const val CONTENT_LEFT = 365f
    const val CONTENT_RIGHT = 1384f
    const val TOP_BAR_HEIGHT = 100f

    /** Top of the scrollable content area (below the top bar). */
    const val CONTENT_TOP = 108f
    /** Bottom of the scrollable content area / panel content. */
    const val CONTENT_BOTTOM = 806f

    const val CARD_STEP = 117f
    const val CARD_HEIGHT = 106f

    /** Detail header card: CONTENT_TOP..CONTENT_TOP+CARD_HEIGHT. */
    const val DETAIL_HEADER_HEIGHT = 89f
    /** First scrollable detail row (the shortcut row). */
    const val DETAIL_ROWS_TOP = 205f
    /** Settings rows start below the shortcut row. */
    const val DETAIL_SETTINGS_TOP = 302f
    const val ROW_STEP = 97f
    const val ROW_HEIGHT = 89f

    const val SLIDER_START = 487f
    const val SLIDER_END = 1286f

    const val OPTION_LEFT = 1080f
    const val OPTION_RIGHT = 1368f
    const val SWITCH_CENTER_X = 1327f
    const val POPUP_ITEM_HEIGHT = 46f

    // --- Elaina violet palette (matches ClickGuiStyle.Dream / GracePalette) ---
    val COLOR_TOP = 0xFF1B1632.toInt()
    val COLOR_BODY = 0xFF171226.toInt()
    val COLOR_SIDE = 0xFF1A1530.toInt()
    val COLOR_CARD = 0xFF221B3A.toInt()
    val COLOR_CARD_ACTIVE = 0xFF2B2148.toInt()
    val COLOR_SELECTED = 0xFF2B2148.toInt()
    val COLOR_TEXT = 0xFFF0EAFA.toInt()
    val COLOR_SECONDARY = 0xFFB39DDB.toInt()
    val COLOR_OFF_TRACK = 0xFF3A3055.toInt()
    val COLOR_OFF_KNOB = 0xFF7E6DA3.toInt()
    val COLOR_TRACK_EMPTY = 0xFF24263C.toInt()
    val COLOR_KNOB = 0xFFE5E0F5.toInt()
    val COLOR_ACCENT_START = 0xFF9D4EDD.toInt()
    val COLOR_ACCENT_END = 0xFFC77DFF.toInt()
    val COLOR_ACCENT_DEEP = 0xFF5A189A.toInt()
    val COLOR_BORDER = 0x558984B0
    val COLOR_DIVIDER = 0x3EAAA4B7
    val COLOR_BACK_BG = 0xFF303458.toInt()
    val COLOR_POPUP_BG = 0xFF2E3151.toInt()
    val COLOR_SCRIM = 0x66000000

    const val CORNER_PANEL = 22f
    const val CORNER_CARD = 13f
    const val CORNER_SMALL = 10f
}
