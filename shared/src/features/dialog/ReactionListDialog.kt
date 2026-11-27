package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReactionReactor
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.components.AvatarImage
import kotlinx.coroutines.launch

/**
 * How many reactors the provider did not list. Telegram only sends a recent
 * window of the people behind a reaction, so the sheet says "and N others"
 * until the rest is loaded with [loadMessageReactionReactors].
 */
internal fun reactionExtraCount(reaction: MessageReactionInfo): Int {
    return (reaction.count - reaction.reactors.size).coerceAtLeast(0)
}

/** The reactors of one reaction that the sheet can actually name. */
internal fun reactionVisibleReactors(reaction: MessageReactionInfo): List<MessageReactionReactor> {
    return reaction.reactors.take(reaction.count.coerceAtLeast(0))
}

private fun reactionKey(reaction: MessageReactionInfo): String {
    return MessageReactionOption(
        emoji = reaction.emoji,
        customEmojiId = reaction.customEmojiId
    ).key
}

/** The reactors fetched beyond the window the message itself carried. */
private data class FullReactorsState(
    val reactors: List<MessageReactionReactor>,
    val totalCount: Int,
    val nextOffset: String,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Lists who reacted to a message, grouped by reaction. It opens from a
 * long-press on a reaction chip, so tapping a chip keeps toggling this
 * account's own reaction.
 */
@Composable
internal fun ReactionListDialog(
    reactions: List<MessageReactionInfo>,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long>,
    session: AccountSession?,
    providerDialogId: String?,
    messageId: Long,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Only reactions whose window was loaded appear here; the rest render
    // straight from the message.
    var fullLists by remember { mutableStateOf<Map<String, FullReactorsState>>(emptyMap()) }

    fun loadReactors(reaction: MessageReactionInfo, offset: String) {
        val currentSession = session ?: return
        val dialogId = providerDialogId ?: return
        val key = reactionKey(reaction)

        if (fullLists[key]?.isLoading == true) return

        val previous = fullLists[key]

        fullLists = fullLists + (key to FullReactorsState(
            reactors = previous?.reactors.orEmpty(),
            totalCount = previous?.totalCount ?: reaction.count,
            nextOffset = previous?.nextOffset.orEmpty(),
            isLoading = true
        ))

        scope.launch {
            currentSession.loadMessageReactionReactors(
                providerDialogId = dialogId,
                messageId = messageId,
                reaction = MessageReactionOption(
                    emoji = reaction.emoji,
                    customEmojiId = reaction.customEmojiId
                ),
                offset = offset
            ).onSuccess { page ->
                fullLists = fullLists + (key to FullReactorsState(
                    reactors = previous?.reactors.orEmpty() + page.reactors,
                    totalCount = page.totalCount,
                    nextOffset = page.nextOffset
                ))
            }.onFailure { failure ->
                fullLists = fullLists + (key to FullReactorsState(
                    reactors = previous?.reactors.orEmpty(),
                    totalCount = previous?.totalCount ?: reaction.count,
                    nextOffset = previous?.nextOffset.orEmpty(),
                    error = failure.message ?: "Failed to load reactions"
                ))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Reactions") },
        text = {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .heightIn(max = 420.dp)
            ) {
                if (reactions.isEmpty()) {
                    Text(
                        text = "No reactions yet",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn {
                        reactions.forEach { reaction ->
                            val key = reactionKey(reaction)
                            val full = fullLists[key]
                            val shown = full?.reactors ?: reactionVisibleReactors(reaction)
                            val total = full?.totalCount ?: reaction.count
                            val extra = (total - shown.size).coerceAtLeast(0)

                            item(key = "reaction:$key") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReactionGlyph(
                                        reaction = reaction,
                                        data = customEmojiData,
                                        adaptiveEmojis = customEmojiAdaptive,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        session = session
                                    )

                                    Text(
                                        text = total.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (extra > 0 && session != null && providerDialogId != null) {
                                        if (full?.isLoading == true) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    loadReactors(
                                                        reaction = reaction,
                                                        offset = full?.nextOffset.orEmpty()
                                                    )
                                                }
                                            ) {
                                                Text("Show all")
                                            }
                                        }
                                    }
                                }
                            }

                            items(
                                items = shown,
                                key = { reactor -> "reactor:$key:${reactor.id}" }
                            ) { reactor ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AvatarImage(
                                        avatar = reactor.avatar,
                                        size = 28.dp
                                    )

                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = reactor.name.ifBlank { "Hidden account" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (extra > 0) {
                                item(key = "extra:$key") {
                                    Column(Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                                        Text(
                                            text = "and $extra more",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )

                                        full?.error?.let { message ->
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        // A partially loaded list can always be
                                        // continued from where it stopped.
                                        if (full != null && full.nextOffset.isNotBlank() &&
                                            !full.isLoading
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    loadReactors(reaction, full.nextOffset)
                                                }
                                            ) {
                                                Text("Load more")
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "divider:$key") {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
