package com.elainaclient.overlay.gui.protohax

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.elainaclient.game.ModuleCategory
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.ConfigurationScreen

/**
 * Protohax click GUI, ported from LuminaCN-2.1.0's `overlay/protohax/ProtohaxUi.kt`.
 *
 * Layout: a large Material3 card inset from the screen, a vertical category rail (all
 * [ModuleCategory] entries, Config included), a divider, and the selected page. Items expand
 * in place to reveal their settings ([ProtohaxModuleContent]).
 *
 * ElainaClient differences from the source:
 *  - `CheatCategory`/`Element`/`GameManager` → [ModuleCategory]/`Module`/ModuleManager.
 *  - The source has a `Home` page; ElainaClient has no Home category, so the GUI opens on Combat
 *    and the page switch only distinguishes Config from module categories.
 *  - `HomeCategoryUi()`/`ConfigCategoryContent()` → the shared [ConfigurationScreen].
 */
class ProtohaxGUI : OverlayWindow() {

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

    @Composable
    override fun Content() {
        val snackbarHostState = remember { SnackbarHostState() }

        Column(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { OverlayManager.dismissOverlayWindow(this) },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(vertical = 30.dp, horizontal = 100.dp)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* swallow taps inside the card */ },
            ) {
                Row(Modifier.fillMaxSize()) {
                    ProtohaxNavigationRail(
                        windowInsets = WindowInsets(8, 8, 8, 8),
                    ) {
                        ModuleCategory.entries.fastForEach { category ->
                            NavigationRailItem(
                                selected = selectedCategory == category,
                                onClick = {
                                    if (selectedCategory != category) {
                                        selectedCategory = category
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(category.iconResId),
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(category.displayName)
                                },
                                alwaysShowLabel = true,
                            )
                        }
                    }

                    VerticalDivider()

                    AnimatedContent(
                        targetState = selectedCategory,
                        label = "animatedPage",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    ) { category ->
                        Box(Modifier.fillMaxSize()) {
                            if (category == ModuleCategory.Config) {
                                ConfigurationScreen(snackbarHostState = snackbarHostState)
                            } else {
                                ProtohaxModuleContent(category)
                            }
                        }
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

/** Vertical rail (LuminaCN `ui/component/NavigationRailX.kt`). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProtohaxNavigationRail(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(windowInsets)
                .widthIn(min = 80.dp)
                .padding(vertical = 10.dp)
                .selectableGroup()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.CenterVertically),
            content = content,
        )
    }
}
