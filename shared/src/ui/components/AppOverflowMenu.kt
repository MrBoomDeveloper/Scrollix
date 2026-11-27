package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.ui.icons.draft_icon
import com.mrboomdev.scrollix.ui.icons.qr_code_scanner_icon
import com.mrboomdev.scrollix.ui.icons.settings_icon

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One entry of the app's overflow menu. */
internal data class AppMenuItem(
    val title: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The app-wide menu entries, in the order they are shown. Both the navigation
 * rail and the chat-list overflow build their menu from this list, so an entry
 * added here appears in both places and the two menus cannot drift apart.
 *
 * Actions that only make sense for the chat list (reordering folders or pinned
 * chats, marking every chat read) are appended by that screen through [extra],
 * because the state they act on belongs to it.
 */
internal fun appMenuItems(
    onOpenDrafts: () -> Unit,
    onOpenSettings: () -> Unit,
    onScanQr: () -> Unit,
    extra: List<AppMenuItem> = emptyList()
): List<AppMenuItem> = listOf(
    AppMenuItem(title = "Drafts", icon = draft_icon, onClick = onOpenDrafts),
    AppMenuItem(title = "Settings", icon = settings_icon, onClick = onOpenSettings),
    AppMenuItem(title = "Scan QR", icon = qr_code_scanner_icon, onClick = onScanQr)
) + extra

/** Renders [items]; the caller owns the expanded state of its own menu. */
@Composable
internal fun AppOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<AppMenuItem>
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp)
    ) {
        items.forEach { item ->
            val icon = item.icon

            DropdownMenuItem(
                leadingIcon = if (icon == null) {
                    null
                } else {
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null
                        )
                    }
                },
                text = {
                    Text(text = item.title)
                },
                enabled = item.enabled,
                onClick = {
                    onDismissRequest()
                    item.onClick()
                }
            )
        }
    }
}
