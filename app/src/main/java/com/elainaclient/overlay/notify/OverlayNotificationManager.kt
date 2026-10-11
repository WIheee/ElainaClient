package com.elainaclient.overlay.notify

import androidx.compose.runtime.mutableStateListOf
import com.elainaclient.overlay.OverlayManager

data class NotificationItem(
    val id: Int,
    val message: String,
    val type: NotificationType,
    val durationMs: Long
)

/**
 * In-game notification toasts. Attaches a [NotificationOverlay] window on demand (only while the
 * relay overlay service is running) and detaches it once the queue drains.
 *
 * Callable from anywhere: `OverlayNotificationManager.show("已启用", NotificationType.SUCCESS)`.
 */
object OverlayNotificationManager {

    private val items = mutableStateListOf<NotificationItem>()
    private var overlay: NotificationOverlay? = null
    private var nextId = 0

    val notifications: List<NotificationItem>
        get() = items

    fun show(
        message: String,
        type: NotificationType = NotificationType.INFO,
        durationMs: Long = 3000L
    ) {
        // Only meaningful while the overlay service is attached.
        if (OverlayManager.currentContext == null) return

        items.add(NotificationItem(nextId++, message, type, durationMs))
        if (overlay == null) {
            NotificationOverlay().also {
                overlay = it
                OverlayManager.showOverlayWindow(it)
            }
        }
    }

    fun success(message: String, durationMs: Long = 3000L) =
        show(message, NotificationType.SUCCESS, durationMs)

    fun warning(message: String, durationMs: Long = 3000L) =
        show(message, NotificationType.WARNING, durationMs)

    fun error(message: String, durationMs: Long = 3000L) =
        show(message, NotificationType.ERROR, durationMs)

    fun info(message: String, durationMs: Long = 3000L) =
        show(message, NotificationType.INFO, durationMs)

    /** Called by the overlay when a card finishes; detaches the window once empty. */
    internal fun remove(id: Int) {
        items.removeAll { it.id == id }
        if (items.isEmpty()) {
            overlay?.let { OverlayManager.dismissOverlayWindow(it) }
            overlay = null
        }
    }
}
