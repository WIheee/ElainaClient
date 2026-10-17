package com.elainaclient.overlay.gui.liquidpe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewConfiguration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin rewrite of LiquidPE's original Canvas click-GUI renderer.
 *
 * The drawing, hit-testing, scrolling, panel drag and inline settings panels are preserved
 * one-for-one; only the data model changed (Kotlin classes, `when` over sealed setting types instead
 * of `instanceof` casts). The unused HUD renderer and the unused grid container were dropped -
 * they were never part of the click GUI path.
 */
class LiquidPeRenderer(
    context: Context,
    private val store: LiquidPeStore,
    private val listener: Listener,
    private val typography: LiquidPeTypography,
) {

    interface Listener {
        fun onTopTabChanged(tab: TopTab)
        fun onSearchRequested()
    }

    private companion object {
        const val PANEL_GRID_COLUMNS = 7
        const val PANEL_GRID_LEFT = 30f
        const val PANEL_GRID_TOP = 194f
        const val PANEL_GRID_CELL_WIDTH = 375f
        const val PANEL_GRID_CELL_HEIGHT = 451f
        const val PANEL_GRID_GAP_X = 30f
        const val PANEL_GRID_GAP_Y = 30f
        const val PANEL_CONTENT_TOP = 60f
        const val PANEL_CONTENT_BOTTOM_PADDING = 8f
        const val PANEL_SCREEN_BOTTOM_INSET = 30f
        const val PANEL_WIDTH = 375f
        const val HIT_CAPACITY = 128

        const val HIT_TOGGLE = 1
        const val HIT_SLIDER = 2
        const val HIT_RANGE_LOW = 3
        const val HIT_RANGE_HIGH = 4
        const val HIT_DROPDOWN = 5
        const val HIT_DROPDOWN_OPTION = 6
        const val HIT_MULTI_OPTION = 7
        const val HIT_BIND = 8
        const val HIT_COLOR = 9
        const val HIT_GROUP = 10
        const val HIT_SV = 11
        const val HIT_HUE = 12
        const val HIT_ALPHA = 13

        val SLIDER_ACTIONS = setOf(HIT_SLIDER, HIT_RANGE_LOW, HIT_RANGE_HIGH, HIT_SV, HIT_HUE, HIT_ALPHA)
    }

    private class SettingHit {
        val bounds = RectF()
        var setting: SettingEntry? = null
        var action = 0
        var index = 0
    }

    private val panels = store.categories
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = LiquidPeFonts.medium()
    }
    private val rect = RectF()
    private val path = Path()
    private val settingHits = Array(HIT_CAPACITY) { SettingHit() }
    private var settingHitCount = 0
    private var activeHit: SettingHit? = null
    private var overlayDropdown: DropdownSetting? = null
    private var overlayPanel: CategoryPanel? = null
    private val overlayDropdownBounds = RectF()
    private var activeTab = TopTab.CLICK_GUI
    private var searchQuery = ""
    private var scrollPanel: CategoryPanel? = null
    private var pressedPanel: CategoryPanel? = null
    private var layoutDraggingPanel: CategoryPanel? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var moved = false
    private var longPressTriggered = false
    var isEditMode = false
        private set
    private var originalPanelX = 0f
    private var originalPanelY = 0f
    private val layoutStorage = PanelLayoutStorage(context)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        val panel = pressedPanel
        if (panel != null && !moved && activeHit == null) beginPanelDrag(panel, downX, downY)
    }

    init {
        restorePanelLayout()
    }

    // =====================================================================================
    // Drawing
    // =====================================================================================

    fun draw(canvas: Canvas, now: Long) {
        settingHitCount = 0
        overlayDropdown = null
        overlayPanel = null
        fill.shader = null
        drawTopTabs(canvas)
        drawSearch(canvas)
        panels.forEach { drawPanel(canvas, it, now) }
        val dropdown = overlayDropdown
        val panel = overlayPanel
        if (dropdown != null && panel != null) drawDropdownOverlay(canvas, panel, dropdown)
    }

    private fun drawTopTabs(canvas: Canvas) {
        setFill(LiquidPeColors.PANEL_DEEP)
        rect.set(1320f, 14f, 1620f, 56f)
        canvas.drawRoundRect(rect, 21f, 21f, fill)

        val labels = arrayOf(
            LiquidPeI18n.t("ClickGUI"),
            LiquidPeI18n.t("HUD Editor"),
            LiquidPeI18n.t("Settings"),
        )
        val widths = floatArrayOf(94f, 111f, 95f)
        var x = 1320f
        labels.forEachIndexed { i, label ->
            val active = activeTab.ordinal == i
            if (active) {
                stroke.color = LiquidPeColors.ACCENT
                stroke.strokeWidth = 2f
                rect.set(x + 3f, 17f, x + widths[i] - 3f, 53f)
                canvas.drawRoundRect(rect, 18f, 18f, stroke)
            }
            setText(
                if (active) Color.WHITE else 0xFFB7B8BD.toInt(),
                14f,
                if (active) LiquidPeFonts.bold() else LiquidPeFonts.medium(),
            )
            drawCentered(canvas, label, x, 14f, widths[i], 42f, text)
            x += widths[i]
        }
    }

    private fun drawSearch(canvas: Canvas) {
        setFill(0xCC010405.toInt())
        rect.set(1020f, 104f, 1920f, 179f)
        canvas.drawRoundRect(rect, 37f, 37f, fill)

        setText(
            if (searchQuery.isEmpty()) LiquidPeColors.TEXT_MUTED else LiquidPeColors.TEXT_NORMAL,
            24f,
            LiquidPeFonts.regular(),
        )
        drawBaseline(canvas, if (searchQuery.isEmpty()) "Search" else searchQuery, 1058f, 104f, 75f, text)

        if (searchQuery.isNotEmpty()) {
            stroke.color = LiquidPeColors.TEXT_MUTED
            stroke.strokeWidth = 3f
            canvas.drawLine(1874f, 132f, 1894f, 152f, stroke)
            canvas.drawLine(1894f, 132f, 1874f, 152f, stroke)
        }
    }

    private fun drawPanel(canvas: Canvas, panel: CategoryPanel, now: Long) {
        val save = canvas.save()
        if (panel === layoutDraggingPanel) {
            canvas.scale(1.04f, 1.04f, panel.x + 187.5f, panel.y + 120f)
        }

        val progress = panel.expansion.get(now)
        panel.contentHeight = calculateContentHeight(panel, now)
        val availableBodyHeight = calculateAvailableBodyHeight(panel)
        val bodyViewportHeight = min(panel.contentHeight, availableBodyHeight)
        val bodyHeight = bodyViewportHeight * progress
        val panelHeight = PANEL_CONTENT_TOP + bodyHeight

        setFill(LiquidPeColors.PANEL)
        rect.set(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + panelHeight)
        canvas.drawRoundRect(rect, LiquidPeMetrics.PANEL_RADIUS, LiquidPeMetrics.PANEL_RADIUS, fill)

        setFill(LiquidPeColors.PANEL_DEEP)
        rect.set(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + LiquidPeMetrics.PANEL_HEADER_HEIGHT)
        canvas.drawRoundRect(rect, LiquidPeMetrics.PANEL_RADIUS, LiquidPeMetrics.PANEL_RADIUS, fill)
        canvas.drawRect(
            panel.x,
            panel.y + LiquidPeMetrics.PANEL_HEADER_HEIGHT - LiquidPeMetrics.PANEL_RADIUS,
            panel.x + PANEL_WIDTH,
            panel.y + LiquidPeMetrics.PANEL_HEADER_HEIGHT,
            fill,
        )

        setFill(LiquidPeColors.PANEL_HIGHLIGHT)
        rect.set(
            panel.x + 3f,
            panel.y + 1f,
            panel.x + PANEL_WIDTH - 3f,
            panel.y + 2f,
        )
        canvas.drawRect(rect, fill)

        setFill(LiquidPeColors.ACCENT)
        canvas.drawRect(
            panel.x,
            panel.y + LiquidPeMetrics.PANEL_HEADER_HEIGHT - 2f,
            panel.x + PANEL_WIDTH,
            panel.y + LiquidPeMetrics.PANEL_HEADER_HEIGHT + 1f,
            fill,
        )

        drawCategoryIcon(canvas, panel.name, panel.x + 34f, panel.y + 29f)
        setText(Color.WHITE, 22f, LiquidPeFonts.bold())
        drawBaseline(canvas, LiquidPeI18n.t(panel.name), panel.x + 62f, panel.y, 58f, text)
        setText(Color.WHITE, 26f, LiquidPeFonts.regular())
        drawCentered(canvas, if (panel.expanded) "−" else "+", panel.x + 326f, panel.y, 44f, 58f, text)

        if (bodyHeight > .5f) {
            val bodySave = canvas.save()
            canvas.clipRect(panel.x, panel.y + PANEL_CONTENT_TOP, panel.x + PANEL_WIDTH, panel.y + panelHeight)
            val maxScroll = max(0f, panel.contentHeight - bodyViewportHeight)
            panel.scrollOffset = panel.scrollOffset.coerceIn(0f, maxScroll)
            panel.targetScrollOffset = panel.targetScrollOffset.coerceIn(0f, maxScroll)
            panel.scrollOffset += (panel.targetScrollOffset - panel.scrollOffset) * .28f

            var y = panel.y + PANEL_CONTENT_TOP - panel.scrollOffset
            panel.modules.forEach { module ->
                if (!matches(module.name)) return@forEach
                drawModuleRow(canvas, panel, module, y, now)
                y += 53f
                val expansion = module.settingsProgress.get(now)
                if (module.hasSettings() && expansion > .001f) {
                    val fullHeight = measureSettings(module.settings, now)
                    val nested = canvas.save()
                    canvas.clipRect(panel.x, y, panel.x + PANEL_WIDTH, y + fullHeight * expansion)
                    drawSettings(canvas, panel, module.settings, y, 0, now)
                    canvas.restoreToCount(nested)
                    y += fullHeight * expansion
                }
            }

            if (panel.contentHeight > bodyViewportHeight) {
                drawScrollbar(canvas, panel, bodyHeight, bodyViewportHeight)
            }
            canvas.restoreToCount(bodySave)
        }
        canvas.restoreToCount(save)
    }

    private fun drawModuleRow(canvas: Canvas, panel: CategoryPanel, module: ModuleEntry, y: Float, now: Long) {
        val color = LiquidPeColors.blend(
            LiquidPeColors.TEXT_NORMAL,
            LiquidPeColors.ACCENT,
            module.enabledProgress.get(now),
        )
        setText(color, 19f, LiquidPeFonts.medium())
        drawCentered(
            canvas,
            LiquidPeI18n.moduleName(module.id, module.name),
            panel.x + 8f,
            y,
            332f,
            53f,
            text,
        )

        if (!module.hasSettings()) return
        stroke.color = 0xFF8A8B91.toInt()
        stroke.strokeWidth = 2.4f
        val cx = panel.x + 344f
        val cy = y + 26f
        if (module.settingsExpanded) {
            canvas.drawLine(cx - 6f, cy - 3f, cx, cy + 3f, stroke)
            canvas.drawLine(cx, cy + 3f, cx + 6f, cy - 3f, stroke)
        } else {
            canvas.drawLine(cx - 3f, cy - 6f, cx + 3f, cy, stroke)
            canvas.drawLine(cx + 3f, cy, cx - 3f, cy + 6f, stroke)
        }
    }

    private fun drawSettings(
        canvas: Canvas,
        panel: CategoryPanel,
        settings: List<SettingEntry>,
        startY: Float,
        level: Int,
        now: Long,
    ): Float {
        val fullHeight = measureSettings(settings, now)
        val railX = panel.x + 17f + level * 13f
        setFill(0x4A06173E)
        canvas.drawRect(railX - 7f, startY, railX + 6f, startY + fullHeight, fill)
        setFill(LiquidPeColors.ACCENT)
        canvas.drawRect(railX, startY, railX + 3f, startY + fullHeight, fill)

        var y = startY + 10f
        settings.forEach { setting ->
            if (!setting.visible) return@forEach
            y = drawSetting(canvas, panel, setting, y, level, now)
        }
        return y + 8f
    }

    private fun drawSetting(
        canvas: Canvas,
        panel: CategoryPanel,
        setting: SettingEntry,
        y: Float,
        level: Int,
        now: Long,
    ): Float {
        val x = panel.x + 27f + level * 13f
        val right = panel.x + 358f
        return when (setting) {
            is BindSetting -> drawBind(canvas, setting, x, right, y)
            is ToggleSetting -> drawToggle(canvas, setting, x, right, y, now)
            is SliderSetting -> drawSlider(canvas, setting, x, right, y)
            is RangeSetting -> drawRange(canvas, setting, x, right, y)
            is DropdownSetting -> drawDropdown(canvas, panel, setting, x, right, y)
            is MultiSelectSetting -> drawMulti(canvas, setting, x, right, y)
            is ColorSetting -> drawColor(canvas, setting, x, right, y)
            is SettingGroup -> {
                val groupHeight = control(46f)
                setText(Color.WHITE, 19f, LiquidPeFonts.bold())
                drawBaseline(canvas, LiquidPeI18n.t(setting.name), x, y, groupHeight, text)
                drawChevron(canvas, right - control(8f), y + groupHeight * .5f, setting.expanded)
                addHit(setting, HIT_GROUP, -1, x - control(5f), y, right, y + groupHeight)

                val childHeight = measureSettings(setting.children, now) * setting.expansion.get(now)
                if (childHeight > .5f) {
                    val save = canvas.save()
                    canvas.clipRect(panel.x, y + groupHeight, panel.x + PANEL_WIDTH, y + groupHeight + childHeight)
                    drawSettings(canvas, panel, setting.children, y + groupHeight, level + 1, now)
                    canvas.restoreToCount(save)
                }
                y + groupHeight + childHeight
            }
        }
    }

    private fun drawBind(canvas: Canvas, setting: BindSetting, x: Float, right: Float, y: Float): Float {
        val height = control(63f)
        val radius = control(6f)
        rect.set(x, y, right, y + height)
        setFill(0xFF010205.toInt())
        canvas.drawRoundRect(rect, radius, radius, fill)
        stroke.color = LiquidPeColors.ACCENT
        stroke.strokeWidth = control(2.5f)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        setText(Color.WHITE, 17f, LiquidPeFonts.medium())
        drawCentered(canvas, LiquidPeI18n.t(setting.name), x, y + control(5f), right - x, control(25f), text)
        setText(
            if (setting.listening) LiquidPeColors.ACCENT else LiquidPeColors.TEXT_MUTED,
            16f,
            LiquidPeFonts.regular(),
        )
        drawCentered(
            canvas,
            if (setting.listening) LiquidPeI18n.t("Press a key") else setting.value,
            x,
            y + control(30f),
            right - x,
            control(25f),
            text,
        )
        addHit(setting, HIT_BIND, -1, x, y, right, y + height)
        return y + control(79f)
    }

    private fun drawToggle(
        canvas: Canvas,
        setting: ToggleSetting,
        x: Float,
        right: Float,
        y: Float,
        now: Long,
    ): Float {
        val progress = setting.progress.get(now)
        val width = control(49f)
        val height = control(21f)
        val knobRadius = control(10.5f)
        val top = y + control(10f)

        setFill(LiquidPeColors.blend(LiquidPeColors.TOGGLE_OFF, LiquidPeColors.ACCENT_DEEP, progress))
        rect.set(x, top, x + width, top + height)
        canvas.drawRoundRect(rect, height * .5f, height * .5f, fill)

        setFill(LiquidPeColors.blend(0xFFF4F4F4.toInt(), LiquidPeColors.ACCENT, progress))
        canvas.drawCircle(x + control(11f) + control(27f) * progress, top + height * .5f, knobRadius, fill)

        setText(LiquidPeColors.TEXT_NORMAL, 18f, LiquidPeFonts.medium())
        drawBaseline(canvas, LiquidPeI18n.t(setting.name), x + control(59f), y, control(40f), text)
        addHit(setting, HIT_TOGGLE, -1, x - control(5f), y, right, y + control(42f))
        return y + control(43f)
    }

    private fun drawSlider(canvas: Canvas, setting: SliderSetting, x: Float, right: Float, y: Float): Float {
        val progress = (setting.value - setting.min) / max(.0001f, setting.max - setting.min)
        setText(LiquidPeColors.TEXT_NORMAL, 18f, LiquidPeFonts.medium())
        drawBaseline(canvas, LiquidPeI18n.t(setting.name), x, y, control(29f), text)
        setText(LiquidPeColors.TEXT_NORMAL, 17f, LiquidPeFonts.regular())
        drawRightBaseline(
            canvas,
            formatValue(setting.value) +
                if (setting.displayUnit.isEmpty()) "" else " " + LiquidPeI18n.t(setting.displayUnit),
            right, y, control(29f), text,
        )

        val ty = y + control(42f)
        val trackHeight = control(4f)
        val thumbRadius = control(9f)
        val trackTop = ty
        setFill(LiquidPeColors.TRACK)
        canvas.drawRoundRect(x, trackTop, right, trackTop + trackHeight, trackHeight * .5f, trackHeight * .5f, fill)
        setFill(LiquidPeColors.ACCENT)
        canvas.drawRoundRect(
            x, trackTop, x + (right - x) * progress, trackTop + trackHeight,
            trackHeight * .5f, trackHeight * .5f, fill,
        )
        canvas.drawCircle(x + (right - x) * progress, trackTop + trackHeight * .5f, thumbRadius, fill)

        addHit(setting, HIT_SLIDER, -1, x, y + control(25f), right, y + control(59f))
        return y + control(69f)
    }

    private fun drawRange(canvas: Canvas, setting: RangeSetting, x: Float, right: Float, y: Float): Float {
        val span = max(.0001f, setting.max - setting.min)
        val low = (setting.low - setting.min) / span
        val high = (setting.high - setting.min) / span
        setText(LiquidPeColors.TEXT_NORMAL, 18f, LiquidPeFonts.medium())
        drawBaseline(canvas, LiquidPeI18n.t(setting.name), x, y, control(29f), text)
        setText(LiquidPeColors.TEXT_NORMAL, 17f, LiquidPeFonts.regular())
        drawRightBaseline(canvas, formatValue(setting.low) + " - " + formatValue(setting.high), right, y, control(29f), text)

        val ty = y + control(42f)
        val trackHeight = control(4f)
        val thumbRadius = control(9f)
        setFill(LiquidPeColors.TRACK)
        canvas.drawRoundRect(x, ty, right, ty + trackHeight, trackHeight * .5f, trackHeight * .5f, fill)
        setFill(LiquidPeColors.ACCENT)
        canvas.drawRoundRect(
            x + (right - x) * low, ty, x + (right - x) * high, ty + trackHeight,
            trackHeight * .5f, trackHeight * .5f, fill,
        )
        canvas.drawCircle(x + (right - x) * low, ty + trackHeight * .5f, thumbRadius, fill)
        canvas.drawCircle(x + (right - x) * high, ty + trackHeight * .5f, thumbRadius, fill)

        val mid = x + (right - x) * (low + high) * .5f
        addHit(setting, HIT_RANGE_LOW, -1, x, y + control(25f), mid, y + control(60f))
        addHit(setting, HIT_RANGE_HIGH, -1, mid, y + control(25f), right, y + control(60f))
        return y + control(72f)
    }

    private fun drawDropdown(
        canvas: Canvas,
        panel: CategoryPanel,
        setting: DropdownSetting,
        x: Float,
        right: Float,
        y: Float,
    ): Float {
        val height = control(42f)
        val radius = control(6f)
        rect.set(x, y, right, y + height)
        setFill(LiquidPeColors.ACCENT_SOFT)
        canvas.drawRoundRect(rect, radius, radius, fill)

        setText(Color.WHITE, 18f, LiquidPeFonts.medium())
        drawBaseline(
            canvas,
            LiquidPeI18n.t(setting.name) + " • " + LiquidPeI18n.t(setting.value),
            x + control(14f), y, height, text,
        )
        drawChevron(canvas, right - control(18f), y + height * .5f, false)
        addHit(setting, HIT_DROPDOWN, -1, x, y, right, y + height)

        if (setting.open) {
            overlayDropdown = setting
            overlayPanel = panel
            overlayDropdownBounds.set(x, y, right, y + height)
        }
        return y + control(61f)
    }

    private fun drawMulti(canvas: Canvas, setting: MultiSelectSetting, x: Float, right: Float, y: Float): Float {
        setText(Color.WHITE, 18f, LiquidPeFonts.bold())
        drawBaseline(canvas, LiquidPeI18n.t(setting.name), x, y, control(35f), text)
        setText(LiquidPeColors.TEXT_NORMAL, 17f, LiquidPeFonts.medium())
        drawRightBaseline(canvas, "${setting.selected.size} / ${setting.options.size}", right, y, control(35f), text)

        var tx = x + control(22f)
        var ty = y + control(39f)
        setting.options.forEachIndexed { i, option ->
            val display = LiquidPeI18n.t(option)
            setText(LiquidPeColors.ACCENT_SOFT, 17f, LiquidPeFonts.medium())
            val w = text.measureText(display) + control(20f)
            if (tx + w > right) {
                tx = x + control(22f)
                ty += control(38f)
            }
            val chipHeight = control(31f)
            val radius = control(5f)
            rect.set(tx, ty, tx + w, ty + chipHeight)
            setFill(if (option in setting.selected) 0xB2182A58.toInt() else 0x78101425)
            canvas.drawRoundRect(rect, radius, radius, fill)
            drawCentered(canvas, display, tx, ty, w, chipHeight, text)
            addHit(setting, HIT_MULTI_OPTION, i, tx, ty, tx + w, ty + chipHeight)
            tx += w + control(8f)
        }
        return max(y + control(77f), ty + control(38f))
    }

    private fun drawColor(canvas: Canvas, setting: ColorSetting, x: Float, right: Float, y: Float): Float {
        setText(LiquidPeColors.TEXT_NORMAL, 18f, LiquidPeFonts.medium())
        drawBaseline(canvas, LiquidPeI18n.t(setting.name), x, y, control(42f), text)
        setText(LiquidPeColors.TEXT_NORMAL, 17f, LiquidPeFonts.regular())
        drawRightBaseline(canvas, toRgbaHex(setting.color), right - control(64f), y, control(42f), text)

        setFill(setting.color)
        rect.set(right - control(46f), y + control(7f), right, y + control(37f))
        canvas.drawRoundRect(rect, control(5f), control(5f), fill)
        stroke.color = LiquidPeColors.ACCENT
        stroke.strokeWidth = control(2f)
        canvas.drawRoundRect(rect, control(5f), control(5f), stroke)
        addHit(setting, HIT_COLOR, -1, x, y, right, y + control(48f))

        if (!setting.expanded) return y + control(55f)

        val pickerY = y + control(55f)
        val svRight = right - control(62f)
        val pickerHeight = control(192f)
        val sv = addHit(setting, HIT_SV, -1, x, pickerY, svRight, pickerY + pickerHeight).bounds
        drawChecker(canvas, sv, control(10f))

        val pure = Color.HSVToColor(floatArrayOf(setting.hue, 1f, 1f))
        fill.shader = LinearGradient(sv.left, 0f, sv.right, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        canvas.drawRect(sv, fill)
        fill.shader = LinearGradient(0f, sv.top, 0f, sv.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(sv, fill)
        fill.shader = null

        stroke.color = Color.WHITE
        stroke.strokeWidth = control(3f)
        canvas.drawCircle(
            sv.left + setting.saturation * sv.width(),
            sv.top + (1 - setting.brightness) * sv.height(),
            control(11f), stroke,
        )

        val hue = addHit(
            setting, HIT_HUE, -1,
            right - control(48f), pickerY, right - control(34f), pickerY + pickerHeight,
        ).bounds
        val rainbow = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED,
        )
        fill.shader = LinearGradient(0f, hue.top, 0f, hue.bottom, rainbow, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(hue, control(2f), control(2f), fill)
        fill.shader = null
        canvas.drawCircle(
            hue.centerX(),
            hue.top + setting.hue / 360f * hue.height(),
            control(11f), stroke,
        )

        val alpha = addHit(
            setting, HIT_ALPHA, -1,
            right - control(20f), pickerY, right - control(6f), pickerY + pickerHeight,
        ).bounds
        drawChecker(canvas, alpha, control(7f))
        val opaque = Color.HSVToColor(floatArrayOf(setting.hue, setting.saturation, setting.brightness))
        fill.shader = LinearGradient(0f, alpha.top, 0f, alpha.bottom, opaque, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(alpha, control(2f), control(2f), fill)
        fill.shader = null
        canvas.drawCircle(
            alpha.centerX(),
            alpha.top + (1 - setting.alpha) * alpha.height(),
            control(11f), stroke,
        )
        return pickerY + control(216f)
    }

    private fun drawDropdownOverlay(canvas: Canvas, panel: CategoryPanel, setting: DropdownSetting) {
        val panelBottom = panel.y + calculatePanelHeight(panel, SystemClock.uptimeMillis())
        val save = canvas.save()
        canvas.clipRect(panel.x, panel.y + PANEL_CONTENT_TOP, panel.x + PANEL_WIDTH, panelBottom)

        val rowHeight = control(41f)
        val top = overlayDropdownBounds.bottom + control(3f)
        val h = setting.options.size * rowHeight
        setFill(0xFF030507.toInt())
        rect.set(overlayDropdownBounds.left, top, overlayDropdownBounds.right, top + h)
        canvas.drawRoundRect(rect, control(6f), control(6f), fill)
        stroke.color = LiquidPeColors.ACCENT
        stroke.strokeWidth = control(2f)
        canvas.drawRoundRect(rect, control(6f), control(6f), stroke)

        setting.options.forEachIndexed { i, option ->
            val display = LiquidPeI18n.t(option)
            setText(
                if (option == setting.value) LiquidPeColors.ACCENT else LiquidPeColors.TEXT_NORMAL,
                18f,
                LiquidPeFonts.medium(),
            )
            drawCentered(canvas, display, rect.left, top + i * rowHeight, rect.width(), rowHeight, text)
            addHit(setting, HIT_DROPDOWN_OPTION, i, rect.left, top + i * rowHeight, rect.right, top + (i + 1) * rowHeight)
        }
        canvas.restoreToCount(save)
    }

    private fun drawScrollbar(canvas: Canvas, panel: CategoryPanel, bodyHeight: Float, bodyMax: Float) {
        val ratio = bodyMax / panel.contentHeight
        val thumb = max(40f, bodyHeight * ratio)
        val maxScroll = panel.contentHeight - bodyMax
        val travel = max(1f, bodyHeight - thumb)
        val top = panel.y + 60f + travel * panel.scrollOffset / max(1f, maxScroll)
        setFill(LiquidPeColors.ACCENT)
        canvas.drawRoundRect(panel.x + 371f, top, panel.x + 374f, top + thumb, 2f, 2f, fill)
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, down: Boolean) {
        val s = control(1f)
        stroke.color = Color.WHITE
        stroke.strokeWidth = control(2f)
        if (down) {
            canvas.drawLine(cx - 5 * s, cy - 3 * s, cx, cy + 2 * s, stroke)
            canvas.drawLine(cx, cy + 2 * s, cx + 5 * s, cy - 3 * s, stroke)
        } else {
            canvas.drawLine(cx - 3 * s, cy - 5 * s, cx + 2 * s, cy, stroke)
            canvas.drawLine(cx + 2 * s, cy, cx - 3 * s, cy + 5 * s, stroke)
        }
    }

    private fun drawChecker(canvas: Canvas, b: RectF, size: Float) {
        var yy = b.top
        while (yy < b.bottom) {
            var xx = b.left
            while (xx < b.right) {
                val row = ((yy - b.top) / size).toInt()
                val col = ((xx - b.left) / size).toInt()
                setFill(if ((row + col) and 1 == 0) 0xFFB8B8B8.toInt() else 0xFF6D6D6D.toInt())
                canvas.drawRect(xx, yy, min(xx + size, b.right), min(yy + size, b.bottom), fill)
                xx += size
            }
            yy += size
        }
    }

    private fun drawCategoryIcon(canvas: Canvas, name: String, cx: Float, cy: Float) {
        stroke.color = Color.WHITE
        stroke.strokeWidth = 3f
        stroke.strokeCap = Paint.Cap.ROUND
        path.reset()
        when (name) {
            "Render", "渲染", "视觉" -> {
                rect.set(cx - 10f, cy - 7f, cx + 10f, cy + 7f)
                canvas.drawOval(rect, stroke)
                setFill(Color.WHITE)
                canvas.drawCircle(cx, cy, 3f, fill)
            }
            "Combat", "战斗" -> {
                canvas.drawLine(cx - 9f, cy - 9f, cx + 9f, cy + 9f, stroke)
                canvas.drawLine(cx + 9f, cy - 9f, cx - 9f, cy + 9f, stroke)
            }
            "Player", "玩家" -> {
                setFill(Color.WHITE)
                canvas.drawCircle(cx, cy - 7f, 6f, fill)
                canvas.drawRoundRect(cx - 9f, cy + 1f, cx + 9f, cy + 12f, 4f, 4f, fill)
            }
            "Movement", "移动" -> {
                path.moveTo(cx - 10f, cy + 8f)
                path.lineTo(cx - 2f, cy)
                path.lineTo(cx + 1f, cy - 8f)
                path.lineTo(cx + 9f, cy - 10f)
                path.moveTo(cx, cy - 2f)
                path.lineTo(cx + 10f, cy + 7f)
                path.moveTo(cx - 2f, cy + 1f)
                path.lineTo(cx - 7f, cy + 12f)
                canvas.drawPath(path, stroke)
            }
            "World", "世界" -> {
                canvas.drawCircle(cx, cy, 10f, stroke)
                canvas.drawLine(cx, cy - 10f, cx, cy + 10f, stroke)
                canvas.drawOval(cx - 6f, cy - 10f, cx + 6f, cy + 10f, stroke)
            }
            else -> {
                canvas.drawCircle(cx, cy, 8f, stroke)
                canvas.drawCircle(cx + 5f, cy - 7f, 3f, stroke)
            }
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    // =====================================================================================
    // Measurement
    // =====================================================================================

    private fun calculateContentHeight(panel: CategoryPanel, now: Long): Float {
        var height = 0f
        panel.modules.forEach { module ->
            if (matches(module.name)) {
                height += 53f
                if (module.hasSettings()) {
                    height += measureSettings(module.settings, now) * module.settingsProgress.get(now)
                }
            }
        }
        return if (height > 0f) height + PANEL_CONTENT_BOTTOM_PADDING else 0f
    }

    private fun calculateAvailableBodyHeight(panel: CategoryPanel): Float =
        max(0f, LiquidPeMetrics.CONTENT_HEIGHT - PANEL_SCREEN_BOTTOM_INSET - panel.y - PANEL_CONTENT_TOP)

    private fun calculatePanelHeight(panel: CategoryPanel, now: Long): Float {
        panel.contentHeight = calculateContentHeight(panel, now)
        return PANEL_CONTENT_TOP +
            min(panel.contentHeight, calculateAvailableBodyHeight(panel)) * panel.expansion.get(now)
    }

    private fun measureSettings(settings: List<SettingEntry>, now: Long): Float {
        var height = 10f
        settings.forEach { if (it.visible) height += measureSetting(it, now) }
        return height + 8f
    }

    private fun measureSetting(setting: SettingEntry, now: Long): Float = when (setting) {
        is ToggleSetting -> control(43f)
        is SliderSetting -> control(69f)
        is RangeSetting -> control(72f)
        is DropdownSetting -> control(61f)
        is BindSetting -> control(79f)
        is MultiSelectSetting -> control(77f + max(0, (setting.options.size - 1) / 3) * 38f)
        is ColorSetting -> control(55f + if (setting.expanded) 216f else 0f)
        is SettingGroup ->
            control(46f) + measureSettings(setting.children, now) * setting.expansion.get(now)
    }

    // =====================================================================================
    // Touch
    // =====================================================================================

    fun onTouchDown(x: Float, y: Float): Boolean {
        cancelPanelLongPress()
        moved = false
        longPressTriggered = false
        downX = x
        downY = y
        lastY = y
        activeHit = findHit(x, y)

        val hit = activeHit
        if (hit != null && hit.action in SLIDER_ACTIONS) {
            updateDrag(hit, x, y)
            return true
        }

        panels.forEach { panel ->
            val height = calculatePanelHeight(panel, SystemClock.uptimeMillis())
            if (x in panel.x..(panel.x + PANEL_WIDTH) && y in panel.y..(panel.y + height)) {
                scrollPanel = panel
                if (y <= panel.y + 59f) {
                    pressedPanel = panel
                    longPressHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                }
                return true
            }
        }
        return true
    }

    fun onTouchMove(x: Float, y: Float): Boolean {
        if (layoutDraggingPanel != null) {
            updatePanelDrag(x, y)
            return true
        }
        val hit = activeHit
        if (hit != null && hit.action in SLIDER_ACTIONS) {
            moved = true
            updateDrag(hit, x, y)
            return true
        }
        if (hypot(x - downX, y - downY) > 7f) {
            moved = true
            cancelPanelLongPress()
        }
        val panel = scrollPanel
        if (panel != null && abs(y - downY) > 7f) {
            moved = true
            panel.targetScrollOffset -= y - lastY
            lastY = y
            return true
        }
        return false
    }

    fun onTouchUp(x: Float, y: Float): Boolean {
        cancelPanelLongPress()
        if (layoutDraggingPanel != null) {
            finishPanelDrag(true)
        } else if (!moved) {
            if (isEditMode && pressedPanel == null) exitEditMode() else handleTap(x, y)
        }
        activeHit = null
        scrollPanel = null
        pressedPanel = null
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val hit = findHit(x, y)
        if (hit != null) {
            handleSettingTap(hit)
            return
        }

        panels.forEach { panel ->
            panel.modules.forEach { module ->
                module.settings.forEach { closeDropdowns(it) }
            }
        }

        if (y in 14f..56f && x in 1320f..1620f) {
            val local = x - 1320f
            setActiveTab(
                when {
                    local < 94f -> TopTab.CLICK_GUI
                    local < 205f -> TopTab.HUD_EDITOR
                    else -> TopTab.SETTINGS
                },
            )
            return
        }

        if (x in 1020f..1920f && y in 104f..179f) {
            if (searchQuery.isNotEmpty() && x > 1850f) setSearchQuery("") else listener.onSearchRequested()
            return
        }

        for (panel in panels) {
            if (x < panel.x || x > panel.x + PANEL_WIDTH) continue
            val panelHeight = calculatePanelHeight(panel, SystemClock.uptimeMillis())
            if (y < panel.y || y > panel.y + panelHeight) continue

            if (y <= panel.y + 59f) {
                panel.expanded = !panel.expanded
                panel.expansion.animateTo(if (panel.expanded) 1f else 0f, LiquidPeDurations.PANEL)
                return
            }

            var cy = panel.y + PANEL_CONTENT_TOP - panel.scrollOffset
            for (module in panel.modules) {
                if (!matches(module.name)) continue
                if (y >= cy && y < cy + 53f) {
                    if (module.hasSettings() && x > panel.x + 300f) {
                        module.settingsExpanded = !module.settingsExpanded
                        module.settingsProgress.animateTo(
                            if (module.settingsExpanded) 1f else 0f,
                            LiquidPeDurations.GROUP,
                        )
                    } else {
                        store.toggleModule(module.id)
                    }
                    return
                }
                cy += 53f + if (module.hasSettings()) {
                    measureSettings(module.settings, SystemClock.uptimeMillis()) * module.settingsProgress.get()
                } else {
                    0f
                }
            }
        }
    }

    private fun handleSettingTap(hit: SettingHit) {
        when (hit.action) {
            HIT_TOGGLE -> (hit.setting as? ToggleSetting)?.let { setting ->
                setting.value = !setting.value
                setting.progress.animateTo(if (setting.value) 1f else 0f, LiquidPeDurations.TOGGLE)
            }
            HIT_DROPDOWN -> (hit.setting as? DropdownSetting)?.let { it.open = !it.open }
            HIT_DROPDOWN_OPTION -> (hit.setting as? DropdownSetting)?.let { setting ->
                setting.value = setting.options.getOrElse(hit.index) { setting.value }
                setting.open = false
            }
            HIT_MULTI_OPTION -> (hit.setting as? MultiSelectSetting)?.let { setting ->
                val option = setting.options.getOrNull(hit.index) ?: return@let
                if (!setting.selected.remove(option)) setting.selected.add(option)
            }
            HIT_BIND -> (hit.setting as? BindSetting)?.let { it.listening = !it.listening }
            HIT_COLOR -> (hit.setting as? ColorSetting)?.let { it.expanded = !it.expanded }
            HIT_GROUP -> (hit.setting as? SettingGroup)?.let { group ->
                group.expanded = !group.expanded
                group.expansion.animateTo(if (group.expanded) 1f else 0f, LiquidPeDurations.GROUP)
            }
        }
    }

    private fun updateDrag(hit: SettingHit, x: Float, y: Float) {
        val p = ((x - hit.bounds.left) / max(1f, hit.bounds.width())).coerceIn(0f, 1f)
        when (hit.action) {
            HIT_SLIDER -> (hit.setting as? SliderSetting)?.let { it.value = it.min + (it.max - it.min) * p }
            HIT_RANGE_LOW -> (hit.setting as? RangeSetting)?.let {
                it.low = min(it.high, it.min + (it.max - it.min) * p)
            }
            HIT_RANGE_HIGH -> (hit.setting as? RangeSetting)?.let {
                it.high = max(it.low, it.min + (it.max - it.min) * p)
            }
            else -> (hit.setting as? ColorSetting)?.let { color ->
                when (hit.action) {
                    HIT_SV -> {
                        color.saturation = p
                        color.brightness = 1f - ((y - hit.bounds.top) / hit.bounds.height()).coerceIn(0f, 1f)
                    }
                    HIT_HUE -> color.hue = 360f * ((y - hit.bounds.top) / hit.bounds.height()).coerceIn(0f, 1f)
                    HIT_ALPHA -> color.alpha = 1f - ((y - hit.bounds.top) / hit.bounds.height()).coerceIn(0f, 1f)
                }
                color.updateColor()
            }
        }
    }

    // =====================================================================================
    // Panel layout / public controls
    // =====================================================================================

    private fun beginPanelDrag(panel: CategoryPanel, x: Float, y: Float) {
        if (panel.layoutInfo == null) return
        isEditMode = true
        longPressTriggered = true
        layoutDraggingPanel = panel
        originalPanelX = panel.x
        originalPanelY = panel.y
        dragOffsetX = x - panel.x
        dragOffsetY = y - panel.y
        updatePanelDrag(x, y)
    }

    private fun updatePanelDrag(x: Float, y: Float) {
        val panel = layoutDraggingPanel ?: return
        if (panel.layoutInfo == null) return
        panel.x = (x - dragOffsetX).coerceIn(0f, LiquidPeMetrics.CONTENT_WIDTH - PANEL_WIDTH)
        panel.y = (y - dragOffsetY).coerceIn(PANEL_GRID_TOP, LiquidPeMetrics.CONTENT_HEIGHT - PANEL_CONTENT_TOP)
        moved = true
    }

    private fun finishPanelDrag(commit: Boolean) {
        val panel = layoutDraggingPanel ?: return
        if (panel.layoutInfo == null) return
        if (!commit) {
            panel.x = originalPanelX
            panel.y = originalPanelY
        }
        panel.layoutInfo?.setFreePosition(panel.x, panel.y)
        layoutDraggingPanel = null
        savePanelLayout()
    }

    fun restorePanelLayout() {
        val persisted = layoutStorage.load()
        panels.forEachIndexed { index, panel ->
            val saved = persisted[panel.id]
            val spanY = if (panel.id == "player") 2 else 1
            val info = if (saved == null) {
                PanelLayoutInfo(
                    panelId = panel.id,
                    gridX = if (index < PANEL_GRID_COLUMNS) index else 0,
                    gridY = if (index < PANEL_GRID_COLUMNS) 0 else 1,
                    spanX = 1,
                    spanY = spanY,
                    visible = true,
                )
            } else {
                PanelLayoutInfo(
                    panelId = panel.id,
                    gridX = saved.gridX,
                    gridY = saved.gridY,
                    spanX = saved.spanX.coerceIn(1, PANEL_GRID_COLUMNS),
                    spanY = saved.spanY.coerceAtLeast(1),
                    visible = saved.visible,
                ).also {
                    it.hasFreePosition = saved.hasFreePosition
                    it.freeX = saved.freeX
                    it.freeY = saved.freeY
                }
            }
            panel.layoutInfo = info

            if (info.hasFreePosition) {
                panel.x = info.freeX.coerceIn(0f, LiquidPeMetrics.CONTENT_WIDTH - PANEL_WIDTH)
                panel.y = info.freeY.coerceIn(PANEL_GRID_TOP, LiquidPeMetrics.CONTENT_HEIGHT - PANEL_CONTENT_TOP)
                info.setFreePosition(panel.x, panel.y)
            } else {
                panel.x = gridToX(min(PANEL_GRID_COLUMNS - info.spanX, info.gridX).coerceAtLeast(0))
                panel.y = gridToY(max(0, info.gridY))
                info.setFreePosition(panel.x, panel.y)
            }
        }
        savePanelLayout()
    }

    fun savePanelLayout() {
        layoutStorage.save(panels.mapNotNull { it.layoutInfo })
    }

    fun resetPanelLayout() {
        layoutStorage.clear()
        panels.forEachIndexed { index, panel ->
            val spanY = if (panel.id == "player") 2 else 1
            panel.layoutInfo = PanelLayoutInfo(panel.id, -1, -1, 1, spanY, true)
            panel.x = gridToX(if (index < PANEL_GRID_COLUMNS) index else 0)
            panel.y = gridToY(if (index < PANEL_GRID_COLUMNS) 0 else 1)
            panel.layoutInfo?.setFreePosition(panel.x, panel.y)
        }
        savePanelLayout()
    }

    fun enterEditMode() {
        isEditMode = true
    }

    fun exitEditMode() {
        cancelPanelLongPress()
        if (layoutDraggingPanel != null) finishPanelDrag(false)
        isEditMode = false
        savePanelLayout()
    }

    fun onBackPressed(): Boolean {
        if (!isEditMode) return false
        exitEditMode()
        return true
    }

    fun onKeyPressed(keyName: String?): ModuleEntry? {
        for (panel in panels) {
            for (module in panel.modules) {
                val listening = findListeningBind(module.settings) ?: continue
                listening.value = keyName?.takeIf { it.isNotEmpty() } ?: "None"
                listening.listening = false
                store.setModuleKeyBind(module.id, listening.value)
                return module
            }
        }
        return null
    }

    fun setActiveTab(tab: TopTab) {
        activeTab = tab
        listener.onTopTabChanged(tab)
    }

    fun getActiveTab(): TopTab = activeTab

    fun setSearchQuery(query: String?) {
        searchQuery = query?.trim().orEmpty()
    }

    fun getSearchQuery(): String = searchQuery

    fun hasActiveAnimations(now: Long): Boolean {
        panels.forEach { panel ->
            if (panel.expansion.isRunning(now) || abs(panel.targetScrollOffset - panel.scrollOffset) > .5f) {
                return true
            }
            panel.modules.forEach { module ->
                if (module.enabledProgress.isRunning(now) || module.settingsProgress.isRunning(now)) {
                    return true
                }
                if (settingsAnimating(module.settings, now)) return true
            }
        }
        return false
    }

    private fun settingsAnimating(settings: List<SettingEntry>, now: Long): Boolean {
        settings.forEach { setting ->
            if (setting is ToggleSetting && setting.progress.isRunning(now)) return true
            if (setting is SettingGroup &&
                (setting.expansion.isRunning(now) || settingsAnimating(setting.children, now))
            ) {
                return true
            }
        }
        return false
    }

    private fun gridToX(gridX: Int): Float =
        PANEL_GRID_LEFT + gridX * (PANEL_GRID_CELL_WIDTH + PANEL_GRID_GAP_X)

    private fun gridToY(gridY: Int): Float =
        PANEL_GRID_TOP + gridY * (PANEL_GRID_CELL_HEIGHT + PANEL_GRID_GAP_Y)

    private fun cancelPanelLongPress() {
        longPressHandler.removeCallbacks(longPressRunnable)
    }

    private fun findHit(x: Float, y: Float): SettingHit? {
        if (!isInsideVisiblePanel(x, y)) return null
        for (i in min(settingHitCount, settingHits.size) - 1 downTo 0) {
            if (settingHits[i].bounds.contains(x, y)) return settingHits[i]
        }
        return null
    }

    private fun isInsideVisiblePanel(x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        return panels.any {
            x >= it.x && x <= it.x + PANEL_WIDTH && y >= it.y && y <= it.y + calculatePanelHeight(it, now)
        }
    }

    private fun closeDropdowns(setting: SettingEntry) {
        when (setting) {
            is DropdownSetting -> setting.open = false
            is SettingGroup -> setting.children.forEach { closeDropdowns(it) }
            else -> Unit
        }
    }

    private fun findListeningBind(list: List<SettingEntry>): BindSetting? {
        list.forEach { setting ->
            if (setting is BindSetting && setting.listening) return setting
            if (setting is SettingGroup) {
                findListeningBind(setting.children)?.let { return it }
            }
        }
        return null
    }

    private fun addHit(setting: SettingEntry, action: Int, index: Int, left: Float, top: Float, right: Float, bottom: Float): SettingHit {
        val hit = settingHits[min(settingHitCount++, settingHits.size - 1)]
        hit.setting = setting
        hit.action = action
        hit.index = index
        hit.bounds.set(left, top, right, bottom)
        return hit
    }

    private fun matches(name: String): Boolean =
        searchQuery.isEmpty() || name.lowercase(Locale.ROOT).contains(searchQuery.lowercase(Locale.ROOT))

    // =====================================================================================
    // Paint helpers
    // =====================================================================================

    private fun control(referenceSize: Float): Float = typography.control(referenceSize)

    private fun setFill(color: Int) {
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.color = color
    }

    private fun setText(color: Int, size: Float, face: Typeface) {
        text.color = color
        text.textSize = typography.size(size)
        text.typeface = face
    }

    private fun drawCentered(c: Canvas, s: String, x: Float, y: Float, w: Float, h: Float, p: Paint) {
        val fm = p.fontMetrics
        c.drawText(s, x + (w - p.measureText(s)) * .5f, y + (h - (fm.descent - fm.ascent)) * .5f - fm.ascent, p)
    }

    private fun drawBaseline(c: Canvas, s: String, x: Float, y: Float, h: Float, p: Paint) {
        val fm = p.fontMetrics
        c.drawText(s, x, y + (h - (fm.descent - fm.ascent)) * .5f - fm.ascent, p)
    }

    private fun drawRightBaseline(c: Canvas, s: String, right: Float, y: Float, h: Float, p: Paint) {
        drawBaseline(c, s, right - p.measureText(s), y, h, p)
    }

    private fun formatValue(value: Float): String {
        if (abs(value - Math.round(value)) < .0001f) return Math.round(value).toString()
        val raw = String.format(Locale.US, if (value < 1f) "%.3f" else "%.2f", value)
        return raw.replace(Regex("0+$"), "").replace(Regex("\\.$"), "")
    }

    private fun toRgbaHex(color: Int): String = String.format(
        Locale.US, "#%02X%02X%02X%02X",
        Color.red(color), Color.green(color), Color.blue(color), Color.alpha(color),
    )
}
