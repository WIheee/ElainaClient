package com.elainaclient.overlay.gui.kitsu

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elainaclient.R
import com.elainaclient.game.ClickGuiStyle
import com.elainaclient.game.LocalClickGuiStyle
import com.elainaclient.game.ModuleCategory
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.gui.classic.ConfigurationScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kitsu click-GUI theme — a full re-skin of the click GUI ported from LuminaCN-2.1.0's
 * `kitsugui/KitsuGUI.kt`: a 660x500 rounded card with a left logo/category sidebar, a header
 * with link/close buttons, and a content area that shows the Kitsu module list
 * ([KitsuModuleContent]) or the shared [ConfigurationScreen] for the Config category.
 *
 * The Kitsu palette is supplied via [LocalClickGuiStyle] so the module cards pick it up.
 */
class KitsuGUI : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = flags or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            if (Build.VERSION.SDK_INT >= 31) {
                // Full-screen blur-behind is the priciest thing this window composites; a smaller
                // radius keeps the frosted look while cutting per-frame cost.
                blurBehindRadius = 12
            }
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            dimAmount = 0.7f
            windowAnimations = 0
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var selectedCategory by mutableStateOf(ModuleCategory.Combat)

    @Composable
    override fun Content() {
        val style = ClickGuiStyle.Kitsu
        CompositionLocalProvider(LocalClickGuiStyle provides style) {
            val snackbarHostState = remember { SnackbarHostState() }
            var shouldAnimate by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                delay(50)
                shouldAnimate = true
            }

            val translateY by animateFloatAsState(
                targetValue = if (shouldAnimate) 0f else 100f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "kitsuSlide"
            )
            val alpha by animateFloatAsState(
                targetValue = if (shouldAnimate) 1f else 0f,
                animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
                label = "kitsuFade"
            )

            val dismissWithAnimation = remember {
                {
                    shouldAnimate = false
                    scope.launch {
                        delay(200)
                        OverlayManager.dismissOverlayWindow(this@KitsuGUI)
                    }
                    Unit
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x70000000), Color(0x90000000))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { dismissWithAnimation() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = style.cardBackground),
                    modifier = Modifier
                        .widthIn(max = 660.dp)
                        .height(500.dp)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .graphicsLayer {
                            translationY = translateY
                            this.alpha = alpha
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* swallow */ },
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Row(Modifier.fillMaxSize()) {
                        Sidebar(style)
                        VerticalDivider(color = style.border)
                        MainCard(style, snackbarHostState, dismissWithAnimation)
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }

    @Composable
    private fun Sidebar(style: ClickGuiStyle) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(
                    color = style.cardBackground,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Elaina",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = style.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(23.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ModuleCategory.entries.size) { index ->
                    val category = ModuleCategory.entries[index]
                    CategoryItem(
                        style = style,
                        category = category,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }
    }

    @Composable
    private fun CategoryItem(
        style: ClickGuiStyle,
        category: ModuleCategory,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) style.buttonBackground else Color.Transparent,
            animationSpec = tween(durationMillis = 200),
            label = "kitsuCatBg"
        )
        val contentColor by animateColorAsState(
            targetValue = if (isSelected) style.textPrimary else style.textSecondary,
            animationSpec = tween(durationMillis = 200),
            label = "kitsuCatFg"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color = backgroundColor, shape = RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = category.iconResId),
                contentDescription = category.displayName,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun MainCard(
        style: ClickGuiStyle,
        snackbarHostState: SnackbarHostState,
        dismissWithAnimation: () -> Unit
    ) {
        Card(
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            colors = CardDefaults.cardColors(containerColor = style.cardBackground),
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCategory.displayName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = style.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ActionButton(
                        style,
                        R.drawable.ic_close,
                        isClose = true,
                        onClick = dismissWithAnimation
                    )
                }

                Spacer(Modifier.height(16.dp))

                key(selectedCategory) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInHorizontally(animationSpec = tween(300)) { it / 4 },
                        exit = fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = tween(200)) { -it / 4 }
                    ) {
                        if (selectedCategory == ModuleCategory.Config) {
                            ConfigurationScreen(snackbarHostState = snackbarHostState)
                        } else {
                            KitsuModuleContent(selectedCategory)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        style: ClickGuiStyle,
        iconRes: Int,
        isClose: Boolean = false,
        onClick: () -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }
        val backgroundColor by animateColorAsState(
            targetValue = when {
                isClose -> Color(0xFFE81123)
                isPressed -> style.buttonBackground
                else -> style.buttonBackground.copy(alpha = 0.6f)
            },
            animationSpec = tween(durationMillis = 150),
            label = "kitsuBtnBg"
        )
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = tween(durationMillis = 100),
            label = "kitsuBtnScale"
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isClose) Color.White else style.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(100)
                isPressed = false
            }
        }
    }

}
