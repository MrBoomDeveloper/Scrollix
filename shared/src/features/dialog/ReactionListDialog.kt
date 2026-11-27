package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionReactor
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.components.AvatarImage

/**
 * How many reactors the provider did not list. Telegram only sends a recent
 * window of the people behind a reaction, so the sheet says "and N others"
 * instead of pretending the list is complete.
 */
internal fun reactionExtraCount(reaction: MessageReactionInfo): Int {
    return (reaction.count - reaction.reactors.size).coerceAtLeast(0)
}

/** The reactors of one reaction that the sheet can actually name. */
internal fun reactionVisibleReactors(reaction: MessageReactionInfo): List<MessageReactionReactor> {
    return reaction.reactors.take(reaction.count.coerceAtLeast(0))
}

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
    onDismiss: () -> Unit
) {
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
                            item(key = "reaction:${reaction.customEmojiId ?: reaction.emoji}") {
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
                                        text = reaction.count.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            items(
                                items = reactionVisibleReactors(reaction),
                                key = { reactor ->
                                    "reactor:${reaction.customEmojiId ?: reaction.emoji}:${reactor.id}"
                                }
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

                            val extra = reactionExtraCount(reaction)

                            if (extra > 0) {
                                item(key = "extra:${reaction.customEmojiId ?: reaction.emoji}") {
                                    Text(
                                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                                        text = "and $extra more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            item(key = "divider:${reaction.customEmojiId ?: reaction.emoji}") {
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
