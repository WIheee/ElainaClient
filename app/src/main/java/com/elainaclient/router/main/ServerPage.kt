package com.elainaclient.router.main

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elainaclient.R
import com.elainaclient.util.LocalSnackbarHostState
import com.elainaclient.util.SnackbarHostStateScope
import com.elainaclient.viewmodel.MainScreenViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

data class ServerInfo(
    val name: String,
    val iconRes: Int,
    val address: String,
    val port: Int,
    val hasSubServers: Boolean = false,
    val subServers: List<SubServerInfo> = emptyList()
)

data class SubServerInfo(
    val name: String,
    val region: String,
    val address: String,
    val port: Int,
    val isSelected: Boolean = false
)

/** User-added server, persisted as JSON in SharedPreferences. */
data class CustomServer(
    val name: String,
    val address: String,
    val port: Int
)

private const val PREFS_NAME = "game_settings"
private const val CUSTOM_SERVERS_KEY = "custom_servers"

private fun loadCustomServers(context: Context): List<CustomServer> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(CUSTOM_SERVERS_KEY, null) ?: return emptyList()
    return try {
        val type = object : TypeToken<List<CustomServer>>() {}.type
        Gson().fromJson<List<CustomServer>>(raw, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveCustomServers(context: Context, list: List<CustomServer>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(CUSTOM_SERVERS_KEY, Gson().toJson(list))
        .apply()
}

val servers = listOf(
    ServerInfo(
        name = "Lifeboat",
        iconRes = R.drawable.lifeboat_icon,
        address = "play.lbsg.net",
        port = 19132
    ),
    ServerInfo(
        name = "CubeCraft",
        iconRes = R.drawable.cubecraft_icon,
        address = "play.cubecraft.net",
        port = 19132
    ),
    ServerInfo(
        name = "NetherGames",
        iconRes = R.drawable.nethergames,
        address = "play.nethergames.org",
        port = 19132
    ),
    ServerInfo(
        name = "DonutSMP",
        iconRes = R.drawable.donutsmp_icon,
        address = "donutsmp.net",
        port = 19132,
    ),
)

/**
 * Content-only server picker (no Scaffold/TopAppBar), embedded in the home dashboard.
 * The list is a plain, non-scrolling Column so it nests inside the dashboard's scroll.
 */
@Composable
fun ServerListContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val mainScreenViewModel: MainScreenViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val model by mainScreenViewModel.captureModeModel.collectAsStateWithLifecycle()
    var customServers by remember { mutableStateOf(loadCustomServers(context)) }
    var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }
    var selectedSubServer by remember { mutableStateOf<SubServerInfo?>(null) }
    var showSubServers by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun selectTarget(server: ServerInfo, sub: SubServerInfo?) {
        selectedServer = server
        selectedSubServer = sub
        mainScreenViewModel.selectCaptureModeModel(
            model.copy(
                serverHostName = sub?.address ?: server.address,
                serverPort = sub?.port ?: server.port
            ).withAutoDetectedServerConfig()
        )
        coroutineScope.launch { snackbarHostState.showSnackbar("已选择 ${server.name}") }
    }

    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        servers.forEach { server ->
            ServerCard(
                name = server.name,
                iconRes = server.iconRes,
                selected = selectedServer?.address == server.address,
                onClick = {
                    if (server.hasSubServers) {
                        selectedServer = server
                        showSubServers = true
                    } else {
                        selectTarget(server, null)
                    }
                }
            )
        }

        if (customServers.isNotEmpty()) {
            Text(
                "自定义服务器",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        customServers.forEach { custom ->
            ServerCard(
                name = custom.name,
                iconRes = R.drawable.ic_web,
                selected = selectedServer?.address == custom.address && selectedServer?.port == custom.port,
                onClick = { selectTarget(ServerInfo(custom.name, R.drawable.ic_web, custom.address, custom.port), null) },
                onDelete = {
                    val updated = customServers.filterNot {
                        it.address == custom.address && it.port == custom.port && it.name == custom.name
                    }
                    customServers = updated
                    saveCustomServers(context, updated)
                }
            )
        }

        TextButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("添加自定义服务器")
        }
    }

    if (showSubServers && selectedServer?.hasSubServers == true) {
        Dialog(onDismissRequest = { showSubServers = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "选择地区与服务器",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    selectedServer?.subServers?.groupBy { it.region }?.forEach { (region, subServers) ->
                        Text(
                            region,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        subServers.forEach { subServer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectTarget(selectedServer!!, subServer)
                                        showSubServers = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSubServer == subServer,
                                    onClick = null
                                )
                                Text(
                                    subServer.name,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, address, port ->
                val updated = customServers + CustomServer(name, address, port)
                customServers = updated
                saveCustomServers(context, updated)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ServerCard(
    name: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.Unspecified
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onDelete != null) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, port: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("19132") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("添加服务器", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("地址（域名 / IP）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { v -> port = v.filter { it.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && address.isNotBlank()) {
                                onSave(name.trim(), address.trim(), port.toIntOrNull() ?: 19132)
                            }
                        }
                    ) { Text("保存") }
                }
            }
        }
    }
}
