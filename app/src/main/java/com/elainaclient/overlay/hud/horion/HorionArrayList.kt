package com.elainaclient.overlay.hud.horion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HorionArrayList : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var mode: ArrayListMode = ArrayListMode.OUTLINE
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var color: ArrayListColor = ArrayListColor.RAINBOW
        set(value) {
            field = value
            invalidate()
        }

    var rainbow: ArrayListRainbow = ArrayListRainbow.PASTEL
        set(value) {
            field = value
            invalidate()
        }

    @ColorInt
    var customColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var font: Typeface? = null
        set(value) {
            field = value
            textPaint.typeface = value ?: Typeface.DEFAULT
            requestLayout()
            invalidate()
        }

    var textSize: Float = DEFAULT_TEXT_SIZE_PX
        set(value) {
            if (field != value) {
                field = value
                textPaint.textSize = value
                refreshBorderPaints()
                requestLayout()
                invalidate()
            }
        }

    var borderThickness: Float? = null
        set(value) {
            field = value
            refreshBorderPaints()
            requestLayout()
            invalidate()
        }

    var cornerRadius: Float = DEFAULT_CORNER_RADIUS_PX
        set(value) {
            field = value
            invalidate()
        }

    var rowPadding: Float = 6f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var textShadowEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var touchModelEnabled: Boolean = false
        set(value) {
            field = value
            isClickable = value
        }

    @ColorInt
    var modeTextColor: Int = 0xFFC5C5C5.toInt()

    var rainbowStepDegrees: Float = 8f
        set(value) {
            field = value
            invalidate()
        }

    var gravity: Int = Gravity.RIGHT or Gravity.TOP
        set(value) {
            if (value != field && gravityChangingAnimationEnabled) {
                gravityFrom = field
                startGravitySlide()
            } else if (value != field) {
                currentY.clear()
            }
            field = value
            requestLayout()
            invalidate()
        }

    var showModes: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var showKeybinds: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var gravityChangingAnimationEnabled = false
    var itemClosingAnimationEnabled = true

    @ColorInt
    @set:JvmName("setListBackgroundColor")
    var backgroundColor: Int = 0x9A000000.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val items = HashMap<Int, ArrayListItem>()

    private val categoryColors = HashMap<Int, Int>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DEFAULT_TEXT_SIZE_PX
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderStrokeWidth()
    }
    private val edgeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderStrokeWidth()
    }

    private val animators = HashMap<Int, ValueAnimator>()
    private val slideProgress = HashMap<Int, Float>()
    private val exitAnimators = HashMap<Int, ValueAnimator>()
    private val exitProgress = HashMap<Int, Float>()
    private val currentY = HashMap<Int, Float>()
    private var lastLayoutTime = 0L

    private var gravityFrom = 0
    private var gravitySlideProgress = 1f
    private var gravityAnimator: ValueAnimator? = null

    private val exitHandler = Handler(Looper.getMainLooper())
    private val pendingExitFinishes = HashMap<Int, Runnable>()

    private var baseHue = 0f
    private var lastFrameTime = 0L

    private class RowLayout(
        val entry: Map.Entry<Int, ArrayListItem>,
        val x: Float,
        val y: Float,
        val width: Float
    )

    fun setItem(id: Int, item: ArrayListItem) {
        val isNew = !items.containsKey(id)
        if (exitAnimators.containsKey(id) || exitProgress.containsKey(id) || pendingExitFinishes.containsKey(id)) {
            cancelExit(id)
        }
        items.put(id, item)
        if (isNew) {
            animateItemIn(id)
        }
        requestLayout()
        invalidate()
    }

    fun removeItem(id: Int) {
        if (!items.containsKey(id)) {
            cancelExit(id)
            currentY.remove(id)
            return
        }
        animateItemOut(id)
    }

    private fun cancelExit(id: Int) {
        exitAnimators.remove(id)?.cancel()
        exitProgress.remove(id)
        pendingExitFinishes.remove(id)?.let { exitHandler.removeCallbacks(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseWidth = paddingLeft + paddingRight
        val baseHeight = paddingTop + paddingBottom
        if (items.isNotEmpty()) {
            var maxRowWidth = 0f
            var totalHeight = 0f
            val rowH = rowHeight()
            for (entry in sortedItems()) {
                maxRowWidth = max(maxRowWidth, measureItemWidth(entry.value))
                totalHeight += rowH
            }
            val stroke = borderStrokeWidth()
            setMeasuredDimension(
                resolveSize(baseWidth + maxRowWidth.toInt() + stroke.toInt(), widthMeasureSpec),
                resolveSize(baseHeight + totalHeight.toInt() + stroke.toInt(), heightMeasureSpec)
            )
        } else {
            setMeasuredDimension(
                resolveSize(baseWidth, widthMeasureSpec),
                resolveSize(baseHeight, heightMeasureSpec)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        updateRainbow()

        val rowH = rowHeight()
        val rowLayouts = computeLayout(gravity)

        val count = rowLayouts.size
        val viewWidth = width.toFloat()

        val now = System.currentTimeMillis()
        val dtMs = if (lastLayoutTime == 0L) 0L else (now - lastLayoutTime).coerceIn(0L, 50L)
        lastLayoutTime = now
        val alpha = (dtMs / 1000f * LAYOUT_SMOOTH_RATE).coerceIn(0f, 1f)

        val gravityTransition = gravitySlideProgress < 1f
        val oldLayout = if (gravityTransition) computeLayout(gravityFrom) else null
        val oldById = oldLayout?.associateBy { it.entry.key }

        val positions = FloatArray(count)
        val rowY = FloatArray(count)
        var stillMoving = gravityTransition
        for ((index, layout) in rowLayouts.withIndex()) {
            val id = layout.entry.key
            val progress = slideProgress[id] ?: 1f
            val exit = exitProgress[id] ?: 0f
            val slideDirection = if (isRightGravity()) 1f else -1f

            var targetX = layout.x
            var targetY = layout.y
            if (gravityTransition) {
                val old = oldById?.get(id)
                if (old != null) {
                    val t = gravitySlideProgress
                    targetX = old.x + (layout.x - old.x) * t
                    targetY = old.y + (layout.y - old.y) * t
                }
                currentY[id] = targetY
            }

            positions[index] = targetX + slideDirection * viewWidth * (1f - progress) + slideDirection * viewWidth * exit

            if (gravityTransition) {
                rowY[index] = targetY
            } else {
                val current = currentY[id]
                if (current == null || !itemClosingAnimationEnabled) {
                    rowY[index] = targetY
                    currentY[id] = targetY
                } else {
                    val smoothed = current + (targetY - current) * alpha
                    if (abs(targetY - smoothed) < 0.5f) {
                        rowY[index] = targetY
                        currentY[id] = targetY
                    } else {
                        rowY[index] = smoothed
                        currentY[id] = smoothed
                        stillMoving = true
                    }
                }
            }
        }

        for ((index, layout) in rowLayouts.withIndex()) {
            val prev = if (index > 0) rowLayouts[index - 1] else null
            val next = if (index < count - 1) rowLayouts[index + 1] else null
            drawRowBackground(
                canvas,
                positions[index],
                rowY[index],
                layout.width,
                rowH,
                index,
                count,
                prev?.width,
                next?.width,
                prev?.let { borderXOf(it.x, it.width) },
                next?.let { borderXOf(it.x, it.width) }
            )
        }
        for ((index, layout) in rowLayouts.withIndex()) {
            drawRowText(canvas, layout.entry.value, positions[index], rowY[index], layout.width, rowH, index, count)
        }
        for ((index, layout) in rowLayouts.withIndex()) {
            val prev = if (index > 0) rowLayouts[index - 1] else null
            val next = if (index < count - 1) rowLayouts[index + 1] else null
            drawRowBorder(
                canvas,
                layout.entry.value,
                positions[index],
                rowY[index],
                layout.width,
                rowH,
                index,
                count,
                prev?.let { borderXOf(it.x, it.width) },
                next?.let { borderXOf(it.x, it.width) },
                prev?.width,
                next?.width,
                prev != null && layout.width > prev.width,
                next != null && layout.width > next.width
            )
        }

        canvas.restore()

        if (color == ArrayListColor.RAINBOW || stillMoving) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animators.values.forEach { it.cancel() }
        animators.clear()
        exitAnimators.values.forEach { it.cancel() }
        exitAnimators.clear()
        gravityAnimator?.cancel()
        gravityAnimator = null
        gravitySlideProgress = 1f
        pendingExitFinishes.values.forEach { exitHandler.removeCallbacks(it) }
        pendingExitFinishes.clear()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchModelEnabled
    }

    private fun drawRowBackground(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        index: Int,
        count: Int,
        prevWidth: Float?,
        nextWidth: Float?,
        prevBorderX: Float?,
        nextBorderX: Float?
    ) {
        backgroundPaint.color = backgroundColor
        if (mode != ArrayListMode.ROUNDED) {
            canvas.drawRect(x, y, x + width, y + height, backgroundPaint)
            return
        }

        val freeRight = isLeftGravity()
        val r = min(cornerRadius, min(width, height) / 2f)

        val widthChangeAtTop = index > 0 && prevWidth != null && prevWidth != width
        val widthChangeAtBottom = index < count - 1 && nextWidth != null && nextWidth != width

        val longerThanPrev = index > 0 && prevWidth != null && width > prevWidth
        val longerThanNext = index < count - 1 && nextWidth != null && width > nextWidth

        val roundTop = (index == 0 && isBottomGravity()) || longerThanPrev
        val roundBottom = (index == count - 1 && isTopGravity()) || longerThanNext

        if (!roundTop && !roundBottom) {
            canvas.drawRect(x, y, x + width, y + height, backgroundPaint)
            return
        }

        val topR = if (longerThanPrev) min(r, abs(width - prevWidth!!) / 2f) else r
        val bottomR = if (longerThanNext) min(r, abs(width - nextWidth!!) / 2f) else r

        val roundTopLeft = roundTop && !freeRight
        val roundTopRight = roundTop && freeRight
        val roundBottomLeft = roundBottom && !freeRight
        val roundBottomRight = roundBottom && freeRight

        val path = Path()
        path.moveTo(x + (if (roundTopLeft) topR else 0f), y)
        path.lineTo(x + width - (if (roundTopRight) topR else 0f), y)
        if (roundTopRight) path.quadTo(x + width, y, x + width, y + topR)
        path.lineTo(x + width, y + height - (if (roundBottomRight) bottomR else 0f))
        if (roundBottomRight) path.quadTo(x + width, y + height, x + width - bottomR, y + height)
        path.lineTo(x + (if (roundBottomLeft) bottomR else 0f), y + height)
        if (roundBottomLeft) path.quadTo(x, y + height, x, y + height - bottomR)
        path.lineTo(x, y + (if (roundTopLeft) topR else 0f))
        if (roundTopLeft) path.quadTo(x, y, x + topR, y)
        path.close()
        canvas.drawPath(path, backgroundPaint)

        if (longerThanPrev && prevBorderX != null) {
            drawBackgroundConnector(canvas, prevBorderX, y, topR, top = true, freeRight)
        }
        if (longerThanNext && nextBorderX != null) {
            drawBackgroundConnector(canvas, nextBorderX, y + height, bottomR, top = false, freeRight)
        }
    }

    private fun drawBackgroundConnector(
        canvas: Canvas,
        farX: Float,
        y: Float,
        r: Float,
        top: Boolean,
        freeRight: Boolean
    ) {
        if (r <= 0f) return
        val dir = if (freeRight) r else -r
        val dy = if (top) -r else r
        val path = Path()
        path.moveTo(farX, y)
        path.lineTo(farX + dir, y)
        path.addArc(
            farX + dir - r, y + dy - r,
            farX + dir + r, y + dy + r,
            if (dy > 0f) 270f else 90f,
            if (freeRight) {
                if (top) 90f else -90f
            } else {
                if (top) -90f else 90f
            }
        )
        path.lineTo(farX, y)
        path.close()
        canvas.drawPath(path, backgroundPaint)
    }

    private fun drawRowText(
        canvas: Canvas,
        item: ArrayListItem,
        x: Float,
        y: Float,
        rowWidth: Float,
        rowHeight: Float,
        index: Int,
        count: Int
    ) {
        val rowColor = colorFor(item, index, count)
        val shadow = fontStrokeWidth()

        val mainText = mainTextFor(item)
        val modeText = modeTextFor(item)
        val mainWidth = textPaint.measureText(mainText)
        val modeWidth = if (modeText != null) textPaint.measureText(modeText) else 0f
        val fm = textPaint.fontMetrics
        val baseline = y + (rowHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val padH = rowPadding

        if (isRightGravity()) {
            val modeX = x + rowWidth - padH - modeWidth
            if (modeText != null) {
                drawTextWithShadow(canvas, modeText, modeX, baseline, modeTextColor, shadow)
            }
            drawTextWithShadow(canvas, mainText, modeX - mainWidth, baseline, rowColor, shadow)
        } else {
            val mainX = x + padH
            drawTextWithShadow(canvas, mainText, mainX, baseline, rowColor, shadow)
            if (modeText != null) {
                drawTextWithShadow(canvas, modeText, mainX + mainWidth, baseline, modeTextColor, shadow)
            }
        }
    }

    private fun drawTextWithShadow(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        color: Int,
        shadowOffset: Float
    ) {
        if (textShadowEnabled) {
            textPaint.color = shadowColorOf(color)
            canvas.drawText(text, x + shadowOffset, baseline + shadowOffset, textPaint)
        }
        textPaint.color = color
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun shadowColorOf(color: Int): Int {
        val alpha = Color.alpha(color)
        val red = (Color.red(color) / 3.5f).toInt().coerceAtMost(255)
        val green = (Color.green(color) / 3.5f).toInt().coerceAtMost(255)
        val blue = (Color.blue(color) / 3.5f).toInt().coerceAtMost(255)
        return Color.argb(alpha, red, green, blue)
    }

    private fun drawRowBorder(
        canvas: Canvas,
        item: ArrayListItem,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        index: Int,
        count: Int,
        prevBorderX: Float?,
        nextBorderX: Float?,
        prevWidth: Float?,
        nextWidth: Float?,
        drawTopConnector: Boolean,
        drawBottomConnector: Boolean
    ) {
        when (mode) {
            ArrayListMode.NONE -> return
            ArrayListMode.EDGE_LINE -> {
                edgeLinePaint.color = colorFor(item, index, count)
                val lineX = if (isRightGravity()) x else x + width
                canvas.drawLine(lineX, y, lineX, y + height, edgeLinePaint)
                return
            }
            ArrayListMode.OUTLINE -> drawRowOutline(canvas, item, x, y, width, height, index, count, prevBorderX, nextBorderX, prevWidth, nextWidth, drawTopConnector, drawBottomConnector, 0f)
            ArrayListMode.ROUNDED -> drawRowOutline(canvas, item, x, y, width, height, index, count, prevBorderX, nextBorderX, prevWidth, nextWidth, drawTopConnector, drawBottomConnector, cornerRadius)
        }
    }

    private fun drawRowOutline(
        canvas: Canvas,
        item: ArrayListItem,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        index: Int,
        count: Int,
        prevBorderX: Float?,
        nextBorderX: Float?,
        prevWidth: Float?,
        nextWidth: Float?,
        drawTopConnector: Boolean,
        drawBottomConnector: Boolean,
        radius: Float
    ) {
        strokePaint.color = colorFor(item, index, count)
        val freeRight = isLeftGravity()
        val r = if (radius > 0f) min(radius, min(width, height) / 2f) else 0f
        val borderX = if (freeRight) x + width else x
        val topOuter = index == 0 && isBottomGravity()
        val bottomOuter = index == count - 1 && isTopGravity()

        val widthChangeAtTop = index > 0 && prevWidth != null && prevWidth != width
        val widthChangeAtBottom = index < count - 1 && nextWidth != null && nextWidth != width

        val topR = when {
            widthChangeAtTop -> min(r, abs(width - prevWidth!!) / 2f)
            topOuter -> r
            else -> 0f
        }
        val bottomR = when {
            widthChangeAtBottom -> min(r, abs(width - nextWidth!!) / 2f)
            bottomOuter -> r
            else -> 0f
        }
        val topInset = topR
        val bottomInset = bottomR

        canvas.drawLine(borderX, y + topInset, borderX, y + height - bottomInset, strokePaint)

        if (topOuter) {
            if (freeRight) {
                val endX = if (r > 0f) x + width - r else x + width
                canvas.drawLine(x, y, endX, y, strokePaint)
                if (r > 0f) {
                    canvas.drawArc(x + width - r * 2f, y, x + width, y + r * 2f, 270f, 90f, false, strokePaint)
                }
            } else {
                val startX = if (r > 0f) x + r else x
                canvas.drawLine(startX, y, x + width, y, strokePaint)
                if (r > 0f) {
                    canvas.drawArc(x, y, x + r * 2f, y + r * 2f, 180f, 90f, false, strokePaint)
                }
            }
        }

        if (bottomOuter) {
            if (freeRight) {
                val endX = if (r > 0f) x + width - r else x + width
                canvas.drawLine(x, y + height, endX, y + height, strokePaint)
                if (r > 0f) {
                    canvas.drawArc(x + width - r * 2f, y + height - r * 2f, x + width, y + height, 0f, 90f, false, strokePaint)
                }
            } else {
                val startX = if (r > 0f) x + r else x
                canvas.drawLine(startX, y + height, x + width, y + height, strokePaint)
                if (r > 0f) {
                    canvas.drawArc(x, y + height - r * 2f, x + r * 2f, y + height, 90f, 90f, false, strokePaint)
                }
            }
        }

        if (drawTopConnector && prevBorderX != null) {
            drawConnector(canvas, borderX, prevBorderX, y, topR, top = true, freeRight)
        }
        if (drawBottomConnector && nextBorderX != null) {
            drawConnector(canvas, borderX, nextBorderX, y + height, bottomR, top = false, freeRight)
        }
    }

    private fun drawConnector(
        canvas: Canvas,
        longerX: Float,
        shorterX: Float,
        y: Float,
        r: Float,
        top: Boolean,
        freeRight: Boolean
    ) {
        if (r <= 0f) {
            canvas.drawLine(longerX, y, shorterX, y, strokePaint)
            return
        }
        val lineFrom = if (longerX < shorterX) longerX + r else longerX - r
        val lineTo = if (shorterX > longerX) shorterX - r else shorterX + r
        canvas.drawLine(lineFrom, y, lineTo, y, strokePaint)

        val longerArc: Int
        val shorterArc: Int
        if (top) {
            longerArc = if (freeRight) ARC_TR else ARC_TL
            shorterArc = if (freeRight) ARC_BL else ARC_BR
        } else {
            longerArc = if (freeRight) ARC_BR else ARC_BL
            shorterArc = if (freeRight) ARC_TL else ARC_TR
        }
        drawCornerArc(canvas, longerArc, longerX, y, r)
        drawCornerArc(canvas, shorterArc, shorterX, y, r)
    }

    private fun drawCornerArc(canvas: Canvas, type: Int, cornerX: Float, cornerY: Float, r: Float) {
        val cx: Float
        val cy: Float
        val start: Float
        when (type) {
            ARC_TL -> {
                cx = cornerX + r
                cy = cornerY + r
                start = 180f
            }
            ARC_TR -> {
                cx = cornerX - r
                cy = cornerY + r
                start = 270f
            }
            ARC_BR -> {
                cx = cornerX - r
                cy = cornerY - r
                start = 0f
            }
            else -> {
                cx = cornerX + r
                cy = cornerY - r
                start = 90f
            }
        }
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, 90f, false, strokePaint)
    }

    private fun colorFor(item: ArrayListItem, index: Int, count: Int): Int {
        return when (color) {
            ArrayListColor.RAINBOW -> rainbowColor(baseHue + index * rainbowStepDegrees)
            ArrayListColor.CATEGORIZED -> categoryColor(item.category)
            ArrayListColor.CUSTOM -> customColor
        }
    }

    private fun rainbowColor(hue: Float): Int {
        val saturation = if (rainbow == ArrayListRainbow.SATURATED) 0.9f else 0.35f
        return Color.HSVToColor(floatArrayOf(hue % 360f, saturation, 1f))
    }

    fun setCategoryColor(category: Int, color: Int) {
        categoryColors[category] = color
        invalidate()
    }

    private fun categoryColor(category: Int): Int {
        return categoryColors[category] ?: Color.WHITE
    }

    private fun updateRainbow() {
        if (color != ArrayListColor.RAINBOW) return
        val now = System.currentTimeMillis()
        if (lastFrameTime == 0L) lastFrameTime = now
        val delta = (now - lastFrameTime).coerceIn(0L, 50L)
        lastFrameTime = now
        baseHue = (baseHue + delta * HUE_SPEED_DEG_PER_MS) % 360f
    }

    private fun animateItemIn(id: Int) {
        animators.remove(id)?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = SLIDE_DURATION_MS
        animator.interpolator = DecelerateInterpolator(2f)
        animator.addUpdateListener {
            slideProgress[id] = it.animatedFraction
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                slideProgress[id] = 1f
                animators.remove(id)
                invalidate()
            }

            override fun onAnimationCancel(animation: Animator) {
                slideProgress[id] = 1f
                animators.remove(id)
            }
        })
        slideProgress[id] = 0f
        animators[id] = animator
        animator.start()
    }

    private fun startGravitySlide() {
        gravityAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = GRAVITY_SLIDE_DURATION_MS
        animator.interpolator = DecelerateInterpolator(2f)
        animator.addUpdateListener {
            gravitySlideProgress = it.animatedFraction
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                gravitySlideProgress = 1f
                gravityAnimator = null
            }

            override fun onAnimationCancel(animation: Animator) {
                gravitySlideProgress = 1f
                gravityAnimator = null
            }
        })
        gravitySlideProgress = 0f
        gravityAnimator = animator
        animator.start()
    }

    private fun animateItemOut(id: Int) {
        cancelExit(id)
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = SLIDE_DURATION_MS
        animator.interpolator = DecelerateInterpolator(2f)
        animator.addUpdateListener {
            val fraction = it.animatedFraction
            exitProgress[id] = fraction
            invalidate()
            if (isItemFullyOut(id, fraction)) {
                animator.cancel()
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                scheduleExitFinish(id)
            }

            override fun onAnimationCancel(animation: Animator) {
                scheduleExitFinish(id)
            }
        })
        exitProgress[id] = 0f
        exitAnimators[id] = animator
        animator.start()
    }

    private fun isItemFullyOut(id: Int, exit: Float): Boolean {
        val item = items[id] ?: return true
        val rowWidth = measureItemWidth(item)
        val layoutX = if (isRightGravity()) width - paddingRight - rowWidth else paddingLeft.toFloat()
        return if (isRightGravity()) {
            layoutX + width * exit >= width
        } else {
            layoutX - width * exit + rowWidth <= 0f
        }
    }

    private fun scheduleExitFinish(id: Int) {
        exitProgress[id] = 1f
        pendingExitFinishes[id]?.let { exitHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingExitFinishes.remove(id)
            finishItemExit(id)
        }
        pendingExitFinishes[id] = runnable
        val delay = if (itemClosingAnimationEnabled) 0L else EXIT_FINISH_DELAY_MS
        exitHandler.postDelayed(runnable, delay)
    }

    private fun finishItemExit(id: Int) {
        exitAnimators.remove(id)
        exitProgress.remove(id)
        pendingExitFinishes.remove(id)?.let { exitHandler.removeCallbacks(it) }
        animators.remove(id)?.cancel()
        slideProgress.remove(id)
        currentY.remove(id)
        items.remove(id)
        requestLayout()
        invalidate()
    }

    private fun sortedItems(): List<Map.Entry<Int, ArrayListItem>> {
        val entries = items.entries
        return if (isTopGravity()) {
            entries.sortedByDescending { measureItemWidth(it.value) }
        } else {
            entries.sortedBy { measureItemWidth(it.value) }
        }
    }

    private fun computeLayout(g: Int): List<RowLayout> {
        val rows = if ((g and Gravity.TOP) == Gravity.TOP) {
            items.entries.sortedByDescending { measureItemWidth(it.value) }
        } else {
            items.entries.sortedBy { measureItemWidth(it.value) }
        }
        val rowH = rowHeight()
        val contentHeight = rowH * rows.size
        val bottom = (g and Gravity.BOTTOM) == Gravity.BOTTOM
        val startY = if (bottom && height - paddingTop - paddingBottom >= contentHeight) {
            height - paddingBottom - contentHeight
        } else {
            paddingTop.toFloat()
        }
        val viewWidth = width.toFloat()
        val right = (g and Gravity.RIGHT) == Gravity.RIGHT
        var y = startY
        val layouts = ArrayList<RowLayout>(rows.size)
        for (entry in rows) {
            val rowWidth = measureItemWidth(entry.value)
            val x = if (right) viewWidth - paddingRight - rowWidth else paddingLeft.toFloat()
            layouts.add(RowLayout(entry, x, y, rowWidth))
            y += rowH
        }
        return layouts
    }

    private fun measureItemWidth(item: ArrayListItem): Float {
        val mainText = mainTextFor(item)
        val modeText = modeTextFor(item)
        val fullText = if (modeText != null) mainText + modeText else mainText
        val textWidth = textPaint.measureText(fullText)
        val padH = rowPadding
        return textWidth + padH * 2f
    }

    private fun fontStrokeWidth(): Float = textPaint.textSize / FONT_STROKE_DIVISOR

    private fun borderStrokeWidth(): Float = borderThickness ?: fontStrokeWidth()

    private fun refreshBorderPaints() {
        val width = borderStrokeWidth()
        strokePaint.strokeWidth = width
        edgeLinePaint.strokeWidth = width
    }

    private fun borderXOf(x: Float, width: Float): Float =
        if (isLeftGravity()) x + width else x

    private fun mainTextFor(item: ArrayListItem): String {
        val builder = StringBuilder(item.text)
        if (showKeybinds && item.keybind != null) {
            builder.append(" [").append(item.keybind).append(']')
        }
        return builder.toString()
    }

    private fun modeTextFor(item: ArrayListItem): String? =
        if (showModes) item.modeText?.let { " $it" } else null

    private fun rowHeight(): Float {
        val fm = textPaint.fontMetrics
        return fm.descent - fm.ascent + rowPadding
    }

    private fun isRightGravity() = (gravity and Gravity.RIGHT) == Gravity.RIGHT
    private fun isLeftGravity() = (gravity and Gravity.LEFT) == Gravity.LEFT
    private fun isTopGravity() = (gravity and Gravity.TOP) == Gravity.TOP
    private fun isBottomGravity() = (gravity and Gravity.BOTTOM) == Gravity.BOTTOM

    companion object {
        private const val DEFAULT_TEXT_SIZE_PX = 14f
        private const val FONT_STROKE_DIVISOR = 10f
        private const val DEFAULT_CORNER_RADIUS_PX = 8f
        private const val SLIDE_DURATION_MS = 400L
        private const val GRAVITY_SLIDE_DURATION_MS = 300L
        private const val EXIT_FINISH_DELAY_MS = 75L
        private const val HUE_SPEED_DEG_PER_MS = 0.04f
        private const val LAYOUT_SMOOTH_RATE = 10f

        private const val ARC_TL = 0
        private const val ARC_TR = 1
        private const val ARC_BR = 2
        private const val ARC_BL = 3
    }
}
