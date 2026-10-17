package com.elainaclient.overlay.gui.open

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elainaclient.overlay.gui.open.OpenContent.Cards
import com.elainaclient.overlay.gui.open.OpenContent.Rows
import com.elainaclient.overlay.gui.open.OpenNav.Sidebar
import com.elainaclient.overlay.gui.open.OpenSettings.Detail
import com.elainaclient.overlay.gui.open.OpenSettings.Inline
import com.elainaclient.overlay.gui.open.OpenSettings.Side

/**
 * Skins for the click GUIs ported from OpenMoxel/OpenClickGUI.
 *
 * The original apps are separate Android projects with their own Canvas drawing code. Pianaixel
 * and PixelSight are reimplemented here as **one parametrised Compose shell** ([OpenGuiWindow])
 * plus style specs: the layouts below reproduce each project's structure (sidebar vs. top tabs,
 * cards vs. rows vs. floating panels, inline/detail/side settings), while module/settings data
 * comes from Elaina's [com.elainaclient.game.ModuleManager].
 *
 * Dream has its own native Canvas port ([com.elainaclient.overlay.gui.dream.DreamGuiWindow]),
 * HackAI a native View port ([com.elainaclient.overlay.gui.hackai.HackAiGuiWindow]), and LiquidPE
 * is the direct host of its rewritten Canvas view; their specs below are kept only as layout
 * references until the remaining Compose themes are retired.
 *
 * All ports are re-skinned with Elaina's violet brand palette and show the "Elaina" brand name
 * (the originals drew their own text logos / text glyphs).
 */
@Immutable
data class OpenGuiPalette(
    val accent: Color,
    val accentAlt: Color,
    val accentDeep: Color,
    val background: Color,
    val header: Color,
    val sidebar: Color,
    val card: Color,
    val cardActive: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val switchOff: Color,
    val scrim: Color,
)

enum class OpenNav { Sidebar, Tabs }
enum class OpenContent { Cards, Rows, Panels }
enum class OpenSettings { Detail, Inline, Side }

@Immutable
data class OpenGuiSpec(
    val palette: OpenGuiPalette,
    val nav: OpenNav,
    val content: OpenContent,
    val settings: OpenSettings,
    val tagline: String = "",
    val showSearch: Boolean = true,
    val showFooter: Boolean = false,
    val panelWidth: Float = 0.92f,
    val panelHeight: Float = 0.86f,
    val panelRadius: Dp = 22.dp,
    val cardRadius: Dp = 14.dp,
    val sidebarWidth: Dp = 190.dp,
    val brandFont: FontFamily = FontFamily.SansSerif,
    val bodyFont: FontFamily = FontFamily.SansSerif,
    val brandSize: TextUnit = 26.sp,
    val twoColumnCards: Boolean = false,
    val panelColumns: Int = 3,
)

object OpenGuiSpecs {

    // Elaina brand palette (matches ui/theme + GracePalette).
    private val Violet = Color(0xFF9D4EDD)
    private val Lavender = Color(0xFFC77DFF)
    private val DeepViolet = Color(0xFF5A189A)

    /** Dream: 1411x820 card list + right detail pane, sidebar categories, violet gradient. */
    val Dream = OpenGuiSpec(
        palette = OpenGuiPalette(
            accent = Violet, accentAlt = Lavender, accentDeep = DeepViolet,
            background = Color(0xFF171226), header = Color(0xFF1B1632), sidebar = Color(0xFF1A1530),
            card = Color(0xFF221B3A), cardActive = Color(0xFF2B2148),
            text = Color(0xFFF0EAFA), textSecondary = Color(0xFFB39DDB), textMuted = Color(0xFF7E6DA3),
            border = Color(0xFF2E2547), switchOff = Color(0xFF3A3055), scrim = Color(0xA6000000),
        ),
        nav = Sidebar, content = Cards, settings = Detail,
        tagline = "Advanced Minecraft Utility",
        showSearch = false, showFooter = false,
        panelRadius = 22.dp, cardRadius = 13.dp, sidebarWidth = 190.dp,
        brandSize = 28.sp,
    )

    /** HackAI: header + search, icon sidebar, single-column cards, long-press inline settings. */
    val HackAi = OpenGuiSpec(
        palette = OpenGuiPalette(
            accent = Lavender, accentAlt = Violet, accentDeep = DeepViolet,
            background = Color(0xFF171226), header = Color(0xFF171226), sidebar = Color(0xFF16112B),
            card = Color(0xFF201A35), cardActive = Color(0xFF2A2148),
            text = Color(0xFFF2EEFA), textSecondary = Color(0xFFA99BC8), textMuted = Color(0xFF7C6D9C),
            border = Color(0xFF2A2140), switchOff = Color(0xFF3A3154), scrim = Color(0xA6000000),
        ),
        nav = Sidebar, content = Cards, settings = Inline,
        tagline = "Advanced Minecraft Utility",
        showSearch = true, showFooter = false,
        panelRadius = 16.dp, cardRadius = 10.dp, sidebarWidth = 186.dp,
        brandSize = 24.sp,
    )

    /** Pianaixel: search + category column, single-column rows with a gear, side settings. */
    val Pianaixel = OpenGuiSpec(
        palette = OpenGuiPalette(
            accent = Lavender, accentAlt = Violet, accentDeep = DeepViolet,
            background = Color(0xB3140F22), header = Color(0xB3140F22), sidebar = Color(0x602E2350),
            card = Color(0x86261E3D), cardActive = Color(0xA63A2C5E),
            text = Color(0xFFF4EDFF), textSecondary = Color(0xFFCBBBE6), textMuted = Color(0xFF8B7AAC),
            border = Color(0xFF4A3A6E), switchOff = Color(0xFF3A3154), scrim = Color(0x99000000),
        ),
        nav = Sidebar, content = Rows, settings = Side,
        tagline = "Minecraft · Elaina",
        showSearch = true, showFooter = true,
        panelRadius = 26.dp, cardRadius = 12.dp, sidebarWidth = 210.dp,
        brandFont = FontFamily.Monospace, bodyFont = FontFamily.SansSerif,
        brandSize = 22.sp,
    )

    /** PixelSight: logo sidebar + header + card list, right-side settings panel, footer. */
    val PixelSight = OpenGuiSpec(
        palette = OpenGuiPalette(
            accent = Lavender, accentAlt = Violet, accentDeep = DeepViolet,
            background = Color(0xE61B1533), header = Color(0xE61B1533), sidebar = Color(0xE6161128),
            card = Color(0xE6241C40), cardActive = Color(0xE62E2450),
            text = Color(0xFFFFFFFF), textSecondary = Color(0xFFC5B8E0), textMuted = Color(0xFF807396),
            border = Color(0xFF332850), switchOff = Color(0xFF3A3154), scrim = Color(0x99000000),
        ),
        nav = Sidebar, content = Cards, settings = Side,
        tagline = "Advanced Minecraft Utility",
        showSearch = true, showFooter = true,
        panelRadius = 28.dp, cardRadius = 18.dp, sidebarWidth = 210.dp,
        brandSize = 34.sp, twoColumnCards = false,
    )

}
