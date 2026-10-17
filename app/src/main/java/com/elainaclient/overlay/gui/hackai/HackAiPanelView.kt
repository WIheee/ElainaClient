package com.elainaclient.overlay.gui.hackai

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.elainaclient.game.BoolValue
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.Value
import com.elainaclient.game.localizedOption
import kotlin.math.roundToInt

/**
 * Kotlin rewrite of HackAI's click-GUI panel (`layout_clickgui.xml` + `CategoryAdapter` +
 * `ModuleAdapter`).
 *
 * Layout preserved: 820x500 dp rounded panel, a 48 dp top bar (Home / search / CN-EN / close), a
 * 200 dp category sidebar and a scrollable module list. Module cards expand on long-press (tap
 * collapses) and expose real Elaina settings: bool rows, sliders, enum/list option chips and the
 * shortcut switch. Colours are Elaina's violet palette.
 */
class HackAiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var english = prefs.getBoolean(KEY_ENGLISH, false)
    private var category: ModuleCategory = loadCategory()
    private var query = ""

    private val expanded = mutableSetOf<String>()
    private val switchBindings = mutableListOf<Pair<Module, HackAiSwitch>>()
    private val shortcutBindings = mutableListOf<Pair<Module, HackAiSwitch>>()

    private var closeListener: (() -> Unit)? = null
    private var categoryList: LinearLayout? = null
    private var moduleList: LinearLayout? = null
    private var moduleScroll: ScrollView? = null
    private var cnButton: TextView? = null
    private var enButton: TextView? = null

    private val refreshTick = object : Runnable {
        override fun run() {
            switchBindings.forEach { (module, switch) ->
                if (switch.checked != module.isEnabled) {
                    switch.setChecked(module.isEnabled, true, false)
                }
            }
            shortcutBindings.forEach { (module, switch) ->
                if (switch.checked != module.isShortcutDisplayed) {
                    switch.setChecked(module.isShortcutDisplayed, true, false)
                }
            }
            postDelayed(this, 500L)
        }
    }

    init {
        orientation = VERTICAL
        background = rounded(HackAiStyle.background, HackAiStyle.PANEL_RADIUS_DP, HackAiStyle.border)
        elevation = dp(18f).toFloat()
        clipToOutline = true
        isClickable = true
        setOnClickListener { /* consume taps on the panel background */ }
        build()
    }

    fun setOnCloseRequested(listener: () -> Unit) {
        closeListener = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(refreshTick)
        postDelayed(refreshTick, 500L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshTick)
        super.onDetachedFromWindow()
    }

    // =========================================================================================
    // Shell
    // =========================================================================================

    private fun build() {
        addView(
            buildTopBar(),
            LayoutParams(LayoutParams.MATCH_PARENT, dp(HackAiStyle.TOP_BAR_HEIGHT_DP)),
        )
        addView(
            separatorView(),
            LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)),
        )
        addView(
            buildContent(),
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        rebuildCategories()
        rebuildModules(resetScroll = true)
        updateLanguageButtons()
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), 0, dp(12f), 0)
        }

        bar.addView(
            textView("Home", 16f, HackAiStyle.text, bold = true),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )

        val search = EditText(context).apply {
            hint = "Search..."
            setHintTextColor(HackAiStyle.muted)
            setTextColor(HackAiStyle.text)
            textSize = 13f
            isSingleLine = true
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(HackAiStyle.searchBackground, 8f)
            setPadding(dp(14f), 0, dp(14f), 0)
            setText(query)
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    expanded.clear()
                    rebuildModules(resetScroll = true)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        bar.addView(
            search,
            LayoutParams(0, dp(34f), 1f).apply {
                leftMargin = dp(16f)
                rightMargin = dp(12f)
            },
        )

        val lang = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(HackAiStyle.chip, 6f, HackAiStyle.border, 1f)
        }
        val cn = languageButton("CN") { setEnglish(false) }
        val en = languageButton("EN") { setEnglish(true) }
        cnButton = cn
        enButton = en
        lang.addView(cn, LayoutParams(dp(30f), LayoutParams.MATCH_PARENT))
        lang.addView(
            View(context).apply { setBackgroundColor(HackAiStyle.separator) },
            LayoutParams(dp(1f), dp(18f)).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        lang.addView(en, LayoutParams(dp(30f), LayoutParams.MATCH_PARENT))
        bar.addView(
            lang,
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(28f)).apply { rightMargin = dp(8f) },
        )

        bar.addView(
            textView("✕", 18f, HackAiStyle.text).apply {
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { closeListener?.invoke() }
            },
            LayoutParams(dp(34f), dp(34f)).apply { leftMargin = dp(2f) },
        )

        return bar
    }

    private fun buildContent(): View {
        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = solid(HackAiStyle.background)
        }

        val sidebar = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12f), dp(14f), dp(12f), dp(8f))
            background = solid(HackAiStyle.sidebarBackground)
        }
        sidebar.addView(textView("Elaina", 19f, HackAiStyle.text, bold = true))
        sidebar.addView(
            textView("Advanced Minecraft Utility", 11f, HackAiStyle.muted).apply {
                setPadding(0, dp(2f), 0, dp(14f))
            },
        )

        val categories = LinearLayout(context).apply { orientation = VERTICAL }
        categoryList = categories
        val categoryScroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(
                categories,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        sidebar.addView(categoryScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(sidebar, LayoutParams(dp(HackAiStyle.SIDEBAR_WIDTH_DP), LayoutParams.MATCH_PARENT))
        content.addView(separatorView(), LayoutParams(dp(1f), LayoutParams.MATCH_PARENT))

        val modules = LinearLayout(context).apply { orientation = VERTICAL }
        moduleList = modules
        val moduleScrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            clipToPadding = false
            addView(
                modules,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        moduleScroll = moduleScrollView
        content.addView(moduleScrollView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        return content
    }

    // =========================================================================================
    // Categories / modules
    // =========================================================================================

    private fun rebuildCategories() {
        val container = categoryList ?: return
        container.removeAllViews()
        HackAiBridge.categories.forEach { entry ->
            val selected = entry == category
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(10f), 0, dp(10f), 0)
                background = rounded(if (selected) HackAiStyle.cardActive else HackAiStyle.card, 8f)
                alpha = if (selected) 1f else 0.78f
                setOnClickListener { selectCategory(entry) }
            }

            val iconBox = FrameLayout(context).apply {
                background = rounded(HackAiStyle.searchBackground, 8f)
            }
            iconBox.addView(
                ImageView(context).apply {
                    setImageResource(entry.iconResId)
                    setColorFilter(if (selected) HackAiStyle.accent else HackAiStyle.muted)
                },
                FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER),
            )
            row.addView(iconBox, LayoutParams(dp(28f), dp(28f)))
            row.addView(
                textView(
                    HackAiBridge.categoryName(entry, english),
                    13f,
                    if (selected) HackAiStyle.accent else HackAiStyle.muted,
                    bold = true,
                ).apply { gravity = Gravity.CENTER_VERTICAL },
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(10f) },
            )

            container.addView(
                row,
                LayoutParams(LayoutParams.MATCH_PARENT, dp(HackAiStyle.CATEGORY_HEIGHT_DP)).apply {
                    bottomMargin = dp(HackAiStyle.CATEGORY_GAP_DP)
                },
            )
        }
    }

    private fun selectCategory(entry: ModuleCategory) {
        if (entry == category) return
        category = entry
        prefs.edit().putString(KEY_CATEGORY, entry.name).apply()
        expanded.clear()
        rebuildCategories()
        rebuildModules(resetScroll = true)
        moduleScroll?.let {
            it.animate().cancel()
            it.alpha = 0f
            it.translationX = dp(16f).toFloat()
            it.animate().alpha(1f).translationX(0f).setDuration(180L).start()
        }
    }

    private fun rebuildModules(resetScroll: Boolean = false) {
        val container = moduleList ?: return
        val scroll = moduleScroll
        val oldScroll = if (resetScroll) 0 else scroll?.scrollY ?: 0
        container.removeAllViews()
        switchBindings.clear()
        shortcutBindings.clear()

        val modules = HackAiBridge.modules(category, query)
        if (modules.isEmpty()) {
            container.addView(
                textView(
                    if (english) "No matching modules" else "没有匹配的模块",
                    12f,
                    HackAiStyle.muted,
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24f), 0, dp(24f))
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
            )
        } else {
            modules.forEach { module ->
                container.addView(
                    buildModuleCard(module),
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8f)
                    },
                )
            }
        }
        scroll?.post { scroll.scrollTo(0, oldScroll) }
    }

    private fun buildModuleCard(module: Module): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = rounded(HackAiStyle.card, HackAiStyle.CARD_RADIUS_DP)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44f)
        }
        val info = LinearLayout(context).apply { orientation = VERTICAL }
        info.addView(
            textView(HackAiBridge.moduleName(module, english), 14f, HackAiStyle.accent).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        info.addView(
            textView(HackAiBridge.moduleDescription(module, english), 11f, HackAiStyle.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3f), 0, 0)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        header.addView(
            info,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12f) },
        )

        val enabledSwitch = HackAiSwitch(context).apply {
            setChecked(module.isEnabled, false)
            onCheckedChange = { module.isEnabled = it }
        }
        header.addView(enabledSwitch, LayoutParams(dp(38f), dp(22f)))
        switchBindings += module to enabledSwitch

        card.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        if (module.name in expanded) {
            card.addView(
                separatorView(),
                LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply {
                    topMargin = dp(10f)
                    bottomMargin = dp(8f)
                },
            )
            HackAiBridge.settings(module).forEach { value -> addSettingRow(card, value) }
            addShortcutRow(card, module)
        }

        card.setOnClickListener {
            if (expanded.remove(module.name)) post { rebuildModules() }
        }
        card.setOnLongClickListener {
            if (expanded.add(module.name)) post { rebuildModules() }
            true
        }
        return card
    }

    // =========================================================================================
    // Settings rows
    // =========================================================================================

    private fun addSettingRow(card: LinearLayout, value: Value<*>) {
        when (value) {
            is BoolValue -> {
                val boolValue = value
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(34f)
                }
                row.addView(
                    settingLabel(value),
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
                )
                val toggle = HackAiSwitch(context).apply {
                    setChecked(boolValue.value, false)
                    onCheckedChange = { boolValue.value = it }
                }
                row.addView(toggle, LayoutParams(dp(38f), dp(22f)))
                card.addView(
                    row,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(4f)
                    },
                )
            }

            else -> if (HackAiBridge.isSlider(value)) {
                addSliderRow(card, value)
            } else if (HackAiBridge.isOption(value)) {
                addOptionRow(card, value)
            }
        }
    }

    private fun addSliderRow(card: LinearLayout, value: Value<*>) {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(6f), 0, 0)
        }
        column.addView(
            settingLabel(value),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        val valueText = textView(
            HackAiBridge.displayValue(value, english),
            13f,
            HackAiStyle.text,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            minimumHeight = dp(18f)
        }
        column.addView(valueText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val (min, max, step) = HackAiBridge.sliderRange(value)
        val slider = HackAiSlider(context).apply {
            configure(min, max, step, HackAiBridge.sliderValue(value))
            onValueChange = { newValue ->
                HackAiBridge.writeSlider(value, newValue)
                valueText.text = HackAiBridge.displayValue(value, english)
            }
        }
        column.addView(
            slider,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(26f)).apply { topMargin = dp(-2f) },
        )
        card.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addOptionRow(card: LinearLayout, value: Value<*>) {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(6f), 0, 0)
        }
        column.addView(
            settingLabel(value),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4f)
            },
        )

        val options = HackAiBridge.options(value)
        val selected = HackAiBridge.selectedIndex(value)
        val group = RadioGroup(context).apply { orientation = HORIZONTAL }
        options.forEachIndexed { index, option ->
            val button = RadioButton(context).apply {
                id = View.generateViewId()
                text = HackAiBridge.optionLabel(option, english)
                textSize = 11f
                gravity = Gravity.CENTER
                buttonDrawable = null
                background = radioBackground()
                setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(HackAiStyle.onSelected, HackAiStyle.muted),
                    ),
                )
                setPadding(dp(8f), 0, dp(8f), 0)
                minWidth = dp(68f)
                minimumWidth = 0
            }
            group.addView(
                button,
                RadioGroup.LayoutParams(LayoutParams.WRAP_CONTENT, dp(26f)).apply {
                    if (index > 0) leftMargin = dp(6f)
                },
            )
        }
        // Check through RadioGroup so its internal checked id matches the visual selection.
        if (selected in options.indices) {
            group.check(group.getChildAt(selected).id)
        }
        group.setOnCheckedChangeListener { parent, checkedId ->
            val index = (0 until parent.childCount)
                .firstOrNull { parent.getChildAt(it).id == checkedId }
            if (index != null) HackAiBridge.selectOption(value, index)
        }

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                group,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(28f),
                ),
            )
        }
        column.addView(
            scroll,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(28f)).apply { topMargin = dp(4f) },
        )
        card.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addShortcutRow(card: LinearLayout, module: Module) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(36f)
        }
        row.addView(
            textView(
                if (english) "Shortcut button" else "显示快捷按钮",
                11f,
                HackAiStyle.muted,
            ),
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        val shortcut = HackAiSwitch(context).apply {
            setChecked(module.isShortcutDisplayed, false)
            onCheckedChange = { HackAiBridge.setShortcut(module, it) }
        }
        row.addView(shortcut, LayoutParams(dp(38f), dp(22f)))
        shortcutBindings += module to shortcut
        card.addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            },
        )
    }

    // =========================================================================================
    // Language
    // =========================================================================================

    private fun setEnglish(value: Boolean) {
        if (english == value) return
        english = value
        prefs.edit().putBoolean(KEY_ENGLISH, value).apply()
        updateLanguageButtons()
        rebuildCategories()
        rebuildModules()
    }

    private fun updateLanguageButtons() {
        styleLanguageButton(cnButton, !english)
        styleLanguageButton(enButton, english)
    }

    private fun styleLanguageButton(button: TextView?, selected: Boolean) {
        button ?: return
        button.background = if (selected) {
            rounded(HackAiStyle.accent, 6f)
        } else {
            null
        }
        button.setTextColor(if (selected) HackAiStyle.onSelected else HackAiStyle.muted)
    }

    private fun languageButton(label: String, onClick: () -> Unit): TextView =
        textView(label, 11f, HackAiStyle.muted, bold = true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
        }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private fun textView(content: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = content
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun settingLabel(value: Value<*>): TextView =
        textView(
            if (english) value.name else value.name.localizedOption(),
            11f,
            HackAiStyle.muted,
        )

    private fun separatorView(): View = View(context).apply {
        setBackgroundColor(HackAiStyle.separator)
    }

    private fun radioBackground(): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_checked),
            rounded(HackAiStyle.accent, 6f),
        )
        addState(intArrayOf(), rounded(HackAiStyle.chip, 6f, HackAiStyle.border, 1f))
    }

    private fun rounded(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeDp: Float = 1f,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
    }

    private fun solid(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PREFS = "settings"
        const val KEY_ENGLISH = "hackai_english"
        const val KEY_CATEGORY = "hackai_category"
    }

    private fun loadCategory(): ModuleCategory {
        val stored = prefs.getString(KEY_CATEGORY, null) ?: return ModuleCategory.Combat
        return HackAiBridge.categories.firstOrNull { it.name == stored } ?: ModuleCategory.Combat
    }
}
