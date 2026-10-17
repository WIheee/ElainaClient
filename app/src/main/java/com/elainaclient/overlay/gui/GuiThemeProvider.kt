package com.elainaclient.overlay.gui

import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.OverlayClickGUI
import com.elainaclient.overlay.gui.grace.GraceGUI
import com.elainaclient.overlay.gui.kitsu.KitsuGUI
import com.elainaclient.overlay.gui.protohax.ProtohaxGUI

/**
 * Builds the click-GUI window for a theme.
 *
 * `KITSU`, `GRACE` and `PROTOHAX` each have their own layout (ported from LuminaCN-2.1.0);
 * `CLASSIC` is the shared classic layout re-skinned by [GUITheme.style].
 */
fun GUITheme.createClickGUI(): OverlayWindow = when (this) {
    GUITheme.KITSU -> KitsuGUI()
    GUITheme.GRACE -> GraceGUI()
    GUITheme.PROTOHAX -> ProtohaxGUI()
    GUITheme.CLASSIC -> OverlayClickGUI(this)
}
