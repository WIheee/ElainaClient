package com.elainaclient.overlay.gui.hackai

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.UISettings
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Overlay host for the HackAI click GUI.
 *
 * The original is an 820x500 dp floating panel; here it is centred in a full-screen scrim and
 * uniformly scaled to the display (and the per-theme UI scale), so the fixed-size layout still
 * fits phones. The real panel lives in [HackAiPanelView].
 */
class HackAiGuiWindow : OverlayWindow() {

    override val immersive: Boolean get() = true

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // Drop NOT_FOCUSABLE so the search EditText can raise the IME and the back key works.
            flags = (flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    override val composeView by lazy {
        ComposeView(OverlayManager.currentContext!!).apply {
            fitsSystemWindows = false
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    @Composable
    override fun Content() {
        AndroidView(
            factory = { ctx ->
                HackAiRootView(ctx).apply {
                    setUiScale(UISettings.scale(ctx, GUITheme.HACKAI))
                    setOnCloseRequested {
                        OverlayManager.dismissOverlayWindow(this@HackAiGuiWindow)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Full-screen scrim + uniformly scaled [HackAiPanelView]. */
private class HackAiRootView(context: Context) : FrameLayout(context) {

    private val panel = HackAiPanelView(context)
    private var uiScale = 1f
    private var closing = false
    private var closeListener: (() -> Unit)? = null

    private val panelWidthPx = dp(HackAiStyle.PANEL_WIDTH_DP).toFloat()
    private val panelHeightPx = dp(HackAiStyle.PANEL_HEIGHT_DP).toFloat()

    init {
        setBackgroundColor(HackAiStyle.scrim)
        addView(
            panel,
            FrameLayout.LayoutParams(
                panelWidthPx.roundToInt(),
                panelHeightPx.roundToInt(),
                Gravity.CENTER,
            ),
        )
        panel.setOnCloseRequested { requestClose() }
        // Taps outside the panel close the GUI; taps on the panel are consumed by it.
        setOnClickListener { requestClose() }
    }

    fun setUiScale(scale: Float) {
        uiScale = scale
        applyScale()
    }

    fun setOnCloseRequested(listener: () -> Unit) {
        closeListener = listener
    }

    fun requestClose() {
        if (closing) return
        closing = true
        animate()
            .alpha(0f)
            .setDuration(140L)
            .withEndAction { closeListener?.invoke() }
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        closing = false
        alpha = 0f
        animate().alpha(1f).setDuration(180L).start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScale()
    }

    private fun applyScale() {
        if (width == 0 || height == 0) return
        val fit = min(width / panelWidthPx, height / panelHeightPx) * uiScale
        panel.pivotX = panelWidthPx / 2f
        panel.pivotY = panelHeightPx / 2f
        panel.scaleX = fit
        panel.scaleY = fit
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
