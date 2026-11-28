package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.onSecondaryClick
import com.mrboomdev.scrollix.ui.icons.send_icon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The send button. Holding it (or right clicking on desktop) opens a menu with
 * scheduled and silent sends, which used to take buttons of their own.
 *
 * Shared by the composer and the attachment review sheet so both offer the
 * same actions from the same gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SendButton(
    enabled: Boolean,
    isSending: Boolean,
    onSend: () -> Unit,
    onSchedule: (() -> Unit)?,
    onSendSilently: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val hasMenu = onSchedule != null || onSendSilently != null

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled || isSending) {
                    colors.primary
                } else {
                    colors.onSurface.copy(alpha = 0.12f)
                }
            )
            .then(
                if (hasMenu && enabled) {
                    Modifier.onSecondaryClick { menuExpanded = true }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Send message",
                onLongClickLabel = if (hasMenu) "Message options" else null,
                onLongClick = if (hasMenu) {
                    { menuExpanded = true }
                } else {
                    null
                },
                onClick = onSend
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary
            )
        } else {
            Icon(
                tint = if (enabled) {
                    colors.onPrimary
                } else {
                    colors.onSurface.copy(alpha = 0.38f)
                },
                imageVector = send_icon,
                contentDescription = "Send message"
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            onSendSilently?.let { sendSilently ->
                DropdownMenuItem(
                    text = { Text("Send silently") },
                    onClick = {
                        menuExpanded = false
                        sendSilently()
                    }
                )
            }

            onSchedule?.let { schedule ->
                DropdownMenuItem(
                    text = { Text("Schedule message") },
                    onClick = {
                        menuExpanded = false
                        schedule()
                    }
                )
            }
        }
    }
}
