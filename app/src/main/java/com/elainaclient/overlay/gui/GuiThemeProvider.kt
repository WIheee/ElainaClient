package com.elainaclient.overlay.gui

import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.OverlayClickGUI

/** Builds the click-GUI window for a theme. All themes share the classic layout, re-skinned by
 *  [GUITheme.style] (see [com.elainaclient.game.ClickGuiStyle]). */
fun GUITheme.createClickGUI(): OverlayWindow = OverlayClickGUI(this)
