package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.components.PickerSticker
import com.mrboomdev.scrollix.ui.icons.select_check_box_icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A pack that occurs in a message. [preview] is the pack's featured (first)
 * emoji, drawn in the menu instead of a generic icon, so the rows look like
 * the packs themselves.
 */
internal data class EmojiPackEntry(
    val pack: StickerPack,
    val preview: MessageMedia?
)

/** The "Emoji packs used" block of the message action menu. */
@Composable
internal fun EmojiPacksMenuSection(
    entries: List<EmojiPackEntry>,
    session: AccountSession?,
    onOpen: (Long) -> Unit
) {
    HorizontalDivider()

    Text(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        text = "Emoji packs used",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary
    )

    entries.forEach { entry ->
        DropdownMenuItem(
            colors = MenuDefaults.itemColors(
                leadingIconColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.primary
            ),

            leadingIcon = {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = entry.preview

                    if (preview != null && session != null) {
                        PickerSticker(
                            media = preview,
                            session = session,
                            size = 24.dp
                        )
                    } else {
                        Icon(
                            imageVector = select_check_box_icon,
                            contentDescription = null
                        )
                    }
                }
            },

            text = {
                Text(text = entry.pack.title)
            },

            onClick = {
                onOpen(entry.pack.id)
            }
        )
    }
}
