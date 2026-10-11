package com.elainaclient.router.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elainaclient.R
import com.elainaclient.game.AccountManager
import com.elainaclient.model.CaptureModeModel
import com.elainaclient.service.Services
import com.elainaclient.ui.theme.WColors
import com.elainaclient.util.LocalSnackbarHostState
import com.elainaclient.util.MinecraftUtils
import com.elainaclient.util.ServerCompatUtils
import com.elainaclient.util.SnackbarHostStateScope
import com.elainaclient.viewmodel.MainScreenViewModel
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Home dashboard, ported from LuminaCN-2.1.0's `NewHomeScreen` (the server-verification
 * "ad gate" and its notice/privacy/update dialogs were intentionally dropped).
 */
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageContent() {
    SnackbarHostStateScope {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = LocalSnackbarHostState.current
        val vm: MainScreenViewModel = viewModel()
        val model by vm.captureModeModel.collectAsStateWithLifecycle()
        val selectedGame by vm.selectedGame.collectAsStateWithLifecycle()

        var isActiveBefore by rememberSaveable { mutableStateOf(Services.isActive) }
        var showConnectionDialog by remember { mutableStateOf(false) }

        val onPostPermissionResult: (Boolean) -> Unit = block@{ isGranted ->
            if (!isGranted) {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(context.getString(R.string.notification_permission_denied))
                }
                return@block
            }
            if (selectedGame == null) {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(context.getString(R.string.select_game_first))
                }
                return@block
            }
            Services.toggle(context, vm.captureModeModel.value)
        }

        val postNotificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted -> onPostPermissionResult(isGranted) }

        val overlayPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (!Settings.canDrawOverlays(context)) {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(context.getString(R.string.overlay_permission_denied))
                }
                return@rememberLauncherForActivityResult
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@rememberLauncherForActivityResult
            }
            onPostPermissionResult(true)
        }

        val requestStart: () -> Unit = {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, R.string.request_overlay_permission, Toast.LENGTH_SHORT).show()
                overlayPermissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onPostPermissionResult(true)
            }
        }

        LaunchedEffect(Services.isActive) {
            if (Services.isActive == isActiveBefore) return@LaunchedEffect
            isActiveBefore = Services.isActive
            snackbarHostState.currentSnackbarData?.dismiss()
            if (Services.isActive) {
                showConnectionDialog = true
                snackbarHostState.showSnackbar(context.getString(R.string.backend_connected))
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.backend_disconnected))
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.home),
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
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!Services.isActive) {
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "服务正在启动...",
                                    actionLabel = "启动应用",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    val intent = selectedGame?.let { context.packageManager.getLaunchIntentForPackage(it) }
                                    if (intent != null) context.startActivity(intent)
                                    else snackbarHostState.showSnackbar("未安装选定的游戏，请先选择游戏")
                                }
                            }
                        }
                        requestStart()
                    },
                    containerColor = if (Services.isActive) WColors.Error else WColors.Primary,
                    contentColor = WColors.OnPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        if (Services.isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (Services.isActive) "停止服务" else "启动服务",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            bottomBar = {
                SnackbarHost(snackbarHostState, modifier = Modifier.animateContentSize())
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AccountCard()
                GameCard()
                ServerConfigCard(vm, model, snackbarHostState)
                ServerListCard()
                ServiceStatusCard()
            }
        }

        if (showConnectionDialog) {
            ConnectionDialog(model) { showConnectionDialog = false }
        }
    }
}

@Composable
private fun AccountCard() {
    val account = AccountManager.selectedAccount ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = WColors.Primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp),
                    tint = WColors.Primary
                )
            }
            Column {
                Text("当前账户", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(account.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameCard() {
    val context = LocalContext.current
    val vm: MainScreenViewModel = viewModel()
    val selectedGame by vm.selectedGame.collectAsStateWithLifecycle()
    val packageInfos by vm.packageInfos.collectAsStateWithLifecycle()
    val packageInfoState by vm.packageInfoState.collectAsStateWithLifecycle()
    var showSelector by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSelector = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = WColors.Primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.Rounded.Extension,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp),
                    tint = WColors.Primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = selectedGame ?: "未选择游戏",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "点击选择要启动的游戏 · 支持 ${MinecraftUtils.RECOMMENDED_VERSION}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(24.dp)
                        .rotate(270f),
                    tint = WColors.Primary
                )
            }
        }
    }

    if (showSelector) {
        LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.fetchPackageInfos() }

        BasicAlertDialog(
            onDismissRequest = { showSelector = false },
            modifier = Modifier.padding(vertical = 24.dp),
            content = {
                Surface(shape = AlertDialogDefaults.shape, tonalElevation = AlertDialogDefaults.TonalElevation) {
                    Column(
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("选择游戏", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
                        LazyColumn(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                if (packageInfoState === MainScreenViewModel.PackageInfoState.Loading) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                            items(packageInfos.size) { index ->
                                val packageInfo = packageInfos[index]
                                val applicationInfo = packageInfo.applicationInfo!!
                                val pm = context.packageManager
                                val icon = remember { applicationInfo.loadIcon(pm).toBitmap().asImageBitmap() }
                                val name = remember { applicationInfo.loadLabel(pm).toString() }
                                val packageName = packageInfo.packageName
                                val versionName = packageInfo.versionName ?: "0.0.0"
                                Card(
                                    onClick = {
                                        vm.selectGame(packageName)
                                        showSelector = false
                                    },
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Icon(bitmap = icon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(versionName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ServerConfigCard(
    vm: MainScreenViewModel,
    model: CaptureModeModel,
    snackbarHostState: androidx.compose.material3.SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    var ip by remember(model.serverHostName) { mutableStateOf(model.serverHostName) }
    var port by remember(model.serverPort) { mutableStateOf(model.serverPort.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("服务器配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("服务器地址") },
                singleLine = true,
                enabled = !Services.isActive,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                label = { Text("服务器端口") },
                singleLine = true,
                enabled = !Services.isActive,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val p = port.toIntOrNull()
                    if (ip.isBlank() || p == null || p !in 0..65535) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("服务器地址或端口无效") }
                        return@Button
                    }
                    vm.selectCaptureModeModel(
                        model.copy(serverHostName = ip.trim(), serverPort = p).withAutoDetectedServerConfig()
                    )
                    coroutineScope.launch { snackbarHostState.showSnackbar("服务器配置已保存") }
                },
                enabled = !Services.isActive,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WColors.Primary, contentColor = WColors.OnPrimary)
            ) { Text("保存配置") }

            if (Services.isActive) {
                Text(
                    "修改服务器设置前请先停止中继。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerListCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = WColors.Primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp),
                            tint = WColors.Primary
                        )
                    }
                    Column {
                        Text("服务器列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (expanded) "点击收起列表" else "点击展开选择服务器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(300),
                        label = "chevron_rotation"
                    )
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp)
                            .rotate(rotation),
                        tint = WColors.Primary
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    ServerListContent(contentPadding = PaddingValues(vertical = 4.dp))
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusCard() {
    val active = Services.isActive
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconScale by animateFloatAsState(if (active) 1.2f else 1f, label = "status_scale")
            Icon(
                if (active) Icons.Filled.Check else Icons.Filled.Stop,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.scale(iconScale)
            )
            Column {
                Text("服务状态", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (active) "运行中" else "已停止",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ConnectionDialog(model: CaptureModeModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val ipAddress = remember {
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("中继已连接", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "打开 Minecraft → 好友 → 通过局域网加入；若未出现，用此 IP 和端口手动添加服务器。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ConnectionRow(
                            label = "IP",
                            value = ipAddress,
                            onCopy = {
                                context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("IP 地址", ipAddress))
                                coroutineScope.launch { snackbarHostState.showSnackbar("IP 已复制到剪贴板") }
                            }
                        )
                        ConnectionRow(
                            label = "Port",
                            value = "19132",
                            onCopy = {
                                context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Port", "19132"))
                                coroutineScope.launch { snackbarHostState.showSnackbar("端口已复制到剪贴板") }
                            }
                        )
                    }
                }

                if (model.isProtectedServer()) {
                    Card(colors = CardDefaults.cardColors(containerColor = WColors.Secondary.copy(alpha = 0.12f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tips", style = MaterialTheme.typography.titleSmall, color = WColors.Secondary, fontWeight = FontWeight.Bold)
                            ServerCompatUtils.getTroubleshootingTips().take(3).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } }
    )
}

@Composable
private fun ConnectionRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(18.dp),
                    tint = WColors.Primary
                )
            }
        }
    }
}
