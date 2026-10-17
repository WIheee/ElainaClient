package com.elainaclient.overlay.gui.liquidpe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Kotlin rewrite of LiquidPE's full-screen click-GUI view.
 *
 * Only the click GUI is kept: the original HUD/binds/notification renderer and its state model were
 * not part of the GUI and are intentionally gone. Visibility transitions, touch routing, back
 * handling, layout persistence and the reference-frame scaling are preserved.
 */
open class LiquidPeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    store: LiquidPeStore = LiquidPeStore(),
) : View(context, attrs), LiquidPeRenderer.Listener {

    fun interface SearchRequestListener {
        fun onSearchRequested(currentQuery: String)
    }

    private val viewport = LiquidPeViewport()
    private val typography = LiquidPeTypography(context)
    private val renderer = LiquidPeRenderer(context, store, this, typography)
    private val contentClip = RectF(
        0f,
        0f,
        LiquidPeMetrics.CONTENT_WIDTH,
        LiquidPeMetrics.CONTENT_HEIGHT,
    )
    private val transition = ClickGuiTransition(true)
    private var clickGuiVisible = true
    private var searchRequestListener: SearchRequestListener? = null
    private var clickGuiClosedListener: Runnable? = null
    private var closedCallbackDispatched = false

    init {
        LiquidPeFonts.initialize(context)
        setFocusable(true)
        setFocusableInTouchMode(true)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport.update(w, h)
        typography.update(context, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val progress = transition.get(now)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (progress > 0f) {
            canvas.drawColor(Color.argb(Math.round(120f * progress), 0, 0, 0))
        }

        val save = canvas.save()
        viewport.apply(canvas)
        canvas.clipRect(contentClip)
        if (progress > 0f) drawAnimatedClickGui(canvas, progress, now)
        canvas.restoreToCount(save)

        if (!transition.isRunning(now) && !clickGuiVisible && progress <= 0f && !closedCallbackDispatched) {
            closedCallbackDispatched = true
            clickGuiClosedListener?.let { post(it) }
        }
        if (transition.isRunning(now) || renderer.hasActiveAnimations(now)) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawAnimatedClickGui(canvas: Canvas, progress: Float, now: Long) {
        val scale = CLICK_GUI_MIN_SCALE + (1f - CLICK_GUI_MIN_SCALE) * progress
        val translationY = CLICK_GUI_TRANSLATION_PX * (1f - progress) /
            max(0.001f, viewport.currentScale)
        val centerX = LiquidPeMetrics.CONTENT_WIDTH * .5f
        val centerY = LiquidPeMetrics.CONTENT_HEIGHT * .5f

        val transformSave = canvas.save()
        canvas.translate(0f, translationY)
        canvas.scale(scale, scale, centerX, centerY)
        val alphaLayer = canvas.saveLayerAlpha(contentClip, Math.round(255f * progress))
        renderer.draw(canvas, now)
        canvas.restoreToCount(alphaLayer)
        canvas.restoreToCount(transformSave)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = viewport.toLogical(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                if (clickGuiVisible) renderer.onTouchDown(p.x, p.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (clickGuiVisible) renderer.onTouchMove(p.x, p.y)
            }
            MotionEvent.ACTION_UP -> {
                if (clickGuiVisible) renderer.onTouchUp(p.x, p.y)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (clickGuiVisible) renderer.onTouchUp(p.x, p.y)
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTopTabChanged(tab: TopTab) {
        invalidate()
    }

    override fun onSearchRequested() {
        searchRequestListener?.onSearchRequested(renderer.getSearchQuery())
    }

    // =====================================================================================
    // Public surface (host bridge)
    // =====================================================================================

    val isClickGuiVisibleRequested: Boolean get() = clickGuiVisible

    val searchQuery: String get() = renderer.getSearchQuery()

    fun setClickGuiVisible(visible: Boolean) {
        if (clickGuiVisible == visible && transition.target == (if (visible) 1f else 0f)) return
        clickGuiVisible = visible
        closedCallbackDispatched = false
        transition.animateTo(
            if (visible) 1f else 0f,
            if (visible) LiquidPeDurations.CLICK_GUI_OPEN else LiquidPeDurations.CLICK_GUI_CLOSE,
        )
        postInvalidateOnAnimation()
    }

    fun setClickGuiVisibleImmediately(visible: Boolean) {
        clickGuiVisible = visible
        closedCallbackDispatched = !visible
        transition.snapTo(if (visible) 1f else 0f)
        invalidate()
    }

    fun setOnClickGuiClosedListener(listener: Runnable?) {
        clickGuiClosedListener = listener
    }

    fun setSearchRequestListener(listener: SearchRequestListener?) {
        searchRequestListener = listener
    }

    fun setSearchQuery(query: String?) {
        renderer.setSearchQuery(query)
        invalidate()
    }

    fun setUiScale(scale: Float) {
        viewport.setMultiplier(scale)
        invalidate()
    }

    fun setActiveTopTab(tab: TopTab) {
        renderer.setActiveTab(tab)
        invalidate()
    }

    fun enterEditMode() {
        renderer.enterEditMode()
        invalidate()
    }

    fun exitEditMode() {
        renderer.exitEditMode()
        invalidate()
    }

    fun isEditMode(): Boolean = renderer.isEditMode

    fun saveLayout() {
        renderer.savePanelLayout()
    }

    fun restoreLayout() {
        renderer.restorePanelLayout()
        invalidate()
    }

    fun resetLayout() {
        renderer.resetPanelLayout()
        invalidate()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && renderer.onBackPressed()) {
            invalidate()
            return true
        }
        val keyName = KeyEvent.keyCodeToString(keyCode)
            .replace("KEYCODE_", "")
            .replace('_', ' ')
        if (renderer.onKeyPressed(keyName) != null) {
            invalidate()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        const val CLICK_GUI_MIN_SCALE = .85f
        const val CLICK_GUI_TRANSLATION_PX = 8f
    }

    /** Time-based visibility interpolation with direction-specific easing. */
    private class ClickGuiTransition(visible: Boolean) {
        private var startValue = if (visible) 1f else 0f
        private var value = startValue
        private var targetValue = startValue
        private var startTime = 0L
        private var duration = 0L

        fun get(now: Long): Float {
            if (value == targetValue || duration <= 0L) return targetValue
            val t = ((now - startTime) / duration.toFloat()).coerceIn(0f, 1f)
            val eased = if (targetValue > startValue) easeOutCubic(t) else t * t * t
            value = startValue + (targetValue - startValue) * eased
            if (t >= 1f) value = targetValue
            return value
        }

        fun animateTo(target: Float, fullDuration: Long) {
            val now = SystemClock.uptimeMillis()
            val current = get(now)
            startValue = current
            targetValue = target
            startTime = now
            duration = max(1L, Math.round(fullDuration * abs(target - current)).toLong())
        }

        fun snapTo(target: Float) {
            startValue = target
            value = target
            targetValue = target
            startTime = 0L
            duration = 0L
        }

        fun isRunning(now: Long): Boolean = value != targetValue && now - startTime < duration

        val target: Float get() = targetValue

        private fun easeOutCubic(t: Float): Float {
            val p = 1f - t
            return 1f - p * p * p
        }
    }
}
