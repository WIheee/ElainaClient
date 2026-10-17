package com.elainaclient.overlay.gui.liquidpe

import com.elainaclient.game.BoolValue
import com.elainaclient.game.EnumValue
import com.elainaclient.game.FloatValue
import com.elainaclient.game.IntValue
import com.elainaclient.game.ListValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.ModuleManager
import com.elainaclient.game.StringValue
import com.elainaclient.game.localizedModule
import com.elainaclient.game.localizedOption
import kotlin.math.roundToInt

/**
 * Two-way bridge between Elaina's module system and LiquidPE's original data model.
 *
 * LiquidPE's Canvas renderer reads/writes plain fields on [ToggleSetting] / [SliderSetting] /
 * [DropdownSetting]; it has no callback API for settings. The bridge therefore keeps one mapping
 * per setting and is pumped in both directions by the host view:
 *  - [push] (ours -> store) before drawing, but only while the user is not touching the view, so
 *    dragging a slider is never overwritten;
 *  - [pull] (store -> ours) after touch events, so the setting the renderer just mutated is written
 *    back into the Elaina [com.elainaclient.game.Value].
 *
 * Module enable/disable is event-driven instead: LiquidPE's store listener toggles Elaina's module
 * directly, and [push] mirrors external changes back into the store's rows.
 */
class LiquidPeBridge {

    val store = LiquidPeStore()

    private val entries = mutableMapOf<String, ModuleEntry>()
    private val modules = mutableMapOf<String, Module>()
    private val bridges = mutableListOf<SettingBridge>()

    private interface SettingBridge {
        fun push()
        fun pull()
    }

    init {
        // Category positions copied from LiquidPE's demo layout (375-wide panels, 405 pitch).
        val categories = listOf(
            Triple(ModuleCategory.Combat, "combat", 30f),
            Triple(ModuleCategory.Motion, "movement", 435f),
            Triple(ModuleCategory.Visual, "render", 840f),
            Triple(ModuleCategory.World, "world", 1245f),
            Triple(ModuleCategory.Misc, "misc", 1650f),
        )

        categories.forEach { (category, id, x) ->
            store.addCategory(id, category.displayName, x, 194f)
        }

        ModuleManager.modules
            .filter { it.category != ModuleCategory.Config }
            .forEach { module ->
                val categoryId = when (module.category) {
                    ModuleCategory.Combat -> "combat"
                    ModuleCategory.Motion -> "movement"
                    ModuleCategory.Visual -> "render"
                    ModuleCategory.World -> "world"
                    ModuleCategory.Misc -> "misc"
                    ModuleCategory.Config -> "misc"
                }
                val entry = store.addModule(module.name, module.name.localizedModule(), categoryId)
                entry.enabled = module.isEnabled
                entry.enabledProgress.snapTo(if (module.isEnabled) 1f else 0f)
                buildSettings(module).forEach { entry.settings.add(it) }

                entries[module.name] = entry
                modules[module.name] = module
            }

        // UI -> Elaina.
        store.addModuleStateListener { module, enabled ->
            modules[module.id]?.isEnabled = enabled
        }
    }

    private fun buildSettings(module: Module): List<SettingEntry> {
        val result = mutableListOf<SettingEntry>()
        module.values.forEach { value ->
            when (value) {
                is BoolValue -> {
                    val setting = ToggleSetting(value.name, value.name.localizedOption(), value.value)
                    result += setting
                    bridges += object : SettingBridge {
                        override fun push() {
                            if (setting.value != value.value) {
                                setting.value = value.value
                                setting.progress.snapTo(if (value.value) 1f else 0f)
                            }
                        }

                        override fun pull() {
                            if (value.value != setting.value) value.value = setting.value
                        }
                    }
                }

                is FloatValue -> {
                    val setting = SliderSetting(
                        value.name,
                        value.name.localizedOption(),
                        value.range.start,
                        value.range.endInclusive,
                        value.value,
                        "",
                    )
                    result += setting
                    bridges += object : SettingBridge {
                        override fun push() {
                            setting.value = value.value.coerceIn(value.range)
                        }

                        override fun pull() {
                            val next = setting.value.coerceIn(value.range)
                            if (value.value != next) value.value = next
                        }
                    }
                }

                is IntValue -> {
                    val setting = SliderSetting(
                        value.name,
                        value.name.localizedOption(),
                        value.range.first.toFloat(),
                        value.range.last.toFloat(),
                        value.value.toFloat(),
                        "",
                    )
                    result += setting
                    bridges += object : SettingBridge {
                        override fun push() {
                            setting.value = value.value.toFloat()
                        }

                        override fun pull() {
                            val next = setting.value.roundToInt().coerceIn(value.range)
                            if (value.value != next) value.value = next
                        }
                    }
                }

                is EnumValue<*> -> addEnumSetting(result, value)

                is ListValue -> {
                    val options = value.listItems.map { it.name }
                    val setting = DropdownSetting(
                        value.name,
                        value.name.localizedOption(),
                        value.value.name,
                        *options.toTypedArray(),
                    )
                    result += setting
                    bridges += object : SettingBridge {
                        override fun push() {
                            if (setting.value != value.value.name) setting.value = value.value.name
                        }

                        override fun pull() {
                            val next = value.listItems.firstOrNull { it.name == setting.value } ?: return
                            if (value.value != next) value.value = next
                        }
                    }
                }

                // The original LiquidPE UI has no free-text control; skip StringValue options
                // (e.g. Scaffold's blacklist) rather than inventing a widget it never had.
                is StringValue -> Unit
            }
        }
        return result
    }

    private fun <T : Enum<T>> addEnumSetting(result: MutableList<SettingEntry>, value: EnumValue<T>) {
        val constants = value.enumClass.enumConstants?.toList().orEmpty()
        val options = constants.map { it.name }
        val setting = DropdownSetting(
            value.name,
            value.name.localizedOption(),
            value.value.name,
            *options.toTypedArray(),
        )
        result += setting
        bridges += object : SettingBridge {
            override fun push() {
                if (setting.value != value.value.name) setting.value = value.value.name
            }

            override fun pull() {
                val next = constants.firstOrNull { it.name == setting.value } ?: return
                if (value.value != next) value.value = next
            }
        }
    }

    /** Elaina -> LiquidPE. Safe to call repeatedly; never overwrites a value being dragged. */
    fun push() {
        entries.forEach { (name, entry) ->
            val module = modules[name] ?: return@forEach
            if (entry.enabled != module.isEnabled) {
                entry.enabled = module.isEnabled
                entry.enabledProgress.snapTo(if (module.isEnabled) 1f else 0f)
            }
        }
        bridges.forEach { it.push() }
    }

    /** LiquidPE -> Elaina. Called after the renderer mutated its model. */
    fun pull() {
        bridges.forEach { it.pull() }
    }
}
