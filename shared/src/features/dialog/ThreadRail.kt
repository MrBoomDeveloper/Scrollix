package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.ui.icons.reply_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact vertical rail of the thread roots loaded for the open chat. Each
 * badge opens the replies of its message, so threads stay reachable without
 * scrolling back to the root bubble.
 */
@Composable
fun MessageThreadRail(
    threads: List<ChatMessage>,
    onOpen: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    selectedMessageId: Long? = null
) {
    if (threads.isEmpty()) return

    Column(
        modifier = modifier
            .width(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        threads.forEach { thread ->
            val isSelected = thread.allMessageIds.any { id -> id == selectedMessageId }
            val count = thread.replyCount

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable { onOpen(thread) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        imageVector = reply_icon,
                        contentDescription = "Open thread",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = count.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
