package com.elainaclient.overlay.gui.liquidpe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.elainaclient.R
import com.elainaclient.game.localizedOption
import kotlin.math.max
import kotlin.math.min

/** Original LiquidPE palette - deliberately not re-skinned. */
object LiquidPeColors {
    val ACCENT = 0xFF3C69FC.toInt()
    val ACCENT_SOFT = 0xFF4778F8.toInt()
    val ACCENT_DEEP = 0xFF12275F.toInt()
    val PANEL = 0xC00A0B0E.toInt()
    val PANEL_DEEP = 0xCC010205.toInt()
    val PANEL_HIGHLIGHT = 0x26FFFFFF
    val TEXT_NORMAL = 0xFFE1E1E3.toInt()
    val TEXT_MUTED = 0xFF88888F.toInt()
    val TRACK = 0xFF303237.toInt()
    val TOGGLE_OFF = 0xFF55575E.toInt()

    fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f),
            Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
            Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
            Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f),
        )
    }
}

object LiquidPeMetrics {
    const val CONTENT_WIDTH = 2940f
    const val CONTENT_HEIGHT = 1837f
    const val PANEL_WIDTH = 375f
    const val PANEL_HEADER_HEIGHT = 59f
    const val PANEL_RADIUS = 9f
    const val PANEL_X = 30f
    const val PANEL_Y = 194f
}

object LiquidPeDurations {
    const val PANEL = 140L
    const val GROUP = 150L
    const val TOGGLE = 140L
    const val MODULE_TOGGLE = 160L
    const val CLICK_GUI_OPEN = 200L
    const val CLICK_GUI_CLOSE = 170L
}

/**
 * The project's own Minecraft font (also used by the ArrayList HUD). The original author's
 * Roboto copy was removed; every LiquidPE label now uses this single font.
 */
object LiquidPeFonts {
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var bold: Typeface? = null

    fun initialize(context: Context) {
        if (regular != null) return
        val bundled = ResourcesCompat.getFont(context, R.font.minecraft) ?: Typeface.DEFAULT
        regular = Typeface.create(bundled, Typeface.NORMAL)
        medium = Typeface.create(bundled, Typeface.BOLD)
        bold = Typeface.create(bundled, Typeface.BOLD)
    }

    fun regular(): Typeface = regular ?: Typeface.DEFAULT
    fun medium(): Typeface = medium ?: Typeface.DEFAULT_BOLD
    fun bold(): Typeface = bold ?: Typeface.DEFAULT_BOLD
}

/** Keeps Canvas text legible on phones/tablets without changing the reference layout. */
class LiquidPeTypography(context: Context) {

    private var scale = 1f
    private var controlScale = 1f

    init {
        update(context, 0, 0)
    }

    fun update(context: Context, widthPx: Int, heightPx: Int) {
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val density = max(1f, metrics.density)
        var shortestWidthDp = min(widthPx, heightPx) / density
        if (shortestWidthDp <= 0f) shortestWidthDp = configuration.smallestScreenWidthDp.toFloat()

        val deviceScale = when {
            shortestWidthDp <= 360f -> .90f
            shortestWidthDp < 600f -> .90f + (shortestWidthDp - 360f) / 240f * .10f
            else -> min(1.12f, 1f + (shortestWidthDp - 600f) / 1200f * .12f)
        }

        controlScale = deviceScale.coerceIn(.90f, 1.12f)
        val systemFontScale = configuration.fontScale.coerceIn(.85f, 1.30f)
        scale = (deviceScale * systemFontScale).coerceIn(.82f, 1.25f)
    }

    fun size(referenceSize: Float): Float = referenceSize * scale

    fun control(referenceSize: Float): Float = referenceSize * controlScale
}

/** Scales the fixed 2940x1837 reference frame into the view, with a user multiplier. */
class LiquidPeViewport {

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var viewWidth = 0
    private var viewHeight = 0
    private var multiplier = 1f
    private val reusablePoint = PointF()

    fun update(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        scale = min(
            width / LiquidPeMetrics.CONTENT_WIDTH,
            height / LiquidPeMetrics.CONTENT_HEIGHT,
        ) * multiplier
        val renderedWidth = LiquidPeMetrics.CONTENT_WIDTH * scale
        val renderedHeight = LiquidPeMetrics.CONTENT_HEIGHT * scale
        // When the enlarged canvas overflows, anchor top-left so the tabs/search stay visible.
        offsetX = max(0f, (width - renderedWidth) * .5f)
        offsetY = max(0f, (height - renderedHeight) * .5f)
    }

    fun apply(canvas: Canvas) {
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
    }

    fun toLogical(screenX: Float, screenY: Float): PointF =
        reusablePoint.apply { set((screenX - offsetX) / scale, (screenY - offsetY) / scale) }

    fun setMultiplier(value: Float) {
        multiplier = if (value <= 0f) 1f else value
        if (viewWidth > 0 && viewHeight > 0) update(viewWidth, viewHeight)
    }

    val currentScale: Float get() = scale
}

/**
 * Small translation layer. Module/setting labels already arrive localized from
 * [com.elainaclient.game.ModuleNames]; only the renderer's fixed UI strings need a map.
 */
object LiquidPeI18n {

    private val labels = mapOf(
        "ClickGUI" to "点击界面",
        "HUD Editor" to "HUD 编辑",
        "Settings" to "设置",
        "Search" to "搜索",
        "Press a key" to "按下一个键",
    )

    fun t(text: String): String = labels[text] ?: text.localizedOption()

    /** Module names are already localized by the bridge. */
    fun moduleName(@Suppress("UNUSED_PARAMETER") id: String, fallback: String): String = fallback
}
