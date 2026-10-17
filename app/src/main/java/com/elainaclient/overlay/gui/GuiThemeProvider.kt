package com.elainaclient.overlay.gui

import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.OverlayClickGUI
import com.elainaclient.overlay.gui.grace.GraceGUI
import com.elainaclient.overlay.gui.kitsu.KitsuGUI
import com.elainaclient.overlay.gui.liquidpe.LiquidPeGuiWindow
import com.elainaclient.overlay.gui.open.OpenGuiSpecs
import com.elainaclient.overlay.gui.open.OpenGuiWindow
import com.elainaclient.overlay.gui.protohax.ProtohaxGUI

/**
 * Builds the click-GUI window for a theme.
 *
 * `KITSU`, `GRACE` and `PROTOHAX` each have their own layout (ported from LuminaCN-2.1.0);
 * `CLASSIC` is the shared classic layout re-skinned by [GUITheme.style].
 *
 * `DREAM` / `HACKAI` / `PIANAIXEL` / `PIXELSIGHT` / `LIQUIDPE` all run on the shared
 * [OpenGuiWindow] shell, configured by their [OpenGuiSpecs] spec (ported from
 * OpenMoxel/OpenClickGUI; LiquidPE keeps its original colours).
 */
fun GUITheme.createClickGUI(): OverlayWindow = when (this) {
    GUITheme.KITSU -> KitsuGUI()
    GUITheme.GRACE -> GraceGUI()
    GUITheme.PROTOHAX -> ProtohaxGUI()
    GUITheme.CLASSIC -> OverlayClickGUI(this)
    GUITheme.DREAM -> OpenGuiWindow(OpenGuiSpecs.Dream, this)
    GUITheme.HACKAI -> OpenGuiWindow(OpenGuiSpecs.HackAi, this)
    GUITheme.PIANAIXEL -> OpenGuiWindow(OpenGuiSpecs.Pianaixel, this)
    GUITheme.PIXELSIGHT -> OpenGuiWindow(OpenGuiSpecs.PixelSight, this)
    // Direct host of LiquidPE's original Canvas view (bridged to ModuleManager), not a Compose
    // reimplementation - it keeps the author's colours and Minecraft font untouched.
    GUITheme.LIQUIDPE -> LiquidPeGuiWindow()
}
