package com.elainaclient.overlay.gui.dream

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import kotlin.math.min

/**
 * Overlay host for the Dream click GUI.
 *
 * The GUI is Dream's original Canvas layout rewritten in Kotlin ([DreamOverlayView] /
 * [DreamRenderer]); this window only supplies the full-screen immersive host, the per-theme UI
 * scale and the open/close animation.
 */
class DreamGuiWindow : OverlayWindow() {

    override val immersive: Boolean get() = true

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // Keep the base immersive flags (FLAG_FULLSCREEN), allow the scaled panel to overflow
            // the view, and drop NOT_FOCUSABLE so the Android back key reaches DreamOverlayView.
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
        var searchActive by remember { mutableStateOf(false) }
        var focusRequests by remember { mutableStateOf(0) }
        var dreamView by remember { mutableStateOf<DreamOverlayView?>(null) }
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val uiScale = UISettings.scale(LocalContext.current, GUITheme.DREAM)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same reference-frame maths as DreamOverlayView, used to lay the transparent text
            // field exactly over the Canvas search box (Canvas draws the text and caret).
            val fit = min(
                maxWidth.value / DreamStyle.REFERENCE_WIDTH,
                maxHeight.value / DreamStyle.REFERENCE_HEIGHT,
            ) * uiScale
            val offsetX = if (DreamStyle.REFERENCE_WIDTH * fit > maxWidth.value) {
                0f
            } else {
                (maxWidth.value - DreamStyle.REFERENCE_WIDTH * fit) / 2f
            }
            val offsetY = if (DreamStyle.REFERENCE_HEIGHT * fit > maxHeight.value) {
                0f
            } else {
                (maxHeight.value - DreamStyle.REFERENCE_HEIGHT * fit) / 2f
            }

            AndroidView(
                factory = { ctx ->
                    DreamOverlayView(ctx).apply {
                        setUiScale(UISettings.scale(ctx, GUITheme.DREAM))
                        setOnCloseRequested {
                            OverlayManager.dismissOverlayWindow(this@DreamGuiWindow)
                        }
                        setOnSearchRequested {
                            searchActive = true
                            query = searchQuery
                            focusRequests++
                        }
                        dreamView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (searchActive) {
                // Text/caret are drawn by the Canvas; this field only owns focus and the IME.
                // Width stops before the Canvas-drawn "X" so that clear taps reach the view.
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        dreamView?.setSearchQuery(it)
                    },
                    modifier = Modifier
                        .offset(
                            x = (offsetX + 374f * fit).dp,
                            y = (offsetY + 31f * fit).dp,
                        )
                        .width((400f * fit).dp)
                        .height((34f * fit).dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.Transparent,
                        fontSize = (20f * fit).sp,
                    ),
                    cursorBrush = SolidColor(Color(0xFFC77DFF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                    ),
                )
            }

            LaunchedEffect(focusRequests) {
                if (focusRequests > 0 && searchActive) {
                    focusRequester.requestFocus()
                }
            }

            // Canvas -> host sync: the magnifier/X/back key/category taps mutate the view state.
            LaunchedEffect(Unit) {
                while (true) {
                    delay(200L)
                    val view = dreamView ?: continue
                    if (view.searchQuery != query) query = view.searchQuery
                    if (view.isSearchActive != searchActive) {
                        searchActive = view.isSearchActive
                        if (!searchActive) focusManager.clearFocus()
                    }
                }
            }
        }
    }
}
