package com.elainaclient.overlay.gui.dream

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.elainaclient.game.BoolValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.Value
import com.elainaclient.overlay.UISettings
import kotlin.math.hypot
import kotlin.math.min

/**
 * Self-drawn Dream click GUI.
 *
 * Kotlin port of OpenMoxel/OpenClickGUI's Dream `ClickGuiView`: the 1411x820 reference frame,
 * panel geometry, icons and animations are preserved, while taps act on Elaina's live modules
 * through [DreamBridge]. Additions required by an overlay host: centred reference scaling + the
 * per-theme UI scale, vertical scrolling (real module lists are longer than Dream's demo list),
 * Android back-key handling, and the real enum/list option popup.
 */
class DreamOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val renderer = DreamRenderer()
    private val state = DreamUiState()

    private var uiScale = 1f
    private var fitScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var activeSlider: Value<*>? = null
    private var popupTapDown = false
    private var closing = false

    private var closeListener: (() -> Unit)? = null
    private var searchRequestListener: (() -> Unit)? = null
    private var contentAnimator: ValueAnimator? = null

    val searchQuery: String get() = state.query
    val isSearchActive: Boolean get() = state.searchActive

    /**
     * Module state is Compose `mutableStateOf` and can be changed from anywhere (shortcut buttons,
     * chat commands, config import). A half-second redraw keeps this Canvas in sync without a
     * bridge or observer.
     */
    private val refreshTick = object : Runnable {
        override fun run() {
            if (closing) return
            invalidate()
            postDelayed(this, 500L)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setUiScale(scale: Float) {
        uiScale = scale.coerceIn(UISettings.MIN_SCALE, UISettings.MAX_SCALE)
        recomputeTransform(width, height)
        invalidate()
    }

    fun setOnCloseRequested(listener: () -> Unit) {
        closeListener = listener
    }

    /** Called when the magnifier is tapped; the host focuses its transparent search field. */
    fun setOnSearchRequested(listener: () -> Unit) {
        searchRequestListener = listener
    }

    /** Text typed in the host's transparent Compose field. */
    fun setSearchQuery(query: String) {
        if (state.query == query) return
        state.query = query
        state.listScroll = 0f
        invalidate()
    }

    fun requestClose() {
        if (closing) return
        closing = true
        contentAnimator?.cancel()
        animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction { closeListener?.invoke() }
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A dismissed overlay may be re-shown from the cached window instance; reset the close
        // state so the GUI is visible and interactive again.
        closing = false
        state.popup = null
        state.searchActive = false
        state.query = ""
        state.contentAlpha = 1f
        state.contentTranslation = 0f
        alpha = 0f
        animate().alpha(1f).setDuration(180L).start()
        requestFocus()
        removeCallbacks(refreshTick)
        postDelayed(refreshTick, 500L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshTick)
        contentAnimator?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeTransform(w, h)
    }

    private fun recomputeTransform(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        fitScale = min(
            w / DreamStyle.REFERENCE_WIDTH,
            h / DreamStyle.REFERENCE_HEIGHT,
        ) * uiScale
        val contentWidth = DreamStyle.REFERENCE_WIDTH * fitScale
        val contentHeight = DreamStyle.REFERENCE_HEIGHT * fitScale
        offsetX = if (contentWidth > w) 0f else (w - contentWidth) / 2f
        offsetY = if (contentHeight > h) 0f else (h - contentHeight) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(DreamStyle.COLOR_SCRIM)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(fitScale, fitScale)
        renderer.draw(canvas, state)
        canvas.restore()
    }

    // =========================================================================================
    // Input
    // =========================================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x - offsetX) / fitScale
        val y = (event.y - offsetY) / fitScale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastY = y
                dragging = false
                popupTapDown = false

                val popup = state.popup
                if (popup != null) {
                    if (renderer.popupIndexAt(state, x, y) >= 0) {
                        popupTapDown = true
                    } else {
                        state.popup = null
                        invalidate()
                    }
                    return true
                }

                activeSlider = renderer.sliderValueAt(state, x, y)
                if (activeSlider != null) {
                    updateSlider(x)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeSlider != null) {
                    updateSlider(x)
                } else if (popupTapDown) {
                    // The popup captures the gesture; no scrolling underneath it.
                } else {
                    if (!dragging && hypot(x - downX, y - downY) > TOUCH_SLOP) {
                        dragging = true
                        state.popup = null
                    }
                    if (dragging) scrollBy(lastY - y)
                }
                lastY = y
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activeSlider != null) {
                    updateSlider(x)
                    activeSlider = null
                    performClick()
                    return true
                }
                if (popupTapDown) {
                    popupTapDown = false
                    selectPopupAt(x, y)
                    performClick()
                    return true
                }
                if (dragging) {
                    dragging = false
                    performClick()
                    return true
                }
                handleTap(x, y)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                activeSlider = null
                popupTapDown = false
                dragging = false
                return true
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        if (renderer.closeTap(x, y)) {
            requestClose()
            return
        }
        if (renderer.searchTap(x, y)) {
            toggleSearch()
            return
        }
        if (state.searchActive && renderer.searchClearTap(x, y)) {
            clearSearchQuery()
            return
        }
        if (renderer.outsidePanel(x, y)) {
            requestClose()
            return
        }

        renderer.categoryAt(x, y)?.let {
            switchCategory(it)
            return
        }

        val selected = state.selectedModule
        if (selected == null) {
            val index = renderer.moduleIndexAt(state, x, y) ?: return
            val module = renderer.moduleAt(state, index) ?: return
            if (renderer.isSwitchTap(x)) {
                DreamBridge.toggle(module)
            } else {
                openDetail(module)
            }
            invalidate()
        } else {
            when (val hit = renderer.detailHit(state, x, y)) {
                DreamHit.Back -> backToList()
                DreamHit.ToggleEnabled -> {
                    DreamBridge.toggle(selected)
                    invalidate()
                }

                DreamHit.ToggleShortcut -> {
                    DreamBridge.setShortcut(selected, !selected.isShortcutDisplayed)
                    invalidate()
                }

                is DreamHit.Toggle -> {
                    (hit.value as? BoolValue)?.let { it.value = !it.value }
                    invalidate()
                }

                is DreamHit.Option -> openPopup(hit.value, hit.anchorTop)
                else -> Unit
            }
        }
    }

    private fun selectPopupAt(x: Float, y: Float) {
        val popup = state.popup ?: return
        val index = renderer.popupIndexAt(state, x, y)
        if (index >= 0) DreamBridge.selectOption(popup.value, index)
        state.popup = null
        invalidate()
    }

    private fun updateSlider(x: Float) {
        val value = activeSlider ?: return
        val fraction = ((x - DreamStyle.SLIDER_START) /
            (DreamStyle.SLIDER_END - DreamStyle.SLIDER_START)).coerceIn(0f, 1f)
        DreamBridge.applyFraction(value, fraction)
        invalidate()
    }

    private fun scrollBy(delta: Float) {
        if (state.selectedModule == null) {
            if (downY >= DreamStyle.CONTENT_TOP) {
                state.listScroll = (state.listScroll + delta)
                    .coerceIn(0f, renderer.maxListScroll(state))
            }
        } else if (downY >= DreamStyle.DETAIL_ROWS_TOP) {
            state.detailScroll = (state.detailScroll + delta)
                .coerceIn(0f, renderer.maxDetailScroll(state))
        }
        invalidate()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                state.popup != null -> state.popup = null
                state.searchActive -> closeSearch()
                state.selectedModule != null -> backToList()
                else -> requestClose()
            }
            invalidate()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // =========================================================================================
    // Search
    // =========================================================================================

    private fun toggleSearch() {
        if (state.searchActive) {
            closeSearch()
            return
        }
        state.searchActive = true
        state.query = ""
        state.selectedModule = null
        state.listScroll = 0f
        state.detailScroll = 0f
        state.popup = null
        searchRequestListener?.invoke()
        invalidate()
    }

    private fun clearSearchQuery() {
        state.query = ""
        state.listScroll = 0f
        invalidate()
    }

    private fun closeSearch() {
        state.searchActive = false
        state.query = ""
        state.listScroll = 0f
        invalidate()
    }

    // =========================================================================================
    // Navigation / animation
    // =========================================================================================

    private fun switchCategory(category: ModuleCategory) {
        val wasSearching = state.searchActive
        if (wasSearching) {
            state.searchActive = false
            state.query = ""
            state.listScroll = 0f
        }
        if (state.category == category && state.selectedModule == null) {
            if (wasSearching) invalidate()
            return
        }
        state.category = category
        state.selectedModule = null
        state.listScroll = 0f
        state.detailScroll = 0f
        state.popup = null
        animateContent(1f, 140L)
    }

    private fun openDetail(module: Module) {
        state.selectedModule = module
        state.detailScroll = 0f
        state.popup = null
        animateContent(-1f, 160L)
    }

    private fun backToList() {
        state.selectedModule = null
        state.detailScroll = 0f
        state.popup = null
        animateContent(1f, 160L)
    }

    private fun openPopup(value: Value<*>, anchorTop: Float) {
        val options = DreamBridge.options(value)
        if (options.isEmpty()) return
        state.popup = DreamOptionPopup(
            value = value,
            options = options,
            selectedIndex = DreamBridge.selectedIndex(value),
            anchorTop = anchorTop,
        )
        invalidate()
    }

    /** Category/detail slide-in, copied from Dream's `animateCategoryChange`. */
    private fun animateContent(direction: Float, durationMs: Long) {
        contentAnimator?.cancel()
        state.contentAlpha = 0f
        state.contentTranslation = direction * 18f
        contentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                state.contentAlpha = fraction
                state.contentTranslation = direction * (1f - fraction) * 18f
                invalidate()
            }
            start()
        }
    }

    private companion object {
        const val TOUCH_SLOP = 8f
    }
}
