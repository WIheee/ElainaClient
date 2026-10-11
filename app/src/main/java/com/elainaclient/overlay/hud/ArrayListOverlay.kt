package com.elainaclient.overlay.hud

import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.elainaclient.R
import com.elainaclient.game.localizedModule
import com.elainaclient.game.module.misc.ArrayListModule
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.hud.horion.ArrayListColor
import com.elainaclient.overlay.hud.horion.ArrayListItem
import com.elainaclient.overlay.hud.horion.ArrayListMode
import com.elainaclient.overlay.hud.horion.ArrayListRainbow
import com.elainaclient.overlay.hud.horion.HorionArrayList
import com.elainaclient.ui.theme.WColors

/**
 * The on-screen module list ("ArrayList"), rendered by the Horion-style [HorionArrayList] custom
 * View (ported from the `openhal4a` project) instead of the old Compose drawing.
 *
 * The module's option surface is preserved (`ArrayListModule` still drives every setter below);
 * options are mapped onto HorionArrayList's own configuration.
 */
class ArrayListOverlay : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 20
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var modules by mutableStateOf(listOf<ModuleInfo>())
    private var sortMode by mutableStateOf(ArrayListModule.SortMode.LENGTH)
    private var animationSpeed by mutableStateOf(300)
    private var showBackground by mutableStateOf(true)
    private var showBorder by mutableStateOf(true)
    private var borderStyle by mutableStateOf(ArrayListModule.BorderStyle.LEFT)
    private var colorMode by mutableStateOf(ArrayListModule.ColorMode.SMOOTH_GRADIENT)
    private var rainbowSpeed by mutableStateOf(1.0f)
    private var fontSize by mutableStateOf(14)
    private var spacing by mutableStateOf(2)
    private var slideAnimation by mutableStateOf(true)
    private var fontStyle by mutableStateOf(ArrayListModule.FontStyle.NORMAL)
    private var glowEffect by mutableStateOf(true)

    data class ModuleInfo(
        val name: String,
        val category: String,
        val isEnabled: Boolean,
        val priority: Int
    )

    companion object {
        val overlayInstance by lazy { ArrayListOverlay() }
        private var shouldShowOverlay = false

        fun showOverlay() {
            if (shouldShowOverlay) {
                try {
                    OverlayManager.showOverlayWindow(overlayInstance)
                } catch (e: Exception) {}
            }
        }

        fun dismissOverlay() {
            try {
                OverlayManager.dismissOverlayWindow(overlayInstance)
            } catch (e: Exception) {}
        }

        fun setOverlayEnabled(enabled: Boolean) {
            shouldShowOverlay = enabled
            if (enabled) showOverlay() else dismissOverlay()
        }

        fun isOverlayEnabled(): Boolean = shouldShowOverlay

        fun setModules(moduleList: List<ModuleInfo>) {
            overlayInstance.modules = moduleList
        }

        fun setSortMode(mode: ArrayListModule.SortMode) {
            overlayInstance.sortMode = mode
        }

        fun setAnimationSpeed(speed: Int) {
            overlayInstance.animationSpeed = speed
        }

        fun setShowBackground(show: Boolean) {
            overlayInstance.showBackground = show
        }

        fun setShowBorder(show: Boolean) {
            overlayInstance.showBorder = show
        }

        fun setBorderStyle(style: ArrayListModule.BorderStyle) {
            overlayInstance.borderStyle = style
        }

        fun setColorMode(mode: ArrayListModule.ColorMode) {
            overlayInstance.colorMode = mode
        }

        fun setRainbowSpeed(speed: Float) {
            overlayInstance.rainbowSpeed = speed
        }

        fun setFontSize(size: Int) {
            overlayInstance.fontSize = size
        }

        fun setSpacing(space: Int) {
            overlayInstance.spacing = space
        }

        // Fade/gradient options are accepted for config compatibility but not rendered by
        // HorionArrayList (it animates and shades on its own).
        fun setFadeAnimation(fade: Boolean) {}

        fun setSlideAnimation(slide: Boolean) {
            overlayInstance.slideAnimation = slide
        }

        fun setFontStyle(style: ArrayListModule.FontStyle) {
            overlayInstance.fontStyle = style
        }

        fun setGradientIntensity(intensity: Float) {}

        fun setGlowEffect(glow: Boolean) {
            overlayInstance.glowEffect = glow
        }

        fun setSmoothGradient(smooth: Boolean) {}
    }

    private fun sortedModules(): List<ModuleInfo> = when (sortMode) {
        ArrayListModule.SortMode.LENGTH -> modules.sortedByDescending { it.name.length }
        ArrayListModule.SortMode.ALPHABETICAL -> modules.sortedBy { it.name }
        ArrayListModule.SortMode.CATEGORY -> modules.sortedBy { it.category }
        ArrayListModule.SortMode.CUSTOM -> modules.sortedByDescending { it.priority }
    }

    private fun categoryIndex(category: String): Int = when (category) {
        "Combat" -> 0
        "Motion" -> 1
        "Visual" -> 2
        "World" -> 3
        "Misc" -> 4
        else -> 5
    }

    @Composable
    override fun Content() {
        if (!isOverlayEnabled()) return

        val context = LocalContext.current
        val density = context.resources.displayMetrics.scaledDensity
        val trackedIds = remember { mutableSetOf<Int>() }
        val minecraftTypeface = remember(context) {
            runCatching { ResourcesCompat.getFont(context, R.font.minecraft) }.getOrNull()
        }

        // Read every option here (in composition) so the AndroidView re-applies whenever any of
        // them changes. Reading them only inside `update` did not reliably trigger a refresh — the
        // list would only catch up after some other recomposition (the "toggle twice" symptom).
        val mode = if (!showBorder) {
            ArrayListMode.NONE
        } else when (borderStyle) {
            ArrayListModule.BorderStyle.NONE -> ArrayListMode.NONE
            ArrayListModule.BorderStyle.FULL -> ArrayListMode.OUTLINE
            else -> ArrayListMode.EDGE_LINE
        }
        val color = when (colorMode) {
            ArrayListModule.ColorMode.RAINBOW -> ArrayListColor.RAINBOW
            ArrayListModule.ColorMode.CATEGORY_BASED -> ArrayListColor.CATEGORIZED
            else -> ArrayListColor.CUSTOM
        }
        val customColor = if (colorMode == ArrayListModule.ColorMode.STATIC) {
            WColors.OnSurface.toArgb()
        } else {
            WColors.Accent.toArgb()
        }
        val textSize = fontSize * density
        val rainbowStep = rainbowSpeed * ((200f / animationSpeed.coerceIn(100, 1000)).coerceIn(2f, 20f))
        val typeface = if (fontStyle == ArrayListModule.FontStyle.MINECRAFT) {
            minecraftTypeface ?: Typeface.MONOSPACE
        } else {
            Typeface.DEFAULT
        }
        val backgroundColor = if (showBackground) 0x9A000000.toInt() else 0
        val items = sortedModules().map { m ->
            m.name.hashCode() to ArrayListItem(
                text = m.name.localizedModule(),
                category = categoryIndex(m.category)
            )
        }

        AndroidView(
            factory = { ctx ->
                HorionArrayList(ctx).apply {
                    gravity = Gravity.TOP or Gravity.END
                    showKeybinds = false
                    showModes = false
                    rainbow = ArrayListRainbow.PASTEL
                }
            },
            update = { view ->
                view.mode = mode
                view.color = color
                view.customColor = customColor
                view.textSize = textSize
                view.rowPadding = spacing.toFloat()
                view.textShadowEnabled = glowEffect
                view.itemClosingAnimationEnabled = slideAnimation
                view.rainbowStepDegrees = rainbowStep
                view.font = typeface
                view.backgroundColor = backgroundColor

                // Sync items: add/update each, drop the ones that are no longer enabled.
                val ids = items.map { it.first }.toSet()
                (trackedIds - ids).toList().forEach { id ->
                    view.removeItem(id)
                    trackedIds.remove(id)
                }
                items.forEach { (id, item) ->
                    view.setItem(id, item)
                    trackedIds.add(id)
                }
            }
        )
    }
}
