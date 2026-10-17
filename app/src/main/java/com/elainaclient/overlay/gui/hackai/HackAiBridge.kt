package com.elainaclient.overlay.gui.hackai

import com.elainaclient.game.BoolValue
import com.elainaclient.game.EnumValue
import com.elainaclient.game.FloatValue
import com.elainaclient.game.IntValue
import com.elainaclient.game.ListItem
import com.elainaclient.game.ListValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.ModuleManager
import com.elainaclient.game.StringValue
import com.elainaclient.game.Value
import com.elainaclient.game.localizedModule
import com.elainaclient.game.localizedOption
import com.elainaclient.overlay.OverlayManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Data adapter for the HackAI click GUI.
 *
 * HackAI's demo repository (`ModuleRepository` / `ModuleItem` / `SubSetting`) is replaced by
 * Elaina's live modules. The card layout is unchanged: name, description, switch, and a long-press
 * expanded area with sliders / option chips / toggles plus the shortcut switch. String values are
 * skipped because the original card has no text-field settings row.
 */
object HackAiBridge {

    val categories: List<ModuleCategory> = listOf(
        ModuleCategory.Combat,
        ModuleCategory.Motion,
        ModuleCategory.Visual,
        ModuleCategory.World,
        ModuleCategory.Misc,
    )

    fun modules(category: ModuleCategory, query: String): List<Module> {
        val q = query.trim()
        return ModuleManager.modules.filter { module ->
            module.category == category &&
                (q.isEmpty() ||
                    module.name.contains(q, true) ||
                    module.name.localizedModule().contains(q, true))
        }
    }

    fun settings(module: Module): List<Value<*>> =
        module.values.filter { it !is StringValue }

    fun moduleName(module: Module, english: Boolean): String =
        if (english) module.name else module.name.localizedModule()

    fun moduleDescription(module: Module, english: Boolean): String {
        val count = settings(module).size
        return if (english) {
            "${module.category.name} · $count options"
        } else {
            "${module.category.displayName} · $count 项设置"
        }
    }

    fun categoryName(category: ModuleCategory, english: Boolean): String =
        if (english) category.name else category.displayName

    fun toggle(module: Module) {
        module.isEnabled = !module.isEnabled
    }

    fun setShortcut(module: Module, shown: Boolean) {
        module.isShortcutDisplayed = shown
        if (shown) {
            OverlayManager.showOverlayWindow(module.overlayShortcutButton)
        } else {
            OverlayManager.dismissOverlayWindow(module.overlayShortcutButton)
        }
    }

    // =========================================================================================
    // Sliders
    // =========================================================================================

    fun isSlider(value: Value<*>): Boolean = value is FloatValue || value is IntValue

    /** min, max, step for HackAiSlider. */
    fun sliderRange(value: Value<*>): Triple<Float, Float, Float> = when (value) {
        is FloatValue -> {
            val span = value.range.endInclusive - value.range.start
            Triple(value.range.start, value.range.endInclusive, if (span >= 50f) 1f else 0.1f)
        }

        is IntValue -> Triple(value.range.first.toFloat(), value.range.last.toFloat(), 1f)
        else -> Triple(0f, 1f, 0.1f)
    }

    fun sliderValue(value: Value<*>): Float = when (value) {
        is FloatValue -> value.value
        is IntValue -> value.value.toFloat()
        else -> 0f
    }

    fun writeSlider(value: Value<*>, newValue: Float) {
        when (value) {
            is FloatValue -> value.value = newValue.coerceIn(value.range)
            is IntValue -> value.value = newValue.roundToInt().coerceIn(value.range)
            else -> Unit
        }
    }

    // =========================================================================================
    // Enums / lists
    // =========================================================================================

    fun isOption(value: Value<*>): Boolean = value is EnumValue<*> || value is ListValue

    fun options(value: Value<*>): List<Any> = when (value) {
        is EnumValue<*> -> value.enumClass.enumConstants?.map { it as Any }.orEmpty()
        is ListValue -> value.listItems.map { it as Any }
        else -> emptyList()
    }

    fun optionLabel(option: Any, english: Boolean): String = when (option) {
        is Enum<*> -> if (english) option.name else option.name.localizedOption()
        is ListItem -> if (english) option.name else option.name.localizedOption()
        else -> option.toString()
    }

    fun selectedIndex(value: Value<*>): Int {
        val current: Any? = when (value) {
            is EnumValue<*> -> value.value
            is ListValue -> value.value
            else -> return -1
        }
        return options(value).indexOfFirst { it == current }
    }

    fun selectOption(value: Value<*>, index: Int) {
        val option = options(value).getOrNull(index) ?: return
        setUntyped(value, option)
    }

    fun displayValue(value: Value<*>, english: Boolean): String = when (value) {
        is FloatValue -> formatNumber(value.value, sliderRange(value).third)
        is IntValue -> value.value.toString()
        is EnumValue<*> -> {
            val enum = value.value as? Enum<*>
            if (english) enum?.name.orEmpty() else enum?.name?.localizedOption().orEmpty()
        }

        is ListValue -> if (english) value.value.name else value.value.name.localizedOption()
        is BoolValue -> if (english) {
            if (value.value) "ON" else "OFF"
        } else {
            if (value.value) "开" else "关"
        }

        is StringValue -> value.value
    }

    fun formatNumber(value: Float, step: Float): String =
        if (step >= 1f || abs(value - value.roundToInt()) < 0.001f) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    /** Same erased cast trick as the Dream port: EnumValue/ListValue cannot be star-assigned. */
    @Suppress("UNCHECKED_CAST")
    private fun setUntyped(value: Value<*>, newValue: Any) {
        (value as Value<Any?>).value = newValue
    }
}
