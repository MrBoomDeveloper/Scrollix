package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat

private val EmojiSize = 20.sp
private const val TrailingContentId = "message_footer"

/** Zero width space, required because [appendInlineContent] rejects empty alternate text. */
private const val TrailingContentAlternateText = "\u200B"

@Composable
internal fun CustomEmojiMessageText(
    modifier: Modifier = Modifier,
    text: String,
    lineHeight: TextUnit = TextUnit.Unspecified,
    emojis: List<CustomEmojiSpan>,
    data: Map<Long, ByteArray>,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    trailingContent: (@Composable () -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onPhoneClick: ((String) -> Unit)? = null
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
        trailingContent == null &&
        onMentionClick == null &&
        onPhoneClick == null
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

    val annotated = buildAnnotatedString {
        if (spans.isEmpty()) {
            // Most messages have no custom emoji, so this is the path that
            // decides whether mentions, links and phone numbers are tappable.
            appendTextWithLinks(text, onMentionClick, onPhoneClick)
        } else {
            var index = 0

            for ((start, end, emojiId) in spans) {
                if (start < index) continue

                appendTextWithLinks(text.substring(index, start), onMentionClick, onPhoneClick)
                appendInlineContent(
                    alternateText = text.substring(start, end),
                    id = emojiId.toString()
                )
                index = end
            }

            appendTextWithLinks(text.substring(index), onMentionClick, onPhoneClick)
        }

        if (trailingContent != null) {
            appendInlineContent(
                alternateText = TrailingContentAlternateText,
                id = TrailingContentId
            )
        }
    }

    val inlineContent = spans.associate { span ->
        span.emojiId.toString() to inlineEmoji(
            id = span.emojiId,
            bytes = data.getValue(span.emojiId)
        )
    }.toMutableMap()

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
internal fun customEmojiModel(id: Long, bytes: ByteArray): DialogFolderEmoji {
    return DialogFolderEmoji(
        customEmojiId = id,
        offset = 0,
        length = 0,
        fallback = "",
        format = detectEmojiFormat(bytes),
        data = bytes
    )
}

private fun inlineEmoji(id: Long, bytes: ByteArray): InlineTextContent {
    return InlineTextContent(
        Placeholder(
            width = EmojiSize,
            height = EmojiSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
        )
    ) {
        val emoji = remember(id, bytes) { customEmojiModel(id, bytes) }

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
