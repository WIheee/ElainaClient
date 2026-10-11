package com.elainaclient.overlay.notify

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.elainaclient.overlay.OverlayWindow
import kotlinx.coroutines.delay

/**
 * Top-centre stack of notification cards. Non-touchable so it never steals input from the game or
 * the click GUI. Colours come from [MaterialTheme.colorScheme].
 */
class NotificationOverlay : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
            flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverlayNotificationManager.notifications.forEach { item ->
                key(item.id) { NotificationCard(item) }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        visible = true
        delay(item.durationMs)
        visible = false
        delay(400)
        OverlayNotificationManager.remove(item.id)
    }

    val remaining by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(item.durationMs.toInt().coerceAtLeast(1), easing = LinearEasing),
        label = "notif_progress"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        val (container, content, icon) = notificationColors(item.type)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = container,
            contentColor = content,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Column(Modifier.widthIn(min = 200.dp, max = 340.dp)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(item.message, style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { remaining },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = content,
                    trackColor = content.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun notificationColors(type: NotificationType): Triple<Color, Color, ImageVector> {
    val cs = MaterialTheme.colorScheme
    return when (type) {
        NotificationType.SUCCESS -> Triple(cs.primaryContainer, cs.onPrimaryContainer, Icons.Filled.CheckCircle)
        NotificationType.WARNING -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, Icons.Filled.Warning)
        NotificationType.ERROR -> Triple(cs.errorContainer, cs.onErrorContainer, Icons.Filled.Error)
        NotificationType.INFO -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, Icons.Filled.Info)
    }
}
