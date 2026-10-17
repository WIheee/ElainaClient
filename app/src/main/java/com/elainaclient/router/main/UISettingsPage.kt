package com.elainaclient.router.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.elainaclient.R
import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.overlay.UISettings
import kotlin.math.roundToInt

/**
 * Dedicated 界面 page: choose the click-GUI theme and tune a per-theme UI scale.
 *
 * All click-GUI sources live under `overlay/` (`overlay/gui/...`), including the Kotlin LiquidPE
 * rewrite (`overlay/gui/liquidpe`). The scale is read when a GUI window is created;
 * changing it while the overlay service is running rebuilds the windows immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UISettingsPageContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

    var selectedTheme by remember { mutableStateOf(GUITheme.fromPref(context)) }
    var scale by remember(selectedTheme) { mutableFloatStateOf(UISettings.scale(context, selectedTheme)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("点击界面主题", style = MaterialTheme.typography.titleMedium)
                    GUITheme.entries.forEach { theme ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedTheme = theme
                                    prefs.edit { putString(GUITheme.PREF_KEY, theme.name) }
                                    OverlayManager.applyTheme()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedTheme == theme, onClick = null)
                            Text(
                                text = theme.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${(UISettings.scale(context, theme) * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            ElevatedCard {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("界面缩放", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "当前主题：${selectedTheme.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = UISettings.MIN_SCALE..UISettings.MAX_SCALE,
                        onValueChangeFinished = {
                            UISettings.setScale(context, selectedTheme, scale)
                            UISettings.applyAll(context)
                        },
                    )
                    Row {
                        TextButton(
                            onClick = {
                                val default = UISettings.defaultScale(selectedTheme)
                                scale = default
                                UISettings.setScale(context, selectedTheme, default)
                                UISettings.applyAll(context)
                            },
                        ) {
                            Text("恢复默认")
                        }
                    }
                    Text(
                        text = "提示：缩放会在下次打开 ClickGUI 时生效；若悬浮窗服务正在运行，会立即重建窗口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
