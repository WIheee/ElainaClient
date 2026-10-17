package com.elainaclient.overlay.gui.grace

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.elainaclient.R
import com.elainaclient.game.BoolValue
import com.elainaclient.game.EnumValue
import com.elainaclient.game.FloatValue
import com.elainaclient.game.IntValue
import com.elainaclient.game.ListValue
import com.elainaclient.game.StringValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.ModuleManager
import com.elainaclient.game.localizedModule
import com.elainaclient.game.localizedOption
import com.elainaclient.overlay.OverlayManager
import kotlin.math.roundToInt

/**
 * Grace's module grid (LuminaCN `ModuleContentX`): adaptive 90dp tiles, one per module in
 * [category]. Modules with settings get a gear that opens [GraceModuleSettingsScreen]; the rest
 * toggle on tap.
 */
@Composable
internal fun GraceModuleContent(
    category: ModuleCategory,
    onOpenSettings: (Module) -> Unit,
) {
    val modules = remember(category) {
        ModuleManager.modules.filter { it.category == category }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 90.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(modules.size) { index ->
            val module = modules[index]
            GraceModuleCard(
                module = module,
                onOpenSettings = { onOpenSettings(module) },
            )
        }
    }
}

/**
 * Grace's per-module settings panel (LuminaCN `ModuleSettingsScreen`): slides in from the left,
 * lists every [Value] plus the shortcut toggle.
 */
@Composable
internal fun GraceModuleSettingsScreen(
    module: Module,
    onDismiss: () -> Unit,
) {
    var shouldAnimate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shouldAnimate = true }

    val offsetX by animateDpAsState(
        targetValue = if (shouldAnimate) 0.dp else (-500).dp,
        animationSpec = tween(durationMillis = 300),
        label = "graceSettingsSlide",
    )

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .wrapContentHeight()
                .padding(16.dp)
                .offset(x = offsetX)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GracePalette.Screen, GracePalette.Screen),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 400f),
                    ),
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            Surface(
                onClick = onDismiss,
                color = Color.Transparent,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = module.name.localizedModule(),
                            modifier = Modifier.align(Alignment.CenterStart),
                            style = MaterialTheme.typography.titleMedium,
                            color = GracePalette.ScreenText.copy(alpha = 0.9f),
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "关闭",
                                tint = GracePalette.ScreenText.copy(alpha = 0.9f),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        module.values.fastForEach { value ->
                            when (value) {
                                is BoolValue -> GraceBoolValueContent(value)
                                is FloatValue -> GraceFloatValueContent(value)
                                is IntValue -> GraceIntValueContent(value)
                                is ListValue -> GraceListValueContent(value)
                                is EnumValue<*> -> GraceEnumValueContent(value)
                                is StringValue -> GraceStringValueContent(value)
                            }
                        }

                        GraceShortcutContent(module)

                        Spacer(Modifier.height(4.dp))

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = GracePalette.ScreenText.copy(alpha = 0.9f),
                            ),
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Value renderers (LuminaCN ModuleContentX, adapted to Elaina's Value API + translations)
// ---------------------------------------------------------------------------------------------

@Composable
private fun GraceListValueContent(value: ListValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = GracePalette.Text,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(value.listItems.toList()) { item ->
                ElevatedFilterChip(
                    selected = value.value == item,
                    onClick = {
                        if (value.value != item) {
                            value.value = item
                        }
                    },
                    label = { Text(text = item.name.localizedOption()) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = GracePalette.Text,
                        selectedContainerColor = Color.Black,
                        selectedLabelColor = GracePalette.Text,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GraceFloatValueContent(value: FloatValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Row {
            Text(
                text = value.name.localizedOption(),
                style = MaterialTheme.typography.bodyMedium,
                color = GracePalette.Text,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%.1f".format(value.value),
                style = MaterialTheme.typography.bodyMedium,
                color = GracePalette.Text,
            )
        }

        GraceGradientSlider(
            value = value.value,
            onValueChange = { newValue -> value.value = newValue },
            valueRange = value.range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GraceIntValueContent(value: IntValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Row {
            Text(
                text = value.name.localizedOption(),
                style = MaterialTheme.typography.bodyMedium,
                color = GracePalette.Text,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value.value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = GracePalette.Text,
            )
        }

        GraceGradientSlider(
            value = value.value.toFloat(),
            onValueChange = { newValue -> value.value = newValue.roundToInt() },
            valueRange = value.range.toFloatRange(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T : Enum<T>> GraceEnumValueContent(value: EnumValue<T>) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = GracePalette.Text,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(value.enumClass.enumConstants?.toList().orEmpty()) { option ->
                ElevatedFilterChip(
                    selected = value.value == option,
                    onClick = {
                        if (value.value != option) {
                            value.value = option
                        }
                    },
                    label = { Text(text = option.name.localizedOption()) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = GracePalette.Text,
                        selectedContainerColor = Color.Black,
                        selectedLabelColor = GracePalette.Text,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GraceStringValueContent(value: StringValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = GracePalette.Text,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GracePalette.BorderA,
                unfocusedBorderColor = GracePalette.Text,
                focusedTextColor = GracePalette.Text,
                unfocusedTextColor = GracePalette.Text,
                cursorColor = GracePalette.BorderA,
            ),
        )
    }
}

@Composable
private fun GraceBoolValueContent(value: BoolValue) {
    Row(
        Modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
            .toggleable(
                value = value.value,
                interactionSource = null,
                indication = null,
                onValueChange = { value.value = it },
            ),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = GracePalette.Text,
        )
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = value.value,
            onCheckedChange = null,
            modifier = Modifier.padding(0.dp),
            colors = CheckboxDefaults.colors(
                uncheckedColor = GracePalette.Text,
                checkedColor = GracePalette.Text,
                checkmarkColor = GracePalette.Text,
            ),
        )
    }
}

@Composable
private fun GraceShortcutContent(module: Module) {
    Row(
        Modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .toggleable(
                value = module.isShortcutDisplayed,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = { shown ->
                    module.isShortcutDisplayed = shown
                    if (shown) {
                        OverlayManager.showOverlayWindow(module.overlayShortcutButton)
                    } else {
                        OverlayManager.dismissOverlayWindow(module.overlayShortcutButton)
                    }
                },
            ),
    ) {
        Text(
            text = stringResource(R.string.shortcut),
            style = MaterialTheme.typography.bodyMedium,
            color = GracePalette.Text,
        )
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = module.isShortcutDisplayed,
            onCheckedChange = null,
            modifier = Modifier.padding(0.dp),
            colors = CheckboxDefaults.colors(
                uncheckedColor = GracePalette.Text,
                checkedColor = GracePalette.Text,
                checkmarkColor = GracePalette.Text,
            ),
        )
    }
}

/**
 * Replaces LuminaCN's external `ColorfulSlider` (SmartToolFactory) with the same look drawn on a
 * Material3 slider: dark rail, pink→cyan→blue gradient fill up to the thumb, light border.
 * Avoids adding a JitPack dependency for one control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraceGradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors(
        thumbColor = GracePalette.Text,
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        colors = colors,
        track = { sliderState ->
            val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
            val fraction = if (span <= 0f) {
                0f
            } else {
                ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3A3A3A))
                    .border(BorderStroke(2.dp, Color(0xFFADADAD)), RoundedCornerShape(6.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF85FB6),
                                    Color(0xFF8EFAFA),
                                    Color(0xFF439CFB),
                                ),
                            ),
                        ),
                )
            }
        },
    )
}

private fun IntRange.toFloatRange() = first.toFloat()..last.toFloat()
