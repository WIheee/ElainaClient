package com.elainaclient.overlay.gui.grace

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elainaclient.game.Module
import com.elainaclient.game.localizedModule

/**
 * Grace's animated-gradient elevated card (LuminaCN `ElevatedCardX`).
 *
 * Centrepiece of the Grace shell. The border gradient sweeps continuously; see the perf note on
 * [GraceNavigationRailItem] - both animations run at ~60 fps while the GUI is open.
 */
@Composable
internal fun GraceElevatedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "graceCard")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "graceCardOffset",
    )

    val brush = remember(offset) {
        Brush.linearGradient(
            colors = listOf(GracePalette.BorderA, GracePalette.BorderB),
            start = Offset(offset, offset),
            end = Offset(offset + 300f, offset + 100f),
        )
    }

    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = GracePalette.Card.copy(alpha = 0.95f)),
        border = BorderStroke(0.5.dp, brush),
        modifier = modifier.padding(horizontal = 80.dp, vertical = 20.dp),
    ) {
        content()
    }
}

/** Horizontal category rail (LuminaCN `NavigationRailY`). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GraceNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = GracePalette.NavRail,
        contentColor = contentColorFor(GracePalette.NavRail),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                content = content,
            )
        }
    }
}

/**
 * Category button with an animated gradient when selected (LuminaCN `NavigationRailItemY`).
 *
 * Perf: this opens a second infinite transition (the card border is the first). It is intentional
 * for the Grace look; if the HUD ever feels heavy, freeze [offset] at a constant instead.
 */
@Composable
internal fun GraceNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconResId: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "graceNavItem")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "graceNavOffset",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .clickable(onClick = onClick)
            .height(40.dp)
            .background(
                brush = if (selected) {
                    Brush.linearGradient(
                        colors = listOf(
                            GracePalette.NavSelectedA,
                            GracePalette.NavSelectedB,
                            GracePalette.NavSelectedC,
                        ),
                        start = Offset(offset, offset),
                        end = Offset(offset + 400f, offset + 400f),
                    )
                } else {
                    SolidColor(Color.Transparent)
                },
                shape = RoundedCornerShape(15.dp),
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                color = Color.White,
                style = TextStyle(fontSize = 16.sp),
            )
        }
    }
}

/** 70dp module tile (LuminaCN `ModuleCardX`). */
@Composable
internal fun GraceModuleCard(
    module: Module,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
) {
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(GracePalette.ModuleEnabledA, GracePalette.ModuleEnabledB),
            start = Offset.Zero,
            end = Offset(500f, 500f),
        )
    }

    Card(
        modifier = modifier
            .size(70.dp)
            .clickable { module.isEnabled = !module.isEnabled },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (!module.isEnabled) GracePalette.ModuleDisabled else Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (module.isEnabled) 8.dp else 2.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (module.isEnabled) Modifier.background(gradientBrush) else Modifier,
                ),
        ) {
            if (module.values.isNotEmpty()) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = module.name.localizedModule(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
