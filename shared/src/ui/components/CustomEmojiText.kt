package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat

private val EmojiSize = 20.sp

@Composable
internal fun CustomEmojiMessageText(
    modifier: Modifier = Modifier,
    text: String,
    lineHeight: TextUnit = TextUnit.Unspecified,
    emojis: List<CustomEmojiSpan>,
    data: Map<Long, ByteArray>
) {
    val spans = emojis
        .filter { span ->
            span.emojiId in data &&
                span.start >= 0 &&
                span.end <= text.length &&
                span.start < span.end
        }
        .sortedBy { it.start }

    if (spans.isEmpty()) {
        Text(
            modifier = modifier,
            lineHeight = lineHeight,
            text = text
        )

        return
    }

    val annotated = buildAnnotatedString {
        var index = 0

        for ((start, end, emojiId) in spans) {
            if (start < index) continue

            appendTextWithLinks(text.substring(index, start))
            appendInlineContent(
                alternateText = text.substring(start, end),
                id = emojiId.toString()
            )
            index = end
        }

        appendTextWithLinks(text.substring(index))
    }

    val inlineContent = spans.associate { span ->
        span.emojiId.toString() to inlineEmoji(
            id = span.emojiId,
            bytes = data.getValue(span.emojiId)
        )
    }

    Text(
        modifier = modifier,
        text = annotated,
        inlineContent = inlineContent
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
        val emoji = remember(id, bytes) {
            DialogFolderEmoji(
                customEmojiId = id,
                offset = 0,
                length = 0,
                fallback = "",
                format = detectEmojiFormat(bytes),
                data = bytes
            )
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

        bytes.size > 4 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() -> DialogFolderEmojiFormat.Webm

        else -> DialogFolderEmojiFormat.Webp
    }
}
