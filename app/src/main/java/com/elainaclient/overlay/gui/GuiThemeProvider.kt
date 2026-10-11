package com.elainaclient.overlay.gui

import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.OverlayClickGUI
import com.elainaclient.overlay.gui.kitsu.KitsuGUI

/**
 * Builds the click-GUI window for a theme.
 *
 * `KITSU` is a distinct layout ([KitsuGUI]); the others currently share the classic layout
 * re-skinned by [GUITheme.style].
 */
fun GUITheme.createClickGUI(): OverlayWindow = when (this) {
    GUITheme.KITSU -> KitsuGUI()
    else -> OverlayClickGUI(this)
}
