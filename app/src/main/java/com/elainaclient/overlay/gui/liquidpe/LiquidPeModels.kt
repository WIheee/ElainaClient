package com.elainaclient.overlay.gui.liquidpe

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Time-based interpolator used by every animated LiquidPE control. */
class AnimationState(initialValue: Float = 0f) {

    private var startValue = initialValue
    private var value = initialValue
    private var targetValue = initialValue
    private var startTime = 0L
    private var duration = 0L

    fun get(now: Long = SystemClock.uptimeMillis()): Float {
        if (value == targetValue || duration <= 0L) return targetValue
        val t = ((now - startTime) / duration.toFloat()).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        value = startValue + (targetValue - startValue) * eased
        if (t >= 1f) value = targetValue
        return value
    }

    fun animateTo(target: Float, durationMs: Long) {
        val now = SystemClock.uptimeMillis()
        startValue = get(now)
        targetValue = target
        startTime = now
        duration = durationMs.coerceAtLeast(1L)
    }

    fun snapTo(target: Float) {
        startValue = target
        value = target
        targetValue = target
        startTime = 0L
        duration = 0L
    }

    fun isRunning(now: Long): Boolean = value != targetValue && now - startTime < duration

    val target: Float get() = targetValue
}

enum class TopTab { CLICK_GUI, HUD_EDITOR, SETTINGS }

enum class SettingType { TOGGLE, SLIDER, RANGE, DROPDOWN, MULTI_SELECT, BIND, COLOR, GROUP }

/** Saved free-layout position of a category panel. */
data class PanelLayoutInfo(
    val panelId: String,
    var gridX: Int,
    var gridY: Int,
    var spanX: Int,
    var spanY: Int,
    var visible: Boolean = true,
) {
    var hasFreePosition: Boolean = false
    var freeX: Float = 0f
    var freeY: Float = 0f

    fun setFreePosition(x: Float, y: Float) {
        hasFreePosition = true
        freeX = x
        freeY = y
    }
}

/** SharedPreferences + JSON layout storage; corrupt data is treated as "never saved". */
class PanelLayoutStorage(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(infos: Collection<PanelLayoutInfo>) {
        val panels = JSONArray()
        infos.filter { it.panelId.isNotEmpty() }.forEach { info ->
            panels.put(
                JSONObject().apply {
                    put("id", info.panelId)
                    put("x", info.gridX)
                    put("y", info.gridY)
                    put("spanX", info.spanX.coerceAtLeast(1))
                    put("spanY", info.spanY.coerceAtLeast(1))
                    put("visible", info.visible)
                    put("freePosition", info.hasFreePosition)
                    if (info.hasFreePosition) {
                        put("freeX", info.freeX.toDouble())
                        put("freeY", info.freeY.toDouble())
                    }
                },
            )
        }
        preferences.edit()
            .putString(KEY_LAYOUT, JSONObject().apply {
                put("version", VERSION)
                put("panels", panels)
            }.toString())
            .apply()
    }

    fun load(): Map<String, PanelLayoutInfo> {
        val result = linkedMapOf<String, PanelLayoutInfo>()
        val raw = preferences.getString(KEY_LAYOUT, null) ?: return result
        runCatching {
            val panels = JSONObject(raw).optJSONArray("panels") ?: return result
            for (i in 0 until panels.length()) {
                val item = panels.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty() || result.containsKey(id)) continue

                val info = PanelLayoutInfo(
                    panelId = id,
                    gridX = item.optInt("x", -1),
                    gridY = item.optInt("y", -1),
                    spanX = item.optInt("spanX", 1).coerceAtLeast(1),
                    spanY = item.optInt("spanY", 1).coerceAtLeast(1),
                    visible = item.optBoolean("visible", true),
                )
                if (item.optBoolean("freePosition", false)) {
                    info.setFreePosition(
                        item.optDouble("freeX", 0.0).toFloat(),
                        item.optDouble("freeY", 0.0).toFloat(),
                    )
                }
                result[id] = info
            }
        }
        return result
    }

    fun clear() {
        preferences.edit().remove(KEY_LAYOUT).apply()
    }

    private companion object {
        const val PREFS_NAME = "draggable_panel_layouts"
        const val KEY_LAYOUT = "layout"
        const val VERSION = 2
    }
}

class CategoryPanel(
    val id: String,
    val name: String,
    var x: Float,
    var y: Float,
) {
    var layoutInfo: PanelLayoutInfo? = null
    val modules = mutableListOf<ModuleEntry>()
    val expansion = AnimationState(1f)
    var expanded = true
    var scrollOffset = 0f
    var targetScrollOffset = 0f
    var contentHeight = 0f
}

class ModuleEntry(
    val id: String,
    val name: String,
    val categoryId: String,
) {
    val settings = mutableListOf<SettingEntry>()
    var keyBind: String = "None"
    var enabled: Boolean = false
    var settingsExpanded: Boolean = false
    val enabledProgress = AnimationState(0f)
    val settingsProgress = AnimationState(0f)

    fun hasSettings(): Boolean = settings.isNotEmpty()
}

sealed class SettingEntry(
    val id: String,
    val name: String,
    val type: SettingType,
) {
    var visible: Boolean = true
}

class ToggleSetting(id: String, name: String, value: Boolean) :
    SettingEntry(id, name, SettingType.TOGGLE) {
    var value: Boolean = value
    val progress = AnimationState(if (value) 1f else 0f)
}

class SliderSetting(
    id: String,
    name: String,
    val min: Float,
    val max: Float,
    value: Float,
    val unit: String?,
) : SettingEntry(id, name, SettingType.SLIDER) {
    var value: Float = value
    val displayUnit: String = unit.orEmpty()
}

class RangeSetting(
    id: String,
    name: String,
    val min: Float,
    val max: Float,
    low: Float,
    high: Float,
) : SettingEntry(id, name, SettingType.RANGE) {
    var low: Float = low
    var high: Float = high
}

class DropdownSetting(
    id: String,
    name: String,
    value: String,
    vararg options: String,
) : SettingEntry(id, name, SettingType.DROPDOWN) {
    val options = options.toMutableList()
    var value: String = value
    var open: Boolean = false
}

class MultiSelectSetting(
    id: String,
    name: String,
    options: List<String>,
    selected: List<String>,
) : SettingEntry(id, name, SettingType.MULTI_SELECT) {
    val options = options.toMutableList()
    val selected = LinkedHashSet(selected)
}

class BindSetting(id: String, name: String, value: String?) :
    SettingEntry(id, name, SettingType.BIND) {
    var value: String = value?.takeIf { it.isNotEmpty() } ?: "None"
    var listening: Boolean = false
}

class ColorSetting(id: String, name: String, rgbaColor: Int, expanded: Boolean) :
    SettingEntry(id, name, SettingType.COLOR) {

    var color: Int = rgbaColor
    var expanded: Boolean = expanded
    var hue: Float
    var saturation: Float
    var brightness: Float
    var alpha: Float

    init {
        val hsv = FloatArray(3)
        Color.colorToHSV(rgbaColor, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        alpha = Color.alpha(rgbaColor) / 255f
    }

    fun updateColor() {
        color = Color.HSVToColor(
            Math.round(alpha * 255f),
            floatArrayOf(hue, saturation, brightness),
        )
    }
}

class SettingGroup(
    id: String,
    name: String,
    expanded: Boolean,
    children: List<SettingEntry>,
) : SettingEntry(id, name, SettingType.GROUP) {
    val children = children.toMutableList()
    var expanded: Boolean = expanded
    val expansion = AnimationState(if (expanded) 1f else 0f)
}
