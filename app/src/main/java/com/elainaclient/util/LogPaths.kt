package com.elainaclient.util

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * On-device log locations.
 *
 * Preferred (easy for the user/developer to grab):
 *  - `Documents/ElainaClient/logs/App/`  — crash reports
 *  - `Documents/ElainaClient/logs/game/` — relay / module / scaffold diagnostics
 *
 * Public Documents is not writable on many Android 11+ devices (WRITE_EXTERNAL_STORAGE is a no-op
 * for apps targeting modern SDKs), so both helpers fall back to
 * `Android/data/com.elainaclient/files/logs/<name>/` and finally the private files dir. Callers
 * should show the returned path so the user knows where the log actually landed.
 */
object LogPaths {

    private const val ROOT = "ElainaClient/logs"

    fun app(context: Context): File = resolve(context, "App")

    fun game(context: Context): File = resolve(context, "game")

    /** Most recently modified `*.log` in the game directory, if any. */
    fun latestGameLog(context: Context): File? {
        val files = game(context).listFiles { file -> file.isFile && file.name.endsWith(".log") }
        return files?.maxByOrNull { it.lastModified() }
    }

    private fun resolve(context: Context, name: String): File {
        val public = runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "$ROOT/$name",
            )
        }.getOrNull()
        if (public != null && ensure(public)) return public

        val external = context.getExternalFilesDir("logs/$name")
        if (external != null && ensure(external)) return external

        val internal = File(context.filesDir, "logs/$name")
        ensure(internal)
        return internal
    }

    private fun ensure(directory: File): Boolean {
        if (!directory.exists()) directory.mkdirs()
        return directory.isDirectory && directory.canWrite()
    }
}
