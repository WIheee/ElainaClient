package com.elainaclient.overlay.gui.dream

import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.Value

/**
 * Mutable view state shared by [DreamOverlayView] (input/animation) and [DreamRenderer] (drawing).
 *
 * The data itself is not copied: [DreamBridge] reads Elaina's [Module]/[Value] objects directly,
 * so toggles and settings always show the live state.
 */
class DreamUiState {
    var category: ModuleCategory = ModuleCategory.Combat
    var selectedModule: Module? = null

    /** Scroll offsets in reference pixels, clamped by the renderer. */
    var listScroll = 0f
    var detailScroll = 0f

    /** Category/detail transition animation. */
    var contentAlpha = 1f
    var contentTranslation = 0f

    /** Open enum/list picker, if any. */
    var popup: DreamOptionPopup? = null

    /** Module search, typed through the transparent Compose field in DreamGuiWindow. */
    var searchActive = false
    var query = ""
}

/** An open option picker anchored to a settings row (enum or list value). */
class DreamOptionPopup(
    val value: Value<*>,
    val options: List<Any>,
    val selectedIndex: Int,
    /** Bottom edge of the row that opened the popup (reference px). */
    val anchorTop: Float,
)
