package com.elainaclient.overlay.gui.dream

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.elainaclient.game.BoolValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.Value
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A tap target inside the Dream panel. */
sealed interface DreamHit {
    data object Back : DreamHit
    data object ToggleEnabled : DreamHit
    data object ToggleShortcut : DreamHit
    data class Toggle(val value: Value<*>) : DreamHit
    data class Option(val value: Value<*>, val anchorTop: Float) : DreamHit
}

/**
 * Layout + Canvas renderer for the Dream click GUI.
 *
 * This is a Kotlin rewrite of OpenMoxel/OpenClickGUI's `Dream/.../ClickGuiView.java`: the drawing
 * order, reference geometry, icons, switches, sliders and category animation are preserved; the
 * demo module repository is replaced by [DreamBridge], and the original Range/FOV/Delay rows are
 * replaced by real module values. The decorative magnifier now opens a live module search, and the
 * two top-right window glyphs are reduced to one exit button. Hit-testing lives here too so the
 * view and renderer cannot drift apart.
 */
class DreamRenderer(private val bridge: DreamBridge = DreamBridge) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()
    private val clipPath = Path()

    private val regularTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private class PopupGeometry(val rect: RectF, val itemHeight: Float)

    // =========================================================================================
    // Drawing
    // =========================================================================================

    fun draw(canvas: Canvas, state: DreamUiState) {
        drawPanelShell(canvas)
        drawTopBar(canvas, state)
        drawSideBar(canvas, state.category)

        val alpha = (255f * state.contentAlpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        val layer = canvas.saveLayerAlpha(
            DreamStyle.SIDE_WIDTH,
            DreamStyle.TOP_BAR_HEIGHT,
            DreamStyle.REFERENCE_WIDTH,
            DreamStyle.REFERENCE_HEIGHT,
            alpha,
        )
        canvas.translate(state.contentTranslation, 0f)
        canvas.save()
        canvas.clipRect(
            DreamStyle.CONTENT_LEFT,
            DreamStyle.CONTENT_TOP,
            DreamStyle.CONTENT_RIGHT,
            DreamStyle.CONTENT_BOTTOM,
        )
        if (state.selectedModule == null) {
            drawModuleList(canvas, state)
        } else {
            drawModuleDetail(canvas, state, state.selectedModule!!)
        }
        canvas.restore()
        canvas.restoreToCount(layer)

        state.popup?.let { drawPopup(canvas, it) }
    }

    private fun drawPanelShell(canvas: Canvas) {
        rect.set(0f, 0f, DreamStyle.REFERENCE_WIDTH, DreamStyle.REFERENCE_HEIGHT)
        clipPath.reset()
        clipPath.addRoundRect(rect, DreamStyle.CORNER_PANEL, DreamStyle.CORNER_PANEL, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        paint.shader = null
        paint.color = DreamStyle.COLOR_BODY
        canvas.drawRect(0f, 0f, DreamStyle.REFERENCE_WIDTH, DreamStyle.REFERENCE_HEIGHT, paint)
        paint.color = DreamStyle.COLOR_TOP
        canvas.drawRect(0f, 0f, DreamStyle.REFERENCE_WIDTH, DreamStyle.TOP_BAR_HEIGHT, paint)
        paint.color = DreamStyle.COLOR_SIDE
        canvas.drawRect(
            0f,
            DreamStyle.TOP_BAR_HEIGHT,
            DreamStyle.SIDE_WIDTH,
            DreamStyle.REFERENCE_HEIGHT,
            paint,
        )
        paint.color = 0x553B3A5B
        canvas.drawRect(
            DreamStyle.SIDE_WIDTH,
            DreamStyle.TOP_BAR_HEIGHT,
            DreamStyle.SIDE_WIDTH + 1.5f,
            DreamStyle.REFERENCE_HEIGHT - 14f,
            paint,
        )
        paint.color = 0x663A3C5D
        canvas.drawRect(
            0f,
            DreamStyle.TOP_BAR_HEIGHT - 1f,
            DreamStyle.REFERENCE_WIDTH,
            DreamStyle.TOP_BAR_HEIGHT,
            paint,
        )
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.color = DreamStyle.COLOR_BORDER
        canvas.drawRoundRect(rect, DreamStyle.CORNER_PANEL, DreamStyle.CORNER_PANEL, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawTopBar(canvas: Canvas, state: DreamUiState) {
        drawHamburger(canvas, 45f, 50f)

        // Brand mark + "Elaina".
        paint.shader = null
        paint.color = DreamStyle.COLOR_ACCENT_DEEP
        canvas.drawCircle(99f, 50f, 15f, paint)
        paint.color = DreamStyle.COLOR_TEXT
        canvas.drawCircle(94f, 47f, 2.2f, paint)
        canvas.drawCircle(102f, 47f, 2.2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawArc(92f, 49f, 106f, 60f, 15f, 150f, false, paint)
        paint.style = Paint.Style.FILL
        drawText(canvas, "Elaina", 128f, 59f, 25f, DreamStyle.COLOR_TEXT, true)

        drawSearch(
            canvas,
            319f,
            50f,
            if (state.searchActive) DreamStyle.COLOR_ACCENT_END else DreamStyle.COLOR_TEXT,
        )

        if (state.searchActive) {
            drawSearchField(canvas, state)
        } else {
            val selected = state.selectedModule
            val crumb = selected?.let { bridge.title(it) } ?: bridge.categoryLabel(state.category)
            val crumbCategory = selected?.category ?: state.category
            rect.set(354f, 25f, 604f, 66f)
            paint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                DreamStyle.COLOR_ACCENT_START,
                DreamStyle.COLOR_ACCENT_END,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(rect, 9f, 9f, paint)
            paint.shader = null
            drawCategoryIcon(canvas, crumbCategory, 382f, 45f, DreamStyle.COLOR_TEXT)
            drawText(
                canvas,
                fitText(crumb, 150f, 20f, false),
                404f,
                53f,
                20f,
                DreamStyle.COLOR_TEXT,
                false,
            )
            drawChevron(canvas, 579f, 46f, DreamStyle.COLOR_TEXT)
        }

        // Single exit button (top-right).
        drawClose(canvas, 1367f, 49f)
    }

    private fun drawSearchField(canvas: Canvas, state: DreamUiState) {
        rect.set(354f, 25f, 820f, 66f)
        paint.shader = null
        paint.color = DreamStyle.COLOR_CARD
        canvas.drawRoundRect(rect, 9f, 9f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = DreamStyle.COLOR_ACCENT_DEEP
        canvas.drawRoundRect(rect, 9f, 9f, paint)
        paint.style = Paint.Style.FILL

        val hint = state.query.ifEmpty { "搜索模块…" }
        drawText(
            canvas,
            fitText(hint, 390f, 20f, false),
            374f,
            53f,
            20f,
            if (state.query.isEmpty()) DreamStyle.COLOR_SECONDARY else DreamStyle.COLOR_TEXT,
            false,
        )

        // Clear the text but stay in search mode.
        paint.color = DreamStyle.COLOR_SECONDARY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(792f, 41f, 804f, 53f, paint)
        canvas.drawLine(804f, 41f, 792f, 53f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSideBar(canvas: Canvas, currentCategory: ModuleCategory) {
        val categories = bridge.categories
        categories.forEachIndexed { index, category ->
            val top = 105f + index * 50f
            val selected = category == currentCategory
            if (selected) {
                paint.shader = null
                paint.color = DreamStyle.COLOR_SELECTED
                canvas.drawRoundRect(15f, top, DreamStyle.SIDE_WIDTH - 17f, top + 46f, 9f, 9f, paint)
                paint.color = DreamStyle.COLOR_TEXT
                canvas.drawRoundRect(25f, top + 7f, 28f, top + 39f, 2f, 2f, paint)
            }
            drawCategoryIcon(canvas, category, 55f, top + 23f, DreamStyle.COLOR_TEXT)
            drawText(
                canvas,
                bridge.categoryLabel(category),
                82f,
                top + 30f,
                21f,
                if (selected) DreamStyle.COLOR_TEXT else DreamStyle.COLOR_SECONDARY,
                selected,
            )
        }

        paint.shader = null
        paint.color = DreamStyle.COLOR_DIVIDER
        canvas.drawRect(26f, 470f, DreamStyle.SIDE_WIDTH - 27f, 471f, paint)
        drawDocument(canvas, 55f, 507f, DreamStyle.COLOR_TEXT)
        val enabled = bridge.categories.sumOf { bridge.enabledCount(it) }
        drawText(canvas, "已启用 $enabled", 82f, 514f, 21f, DreamStyle.COLOR_SECONDARY, false)

        drawTextCentered(canvas, "Elaina", DreamStyle.SIDE_WIDTH / 2f, 774f, 21f, DreamStyle.COLOR_TEXT, true)
        drawTextCentered(canvas, "B2", DreamStyle.SIDE_WIDTH / 2f, 796f, 17f, DreamStyle.COLOR_SECONDARY, false)
    }

    private fun drawModuleList(canvas: Canvas, state: DreamUiState) {
        val modules = visibleModules(state)
        state.listScroll = state.listScroll.coerceIn(0f, maxListScroll(state))

        canvas.save()
        canvas.translate(0f, -state.listScroll)
        modules.forEachIndexed { index, module ->
            drawListCard(canvas, module, DreamStyle.CONTENT_TOP + index * DreamStyle.CARD_STEP)
        }
        canvas.restore()

        val label = if (state.query.isBlank()) {
            "分类：${bridge.categoryLabel(state.category)}"
        } else {
            "搜索：${state.query}"
        }
        drawText(
            canvas,
            fitText(label, 130f, 18f, false),
            1252f,
            792f,
            18f,
            DreamStyle.COLOR_SECONDARY,
            false,
        )
    }

    /** Current category, or search results across all categories while a query is typed. */
    private fun visibleModules(state: DreamUiState): List<Module> =
        if (state.query.isBlank()) bridge.modules(state.category) else bridge.search(state.query)

    fun moduleAt(state: DreamUiState, index: Int): Module? =
        visibleModules(state).getOrNull(index)

    private fun drawListCard(canvas: Canvas, module: Module, top: Float) {
        rect.set(
            DreamStyle.CONTENT_LEFT,
            top,
            DreamStyle.CONTENT_RIGHT,
            top + DreamStyle.CARD_HEIGHT,
        )
        paint.shader = null
        paint.color = if (module.isEnabled) DreamStyle.COLOR_CARD_ACTIVE else DreamStyle.COLOR_CARD
        canvas.drawRoundRect(rect, DreamStyle.CORNER_CARD, DreamStyle.CORNER_CARD, paint)

        val titleWidth = DreamStyle.CONTENT_RIGHT - (DreamStyle.CONTENT_LEFT + 20f) - 120f
        drawText(
            canvas,
            fitText(bridge.title(module), titleWidth, 27f, false),
            DreamStyle.CONTENT_LEFT + 20f,
            top + 45f,
            27f,
            if (module.isEnabled) DreamStyle.COLOR_ACCENT_END else DreamStyle.COLOR_TEXT,
            false,
        )
        drawText(
            canvas,
            bridge.controlsLabel(module),
            DreamStyle.CONTENT_LEFT + 20f,
            top + 72f,
            19f,
            DreamStyle.COLOR_SECONDARY,
            false,
        )
        drawSwitch(canvas, DreamStyle.CONTENT_RIGHT - 72f, top + 53f, module.isEnabled)
    }

    private fun drawModuleDetail(canvas: Canvas, state: DreamUiState, module: Module) {
        drawDetailHeader(canvas, module)

        val values = bridge.settings(module)
        state.detailScroll = state.detailScroll.coerceIn(0f, maxDetailScroll(state))

        canvas.save()
        canvas.clipRect(
            DreamStyle.CONTENT_LEFT,
            DreamStyle.DETAIL_ROWS_TOP,
            DreamStyle.CONTENT_RIGHT,
            DreamStyle.CONTENT_BOTTOM,
        )
        canvas.translate(0f, -state.detailScroll)
        drawShortcutRow(canvas, module, DreamStyle.DETAIL_ROWS_TOP)
        values.forEachIndexed { index, value ->
            drawSettingRow(
                canvas,
                value,
                DreamStyle.DETAIL_SETTINGS_TOP + index * DreamStyle.ROW_STEP,
            )
        }
        canvas.restore()

        drawText(
            canvas,
            "${values.size} 项设置",
            1266f,
            792f,
            18f,
            DreamStyle.COLOR_SECONDARY,
            false,
        )
    }

    private fun drawDetailHeader(canvas: Canvas, module: Module) {
        rect.set(
            DreamStyle.CONTENT_LEFT,
            DreamStyle.CONTENT_TOP,
            DreamStyle.CONTENT_RIGHT,
            DreamStyle.CONTENT_TOP + DreamStyle.DETAIL_HEADER_HEIGHT,
        )
        paint.shader = null
        paint.color = DreamStyle.COLOR_CARD
        canvas.drawRoundRect(rect, DreamStyle.CORNER_CARD, DreamStyle.CORNER_CARD, paint)

        paint.color = DreamStyle.COLOR_BACK_BG
        canvas.drawRoundRect(386f, 122f, 505f, 181f, 10f, 10f, paint)
        drawText(canvas, "< 返回", 405f, 159f, 22f, DreamStyle.COLOR_TEXT, true)

        drawText(
            canvas,
            fitText(bridge.title(module), 600f, 29f, true),
            528f,
            164f,
            29f,
            DreamStyle.COLOR_TEXT,
            true,
        )
        drawText(canvas, "启用", 1185f, 161f, 20f, DreamStyle.COLOR_SECONDARY, false)
        drawSwitch(canvas, DreamStyle.SWITCH_CENTER_X, 153f, module.isEnabled)
    }

    private fun drawShortcutRow(canvas: Canvas, module: Module, top: Float) {
        drawRowCard(canvas, top)
        drawText(canvas, "显示快捷按钮", 386f, top + 56f, 24f, DreamStyle.COLOR_TEXT, false)
        drawSwitch(canvas, DreamStyle.SWITCH_CENTER_X, top + 44f, module.isShortcutDisplayed)
    }

    private fun drawSettingRow(canvas: Canvas, value: Value<*>, top: Float) {
        drawRowCard(canvas, top)
        drawText(
            canvas,
            fitText(bridge.settingLabel(value), 650f, 23f, false),
            386f,
            top + 56f,
            23f,
            DreamStyle.COLOR_TEXT,
            false,
        )

        when (value) {
            is BoolValue -> drawSwitch(
                canvas,
                DreamStyle.SWITCH_CENTER_X,
                top + 44f,
                value.value,
            )

            else -> if (bridge.isSlider(value)) {
                drawSlider(canvas, bridge.fractionOf(value), top)
                drawText(
                    canvas,
                    bridge.displayValue(value),
                    1306f,
                    top + 56f,
                    20f,
                    DreamStyle.COLOR_TEXT,
                    false,
                )
            } else if (bridge.isOption(value)) {
                rect.set(
                    DreamStyle.OPTION_LEFT,
                    top + 14f,
                    DreamStyle.OPTION_RIGHT,
                    top + 75f,
                )
                paint.shader = LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    DreamStyle.COLOR_ACCENT_START,
                    DreamStyle.COLOR_ACCENT_END,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                paint.shader = null
                drawTextCentered(
                    canvas,
                    bridge.displayValue(value) + " >",
                    rect.centerX(),
                    top + 53f,
                    20f,
                    DreamStyle.COLOR_TEXT,
                    false,
                )
            }
        }
    }

    private fun drawRowCard(canvas: Canvas, top: Float) {
        rect.set(DreamStyle.CONTENT_LEFT, top, DreamStyle.CONTENT_RIGHT, top + DreamStyle.ROW_HEIGHT)
        paint.shader = null
        paint.color = DreamStyle.COLOR_CARD
        canvas.drawRoundRect(rect, DreamStyle.CORNER_CARD, DreamStyle.CORNER_CARD, paint)
    }

    private fun drawSlider(canvas: Canvas, fraction: Float, top: Float) {
        val y = top + 45f
        paint.shader = null
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 10f
        paint.color = DreamStyle.COLOR_TRACK_EMPTY
        canvas.drawLine(DreamStyle.SLIDER_START, y, DreamStyle.SLIDER_END, y, paint)

        val knobX = DreamStyle.SLIDER_START +
            fraction.coerceIn(0f, 1f) * (DreamStyle.SLIDER_END - DreamStyle.SLIDER_START)
        paint.shader = LinearGradient(
            DreamStyle.SLIDER_START,
            y,
            DreamStyle.SLIDER_END,
            y,
            DreamStyle.COLOR_ACCENT_START,
            DreamStyle.COLOR_ACCENT_END,
            Shader.TileMode.CLAMP,
        )
        canvas.drawLine(DreamStyle.SLIDER_START, y, knobX, y, paint)
        paint.shader = null
        paint.color = DreamStyle.COLOR_KNOB
        canvas.drawCircle(knobX, y, 16f, paint)
        paint.color = 0x40383357
        canvas.drawCircle(knobX, y + 2f, 18f, paint)
    }

    private fun drawSwitch(canvas: Canvas, centerX: Float, centerY: Float, enabled: Boolean) {
        rect.set(centerX - 40f, centerY - 25f, centerX + 40f, centerY + 25f)
        paint.shader = if (enabled) {
            LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                DreamStyle.COLOR_ACCENT_START,
                DreamStyle.COLOR_ACCENT_END,
                Shader.TileMode.CLAMP,
            )
        } else {
            null
        }
        paint.color = if (enabled) DreamStyle.COLOR_TEXT else DreamStyle.COLOR_OFF_TRACK
        canvas.drawRoundRect(rect, 25f, 25f, paint)
        paint.shader = null
        paint.color = if (enabled) DreamStyle.COLOR_TEXT else DreamStyle.COLOR_OFF_KNOB
        canvas.drawCircle(if (enabled) centerX + 20f else centerX - 20f, centerY, 19f, paint)
    }

    // =========================================================================================
    // Popup
    // =========================================================================================

    private fun drawPopup(canvas: Canvas, popup: DreamOptionPopup) {
        val geometry = popupGeometry(popup) ?: return
        paint.shader = null
        paint.color = DreamStyle.COLOR_POPUP_BG
        canvas.drawRoundRect(geometry.rect, DreamStyle.CORNER_SMALL, DreamStyle.CORNER_SMALL, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = DreamStyle.COLOR_BORDER
        canvas.drawRoundRect(geometry.rect, DreamStyle.CORNER_SMALL, DreamStyle.CORNER_SMALL, paint)
        paint.style = Paint.Style.FILL

        popup.options.forEachIndexed { index, option ->
            val itemTop = geometry.rect.top + index * geometry.itemHeight
            if (index == popup.selectedIndex) {
                rect.set(
                    geometry.rect.left + 4f,
                    itemTop + 3f,
                    geometry.rect.right - 4f,
                    itemTop + geometry.itemHeight - 3f,
                )
                paint.shader = LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    DreamStyle.COLOR_ACCENT_START,
                    DreamStyle.COLOR_ACCENT_END,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.shader = null
            }
            drawTextCentered(
                canvas,
                fitText(
                    bridge.optionLabel(option),
                    geometry.rect.width() - 26f,
                    20f,
                    false,
                ),
                geometry.rect.centerX(),
                itemTop + geometry.itemHeight / 2f + 7f,
                20f,
                DreamStyle.COLOR_TEXT,
                index == popup.selectedIndex,
            )
        }
    }

    private fun popupGeometry(popup: DreamOptionPopup): PopupGeometry? {
        val count = popup.options.size
        if (count == 0) return null
        val available = DreamStyle.CONTENT_BOTTOM - DreamStyle.CONTENT_TOP
        val itemHeight = min(DreamStyle.POPUP_ITEM_HEIGHT, available / count)
        val total = itemHeight * count
        var top = popup.anchorTop
        if (top + total > DreamStyle.CONTENT_BOTTOM) top = popup.anchorTop - total
        top = top.coerceIn(DreamStyle.CONTENT_TOP, DreamStyle.CONTENT_BOTTOM - total)
        return PopupGeometry(RectF(1083f, top, 1371f, top + total), itemHeight)
    }

    // =========================================================================================
    // Hit testing
    // =========================================================================================

    fun categoryAt(x: Float, y: Float): ModuleCategory? {
        if (x < 0f || x >= DreamStyle.SIDE_WIDTH || y < 105f) return null
        val index = ((y - 105f) / 50f).toInt()
        if (index !in bridge.categories.indices) return null
        val top = 105f + index * 50f
        if (y > top + 46f) return null
        return bridge.categories[index]
    }

    fun moduleIndexAt(state: DreamUiState, x: Float, y: Float): Int? {
        if (state.selectedModule != null) return null
        if (x < DreamStyle.CONTENT_LEFT || x > DreamStyle.CONTENT_RIGHT) return null
        if (y < DreamStyle.CONTENT_TOP || y > DreamStyle.CONTENT_BOTTOM) return null
        val scroll = state.listScroll.coerceIn(0f, maxListScroll(state))
        val local = y + scroll - DreamStyle.CONTENT_TOP
        if (local < 0f) return null
        val index = (local / DreamStyle.CARD_STEP).toInt()
        val modules = visibleModules(state)
        if (index !in modules.indices) return null
        if (local - index * DreamStyle.CARD_STEP > DreamStyle.CARD_HEIGHT) return null
        return index
    }

    fun isSwitchTap(x: Float): Boolean = x > DreamStyle.CONTENT_RIGHT - 130f

    /** Magnifier in the top bar toggles the search field. */
    fun searchTap(x: Float, y: Float): Boolean =
        y in 25f..75f && x in 292f..346f

    /** "X" inside the search field clears the query but stays in search mode. */
    fun searchClearTap(x: Float, y: Float): Boolean =
        y in 30f..62f && x in 780f..818f

    /** The single exit button at the top-right. */
    fun closeTap(x: Float, y: Float): Boolean =
        y in 20f..80f && x in 1340f..1394f

    fun outsidePanel(x: Float, y: Float): Boolean =
        x < 0f || x > DreamStyle.REFERENCE_WIDTH || y < 0f || y > DreamStyle.REFERENCE_HEIGHT

    fun detailHit(state: DreamUiState, x: Float, y: Float): DreamHit? {
        val module = state.selectedModule ?: return null

        if (y in DreamStyle.CONTENT_TOP..(DreamStyle.CONTENT_TOP + DreamStyle.DETAIL_HEADER_HEIGHT)) {
            if (x < 515f) return DreamHit.Back
            if (x > 1240f) return DreamHit.ToggleEnabled
            return null
        }
        if (y < DreamStyle.DETAIL_ROWS_TOP || y > DreamStyle.CONTENT_BOTTOM) return null

        val scroll = state.detailScroll.coerceIn(0f, maxDetailScroll(state))
        val local = y + scroll
        if (local in DreamStyle.DETAIL_ROWS_TOP..(DreamStyle.DETAIL_ROWS_TOP + DreamStyle.ROW_HEIGHT)) {
            return if (x > 1210f) DreamHit.ToggleShortcut else null
        }

        val offset = local - DreamStyle.DETAIL_SETTINGS_TOP
        if (offset < 0f) return null
        val index = (offset / DreamStyle.ROW_STEP).toInt()
        val values = bridge.settings(module)
        if (index !in values.indices) return null
        if (offset - index * DreamStyle.ROW_STEP > DreamStyle.ROW_HEIGHT) return null

        val value = values[index]
        val top = DreamStyle.DETAIL_SETTINGS_TOP + index * DreamStyle.ROW_STEP
        return when {
            value is BoolValue && x > 1210f -> DreamHit.Toggle(value)
            // The popup is drawn outside the scrolled layer, so anchor it at the row's
            // on-screen (post-scroll) bottom edge.
            bridge.isOption(value) && x > 1040f ->
                DreamHit.Option(
                    value,
                    top - scroll + DreamStyle.ROW_HEIGHT + 2f,
                )

            else -> null
        }
    }

    /** Returns the slider under the pointer, if the row is a float/int value. */
    fun sliderValueAt(state: DreamUiState, x: Float, y: Float): Value<*>? {
        if (state.selectedModule == null) return null
        if (x < 455f || x > 1300f) return null
        if (y < DreamStyle.DETAIL_ROWS_TOP || y > DreamStyle.CONTENT_BOTTOM) return null

        val local = y + state.detailScroll.coerceIn(0f, maxDetailScroll(state))
        val offset = local - DreamStyle.DETAIL_SETTINGS_TOP
        if (offset < 0f) return null
        val index = (offset / DreamStyle.ROW_STEP).toInt()
        val values = bridge.settings(state.selectedModule!!)
        if (index !in values.indices) return null
        if (offset - index * DreamStyle.ROW_STEP > DreamStyle.ROW_HEIGHT) return null
        val value = values[index]
        return if (bridge.isSlider(value)) value else null
    }

    fun popupIndexAt(state: DreamUiState, x: Float, y: Float): Int {
        val popup = state.popup ?: return -1
        val geometry = popupGeometry(popup) ?: return -1
        if (!geometry.rect.contains(x, y)) return -1
        val index = ((y - geometry.rect.top) / geometry.itemHeight).toInt()
        return if (index in popup.options.indices) index else -1
    }

    fun maxListScroll(state: DreamUiState): Float {
        val count = visibleModules(state).size
        return max(0f, count * DreamStyle.CARD_STEP - (DreamStyle.CONTENT_BOTTOM - DreamStyle.CONTENT_TOP))
    }

    fun maxDetailScroll(state: DreamUiState): Float {
        val module = state.selectedModule ?: return 0f
        val count = bridge.settings(module).size
        val contentHeight = DreamStyle.ROW_HEIGHT + count * DreamStyle.ROW_STEP
        return max(0f, contentHeight - (DreamStyle.CONTENT_BOTTOM - DreamStyle.DETAIL_ROWS_TOP))
    }

    // =========================================================================================
    // Text / icons
    // =========================================================================================

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        medium: Boolean,
    ) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.typeface = if (medium) mediumTypeface else regularTypeface
        paint.textSize = size
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawTextCentered(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baseline: Float,
        size: Float,
        color: Int,
        medium: Boolean,
    ) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.typeface = if (medium) mediumTypeface else regularTypeface
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, centerX, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun fitText(text: String, maxWidth: Float, size: Float, medium: Boolean): String {
        paint.typeface = if (medium) mediumTypeface else regularTypeface
        paint.textSize = size
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end) + "…"
            if (paint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return ""
    }

    private fun drawHamburger(canvas: Canvas, x: Float, y: Float) {
        paint.shader = null
        paint.color = DreamStyle.COLOR_TEXT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - 13f, y - 9f, x + 13f, y - 9f, paint)
        canvas.drawLine(x - 13f, y, x + 13f, y, paint)
        canvas.drawLine(x - 13f, y + 9f, x + 13f, y + 9f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSearch(canvas: Canvas, x: Float, y: Float, color: Int) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        canvas.drawCircle(x - 2f, y - 3f, 8f, paint)
        canvas.drawLine(x + 4f, y + 3f, x + 12f, y + 11f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawClose(canvas: Canvas, x: Float, y: Float) {
        paint.shader = null
        paint.color = DreamStyle.COLOR_TEXT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - 9f, y - 9f, x + 9f, y + 9f, paint)
        canvas.drawLine(x + 9f, y - 9f, x - 9f, y + 9f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawChevron(canvas: Canvas, x: Float, y: Float, color: Int) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f
        val path = Path()
        path.moveTo(x - 3f, y - 6f)
        path.lineTo(x + 4f, y)
        path.lineTo(x - 3f, y + 6f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCategoryIcon(
        canvas: Canvas,
        category: ModuleCategory,
        x: Float,
        y: Float,
        color: Int,
    ) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        when (category) {
            ModuleCategory.Combat -> {
                canvas.drawLine(x - 9f, y + 9f, x + 9f, y - 9f, paint)
                canvas.drawLine(x - 10f, y + 1f, x - 3f, y + 8f, paint)
                canvas.drawLine(x + 1f, y - 8f, x + 8f, y - 1f, paint)
            }

            ModuleCategory.Motion -> {
                canvas.drawCircle(x, y - 8f, 3f, paint)
                canvas.drawLine(x, y - 4f, x - 2f, y + 3f, paint)
                canvas.drawLine(x - 2f, y + 3f, x - 9f, y + 8f, paint)
                canvas.drawLine(x - 1f, y + 2f, x + 7f, y + 8f, paint)
                canvas.drawLine(x - 1f, y - 1f, x + 7f, y + 1f, paint)
            }

            ModuleCategory.Visual -> {
                rect.set(x - 13f, y - 8f, x + 13f, y + 8f)
                canvas.drawOval(rect, paint)
                canvas.drawCircle(x, y, 4f, paint)
            }

            ModuleCategory.World -> {
                canvas.drawCircle(x, y, 11f, paint)
                canvas.drawLine(x - 10f, y, x + 10f, y, paint)
                canvas.drawArc(x - 6f, y - 11f, x + 6f, y + 11f, 90f, 180f, false, paint)
            }

            ModuleCategory.Misc, ModuleCategory.Config -> {
                canvas.drawRoundRect(x - 10f, y - 10f, x + 10f, y + 10f, 3f, 3f, paint)
                canvas.drawLine(x - 5f, y - 5f, x + 5f, y - 5f, paint)
                canvas.drawLine(x - 5f, y + 5f, x + 5f, y + 5f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawDocument(canvas: Canvas, x: Float, y: Float, color: Int) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        val path = Path()
        path.moveTo(x - 8f, y - 11f)
        path.lineTo(x + 4f, y - 11f)
        path.lineTo(x + 9f, y - 6f)
        path.lineTo(x + 9f, y + 11f)
        path.lineTo(x - 8f, y + 11f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawLine(x + 4f, y - 11f, x + 4f, y - 6f, paint)
        canvas.drawLine(x + 4f, y - 6f, x + 9f, y - 6f, paint)
        paint.style = Paint.Style.FILL
    }
}
