package com.elainaclient.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette for the in-game click GUI. The module list body ([ModuleContent]) and the shell read
 * their colours from [LocalClickGuiStyle], so switching the theme re-skins the whole GUI without
 * duplicating the layout.
 */
@Immutable
data class ClickGuiStyle(
    val accent: Color,
    val accentSecondary: Color,
    val accentDark: Color,
    val background: Color,
    val cardBackground: Color,
    val cardBackgroundExpanded: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderActive: Color,
    val errorRed: Color,
    val headerBackground: Color,
    val sidebarBackground: Color,
    val buttonBackground: Color,
    val cornerRadius: Float = 20f
) {
    companion object {
        val Classic = ClickGuiStyle(
            accent = Color(0xFFE63946), accentSecondary = Color(0xFFDC2F3E), accentDark = Color(0xFF8B1A1F),
            background = Color(0xFF0A0A0A), cardBackground = Color(0xFF121212), cardBackgroundExpanded = Color(0xFF1A1212),
            textPrimary = Color(0xFFE8E8E8), textSecondary = Color(0xFFB0B0B0), textMuted = Color(0xFF666666),
            border = Color(0xFF2A1A1A), borderActive = Color(0xFF3A2222), errorRed = Color(0xFFCF222E),
            headerBackground = Color(0xFF161212), sidebarBackground = Color(0xFF1A1212), buttonBackground = Color(0xFF251A1A)
        )

        val Kitsu = ClickGuiStyle(
            accent = Color(0xFF8B7CF6), accentSecondary = Color(0xFF7C6CF0), accentDark = Color(0xFF4B3FA8),
            background = Color(0xFF14131B), cardBackground = Color(0xFF1B1926), cardBackgroundExpanded = Color(0xFF221F30),
            textPrimary = Color(0xFFEDEBF5), textSecondary = Color(0xFFA9A3C0), textMuted = Color(0xFF6E6885),
            border = Color(0xFF2C2940), borderActive = Color(0xFF3B3757), errorRed = Color(0xFFE5484D),
            headerBackground = Color(0xFF1B1926), sidebarBackground = Color(0xFF1B1926), buttonBackground = Color(0xFF2A2740),
            cornerRadius = 18f
        )

        val Grace = ClickGuiStyle(
            accent = Color(0xFF9D4EDD), accentSecondary = Color(0xFF7B2CBF), accentDark = Color(0xFF5A189A),
            background = Color(0xFF0E0A14), cardBackground = Color(0xFF171226), cardBackgroundExpanded = Color(0xFF1E1730),
            textPrimary = Color(0xFFEDE7F6), textSecondary = Color(0xFFB39DDB), textMuted = Color(0xFF6D5B8C),
            border = Color(0xFF2A2140), borderActive = Color(0xFF3A2E57), errorRed = Color(0xFFCF222E),
            headerBackground = Color(0xFF171226), sidebarBackground = Color(0xFF171226), buttonBackground = Color(0xFF251A3A),
            cornerRadius = 22f
        )

        val Protohax = ClickGuiStyle(
            accent = Color(0xFF4C8DFF), accentSecondary = Color(0xFF3B78E0), accentDark = Color(0xFF25509E),
            background = Color(0xFF101418), cardBackground = Color(0xFF161B20), cardBackgroundExpanded = Color(0xFF1D242B),
            textPrimary = Color(0xFFE6EAF0), textSecondary = Color(0xFF9AA6B2), textMuted = Color(0xFF67717C),
            border = Color(0xFF232C36), borderActive = Color(0xFF33404D), errorRed = Color(0xFFE5484D),
            headerBackground = Color(0xFF161B20), sidebarBackground = Color(0xFF161B20), buttonBackground = Color(0xFF202A34),
            cornerRadius = 14f
        )
    }
}

val LocalClickGuiStyle = staticCompositionLocalOf { ClickGuiStyle.Classic }
