package com.elainaclient.overlay.gui.protohax

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
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
 * Protohax module list (LuminaCN `ModuleContentY`): a single column of cards. Tapping a card with
 * settings expands it in place; a card without settings toggles the module.
 *
 * The source loaded the module list off the main thread behind a spinner; ElainaClient's
 * [ModuleManager.modules] is an in-memory singleton, so the list is read directly.
 */
@Composable
internal fun ProtohaxModuleContent(category: ModuleCategory) {
    val modules = remember(category) {
        ModuleManager.modules.filter { it.category == category }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(modules.size) { index ->
            ProtohaxModuleCard(modules[index])
        }
    }
}

@Composable
private fun ProtohaxModuleCard(module: Module) {
    val hasSettings = module.values.isNotEmpty()

    val background by animateColorAsState(
        targetValue = if (module.isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.background
        },
        label = "protohaxCardBg",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (hasSettings) {
                        module.isExpanded = !module.isExpanded
                    } else {
                        module.isEnabled = !module.isEnabled
                    }
                },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = background),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(10.dp),
            ) {
                Text(
                    text = module.name.localizedModule(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (module.isExpanded) {
                        contentColorFor(MaterialTheme.colorScheme.primary)
                    } else {
                        contentColorFor(MaterialTheme.colorScheme.background)
                    },
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = module.isEnabled,
                    onCheckedChange = { module.isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedBorderColor = if (module.isExpanded) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = if (module.isExpanded) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        uncheckedThumbColor = if (module.isExpanded) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier
                        .width(52.dp)
                        .height(32.dp),
                )
            }

            if (module.isExpanded && hasSettings) {
                module.values.fastForEach { value ->
                    when (value) {
                        is BoolValue -> ProtohaxBoolValueContent(value)
                        is FloatValue -> ProtohaxFloatValueContent(value)
                        is IntValue -> ProtohaxIntValueContent(value)
                        is ListValue -> ProtohaxListValueContent(value)
                        is EnumValue<*> -> ProtohaxEnumValueContent(value)
                        is StringValue -> ProtohaxStringValueContent(value)
                    }
                }
                ProtohaxShortcutContent(module)
            }
        }
    }
}

@Composable
private fun ProtohaxListValueContent(value: ListValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            value.listItems.forEach { item ->
                ElevatedFilterChip(
                    selected = value.value == item,
                    onClick = {
                        if (value.value != item) {
                            value.value = item
                        }
                    },
                    label = { Text(item.name.localizedOption()) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtohaxFloatValueContent(value: FloatValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Row {
            Text(
                text = value.name.localizedOption(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value.value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surface,
            )
        }

        ProtohaxSlider(
            value = value.value,
            valueRange = value.range,
            onValueChange = { newValue ->
                val rounded = ((newValue * 100.0).roundToInt() / 100.0).toFloat()
                if (value.value != rounded) {
                    value.value = rounded
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtohaxIntValueContent(value: IntValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Row {
            Text(
                text = value.name.localizedOption(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value.value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surface,
            )
        }

        ProtohaxSlider(
            value = value.value.toFloat(),
            valueRange = value.range.toFloatRange(),
            onValueChange = { newValue ->
                val rounded = newValue.roundToInt()
                if (value.value != rounded) {
                    value.value = rounded
                }
            },
        )
    }
}

/** Material3 slider styled like the Protohax source (thin height, no stop indicator). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtohaxSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.surface,
        activeTrackColor = MaterialTheme.colorScheme.surface,
        activeTickColor = MaterialTheme.colorScheme.surface,
        inactiveTickColor = MaterialTheme.colorScheme.outlineVariant,
        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    )
    val interactionSource = remember { MutableInteractionSource() }

    Slider(
        value = animateFloatAsState(
            targetValue = value,
            label = "protohaxSlider",
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        ).value,
        valueRange = valueRange,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                thumbSize = DpSize(4.dp, 22.dp),
                enabled = true,
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                colors = colors,
                enabled = true,
                sliderState = sliderState,
                drawStopIndicator = null,
                thumbTrackGapSize = 4.dp,
            )
        },
        onValueChange = onValueChange,
    )
}

@Composable
private fun <T : Enum<T>> ProtohaxEnumValueContent(value: EnumValue<T>) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            value.enumClass.enumConstants?.forEach { option ->
                ElevatedFilterChip(
                    selected = value.value == option,
                    onClick = {
                        if (value.value != option) {
                            value.value = option
                        }
                    },
                    label = { Text(option.name.localizedOption()) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProtohaxStringValueContent(value: StringValue) {
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Text(
            text = value.name.localizedOption(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun ProtohaxBoolValueContent(value: BoolValue) {
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
            color = MaterialTheme.colorScheme.surface,
        )
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = value.value,
            onCheckedChange = null,
            modifier = Modifier.padding(0.dp),
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.surface,
                checkedColor = MaterialTheme.colorScheme.surface,
                checkmarkColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun ProtohaxShortcutContent(module: Module) {
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
            color = MaterialTheme.colorScheme.surface,
        )
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = module.isShortcutDisplayed,
            onCheckedChange = null,
            modifier = Modifier.padding(0.dp),
            colors = CheckboxDefaults.colors(
                uncheckedColor = MaterialTheme.colorScheme.surface,
                checkedColor = MaterialTheme.colorScheme.surface,
                checkmarkColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

private fun IntRange.toFloatRange() = first.toFloat()..last.toFloat()
