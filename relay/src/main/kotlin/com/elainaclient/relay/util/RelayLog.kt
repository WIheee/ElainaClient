package com.elainaclient.relay.util

/**
 * Connection-path diagnostics for `:relay`.
 *
 * `:relay` has no Android SDK, so it cannot use `android.util.Log` or write files itself. It prints
 * (visible in logcat) and forwards every line to an optional [sink], which the app installs so the
 * same lines end up in a file the user can hand over without a PC.
 *
 * Keep this focused on the connection lifecycle - bind, dial, login, disconnect - not on per-packet
 * traffic. Per-packet output belongs in a module the user turns on deliberately, not here.
 */
object RelayLog {

    @Volatile
    private var sink: ((String) -> Unit)? = null

    /** Installed by the app (`NetLog`). Pass null to detach. */
    fun setSink(newSink: ((String) -> Unit)?) {
        sink = newSink
    }

    fun log(tag: String, message: String) {
        val line = "[$tag] $message"
        println(line)
        // A broken sink must never take down the relay.
        runCatching { sink?.invoke(line) }
    }
}
