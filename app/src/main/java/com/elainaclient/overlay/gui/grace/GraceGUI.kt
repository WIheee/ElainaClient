package com.elainaclient.overlay.gui.grace

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.ConfigurationScreen

/**
 * Grace click GUI, ported from LuminaCN-2.1.0's `overlay/grace/GraceMenuUi.kt`.
 *
 * Layout: a full-screen dimming/blurred backdrop, a 70dp horizontal category rail (brand name +
 * every [ModuleCategory], including Config), and the category body below it. Modules with settings
 * open [GraceModuleSettingsScreen] as a left-sliding panel.
 *
 * ElainaClient differences from the source:
 *  - `CheatCategory`/`Element`/`GameManager` → [ModuleCategory]/[Module]/ModuleManager.
 *  - No `Home` category exists here, so none is filtered out.
 *  - `ConfigCategoryContent()` → the shared [ConfigurationScreen].
 */
class GraceGUI : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = flags or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 15
            }

            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

            dimAmount = 0.4f
            windowAnimations = android.R.style.Animation_Dialog
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var selectedCategory by mutableStateOf(ModuleCategory.Combat)
    private var settingsModule by mutableStateOf<Module?>(null)

    @Composable
    override fun Content() {
        val snackbarHostState = remember { SnackbarHostState() }

        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { OverlayManager.dismissOverlayWindow(this) },
            contentAlignment = Alignment.Center,
        ) {
            GraceElevatedCard {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                ) {
                    Box(
                        modifier = Modifier.height(70.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            GraceNavigationRail(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                            ) {
                                Text(
                                    text = "Elaina",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                ModuleCategory.entries.forEach { category ->
                                    GraceNavigationRailItem(
                                        selected = selectedCategory == category,
                                        onClick = { selectedCategory = category },
                                        iconResId = category.iconResId,
                                        label = category.displayName,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = selectedCategory,
                        label = "gracePage",
                        modifier = Modifier.fillMaxSize(),
                    ) { category ->
                        if (category == ModuleCategory.Config) {
                            ConfigurationScreen(
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        } else {
                            GraceModuleContent(category) { settingsModule = it }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )

            settingsModule?.let { module ->
                GraceModuleSettingsScreen(module) { settingsModule = null }
            }
        }
    }
}
