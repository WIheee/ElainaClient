package com.elainaclient.overlay.gui.hackai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * Kotlin port of HackAI's `SettingSlider`, re-coloured with Elaina's violet accent.
 *
 * The original hard-codes pixel sizes; this port expresses them in dp so the 28 dp slider looks the
 * same at any density.
 */
class HackAiSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onValueChange: ((Float) -> Unit)? = null

    private var minValue = 0f
    private var maxValue = 1f
    private var step = 0f
    private var value = 0f

    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobRect = RectF()

    init {
        knobPaint.color = Color.WHITE
        knobPaint.setShadowLayer(dp(1.5f), 0f, dp(0.4f), 0x77424952)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun configure(
        rangeMin: Float,
        rangeMax: Float,
        rangeStep: Float,
        currentValue: Float,
        animated: Boolean = false,
    ) {
        minValue = rangeMin
        maxValue = rangeMax
        step = rangeStep
        setValue(currentValue, animated, false)
    }

    fun setValue(newValue: Float, animated: Boolean) = setValue(newValue, animated, true)

    fun setValue(newValue: Float, animated: Boolean, notify: Boolean) {
        val snapped = snap(newValue)
        animator?.cancel()
        if (animated) {
            animator = ValueAnimator.ofFloat(value, snapped).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    value = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            value = snapped
            invalidate()
        }
        if (notify) onValueChange?.invoke(snapped)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val start = paddingLeft + dp(3f)
        val end = width - paddingRight - dp(5f)
        val percent = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val x = start + (end - start) * percent

        trackPaint.shader = null
        trackPaint.color = 0xFF3D3E46.toInt()
        trackPaint.strokeWidth = dp(5.5f)
        trackPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(start, centerY, end, centerY, trackPaint)

        fillPaint.shader = LinearGradient(
            start,
            0f,
            end,
            0f,
            HackAiStyle.accent,
            HackAiStyle.accentSecondary,
            Shader.TileMode.CLAMP,
        )
        fillPaint.strokeWidth = dp(3f)
        fillPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(start, centerY, x, centerY, fillPaint)

        knobRect.set(x - dp(5f), centerY - dp(5f), x + dp(5f), centerY + dp(5f))
        canvas.drawRoundRect(knobRect, dp(2.5f), dp(2.5f), knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromX(event.x)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun updateFromX(x: Float) {
        val start = paddingLeft + dp(3f)
        val end = width - paddingRight - dp(5f)
        val percent = ((x - start) / (end - start)).coerceIn(0f, 1f)
        setValue(minValue + (maxValue - minValue) * percent, false)
    }

    private fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(minValue, maxValue)
        if (step <= 0f) return clamped
        return (minValue + ((clamped - minValue) / step).roundToInt() * step)
            .coerceIn(minValue, maxValue)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
