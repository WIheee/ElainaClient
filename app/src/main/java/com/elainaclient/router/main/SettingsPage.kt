package com.elainaclient.router.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.elainaclient.R
import com.elainaclient.game.ModuleManager
import com.elainaclient.overlay.GUITheme
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.ui.theme.AppTheme
import com.elainaclient.ui.theme.ThemeMode
import com.elainaclient.util.LocalSnackbarHostState
import com.elainaclient.util.SnackbarHostStateScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageContent() {
    SnackbarHostStateScope {
        val context = LocalContext.current
        val snackbarHostState = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

        var showOpacityDialog by rememberSaveable { mutableStateOf(false) }
        var showThemeDialog by rememberSaveable { mutableStateOf(false) }
        var showGuiThemeDialog by rememberSaveable { mutableStateOf(false) }
        var showFileNameDialog by rememberSaveable { mutableStateOf(false) }
        var configFileName by rememberSaveable { mutableStateOf("") }
        var isExporting by remember { mutableStateOf(false) }

        var overlayOpacity by remember {
            mutableFloatStateOf(prefs.getFloat("overlay_opacity", 1f))
        }
        var shortcutOpacity by remember {
            mutableFloatStateOf(prefs.getFloat("shortcut_opacity", 1f))
        }

        val importConfigPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                val ok = ModuleManager.importConfigFromFile(context, it)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "配置导入成功" else "导入配置失败"
                    )
                }
            }
        }


        suspend fun exportConfig(fileName: String): Result<String> = withContext(Dispatchers.IO) {
            try {

                val configsDir = ModuleManager.getElainaClientConfigsDirectory()
                    ?: return@withContext Result.failure(Exception("创建 ElainaClient 目录失败"))


                val finalFileName = if (fileName.isBlank()) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    "config_$timestamp"
                } else {
                    fileName
                }

                val configFile = File(configsDir, "$finalFileName.json")


                val success = ModuleManager.exportConfigToFile(context, configFile.absolutePath)

                if (success) {
                    Result.success(configFile.absolutePath)
                } else {
                    Result.failure(Exception("导出配置失败"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = { SnackbarHost(LocalSnackbarHostState.current) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showOpacityDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.overlay_opacity_settings),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.overlay_opacity_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Rounded.Settings, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(0.8f).size(20.dp)
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showThemeDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DarkMode, null, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("外观主题", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                themeModeLabel(AppTheme.mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.Settings, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(0.8f).size(20.dp)
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showGuiThemeDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("点击界面主题", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                GUITheme.fromPref(context).displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.Settings, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.scale(0.8f).size(20.dp)
                        )
                    }
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text("配置管理", style = MaterialTheme.typography.bodyLarge)
                                Text("保存与加载模块配置", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        FilledTonalButton(
                            onClick = { importConfigPicker.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("导入配置")
                        }

                        FilledTonalButton(
                            onClick = { showFileNameDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isExporting) "导出中…" else "导出配置")
                        }
                    }
                }

                if (showFileNameDialog) {
                    BasicAlertDialog(
                        onDismissRequest = {
                            if (!isExporting) {
                                showFileNameDialog = false
                                configFileName = ""
                            }
                        },
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = AlertDialogDefaults.shape,
                            tonalElevation = AlertDialogDefaults.TonalElevation
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "导出配置",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                Text(
                                    "配置将保存到：Documents/ElainaClient/configs/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = configFileName,
                                    onValueChange = { configFileName = it },
                                    label = { Text("配置名称（可选）") },
                                    placeholder = { Text("my-config") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isExporting,
                                    supportingText = {
                                        Text(
                                            if (configFileName.isBlank())
                                                "留空则使用时间戳"
                                            else
                                                "文件：$configFileName.json"
                                        )
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            showFileNameDialog = false
                                            configFileName = ""
                                        },
                                        enabled = !isExporting
                                    ) {
                                        Text("取消")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            isExporting = true
                                            scope.launch {
                                                val result = exportConfig(configFileName)
                                                isExporting = false

                                                result.onSuccess { filePath ->
                                                    snackbarHostState.showSnackbar(
                                                        message = "Config saved successfully!\nLocation: $filePath",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }.onFailure { error ->
                                                    snackbarHostState.showSnackbar(
                                                        message = "导出失败：${error.message}",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }

                                                showFileNameDialog = false
                                                configFileName = ""
                                            }
                                        },
                                        enabled = !isExporting
                                    ) {
                                        if (isExporting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(if (isExporting) "保存中…" else "导出")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showGuiThemeDialog) {
            val currentGuiTheme = GUITheme.fromPref(context)
            BasicAlertDialog(
                onDismissRequest = { showGuiThemeDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "点击界面主题",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GUITheme.entries.forEach { guiTheme ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = guiTheme.ported,
                                onClick = {
                                    prefs.edit { putString(GUITheme.PREF_KEY, guiTheme.name) }
                                    OverlayManager.applyTheme()
                                    showGuiThemeDialog = false
                                }
                            ) {
                                Row(
                                    Modifier.padding(15.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentGuiTheme == guiTheme,
                                        onClick = null,
                                        enabled = guiTheme.ported,
                                    )
                                    Column {
                                        Text(
                                            guiTheme.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (guiTheme.ported) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        if (!guiTheme.ported) {
                                            Text(
                                                "未移植，请等待",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showThemeDialog) {
            val currentMode = AppTheme.mode
            BasicAlertDialog(
                onDismissRequest = { showThemeDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "外观主题",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ThemeMode.entries.forEach { option ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    AppTheme.set(context, option)
                                    showThemeDialog = false
                                }
                            ) {
                                Row(
                                    Modifier.padding(15.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = currentMode == option, onClick = null)
                                    Text(themeModeLabel(option), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showOpacityDialog) {
            BasicAlertDialog(
                onDismissRequest = { showOpacityDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.overlay_opacity_settings),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text(
                                    stringResource(R.string.overlay_opacity),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = overlayOpacity,
                                    onValueChange = {
                                        overlayOpacity = it
                                        prefs.edit { putFloat("overlay_opacity", it) }
                                        OverlayManager.updateOverlayOpacity(it)
                                    },
                                    valueRange = 0f..1f
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.shortcut_opacity),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = shortcutOpacity,
                                    onValueChange = {
                                        shortcutOpacity = it
                                        prefs.edit { putFloat("shortcut_opacity", it) }
                                        OverlayManager.updateShortcutOpacity(it)
                                    },
                                    valueRange = 0f..1f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}