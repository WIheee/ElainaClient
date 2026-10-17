package com.elainaclient.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.elainaclient.R
import com.elainaclient.application.AppContext
import com.elainaclient.ui.theme.AppTheme
import com.elainaclient.ui.theme.ElainaClientTheme
import java.io.File

/**
 * Shown by [AppContext] after an uncaught exception.
 *
 * Layout follows LuminaCN's crash screen: an error card, a monospace selectable stack trace and a
 * bottom action bar. Added here: the path of the automatically written crash report and a share
 * action, so testers can send the report without hunting through the file system.
 */
class CrashHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val message = intent?.getStringExtra(AppContext.EXTRA_MESSAGE)
        val stackTrace = intent?.getStringExtra(AppContext.EXTRA_STACK_TRACE).orEmpty()
        val logPath = intent?.getStringExtra(AppContext.EXTRA_LOG_PATH)
        if (message == null) {
            restartApp(this)
            return
        }

        setContent {
            ElainaClientTheme(darkTheme = AppTheme.isDark()) {
                CrashScreen(
                    stackTrace = stackTrace.ifEmpty { message },
                    logPath = logPath,
                    onCopy = { copyToClipboard(this, message) },
                    onShare = { shareCrashReport(this, message, logPath) },
                    onRestart = { restartApp(this) },
                )
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ElainaClient crash log", text))
        Toast.makeText(context, context.getString(R.string.crash_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * Shares the report file when possible (copied into the cache so the FileProvider can expose
     * it), otherwise falls back to the full report text.
     */
    private fun shareCrashReport(context: Context, message: String, logPath: String?) {
        val source = logPath?.let { File(it) }?.takeIf { it.isFile }
        val cached = source?.let { file ->
            runCatching {
                val directory = File(context.cacheDir, "crash").apply { mkdirs() }
                val target = File(directory, file.name)
                file.copyTo(target, overwrite = true)
                target
            }.getOrNull()
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ElainaClient crash log")
            putExtra(Intent.EXTRA_TEXT, message)
            if (cached != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    cached,
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "crash log", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.crash_share_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            Toast.makeText(context, R.string.crash_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = launch?.component
        if (component != null) {
            runCatching {
                context.startActivity(Intent.makeRestartActivityTask(component))
            }
        }
        Runtime.getRuntime().exit(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashScreen(
    stackTrace: String,
    logPath: String?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRestart: () -> Unit,
) {
    BackHandler { onRestart() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_happened)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            )
        },
        bottomBar = {
            ActionBottomBar(
                onRestart = onRestart,
                onCopy = onCopy,
                onShare = onShare,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.crash_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.crash_description),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (!logPath.isNullOrBlank()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.crash_log_saved),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = logPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.crash_stack_trace_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = stackTrace,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBottomBar(
    onRestart: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onRestart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.crash_restart))
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.crash_copy_log))
            }
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.crash_share_log))
            }
        }
    }
}
