package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.features.dialog.media.ChatAnimationPreview
import com.mrboomdev.scrollix.features.dialog.media.ChatSticker
import com.mrboomdev.scrollix.features.dialog.media.ChatVideoPreview
import com.mrboomdev.scrollix.features.dialog.media.SpoilerOverlay
import com.mrboomdev.scrollix.features.dialog.media.loadingBlur
import com.mrboomdev.scrollix.features.dialog.media.mediaSpoilerBlur
import com.mrboomdev.scrollix.features.dialog.message.CustomEmojiMessageText
import com.mrboomdev.scrollix.features.home.FolderEmojiImage
import com.mrboomdev.scrollix.features.profile.formatCount
import com.mrboomdev.scrollix.features.search.formatFileSize
import com.mrboomdev.scrollix.ui.components.BytesImage
import com.mrboomdev.scrollix.ui.components.MediaImage
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.savedMediaFileName
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Corner radius of photos, videos and GIFs, matching Telegram's bubble. */
internal val MediaCornerRadius = 16.dp

/** Centered date-pill styling for informational service messages. */

@Composable
internal fun ServiceMessagePill(text: String) {
    if (text.isBlank()) return

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
internal fun UndiscoveredMessagesRow(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )

            Spacer(Modifier.size(8.dp))
        }

        Text(
            text = if (isLoading) {
                "Loading messages..."
            } else {
                "Undiscovered messages — tap or scroll to load"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
internal fun UnreadMessagesHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                text = "Unread messages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
internal fun BoxScope.MessageListOverlays(
    isLoadingMore: Boolean,
    showNewMessages: Boolean,
    showUnreadJump: Boolean,
    isAtBottom: Boolean,
    bottomInset: Dp,
    onScrollToUnread: () -> Unit,
    onScrollToLatest: () -> Unit
) {
    AnimatedVisibility(
        visible = isLoadingMore,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }

    // Floating jump back to the newest message while the list is scrolled up.
    AnimatedVisibility(
        visible = showNewMessages || !isAtBottom,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = bottomInset + 8.dp),
        enter = slideIn(tween(100)) { IntOffset(0, it.height) },
        exit = slideOut(tween(100)) { IntOffset(0, it.height) },
    ) {
        if (showNewMessages) {
            Button(onClick = onScrollToLatest) {
                Text("New messages")
            }
        } else {
            SmallFloatingActionButton(
                onClick = onScrollToLatest,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    modifier = Modifier.rotate(-90f),
                    imageVector = arrow_back_icon,
                    contentDescription = "Scroll to the latest message"
                )
            }
        }
    }

    // Jumps back to the first unread message while it is above the viewport.
    // The chip is centered so it never competes with the latest-message button
    // at the trailing edge, and it disappears for good once used on this
    // screen instead of following every scroll.
    AnimatedVisibility(
        visible = showUnreadJump,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomInset + 12.dp)
    ) {
        Button(onClick = onScrollToUnread) {
            Text("Unread messages")
        }
    }
}

internal sealed interface ScrollRequest {
    data class Message(val messageId: Long) : ScrollRequest
    data object Latest : ScrollRequest
    data object First : ScrollRequest
}

internal fun replyFor(
    message: ChatMessage,
    messagesById: Map<Long, ChatMessage>,
    loadedReplies: Map<Long, ChatMessage> = emptyMap()
): MessageReply? {
    val reply = message.replyTo ?: return null
    val original = messagesById[reply.messageId]
        ?: loadedReplies[reply.messageId]

    return if (original != null) {
        reply.copy(
            senderName = if (original.isOutgoing) "You" else original.senderName,
            text = original.replySnippet(),
            media = original.media
        )
    } else {
        reply.copy(text = reply.text.ifBlank { "Message" })
    }
}

internal fun ChatMessage.replySnippet(): String {
    if (text.isNotBlank()) return text
    if (poll != null) return "Poll"

    val media = media ?: return "Message"

    return when (media.kind) {
        MessageMediaKind.Photo -> "Photo"
        MessageMediaKind.Video -> "Video"
        MessageMediaKind.Animation -> "GIF"
        MessageMediaKind.Sticker -> "Sticker"
        MessageMediaKind.VideoNote -> "Video message"
        MessageMediaKind.VoiceNote -> "Voice message"
        MessageMediaKind.Audio -> media.fileName ?: "Audio"
        MessageMediaKind.File -> media.fileName ?: "File"
    }
}

@Composable
internal fun ReplyImageThumbnail(
    media: MessageMedia,
    session: AccountSession?
) {
    var failed by remember(media.previewFileId, media.minithumbnail) { mutableStateOf(false) }
    val minithumbnail = media.minithumbnail

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        MediaImage(
            session = session,
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { failed = true }
        )

        if (failed && minithumbnail != null) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
internal fun MessageImagePreview(
    media: MessageMedia,
    session: AccountSession?,
    onClick: () -> Unit
) {
    var isLoading by remember(media.previewFileId, media.minithumbnail) {
        mutableStateOf(true)
    }
    var failed by remember(media.previewFileId, media.minithumbnail) { mutableStateOf(false) }
    var revealed by remember(media.fileId, media.fileUniqueId) {
        mutableStateOf(!media.isSpoiler)
    }
    val minithumbnail = media.minithumbnail
    val hasPreview = minithumbnail != null || !failed

    // The preview bitmap can be cropped, so its own ratio must never resize
    // the bubble after it decodes. The dimensions that come with the message
    // are stable, which keeps the list from shifting under the user.
    val aspectRatio = if (media.width > 0 && media.height > 0) {
        (media.width.toFloat() / media.height).coerceIn(0.6f, 2.5f)
    } else {
        // A decoded preview can be cropped, so its ratio is not a stable
        // layout input. Keep one fixed fallback for this message forever.
        4f / 3f
    }

    Box(
        modifier = Modifier
            // Telegram sizes a photo from the bubble width, caps the height
            // at about a third of the screen and crops what does not fit
            // (`ChatMessageCell` caps the photo at `displaySize.y / 3` and
            // keeps the width), so no photo is ever letterboxed.
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 320.dp)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(MediaCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = hasPreview) {
                if (!revealed) revealed = true else onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (minithumbnail != null) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .loadingBlur(isLoading)
                    .mediaSpoilerBlur(!revealed, radius = 26.dp),
                contentScale = ContentScale.Crop
            )
        }

        MediaImage(
            session = session,
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .mediaSpoilerBlur(!revealed, radius = 26.dp),
            contentScale = ContentScale.Crop,
            onSuccess = {
                isLoading = false
                failed = false
            },
            onError = {
                isLoading = false
                failed = true
            }
        )

        if (!revealed && (minithumbnail != null || !isLoading)) {
            SpoilerOverlay(onReveal = { revealed = true })
        }

        if (failed && minithumbnail == null) {
            Text(
                text = "Image",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun RichBlockMediaPreview(
    media: MessageMedia,
    session: AccountSession?
) {
    when (media.kind) {
        MessageMediaKind.Photo -> MessageImagePreview(
            media = media,
            session = session
        ) {
            MediaViewer.showImage(media, session)
        }

        MessageMediaKind.Video -> ChatVideoPreview(media, session) {
            MediaViewer.showVideo(media, session)
        }

        MessageMediaKind.Animation -> ChatAnimationPreview(media, session) {
            MediaViewer.showVideo(media, session)
        }

        MessageMediaKind.Sticker -> ChatSticker(media, session)

        else -> Unit
    }
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
internal fun MessagePollCard(
    poll: MessagePollInfo,
    isOutgoing: Boolean,
    session: AccountSession?,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long> = emptySet(),
    /** True for the author's own open poll when the provider can close it. */
    canClose: Boolean = false,
    onOptionClick: (MessagePollOptionInfo) -> Unit,
    onRetractVote: () -> Unit,
    onClosePoll: (() -> Unit)? = null
) {
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val hasVotes = poll.totalVoterCount > 0 || poll.options.any { option -> option.isChosen }
    val showResults = poll.canSeeResults && (hasVotes || poll.isClosed)
    val canVote = !poll.isClosed && session != null
    val hasChosenOption = poll.options.any { option -> option.isChosen }
    val canRetractVote = canVote && hasChosenOption && !poll.isQuiz && poll.allowsRevoting

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            LocalContentColor provides contentColor
        ) {
            CustomEmojiMessageText(
                text = poll.question.ifBlank { "Poll" },
                emojis = poll.customEmojis,
                data = customEmojiData,
                adaptiveEmojis = customEmojiAdaptive
            )
        }

        Text(
            text = pollTypeLabel(poll),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor
        )

        poll.options.forEachIndexed { index, option ->
            PollOptionRow(
                option = option,
                showResults = showResults,
                isCorrect = poll.isQuiz && index in poll.correctOptionIds,
                customEmojiData = customEmojiData,
                customEmojiAdaptive = customEmojiAdaptive,
                session = session,
                accent = accent,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                onClick = if (canVote && !(poll.isQuiz && hasChosenOption)) {
                    { onOptionClick(option) }
                } else {
                    null
                }
            )
        }

        if (canRetractVote) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRetractVote)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = "Retract vote",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        if (canClose && !poll.isClosed && onClosePoll != null) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClosePoll)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = "Stop poll",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        poll.explanation?.takeIf { text -> text.isNotBlank() }?.let { explanation ->
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }

        Text(
            text = when {
                poll.totalVoterCount == 1 -> "1 vote"
                poll.totalVoterCount > 1 -> "${formatCount(poll.totalVoterCount)} votes"
                else -> "No votes yet"
            },
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor
        )
    }
}

@Composable
internal fun PollOptionRow(
    option: MessagePollOptionInfo,
    showResults: Boolean,
    isCorrect: Boolean,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession?,
    accent: Color,
    contentColor: Color,
    secondaryColor: Color,
    onClick: (() -> Unit)? = null
) {
    val fraction = (option.votePercentage / 100f).coerceIn(0f, 1f)
    val isHighlighted = option.isChosen || isCorrect

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.06f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(
                if (showResults && fraction > 0f) {
                    Modifier.drawBehind {
                        drawRect(
                            color = accent.copy(
                                alpha = if (option.isChosen) 0.28f else 0.16f
                            ),
                            size = Size(size.width * fraction, size.height)
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            option.media?.let { optionMedia ->
                PollOptionMedia(media = optionMedia, session = session)
            }

            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal
                ),
                LocalContentColor provides if (option.isChosen) accent else contentColor
            ) {
                CustomEmojiMessageText(
                    modifier = Modifier.weight(1f),
                    text = option.text,
                    emojis = option.customEmojis,
                    data = customEmojiData,
                    adaptiveEmojis = customEmojiAdaptive
                )
            }

            if (showResults) {
                Text(
                    text = "${option.votePercentage}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (option.isChosen) accent else secondaryColor
                )
            }

            if (isCorrect) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }
    }
}

@Composable
internal fun PollOptionMedia(
    media: MessageMedia,
    session: AccountSession?
) {
    if (media.kind == MessageMediaKind.Sticker) {
        var data by remember(media.fileId, session) { mutableStateOf<ByteArray?>(null) }

        LaunchedEffect(media.fileId, session) {
            data = session?.loadMediaBytes(
                fileId = media.fileId,
                uniqueId = media.stickerUniqueId ?: media.fileUniqueId
            )
        }

        val emoji = remember(
            media.fileId,
            data,
            media.stickerFormat,
            media.needsRepainting
        ) {
            DialogFolderEmoji(
                customEmojiId = (media.fileId ?: 0).toLong(),
                offset = 0,
                length = 0,
                fallback = "",
                needsRepainting = media.needsRepainting,
                format = when (media.stickerFormat) {
                    MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                    MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                    MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
                },
                data = data
            )
        }

        FolderEmojiImage(
            emoji = emoji,
            animate = true,
            size = 28.dp,
            color = Color.Unspecified,
            contentModifier = Modifier.size(28.dp)
        )

        return
    }

    var failed by remember(media.previewFileId, media.minithumbnail) { mutableStateOf(false) }
    val minithumbnail = media.minithumbnail

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        MediaImage(
            session = session,
            fileId = media.previewFileId ?: media.fileId,
            uniqueId = media.previewUniqueId ?: media.fileUniqueId,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { failed = true }
        )

        if (failed && minithumbnail != null) {
            BytesImage(
                bytes = minithumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

internal fun pollTypeLabel(poll: MessagePollInfo): String {
    val parts = mutableListOf<String>()

    parts += when {
        poll.isQuiz -> "Quiz"
        poll.isAnonymous -> "Anonymous poll"
        else -> "Public poll"
    }

    if (poll.allowsMultipleAnswers) parts += "Multiple answers"
    if (poll.allowsRevoting) parts += "Revoting"
    if (poll.isClosed) parts += "Final results"

    return parts.joinToString(" · ")
}

internal fun MessageMedia.isGalleryMedia(): Boolean {
    return when (kind) {
        MessageMediaKind.Photo,
        MessageMediaKind.Video,
        MessageMediaKind.Animation,
        MessageMediaKind.VideoNote -> true

        else -> false
    }
}

@Composable
internal fun NotSendableNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = "You can't send messages to this chat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
