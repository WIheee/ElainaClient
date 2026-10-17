package com.elainaclient.overlay

import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope

@Suppress("MemberVisibilityCanBePrivate")
abstract class OverlayWindow {

    /**
     * Full-screen click GUIs set this to hide the status bar (clock/battery) and the navigation
     * bar, matching the original standalone apps. Small widget overlays leave it false so they
     * never disturb the game's system UI.
     */
    protected open val immersive: Boolean
        get() = false

    open val layoutParams by lazy {
        LayoutParams().apply {
            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            if (immersive) applyImmersiveCompat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha =
                    (OverlayManager.currentContext!!.getSystemService(Service.INPUT_SERVICE) as? InputManager)?.maximumObscuringOpacityForTouch
                        ?: 0.8f
            }
        }
    }

    /**
     * FLAG_FULLSCREEN is deprecated on API 30+ in favour of WindowInsetsController, but it is
     * still the only flag that reliably hides the status bar for non-Activity `WindowManager`
     * overlay windows. Kept as a compatibility fallback next to the immersive systemUiVisibility
     * flags set on [composeView]; isolated here so callers don't repeat the deprecated constant.
     */
    @Suppress("DEPRECATION")
    private fun LayoutParams.applyImmersiveCompat() {
        flags = flags or LayoutParams.FLAG_FULLSCREEN
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    open val composeView by lazy {
        ComposeView(OverlayManager.currentContext!!).apply {
            if (immersive) {
                fitsSystemWindows = false
                @Suppress("DEPRECATION")
                systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    val windowManager: WindowManager
        get() = OverlayManager.currentContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val lifecycleOwner = OverlayLifecycleOwner()

    val viewModelStore = ViewModelStore()

    val composeScope: CoroutineScope

    val recomposer: Recomposer

    var firstRun = true

    init {
        lifecycleOwner.performRestore(null)

        val coroutineContext = AndroidUiDispatcher.CurrentThread
        composeScope = CoroutineScope(coroutineContext)
        recomposer = Recomposer(coroutineContext)
    }

    @Composable
    abstract fun Content()

}