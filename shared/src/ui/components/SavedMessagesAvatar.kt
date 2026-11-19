package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.mrboomdev.scrollix.ui.icons.keep_icon

/** Avatar of the chat with yourself, which Telegram shows as Saved Messages. */
@Composable
internal fun SavedMessagesAvatar(
    modifier: Modifier = Modifier,
    size: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(size * 0.56f),
            imageVector = keep_icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
