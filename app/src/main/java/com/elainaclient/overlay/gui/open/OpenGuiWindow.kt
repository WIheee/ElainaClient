package com.elainaclient.overlay.gui.open

import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elainaclient.R
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
import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.OverlayWindow
import com.elainaclient.overlay.UISettings
import com.elainaclient.overlay.gui.classic.ConfigurationScreen
import kotlin.math.roundToInt

/**
 * Generic Compose shell for the five OpenClickGUI ports. See [OpenGuiSpec] for the per-theme
 * layout/palette configuration.
 *
 * The original projects each ship a standalone Canvas view with their own module repository.
 * Here the visual structure is reproduced (sidebar/tabs, cards/rows/panels, inline/detail/side
 * settings) while the data is Elaina's [ModuleManager]; that is the "refactor" part of the port.
 */
class OpenGuiWindow(
    private val spec: OpenGuiSpec,
    private val theme: GUITheme,
) : OverlayWindow() {

    override val immersive: Boolean get() = true

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // Inherit the base immersive flags (FLAG_FULLSCREEN is added there for `immersive`
            // windows); drop NOT_FOCUSABLE so the search box / config fields can raise the IME.
            // The self-drawn scrim is dismissed on an outside tap, so the game is never left with
            // a stuck overlay.
            flags = (flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    /**
     * A full-screen overlay window does not inherit an Activity's immersive flags, so the system
     * UI has to be hidden on the view itself as well - otherwise the status bar (clock/battery)
     * stays visible above the GUI.
     */
    override val composeView by lazy {
        androidx.compose.ui.platform.ComposeView(OverlayManager.currentContext!!).apply {
            fitsSystemWindows = false
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private var category by mutableStateOf(ModuleCategory.Combat)
    private var settingsModule by mutableStateOf<Module?>(null)
    private var expandedModule by mutableStateOf<Module?>(null)
    private var query by mutableStateOf("")
    private var tab by mutableStateOf(0)

    @Composable
    override fun Content() {
        val p = spec.palette
        val context = LocalContext.current
        val uiScale = remember(theme) { UISettings.scale(context, theme) }
        val snackbarHostState = remember { SnackbarHostState() }
        val shape = RoundedCornerShape(spec.panelRadius)

        Box(
            Modifier
                .fillMaxSize()
                .background(p.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { OverlayManager.dismissOverlayWindow(this@OpenGuiWindow) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(spec.panelWidth)
                    .fillMaxHeight(spec.panelHeight)
                    .graphicsLayer(scaleX = uiScale, scaleY = uiScale)
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .background(p.background)
                    .border(1.dp, p.border, shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* consume taps inside the panel */ },
            ) {
                if (spec.nav == OpenNav.Sidebar) {
                    SidebarShell(snackbarHostState)
                } else {
                    TabsShell(snackbarHostState)
                }

                if (spec.settings == OpenSettings.Side && settingsModule != null) {
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        SideSettingsPanel(settingsModule!!) { settingsModule = null }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }

    // =========================================================================================
    // Shells
    // =========================================================================================

    @Composable
    private fun SidebarShell(snackbarHostState: SnackbarHostState) {
        val p = spec.palette
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(spec.sidebarWidth)
                    .fillMaxHeight()
                    .background(p.sidebar)
                    .padding(vertical = 18.dp),
            ) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        text = "Elaina",
                        color = p.text,
                        fontSize = spec.brandSize,
                        fontFamily = spec.brandFont,
                        fontWeight = FontWeight.Bold,
                    )
                    if (spec.tagline.isNotEmpty()) {
                        Text(
                            text = spec.tagline,
                            color = p.textMuted,
                            fontSize = 10.sp,
                            fontFamily = spec.bodyFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                ModuleCategory.entries.forEach { entry ->
                    CategoryRow(entry)
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = "Elaina · B2",
                    color = p.textMuted,
                    fontSize = 11.sp,
                    fontFamily = spec.bodyFont,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                HeaderBar(snackbarHostState)
                Box(Modifier.weight(1f)) {
                    SidebarBody(snackbarHostState)
                }
                if (spec.showFooter) FooterBar()
            }
        }
    }

    @Composable
    private fun TabsShell(snackbarHostState: SnackbarHostState) {
        val p = spec.palette
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TabChip("ClickGUI", tab == 0) { tab = 0 }
                Spacer(Modifier.width(6.dp))
                TabChip("配置", tab == 1) { tab = 1 }
            }

            Spacer(Modifier.height(10.dp))

            if (spec.showSearch && tab == 0) {
                SearchBox(query, { query = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }

            Box(Modifier.weight(1f)) {
                if (tab == 1) {
                    ConfigurationScreen(snackbarHostState, Modifier.fillMaxSize())
                } else {
                    PanelsBody()
                }
            }
        }
    }

    @Composable
    private fun HeaderBar(snackbarHostState: SnackbarHostState) {
        val p = spec.palette
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .background(p.header)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (settingsModule != null && spec.settings == OpenSettings.Detail) {
                    "配置"
                } else {
                    category.displayName
                },
                color = p.text,
                fontSize = 18.sp,
                fontFamily = spec.bodyFont,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.weight(1f))

            if (spec.showSearch) {
                SearchBox(query, { query = it }, Modifier.widthIn(max = 240.dp).width(200.dp))
                Spacer(Modifier.width(10.dp))
            }

            IconButton(onClick = { OverlayManager.dismissOverlayWindow(this@OpenGuiWindow) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "关闭",
                    tint = p.textSecondary,
                )
            }
        }
    }

    @Composable
    private fun FooterBar() {
        val p = spec.palette
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(p.header)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Elaina",
                color = p.text,
                fontSize = 12.sp,
                fontFamily = spec.bodyFont,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "已启用 ${ModuleManager.modules.count { it.isEnabled }}",
                color = p.accentAlt,
                fontSize = 12.sp,
                fontFamily = spec.bodyFont,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    @Composable
    private fun CategoryRow(entry: ModuleCategory) {
        val p = spec.palette
        val selected = category == entry
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) p.cardActive else Color.Transparent)
                .clickable {
                    category = entry
                    settingsModule = null
                    expandedModule = null
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(entry.iconResId),
                contentDescription = null,
                tint = if (selected) p.accentAlt else p.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.displayName,
                color = if (selected) p.text else p.textSecondary,
                fontSize = 14.sp,
                fontFamily = spec.bodyFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (selected) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.accent),
                )
            }
        }
    }

    @Composable
    private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
        val p = spec.palette
        Box(
            Modifier
                .clip(RoundedCornerShape(21.dp))
                .background(p.header)
                .then(
                    if (selected) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(21.dp))
                    else Modifier,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                color = if (selected) p.text else p.textSecondary,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }

    // =========================================================================================
    // Bodies
    // =========================================================================================

    @Composable
    private fun SidebarBody(snackbarHostState: SnackbarHostState) {
        when {
            settingsModule != null && spec.settings == OpenSettings.Detail -> {
                DetailSettings(settingsModule!!) { settingsModule = null }
            }

            category == ModuleCategory.Config -> {
                ConfigurationScreen(snackbarHostState, Modifier.fillMaxSize())
            }

            spec.content == OpenContent.Panels -> PanelsBody()
            spec.content == OpenContent.Rows -> RowsBody()
            else -> CardsBody()
        }
    }

    private fun filteredModules(): List<Module> {
        val q = query.trim()
        return ModuleManager.modules.filter { module ->
            val inScope = q.isNotEmpty() || module.category == category
            val matches = q.isEmpty() ||
                module.name.contains(q, true) ||
                module.name.localizedModule().contains(q, true) ||
                module.category.displayName.contains(q)
            inScope && matches
        }
    }

    @Composable
    private fun CardsBody() {
        val p = spec.palette
        val modules = remember(category, query) { filteredModules() }

        LazyVerticalGrid(
            columns = if (spec.twoColumnCards) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(modules.size) { index ->
                val module = modules[index]
                Column {
                    ModuleCard(module)
                    if (spec.settings == OpenSettings.Inline && expandedModule == module) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(spec.cardRadius))
                                .background(p.card.copy(alpha = 0.55f))
                                .padding(10.dp),
                        ) {
                            SettingsContent(module)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ModuleCard(module: Module) {
        val p = spec.palette
        val onOpenSettings = {
            when (spec.settings) {
                OpenSettings.Inline -> {
                    expandedModule = if (expandedModule == module) null else module
                }
                else -> settingsModule = module
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(spec.cardRadius))
                .background(if (module.isEnabled) p.cardActive else p.card)
                .border(
                    1.dp,
                    if (module.isEnabled) p.accent.copy(alpha = 0.45f) else p.border,
                    RoundedCornerShape(spec.cardRadius),
                )
                .combinedClickable(
                    onClick = { module.isEnabled = !module.isEnabled },
                    onLongClick = { if (module.values.isNotEmpty()) onOpenSettings() },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = module.name.localizedModule(),
                    color = if (module.isEnabled) p.accentAlt else p.text,
                    fontSize = 15.sp,
                    fontFamily = spec.bodyFont,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${module.category.displayName} · ${module.values.size} 项设置",
                    color = p.textMuted,
                    fontSize = 11.sp,
                    fontFamily = spec.bodyFont,
                )
            }

            if (module.values.isNotEmpty()) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = "设置",
                        tint = p.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            OpenSwitch(
                checked = module.isEnabled,
                onColor = p.accent,
                offColor = p.switchOff,
            ) { module.isEnabled = it }
        }
    }

    @Composable
    private fun RowsBody() {
        val p = spec.palette
        val modules = remember(category, query) { filteredModules() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(modules.size) { index ->
                val module = modules[index]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spec.cardRadius))
                        .background(if (module.isEnabled) p.cardActive else p.card)
                        .clickable { module.isEnabled = !module.isEnabled }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = module.name.localizedModule(),
                            color = if (module.isEnabled) p.accentAlt else p.text,
                            fontSize = 15.sp,
                            fontFamily = spec.bodyFont,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = module.category.displayName,
                            color = p.textMuted,
                            fontSize = 11.sp,
                            fontFamily = spec.bodyFont,
                        )
                    }

                    if (module.values.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(p.card)
                                .clickable { settingsModule = module },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "设置",
                                tint = p.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    OpenSwitch(
                        checked = module.isEnabled,
                        onColor = p.accent,
                        offColor = p.switchOff,
                    ) { module.isEnabled = it }
                }
            }
        }
    }

    /** LiquidPE's signature layout: one expanding panel per category, laid out in a grid. */
    @Composable
    private fun PanelsBody() {
        val p = spec.palette
        val q = query.trim()
        val categories = remember(query) {
            ModuleCategory.entries.filter { it != ModuleCategory.Config }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(spec.panelColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories.size) { index ->
                val cat = categories[index]
                val modules = ModuleManager.modules.filter { m ->
                    m.category == cat && (q.isEmpty() ||
                        m.name.contains(q, true) ||
                        m.name.localizedModule().contains(q, true))
                }
                if (modules.isEmpty() && q.isNotEmpty()) return@items

                var expanded by remember(cat) { mutableStateOf(true) }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spec.panelRadius))
                        .background(p.header)
                        .border(1.dp, p.border, RoundedCornerShape(spec.panelRadius)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(p.accent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(cat.iconResId),
                            contentDescription = null,
                            tint = p.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = cat.displayName,
                            color = p.text,
                            fontSize = 14.sp,
                            fontFamily = spec.bodyFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (expanded) "−" else "+",
                            color = p.textSecondary,
                            fontSize = 18.sp,
                            fontFamily = spec.bodyFont,
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            modules.forEach { module ->
                                Column {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(spec.cardRadius))
                                            .clickable { module.isEnabled = !module.isEnabled }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = module.name.localizedModule(),
                                            color = if (module.isEnabled) p.accent else p.textSecondary,
                                            fontSize = 13.sp,
                                            fontFamily = spec.bodyFont,
                                            fontWeight = if (module.isEnabled) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (module.values.isNotEmpty()) {
                                            Text(
                                                text = if (expandedModule == module) "▾" else "▸",
                                                color = p.textMuted,
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .clickable {
                                                        expandedModule =
                                                            if (expandedModule == module) null else module
                                                    }
                                                    .padding(horizontal = 6.dp),
                                            )
                                        }
                                    }
                                    if (expandedModule == module) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(p.card)
                                                .padding(8.dp),
                                        ) {
                                            SettingsContent(module)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================================
    // Settings
    // =========================================================================================

    @Composable
    private fun DetailSettings(module: Module, onBack: () -> Unit) {
        val p = spec.palette
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.card)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("‹ 返回", color = p.textSecondary, fontSize = 13.sp, fontFamily = spec.bodyFont)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = module.name.localizedModule(),
                    color = p.text,
                    fontSize = 20.sp,
                    fontFamily = spec.bodyFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                OpenSwitch(
                    checked = module.isEnabled,
                    onColor = p.accent,
                    offColor = p.switchOff,
                ) { module.isEnabled = it }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                SettingsContent(module)
            }
        }
    }

    @Composable
    private fun SideSettingsPanel(module: Module, onDismiss: () -> Unit) {
        val p = spec.palette
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.46f)
                    .background(p.header)
                    .border(1.dp, p.border)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.name.localizedModule(),
                        color = p.text,
                        fontSize = 18.sp,
                        fontFamily = spec.bodyFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    OpenSwitch(
                        checked = module.isEnabled,
                        onColor = p.accent,
                        offColor = p.switchOff,
                    ) { module.isEnabled = it }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "关闭",
                            tint = p.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SettingsContent(module)
                }
            }
        }
    }

    @Composable
    private fun SettingsContent(module: Module) {
        val p = spec.palette
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            module.values.forEach { value ->
                when (value) {
                    is BoolValue -> BoolRow(value)
                    is FloatValue -> FloatRow(value)
                    is IntValue -> IntRow(value)
                    is ListValue -> ListRow(value)
                    is EnumValue<*> -> EnumRow(value)
                    is StringValue -> StringRow(value)
                }
            }

            ShortcutRow(module)
        }
    }

    @Composable
    private fun BoolRow(value: BoolValue) {
        val p = spec.palette
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.name.localizedOption(),
                color = p.text,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
                modifier = Modifier.weight(1f),
            )
            OpenSwitch(
                checked = value.value,
                onColor = p.accent,
                offColor = p.switchOff,
            ) { value.value = it }
        }
    }

    @Composable
    private fun FloatRow(value: FloatValue) {
        val p = spec.palette
        Column {
            Row {
                Text(
                    text = value.name.localizedOption(),
                    color = p.text,
                    fontSize = 13.sp,
                    fontFamily = spec.bodyFont,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f".format(value.value),
                    color = p.accentAlt,
                    fontSize = 13.sp,
                    fontFamily = spec.bodyFont,
                )
            }
            Slider(
                value = value.value,
                onValueChange = { value.value = it },
                valueRange = value.range,
                colors = sliderColors(),
            )
        }
    }

    @Composable
    private fun IntRow(value: IntValue) {
        val p = spec.palette
        Column {
            Row {
                Text(
                    text = value.name.localizedOption(),
                    color = p.text,
                    fontSize = 13.sp,
                    fontFamily = spec.bodyFont,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value.value.toString(),
                    color = p.accentAlt,
                    fontSize = 13.sp,
                    fontFamily = spec.bodyFont,
                )
            }
            Slider(
                value = value.value.toFloat(),
                onValueChange = { value.value = it.roundToInt() },
                valueRange = value.range.toFloatRange(),
                colors = sliderColors(),
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ListRow(value: ListValue) {
        val p = spec.palette
        Column {
            Text(
                text = value.name.localizedOption(),
                color = p.text,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                value.listItems.forEach { item ->
                    Chip(
                        label = item.name.localizedOption(),
                        selected = value.value == item,
                    ) { value.value = item }
                }
            }
        }
    }

    @Composable
    private fun <T : Enum<T>> EnumRow(value: EnumValue<T>) {
        val p = spec.palette
        Column {
            Text(
                text = value.name.localizedOption(),
                color = p.text,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
            )
            Spacer(Modifier.height(6.dp))
            EnumChips(value)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun <T : Enum<T>> EnumChips(value: EnumValue<T>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            value.enumClass.enumConstants?.forEach { option ->
                Chip(
                    label = option.name.localizedOption(),
                    selected = value.value == option,
                ) { value.value = option }
            }
        }
    }

    @Composable
    private fun StringRow(value: StringValue) {
        val p = spec.palette
        Column {
            Text(
                text = value.name.localizedOption(),
                color = p.text,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = value.value,
                onValueChange = { value.value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = spec.bodyFont,
                    color = p.text,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.border,
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    cursorColor = p.accent,
                ),
            )
        }
    }

    @Composable
    private fun ShortcutRow(module: Module) {
        val p = spec.palette
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.shortcut),
                color = p.text,
                fontSize = 13.sp,
                fontFamily = spec.bodyFont,
                modifier = Modifier.weight(1f),
            )
            OpenSwitch(
                checked = module.isShortcutDisplayed,
                onColor = p.accent,
                offColor = p.switchOff,
            ) { shown ->
                module.isShortcutDisplayed = shown
                if (shown) {
                    OverlayManager.showOverlayWindow(module.overlayShortcutButton)
                } else {
                    OverlayManager.dismissOverlayWindow(module.overlayShortcutButton)
                }
            }
        }
    }

    // =========================================================================================
    // Small widgets
    // =========================================================================================

    @Composable
    private fun OpenSwitch(
        checked: Boolean,
        onColor: Color,
        offColor: Color,
        onChange: (Boolean) -> Unit,
    ) {
        val progress by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(160),
            label = "openSwitch",
        )
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(lerp(offColor, onColor, progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onChange(!checked) }
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = 18.dp * progress)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }

    @Composable
    private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        val p = spec.palette
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) p.accent.copy(alpha = 0.85f) else p.card)
                .border(1.dp, if (selected) p.accentAlt else p.border, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else p.textSecondary,
                fontSize = 12.sp,
                fontFamily = spec.bodyFont,
            )
        }
    }

    @Composable
    private fun SearchBox(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
        val p = spec.palette
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = p.text,
                fontFamily = spec.bodyFont,
            ),
            modifier = modifier,
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.card)
                        .border(1.dp, p.border, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchGlyph(p.textMuted)
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "搜索模块…",
                                color = p.textMuted,
                                fontSize = 12.sp,
                                fontFamily = spec.bodyFont,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }

    /** Tiny magnifier drawn with boxes, so no extra icon asset is needed. */
    @Composable
    private fun SearchGlyph(tint: Color) {
        Box(Modifier.size(12.dp)) {
            Box(
                Modifier
                    .size(9.dp)
                    .border(1.5.dp, tint, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(2.dp)
                    .height(5.dp)
                    .background(tint),
            )
        }
    }

    @Composable
    private fun sliderColors() = SliderDefaults.colors(
        thumbColor = spec.palette.accentAlt,
        activeTrackColor = spec.palette.accent,
        inactiveTrackColor = spec.palette.switchOff,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
}

private fun IntRange.toFloatRange() = first.toFloat()..last.toFloat()
