package com.elainaclient.overlay.gui.kitsu

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.elainaclient.game.BoolValue
import com.elainaclient.game.ClickGuiStyle
import com.elainaclient.game.EnumValue
import com.elainaclient.game.FloatValue
import com.elainaclient.game.IntValue
import com.elainaclient.game.ListValue
import com.elainaclient.game.LocalClickGuiStyle
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.ModuleManager
import com.elainaclient.game.StringValue
import com.elainaclient.game.localizedModule
import com.elainaclient.game.localizedOption
import com.elainaclient.overlay.OverlayManager
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Kitsu-styled module list (ported from LuminaCN-2.1.0 `kitsugui/ElementUI.kt`, re-wired to
 * ElainaClient's [Module]/[com.elainaclient.game.Value] API and themed via [LocalClickGuiStyle]).
 */
@Composable
fun KitsuModuleContent(category: ModuleCategory, modifier: Modifier = Modifier) {
    val modules = remember(category) {
        ModuleManager.modules.filter { !it.private && it.category === category }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(modules.size, key = { modules[it].name }) { index ->
            KitsuModuleCard(modules[index])
        }
    }
}

@Composable
private fun KitsuModuleCard(module: Module) {
    val style = LocalClickGuiStyle.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = module.isEnabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { module.isEnabled = it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = style.cardBackgroundExpanded)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic)
                    ),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = module.name.localizedModule(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = style.textPrimary
                        )
                        Text(
                            text = "启用这个模块",
                            style = MaterialTheme.typography.bodySmall,
                            color = style.textMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = style.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        module.values.forEach { value ->
                            when (value) {
                                is BoolValue -> KitsuBoolValue(value)
                                is FloatValue -> KitsuFloatValue(value)
                                is IntValue -> KitsuIntValue(value)
                                is ListValue -> KitsuChoiceValue(value)
                                is EnumValue<*> -> KitsuEnumValue(value)
                                is StringValue -> KitsuStringValue(value)
                            }
                        }
                        KitsuShortcutContent(module)
                    }
                }
            }

            val accentBarHeight by animateFloatAsState(
                targetValue = if (isExpanded) 150f else 70f,
                animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic),
                label = "KitsuAccentBarHeight"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(8.dp)
                    .height(accentBarHeight.dp)
                    .background(
                        color = if (module.isEnabled && !isExpanded) style.accent else Color.Transparent,
                        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                    )
            )
        }
    }
}

@Composable
private fun KitsuLabel(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = LocalClickGuiStyle.current.textPrimary
    )
}

@Composable
private fun KitsuBoolValue(value: BoolValue) {
    val style = LocalClickGuiStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .toggleable(
                value = value.value,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { value.value = it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        KitsuLabel(value.name.localizedOption())
        Switch(
            checked = value.value,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                uncheckedThumbColor = style.textMuted,
                checkedTrackColor = style.accent,
                uncheckedTrackColor = style.buttonBackground,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitsuFloatValue(value: FloatValue) {
    val style = LocalClickGuiStyle.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KitsuLabel(value.name.localizedOption())
            Text(
                text = String.format(Locale.US, "%.2f", value.value),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = style.textPrimary
            )
        }
        val colors = SliderDefaults.colors(
            thumbColor = style.accent,
            activeTrackColor = style.accent,
            inactiveTrackColor = style.buttonBackground
        )
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = value.value,
            valueRange = value.range,
            colors = colors,
            onValueChange = {
                val rounded = ((it * 100.0).roundToInt() / 100.0).toFloat()
                if (value.value != rounded) value.value = rounded
            },
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    thumbSize = DpSize(20.dp, 20.dp),
                    enabled = true
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KitsuIntValue(value: IntValue) {
    val style = LocalClickGuiStyle.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KitsuLabel(value.name.localizedOption())
            Text(
                text = value.value.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = style.textPrimary
            )
        }
        val colors = SliderDefaults.colors(
            thumbColor = style.accent,
            activeTrackColor = style.accent,
            inactiveTrackColor = style.buttonBackground
        )
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = value.value.toFloat(),
            valueRange = value.range.first.toFloat()..value.range.last.toFloat(),
            steps = (value.range.last - value.range.first - 1).coerceAtLeast(0),
            colors = colors,
            onValueChange = {
                val next = it.roundToInt()
                if (value.value != next) value.value = next
            },
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    thumbSize = DpSize(20.dp, 20.dp),
                    enabled = true
                )
            }
        )
    }
}

@Composable
private fun KitsuChoiceValue(value: ListValue) {
    val style = LocalClickGuiStyle.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        KitsuLabel(value.name.localizedOption())
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            value.listItems.forEach { item ->
                val isSelected = value.value == item
                KitsuChip(
                    label = item.name.localizedOption(),
                    isSelected = isSelected,
                    onClick = { if (value.value != item) value.value = item }
                )
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> KitsuEnumValue(value: EnumValue<T>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        KitsuLabel(value.name.localizedOption())
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            value.enumClass.enumConstants?.forEach { option ->
                val isSelected = value.value == option
                KitsuChip(
                    label = option.name.localizedOption(),
                    isSelected = isSelected,
                    onClick = { if (value.value != option) value.value = option }
                )
            }
        }
    }
}

@Composable
private fun KitsuChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val style = LocalClickGuiStyle.current
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        },
        shape = RoundedCornerShape(12.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) Color.Transparent else style.border
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = style.buttonBackground,
            labelColor = style.textPrimary,
            selectedContainerColor = style.accent,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun KitsuStringValue(value: StringValue) {
    val style = LocalClickGuiStyle.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        KitsuLabel(value.name.localizedOption())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = style.accent,
                unfocusedBorderColor = style.border,
                focusedTextColor = style.textPrimary,
                unfocusedTextColor = style.textPrimary,
                cursorColor = style.accent
            )
        )
    }
}

@Composable
private fun KitsuShortcutContent(module: Module) {
    val style = LocalClickGuiStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .toggleable(
                value = module.isShortcutDisplayed,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                module.isShortcutDisplayed = it
                if (it) OverlayManager.showOverlayWindow(module.overlayShortcutButton)
                else OverlayManager.dismissOverlayWindow(module.overlayShortcutButton)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Shortcut,
                contentDescription = null,
                tint = style.textMuted,
                modifier = Modifier.size(16.dp)
            )
            Text("快捷键", style = MaterialTheme.typography.bodyMedium, color = style.textPrimary)
        }
        Switch(
            checked = module.isShortcutDisplayed,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                uncheckedThumbColor = style.textMuted,
                checkedTrackColor = style.accent,
                uncheckedTrackColor = style.buttonBackground,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
