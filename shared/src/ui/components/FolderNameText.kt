package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji

@Composable
fun FolderNameText(
    folder: DialogFolder,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    emojiSize: Dp = 16.dp,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val emojis = folder.customEmojis

    if (emojis.isEmpty()) {
        Text(
            text = folder.name,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    val annotatedString = remember(folder.name, emojis) {
        buildAnnotatedString {
            var cursor = 0

            for (emoji in emojis) {
                if (emoji.offset > cursor) {
                    append(folder.name.substring(cursor, emoji.offset))
                }

                appendInlineContent(emoji.inlineContentId, emoji.fallback)
                cursor = (emoji.offset + emoji.length).coerceAtMost(folder.name.length)
            }

            if (cursor < folder.name.length) {
                append(folder.name.substring(cursor))
            }
        }
    }
    val placeholderSize = with(LocalDensity.current) { emojiSize.toSp() }
    val inlineContent = remember(
        folder.animateCustomEmoji,
        emojis,
        emojiSize,
        placeholderSize,
        color
    ) {
        emojis.associate { emoji ->
            emoji.inlineContentId to InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderSize,
                    height = placeholderSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                FolderEmojiImage(
                    emoji = emoji,
                    animate = folder.animateCustomEmoji,
                    size = emojiSize,
                    color = color
                )
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent
    )
}

private val DialogFolderEmoji.inlineContentId: String
    get() = "folder_emoji_${customEmojiId}_$offset"
