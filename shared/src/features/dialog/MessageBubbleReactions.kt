package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DefaultQuickReactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ReactionPickerRow(
    message: ChatMessage,
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    session: AccountSession?,
    isChannel: Boolean = false,
    onReaction: (MessageReactionOption) -> Unit
) {
    var expanded by remember(message.messageId) { mutableStateOf(false) }
    var reactionsLoading by remember(message.messageId) { mutableStateOf(true) }
    var available by remember(message.messageId) {
        mutableStateOf<List<MessageReactionOption>>(emptyList())
    }
    var packs by remember(message.messageId) {
        mutableStateOf<List<StickerPack>>(emptyList())
    }
    var packItems by remember(message.messageId) {
        mutableStateOf<Map<Long, List<MessageMedia>>>(emptyMap())
    }

    val scope = rememberCoroutineScope()
    val settings by AppSettingsStore.state.collectAsState()

    // Reactions are loaded as soon as the menu opens so a channel only ever
    // offers the set the server enabled for that post. Pack reactions are not
    // fetched for channels: arbitrary custom emoji are not sendable there.
    LaunchedEffect(session, message.messageId) {
        val currentSession = session

        if (currentSession == null) {
            reactionsLoading = false
            return@LaunchedEffect
        }

        reactionsLoading = true
        available = currentSession
            .loadAvailableReactions(message.providerDialogId, message.messageId)
            .getOrDefault(emptyList())
        reactionsLoading = false
    }

    LaunchedEffect(expanded, session, message.messageId, isChannel) {
        if (!expanded || isChannel) return@LaunchedEffect

        val currentSession = session ?: return@LaunchedEffect

        if (packs.isEmpty()) {
            val loadedPacks = currentSession
                .loadStickerPacks(StickerPackKind.CustomEmoji)
                .getOrDefault(emptyList())

            packs = loadedPacks
            packItems = coroutineScope {
                loadedPacks.map { pack ->
                    async {
                        pack.id to currentSession.loadStickerSet(pack.id)
                            .getOrNull()
                            ?.stickers
                            .orEmpty()
                    }
                }.awaitAll().toMap()
            }
        }
    }

    val quick = remember(
        settings.quickReactions,
        settings.pinnedCustomReactions
    ) {
        val pinned = settings.quickReactions.map(::MessageReactionOption) +
            settings.pinnedCustomReactions.mapNotNull { custom ->
                custom.customEmojiId?.let { id ->
                    MessageReactionOption(emoji = custom.emoji, customEmojiId = id)
                }
            }

        pinned.takeIf { it.isNotEmpty() }
            ?: DefaultQuickReactions.map(::MessageReactionOption)
    }

    // Channels (and chats with a restricted reaction set) only accept the
    // reactions the server lists. The user's pinned quick reactions must not
    // stay in the menu when they are not sendable here.
    val sendableQuick = remember(quick, available, isChannel) {
        if (available.isEmpty()) {
            // A channel with disabled or restricted reactions must not fall
            // back to the user's global quick reactions.
            if (isChannel) emptyList() else quick
        } else {
            quick.filter { option -> available.any { it.key == option.key } }
        }
    }

    val entries = remember(sendableQuick, available, packs, packItems) {
        buildList {
            add(
                ReactionMenuEntry.Available(
                    options = if (isChannel) {
                        available
                    } else {
                        (sendableQuick + available).distinctBy { it.key }
                    }
                )
            )

            packs.forEach { pack ->
                add(ReactionMenuEntry.PackHeader(pack))

                packItems[pack.id].orEmpty().chunked(8).forEach { row ->
                    add(ReactionMenuEntry.PackRow(stickers = row))
                }
            }
        }
    }

    val packJumps = remember(packs) {
        packs.associate { pack -> pack.id to BringIntoViewRequester() }
    }

    fun isChosen(option: MessageReactionOption): Boolean {
        return message.reactions.any { reaction ->
            reaction.isChosen && if (option.customEmojiId != null) {
                reaction.customEmojiId == option.customEmojiId
            } else {
                reaction.customEmojiId == null && reaction.emoji == option.emoji
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (isChannel) {
            when {
                reactionsLoading -> Text(
                    text = "Loading reactions...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                available.isEmpty() -> Text(
                    text = "Reactions are disabled in this channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    available.forEach { option ->
                        ReactionPickerButton(
                            option = option,
                            chosen = isChosen(option),
                            data = data,
                            adaptiveEmojis = adaptiveEmojis,
                            session = session,
                            onClick = { onReaction(option) }
                        )
                    }
                }
            }
        } else if (!expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                sendableQuick.ifEmpty { available }.forEach { option ->
                    ReactionPickerButton(
                        option = option,
                        chosen = isChosen(option),
                        data = data,
                        adaptiveEmojis = adaptiveEmojis,
                        session = session,
                        onClick = { onReaction(option) }
                    )
                }

                TextButton(onClick = { expanded = true }) {
                    Text("+")
                }
            }
        } else {
            // Menu content is measured with intrinsics, which lazy lists can't
            // provide, so everything is laid out plainly and the menu scrolls.
            if (packs.isNotEmpty()) {
                // A quick jump bar keeps every pack one tap away instead of
                // making the user scroll the whole collection.
                packs.chunked(6).forEach { rowPacks ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowPacks.forEach { pack ->
                            val cover = packItems[pack.id]?.firstOrNull()

                            TextButton(
                                onClick = {
                                    packJumps[pack.id]?.let { requester ->
                                        scope.launch { requester.bringIntoView() }
                                    }
                                }
                            ) {
                                if (cover != null && cover.stickerId != null) {
                                    ReactionGlyph(
                                        reaction = MessageReactionInfo(
                                            emoji = cover.stickerEmoji ?: "",
                                            count = 0,
                                            customEmojiId = cover.stickerId
                                        ),
                                        data = data,
                                        adaptiveEmojis = adaptiveEmojis,
                                        color = LocalContentColor.current,
                                        session = session
                                    )
                                }

                                Text(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .widthIn(max = 120.dp),
                                    text = pack.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is ReactionMenuEntry.Available -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow = 8
                        ) {
                            entry.options.forEach { option ->
                                ReactionPickerButton(
                                    option = option,
                                    chosen = isChosen(option),
                                    data = data,
                                    adaptiveEmojis = adaptiveEmojis,
                                    session = session,
                                    onClick = { onReaction(option) }
                                )
                            }
                        }

                        is ReactionMenuEntry.PackHeader -> Text(
                            modifier = Modifier
                                .then(
                                    packJumps[entry.pack.id]?.let { requester ->
                                        Modifier.bringIntoViewRequester(requester)
                                    } ?: Modifier
                                )
                                .padding(top = 6.dp, bottom = 2.dp),
                            text = entry.pack.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        is ReactionMenuEntry.PackRow -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            maxItemsInEachRow = 8
                        ) {
                            entry.stickers.forEach { sticker ->
                                val emojiId = sticker.stickerId
                                val emoji = sticker.stickerEmoji

                                val option = when {
                                    emojiId != null -> MessageReactionOption(
                                        emoji = emoji ?: "",
                                        customEmojiId = emojiId
                                    )

                                    !emoji.isNullOrBlank() -> MessageReactionOption(emoji)
                                    else -> null
                                } ?: return@forEach

                                ReactionPickerButton(
                                    option = option,
                                    chosen = isChosen(option),
                                    data = data,
                                    adaptiveEmojis = adaptiveEmojis,
                                    session = session,
                                    onClick = { onReaction(option) }
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { expanded = false }
            ) {
                Text("Less")
            }
        }
    }
}

private sealed interface ReactionMenuEntry {
    data class Available(
        val options: List<MessageReactionOption>
    ) : ReactionMenuEntry

    data class PackHeader(
        val pack: StickerPack
    ) : ReactionMenuEntry

    data class PackRow(
        val stickers: List<MessageMedia>
    ) : ReactionMenuEntry
}

@Composable
internal fun ReactionPickerButton(
    option: MessageReactionOption,
    chosen: Boolean,
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    session: AccountSession?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (chosen) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        }
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            ReactionGlyph(
                reaction = MessageReactionInfo(
                    emoji = option.emoji,
                    count = 0,
                    customEmojiId = option.customEmojiId
                ),
                data = data,
                adaptiveEmojis = adaptiveEmojis,
                color = LocalContentColor.current,
                session = session
            )
        }
    }
}
