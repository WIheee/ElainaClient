package com.elainaclient.overlay.gui.dream

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
 * Adapter between Dream's Canvas UI and Elaina's live module system.
 *
 * Unlike [com.elainaclient.overlay.gui.liquidpe.LiquidPeBridge] there is no copied model to keep in
 * sync: the renderer reads [Module] / [Value] directly, and the view writes through the helpers
 * below. A periodic invalidate in [DreamOverlayView] picks up external changes (shortcut buttons,
 * commands, config import).
 *
 * The original Dream panel hard-coded Range/FOV/Delay/Scaffold-Mode rows for a handful of demo
 * modules. Those are gone: [settings] exposes every real [Value] on the module, and string values
 * are skipped because the original Canvas UI has no text-field widget.
 */
object DreamBridge {

    val categories: List<ModuleCategory> = listOf(
        ModuleCategory.Combat,
        ModuleCategory.Motion,
        ModuleCategory.Visual,
        ModuleCategory.World,
        ModuleCategory.Misc,
    )

    fun modules(category: ModuleCategory): List<Module> =
        ModuleManager.modules.filter { it.category == category }

    /** Case-insensitive search across every non-Config category. */
    fun search(query: String): List<Module> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return ModuleManager.modules.filter { module ->
            module.category != ModuleCategory.Config &&
                (module.name.contains(q, true) ||
                    module.name.localizedModule().contains(q, true) ||
                    module.category.displayName.contains(q, true))
        }
    }

    fun settings(module: Module): List<Value<*>> =
        module.values.filter { it !is StringValue }

    fun title(module: Module): String = module.name.localizedModule()

    fun categoryLabel(category: ModuleCategory): String = category.displayName

    fun settingLabel(value: Value<*>): String = value.name.localizedOption()

    fun controlsLabel(module: Module): String {
        val count = settings(module).size
        return "$count 项设置"
    }

    fun enabledCount(category: ModuleCategory): Int =
        modules(category).count { it.isEnabled }

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

    fun fractionOf(value: Value<*>): Float = when (value) {
        is FloatValue -> {
            val span = value.range.endInclusive - value.range.start
            if (span <= 0f) 0f else ((value.value - value.range.start) / span).coerceIn(0f, 1f)
        }

        is IntValue -> {
            val span = value.range.last - value.range.first
            if (span <= 0) 0f else ((value.value - value.range.first).toFloat() / span).coerceIn(0f, 1f)
        }

        else -> 0f
    }

    fun applyFraction(value: Value<*>, fraction: Float) {
        val f = fraction.coerceIn(0f, 1f)
        when (value) {
            is FloatValue -> {
                val span = value.range.endInclusive - value.range.start
                value.value = (value.range.start + span * f).coerceIn(value.range)
            }

            is IntValue -> {
                val span = value.range.last - value.range.first
                value.value = (value.range.first + (span * f).roundToInt()).coerceIn(value.range)
            }

            else -> Unit
        }
    }

    fun displayValue(value: Value<*>): String = when (value) {
        is FloatValue -> formatFloat(value.value)
        is IntValue -> value.value.toString()
        is EnumValue<*> -> (value.value as? Enum<*>)?.name?.localizedOption().orEmpty()
        is ListValue -> value.value.name.localizedOption()
        is BoolValue -> if (value.value) "开" else "关"
        is StringValue -> value.value
        else -> value.value.toString()
    }

    // =========================================================================================
    // Enums / lists (option rows)
    // =========================================================================================

    fun isOption(value: Value<*>): Boolean = value is EnumValue<*> || value is ListValue

    fun options(value: Value<*>): List<Any> = when (value) {
        is EnumValue<*> -> value.enumClass.enumConstants?.map { it as Any }.orEmpty()
        is ListValue -> value.listItems.map { it as Any }
        else -> emptyList()
    }

    fun optionLabel(option: Any): String = when (option) {
        is Enum<*> -> option.name.localizedOption()
        is ListItem -> option.name.localizedOption()
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

    /**
     * [EnumValue] is invariant in `T : Enum<T>` and [ListValue] stores a [ListItem], so a
     * star-projected value cannot be assigned directly. The erased runtime type is identical.
     */
    @Suppress("UNCHECKED_CAST")
    private fun setUntyped(value: Value<*>, newValue: Any) {
        (value as Value<Any?>).value = newValue
    }

    private fun formatFloat(value: Float): String =
        if (abs(value - value.roundToInt()) < 0.001f) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
