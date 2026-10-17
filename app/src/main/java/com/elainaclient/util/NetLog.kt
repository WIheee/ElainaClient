package com.elainaclient.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.elainaclient.relay.util.RelayLog
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Connection diagnostics, written to a file the user can retrieve without a PC.
 *
 * Every line also goes to logcat under [TAG], and lines from `:relay` (which cannot write files on
 * its own) arrive through [RelayLog]'s sink.
 *
 * Location, in order of preference:
 *  1. `Documents/ElainaClient/logs/net-<timestamp>.log` - easiest to reach, but public Documents
 *     needs storage access the app may not have on modern Android.
 *  2. `Android/data/com.elainaclient/files/logs/` - no permission needed; reachable from an IDE or
 *     via adb.
 */
object NetLog {

    const val TAG = "ElainaNet"

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var currentFile: File? = null

    /** The file lines are currently being appended to, or null when not logging. */
    val file: File?
        get() = currentFile

    @Synchronized
    fun start(context: Context) {
        if (writer != null) return

        try {
            val directory = resolveDirectory(context)
            directory.mkdirs()
            val name = "net-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"
            val target = File(directory, name)
            writer = target.bufferedWriter()
            currentFile = target

            RelayLog.setSink { line -> append(line) }
            log("NET", "===== log started: ${target.absolutePath} =====")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start connection log", e)
        }
    }

    @Synchronized
    fun stop() {
        RelayLog.setSink(null)
        log("NET", "===== log stopped =====")
        runCatching {
            writer?.flush()
            writer?.close()
        }
        writer = null
        currentFile = null
    }

    fun log(tag: String, message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$stamp [$tag] $message"
        Log.i(TAG, line)
        append(line)
    }

    @Synchronized
    private fun append(line: String) {
        val out = writer ?: return
        runCatching {
            out.appendLine(line)
            out.flush()
        }
    }

    private fun resolveDirectory(context: Context): File {
        val documents = try {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ElainaClient/logs"
            )
        } catch (_: Exception) {
            null
        }

        if (documents != null && (documents.exists() || documents.mkdirs()) && documents.canWrite()) {
            return documents
        }

        val external = context.getExternalFilesDir("logs")
        if (external != null && (external.exists() || external.mkdirs()) && external.canWrite()) {
            return external
        }

        return File(context.filesDir, "logs")
    }
}
