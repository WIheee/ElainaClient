package com.elainaclient.application

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.elainaclient.activity.CrashHandlerActivity
import com.elainaclient.util.LogPaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppContext : Application(), Thread.UncaughtExceptionHandler {

    companion object {
        lateinit var instance: AppContext
            private set

        // Helper to check if initialized
        val isInitialized: Boolean
            get() = ::instance.isInitialized

        const val EXTRA_MESSAGE = "message"
        const val EXTRA_STACK_TRACE = "stack_trace"
        const val EXTRA_LOG_PATH = "log_path"

        private const val TAG = "ElainaCrash"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = runCatching { e.stackTraceToString() }.getOrElse { e.toString() }
        val report = runCatching { buildCrashReport(t, stackTrace) }
            .getOrElse { "Failed to build the crash report: ${it.stackTraceToString()}\n\n$stackTrace" }

        Log.e(TAG, "Uncaught exception on thread ${t.name}", e)
        Log.e(TAG, report)

        val logFile = runCatching { writeCrashLog(report) }.getOrNull()

        runCatching {
            startActivity(Intent(this, CrashHandlerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_MESSAGE, report)
                putExtra(EXTRA_STACK_TRACE, stackTrace)
                putExtra(EXTRA_LOG_PATH, logFile?.absolutePath)
            })
        }.onFailure {
            Log.e(TAG, "Failed to open the crash screen", it)
        }

        Process.killProcess(Process.myPid())
    }

    /**
     * Crash reports live in `Documents/ElainaClient/logs/App/` (or the app-specific fallback, see
     * [LogPaths]). The returned file path is shown on the crash screen.
     */
    private fun writeCrashLog(report: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val name = "crash-$stamp.log"

        val directory = LogPaths.app(this)
        directory.mkdirs()
        val preferred = File(directory, name)
        runCatching { preferred.writeText(report) }
            .onSuccess { return preferred }

        // Last-resort fallback if the resolved directory turned read-only between the check and
        // the write (public Documents can be restricted at any time).
        val fallbackDir = File(filesDir, "logs/App").apply { mkdirs() }
        return File(fallbackDir, name).apply { writeText(report) }
    }

    /**
     * Everything a developer needs to reproduce a report: app/version, device, crash thread and
     * stack, plus the tail of the current game diagnostics log (NetLog writes to
     * `Documents/ElainaClient/logs/game/`).
     */
    private fun buildCrashReport(thread: Thread, stackTrace: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "?"
        val versionCode = packageInfo?.longVersionCode ?: 0L

        val gameLog = runCatching { LogPaths.latestGameLog(this) }.getOrNull()
        val gameTail = gameLog?.let { file ->
            runCatching { file.readLines().takeLast(200) }.getOrDefault(emptyList())
        }.orEmpty()

        return buildString {
            appendLine("===== ElainaClient crash report =====")
            appendLine("Time: $time")
            appendLine("App: ElainaClient $versionName ($versionCode)")
            appendLine("Package: $packageName")
            appendLine("Process: ${Application.getProcessName()} (pid ${Process.myPid()})")
            appendLine("Thread: ${thread.name}")
            appendLine("Thread group: ${thread.threadGroup?.name}")
            appendLine()
            appendLine("--- Device ---")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Locale: ${Locale.getDefault()}")
            appendLine()
            appendLine("--- Stack trace ---")
            appendLine(stackTrace)
            appendLine()
            appendLine("--- Recent game log (last 200 lines) ---")
            if (gameLog != null) {
                appendLine("File: ${gameLog.absolutePath}")
                gameTail.forEach { appendLine(it) }
            } else {
                appendLine("(no game log found)")
            }
        }
    }
}
