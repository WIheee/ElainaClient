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
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

/**
 * Kotlin port of HackAI's `AnimatedSwitch` (38x22 dp), re-coloured with Elaina's violet accent.
 */
class HackAiSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onCheckedChange: ((Boolean) -> Unit)? = null

    var checked: Boolean = false
        private set

    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
        thumbPaint.color = Color.WHITE
        thumbPaint.setShadowLayer(dp(2f), 0f, dp(1f), 0x66333A44)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnClickListener { setChecked(!checked, true, true) }
    }

    fun setChecked(checked: Boolean, animated: Boolean) = setChecked(checked, animated, false)

    fun setChecked(checked: Boolean, animated: Boolean, notify: Boolean) {
        val target = if (checked) 1f else 0f
        if (checked == this.checked && progress == target) return
        this.checked = checked
        animator?.cancel()
        if (animated) {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = 220L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = target
            invalidate()
        }
        if (notify) onCheckedChange?.invoke(checked)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        if (progress > 0f) {
            trackPaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                0f,
                HackAiStyle.accent,
                HackAiStyle.accentSecondary,
                Shader.TileMode.CLAMP,
            )
        } else {
            trackPaint.shader = null
            trackPaint.color = HackAiStyle.switchOff
        }
        canvas.drawRoundRect(rect, height / 2f, height / 2f, trackPaint)

        val radius = height / 2f - dp(1.5f)
        val x = dp(1.5f) + radius + max(width - height, 0) * progress
        canvas.drawCircle(x, height / 2f, radius, thumbPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
