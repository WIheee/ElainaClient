package com.elainaclient.overlay.gui.liquidpe

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.UISettings
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * LiquidPE click GUI — a full **Kotlin rewrite** of OpenMoxel/OpenClickGUI's LiquidPE renderer
 * (`LiquidPeRenderer` / `LiquidPeOverlayView` / `LiquidPeModels` / `LiquidPeStore`).
 *
 * The drawing, hit-testing, panel drag, animations and inline settings are preserved; the data
 * model is Kotlin (sealed settings, nullable-safe fields) and the unused HUD/binds renderer and
 * grid container were dropped. [LiquidPeBridge] keeps LiquidPE's model in sync with Elaina's
 * modules/values. Colours and the project's own `minecraft.ttf` are kept as-is.
 *
 * Host additions:
 *  - immersive window flags (no status bar);
 *  - an invisible top-right escape hatch;
 *  - a transparent Compose text field laid exactly over the Canvas search box, so the search is
 *    typed inline (the Canvas renderer draws the text and caret);
 *  - the per-theme UI scale from the 界面 page.
 */
class LiquidPeGuiWindow : OverlayWindow() {

    override val immersive: Boolean get() = true

    private val bridge = LiquidPeBridge()
    private var liquidView: SyncedView? = null

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // Inherit the base immersive flags (FLAG_FULLSCREEN is added there for `immersive`
            // windows); drop NOT_FOCUSABLE so hardware keys reach the Canvas view and the inline
            // search field can raise the IME.
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
        var query by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val uiScale = UISettings.scale(LocalContext.current, GUITheme.LIQUIDPE)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same reference-frame maths as ReferenceViewport: the Canvas fills
            // min(w/2940, h/1837) * uiScale, centred unless it overflows (then top-left anchored).
            // Used to place the invisible search input exactly over the drawn search box.
            val fit = min(maxWidth.value / 2940f, maxHeight.value / 1837f) * uiScale
            val offsetX = max(0f, (maxWidth.value - 2940f * fit) / 2f)
            val offsetY = max(0f, (maxHeight.value - 1837f * fit) / 2f)

            AndroidView(
                factory = { context ->
                    SyncedView(context, bridge) { close() }.apply {
                        setUiScale(UISettings.scale(context, GUITheme.LIQUIDPE))
                        setSearchRequestListener { focusRequester.requestFocus() }
                        setOnClickGuiClosedListener {
                            OverlayManager.dismissOverlayWindow(this@LiquidPeGuiWindow)
                        }
                        setClickGuiVisibleImmediately(true)
                        bridge.push()
                        liquidView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Inline search input. Text/caret are drawn by the Canvas renderer; this field only
            // owns focus and the IME, so nothing is duplicated visually.
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    liquidView?.setSearchQuery(it)
                },
                modifier = Modifier
                    .offset(x = (offsetX + 1020f * fit).dp, y = (offsetY + 104f * fit).dp)
                    .width((830f * fit).dp)
                    .height((75f * fit).dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = (26f * fit).sp,
                ),
                cursorBrush = SolidColor(Color(0xFF3C69FC)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            // The Canvas's own "X" clears its query; mirror that back into the input field.
            LaunchedEffect(Unit) {
                while (true) {
                    delay(250)
                    val current = liquidView?.searchQuery ?: ""
                    if (current != query) query = current
                }
            }

            // Invisible escape hatch; the original has no close glyph either.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() },
            )
        }
    }

    private fun close() {
        val view = liquidView
        if (view != null && view.isClickGuiVisibleRequested) {
            view.setClickGuiVisible(false)
        } else {
            OverlayManager.dismissOverlayWindow(this)
        }
    }

    /**
     * Subclass that pumps the two-way bridge around the original view's own draw/touch pipeline.
     * No drawing code is overridden - only the sync points.
     */
    private class SyncedView(
        context: Context,
        private val bridge: LiquidPeBridge,
        private val onBack: () -> Unit,
    ) : LiquidPeOverlayView(context, null, bridge.store) {

        private var touching = false
        private var lastPush = 0L

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            if (!touching && now - lastPush > 400) {
                lastPush = now
                bridge.push()
            }
            super.onDraw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touching = true
                MotionEvent.ACTION_MOVE -> bridge.pull()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    bridge.pull()
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (super.onKeyUp(keyCode, event)) return true
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                onBack()
                return true
            }
            return false
        }
    }
}
