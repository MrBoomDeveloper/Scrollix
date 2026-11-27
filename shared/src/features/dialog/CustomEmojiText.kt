package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DeepLinkHandler
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.features.home.FolderEmojiImage
import com.mrboomdev.scrollix.ui.components.AvatarImage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EmojiSize = 20.sp
private val MentionAvatarSize = 18.sp
private val MentionAvatarSizeDp = 18.dp
private const val TrailingContentId = "message_footer"
private const val MentionAvatarIdPrefix = "mention-avatar:"

/** Zero width space, required because [appendInlineContent] rejects empty alternate text. */
private const val TrailingContentAlternateText = "\u200B"

@Composable
internal fun CustomEmojiMessageText(
    modifier: Modifier = Modifier,
    text: String,
    lineHeight: TextUnit = TextUnit.Unspecified,
    emojis: List<CustomEmojiSpan>,
    formatting: List<MessageTextFormat> = emptyList(),
    data: Map<Long, ByteArray>,
    adaptiveEmojis: Set<Long> = emptySet(),
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    trailingContent: (@Composable () -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onPhoneClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    mentionAvatars: Map<String, Avatar> = emptyMap(),
    /** Query of the active in-chat search; its matches are marked. */
    highlightQuery: String? = null
) {
    val spans = emojis
        .filter { span ->
            span.emojiId in data &&
                span.start >= 0 &&
                span.end <= text.length &&
                span.start < span.end
        }
        .sortedBy { it.start }

    if (
        spans.isEmpty() &&
        formatting.isEmpty() &&
        trailingContent == null &&
        onMentionClick == null &&
        onPhoneClick == null &&
        onHashtagClick == null &&
        mentionAvatars.isEmpty() &&
        highlightQuery.isNullOrBlank()
    ) {
        Text(
            modifier = modifier,
            style = style,
            color = color,
            lineHeight = lineHeight,
            maxLines = maxLines,
            overflow = overflow,
            text = text
        )

        return
    }

    /** Natural width of [trailingContent], measured during layout. */
    var trailingWidth by remember { mutableStateOf(0) }

    // A spoiler is hidden until the reader taps it; the state is per message
    // and resets when a different text is rendered in this slot.
    var revealedSpoilers by remember(text) { mutableStateOf(false) }

    val annotated = buildAnnotatedString {
        if (spans.isEmpty()) {
            // Most messages have no custom emoji, so this is the path that
            // decides whether mentions, links and phone numbers are tappable.
            appendTextWithLinks(
                text,
                onMentionClick,
                onPhoneClick,
                onHashtagClick,
                mentionAvatars
            )
        } else {
            var index = 0

            for ((start, end, emojiId) in spans) {
                if (start < index) continue

                appendTextWithLinks(
                    text.substring(index, start),
                    onMentionClick,
                    onPhoneClick,
                    onHashtagClick,
                    mentionAvatars
                )
                appendInlineContent(
                    alternateText = text.substring(start, end),
                    id = emojiId.toString()
                )
                index = end
            }

            appendTextWithLinks(
                text.substring(index),
                onMentionClick,
                onPhoneClick,
                onHashtagClick,
                mentionAvatars
            )
        }

        if (trailingContent != null) {
            appendInlineContent(
                alternateText = TrailingContentAlternateText,
                id = TrailingContentId
            )
        }

        // Marks are added before the formatting spans so a hidden spoiler keeps
        // its own background instead of being revealed by the highlight.
        val highlightRanges = searchHighlightRanges(text, highlightQuery.orEmpty())

        if (highlightRanges.isNotEmpty()) {
            val highlightStyle = SpanStyle(
                background = MaterialTheme.colorScheme.tertiaryContainer
            )

            highlightRanges.forEach { range ->
                addStyle(highlightStyle, start = range.first, end = range.last + 1)
            }
        }

        formatting.forEach { format ->
            val start = format.start.coerceIn(0, text.length)
            val end = format.end.coerceIn(start, text.length)

            if (start >= end) return@forEach

            val hidden = format.spoiler && !revealedSpoilers

            addStyle(
                style = format.spanStyle(
                    linkColor = MaterialTheme.colorScheme.primary,
                    spoilerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    spoilerHidden = hidden,
                    blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                start = start,
                end = end
            )

            format.url?.takeIf { url -> url.isNotBlank() }?.let { url ->
                // Telegram links stay inside the app, the same way the
                // auto-detected links of a message do.
                if (DeepLinkHandler.isTelegramLink(url)) {
                    addLink(
                        clickable = LinkAnnotation.Clickable(
                            tag = "deep-link:$url",
                            linkInteractionListener = { DeepLinkHandler.publish(url) }
                        ),
                        start = start,
                        end = end
                    )
                } else {
                    addLink(
                        url = LinkAnnotation.Url(url = url),
                        start = start,
                        end = end
                    )
                }
            }

            if (hidden) {
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = "spoiler:$start:$end",
                        linkInteractionListener = { revealedSpoilers = true }
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }

    val inlineContent = spans.associate { span ->
        span.emojiId.toString() to inlineEmoji(
            id = span.emojiId,
            bytes = data.getValue(span.emojiId),
            needsRepainting = span.emojiId in adaptiveEmojis
        )
    }.toMutableMap()

    mentionAvatars.forEach { (username, avatar) ->
        inlineContent["$MentionAvatarIdPrefix$username"] = InlineTextContent(
            Placeholder(
                width = MentionAvatarSize,
                height = MentionAvatarSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            AvatarImage(avatar = avatar, size = MentionAvatarSizeDp)
        }
    }

    if (trailingContent != null) {
        inlineContent[TrailingContentId] = inlineTrailingContent(
            widthPx = trailingWidth,
            height = if (lineHeight != TextUnit.Unspecified) lineHeight else EmojiSize,
            onMeasured = { width -> trailingWidth = width }
        ) {
            trailingContent()
        }
    }

    Text(
        modifier = modifier,
        style = style,
        color = color,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        text = annotated,
        inlineContent = inlineContent
    )
}

/**
 * Wraps [content] into an inline placeholder which takes exactly as much space as
 * the content needs and lets every gesture through to the bubble this text belongs to.
 *
 * The placeholder width must be known up front, while the content is measured by
 * the text layout, so the width is measured once and fed back into the placeholder.
 */
@Composable
private fun inlineTrailingContent(
    widthPx: Int,
    height: TextUnit,
    onMeasured: (Int) -> Unit,
    content: @Composable () -> Unit
): InlineTextContent {
    val density = LocalDensity.current

    return InlineTextContent(
        Placeholder(
            width = with(density) { widthPx.toSp() },
            height = height,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) {
        // Deliberately no clickable/pointerInput here, otherwise the footer would
        // swallow taps and long presses instead of letting them reach the bubble.
        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .onSizeChanged { onMeasured(it.width) }
        ) {
            content()
        }
    }
}

/** Builds a renderable custom emoji out of the raw sticker bytes. */
internal fun customEmojiModel(
    id: Long,
    bytes: ByteArray,
    needsRepainting: Boolean = false
): DialogFolderEmoji {
    return DialogFolderEmoji(
        customEmojiId = id,
        offset = 0,
        length = 0,
        fallback = "",
        needsRepainting = needsRepainting,
        format = detectEmojiFormat(bytes),
        data = bytes
    )
}

private fun inlineEmoji(
    id: Long,
    bytes: ByteArray,
    needsRepainting: Boolean
): InlineTextContent {
    return InlineTextContent(
        Placeholder(
            width = EmojiSize,
            height = EmojiSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) {
        val emoji = remember(id, bytes, needsRepainting) {
            customEmojiModel(id, bytes, needsRepainting)
        }

        FolderEmojiImage(
            emoji = emoji,
            animate = true,
            size = 20.dp,
            color = Color.Unspecified,
            contentModifier = Modifier.size(20.dp)
        )
    }
}

private fun detectEmojiFormat(bytes: ByteArray): DialogFolderEmojiFormat {
    return when {
        bytes.size > 2 &&
            bytes[0] == 0x1F.toByte() &&
            bytes[1] == 0x8B.toByte() -> DialogFolderEmojiFormat.Tgs

        bytes.size > 11 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte() -> DialogFolderEmojiFormat.Webp

        bytes.size > 3 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte() -> DialogFolderEmojiFormat.Webm

        else -> DialogFolderEmojiFormat.Webp
    }
}
