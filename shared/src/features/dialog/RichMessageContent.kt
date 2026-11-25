package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageRichBlock
import com.mrboomdev.scrollix.data.model.MessageRichTextStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Renders the blocks of a rich message inside a bubble.
 *
 * [media] is supplied by the dialog screen, because loading and displaying
 * attachments needs the session which the bubble only knows as an interface.
 */
@Composable
internal fun RichMessageContent(
    blocks: List<MessageRichBlock>,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    onMentionClick: ((String) -> Unit)? = null,
    onPhoneClick: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    media: (@Composable (MessageMedia) -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageRichBlock.Text -> RichBlockText(
                    block = block,
                    isOutgoing = isOutgoing,
                    onMentionClick = onMentionClick,
                    onPhoneClick = onPhoneClick,
                    onHashtagClick = onHashtagClick
                )

                is MessageRichBlock.Media -> Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    media?.invoke(block.media)

                    if (block.caption.isNotBlank()) {
                        Text(
                            text = block.caption,
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor(isOutgoing)
                        )
                    }
                }

                MessageRichBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(accentColor(isOutgoing).copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun RichBlockText(
    block: MessageRichBlock.Text,
    isOutgoing: Boolean,
    onMentionClick: ((String) -> Unit)?,
    onPhoneClick: ((String) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?
) {
    val accent = accentColor(isOutgoing)
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val annotated = remember(block, accent, onMentionClick, onPhoneClick, onHashtagClick) {
        buildRichBlockText(block, accent, onMentionClick, onPhoneClick, onHashtagClick)
    }

    val style = when (block.style) {
        MessageRichTextStyle.Title -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )

        MessageRichTextStyle.Header -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold
        )

        MessageRichTextStyle.Subheader -> MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold
        )

        MessageRichTextStyle.Paragraph -> MaterialTheme.typography.bodyLarge

        MessageRichTextStyle.Preformatted -> MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace
        )

        MessageRichTextStyle.Quote -> MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic
        )

        MessageRichTextStyle.Footer -> MaterialTheme.typography.labelMedium
    }

    val color = if (block.style == MessageRichTextStyle.Footer) {
        secondaryColor(isOutgoing)
    } else {
        contentColor
    }

    when (block.style) {
        MessageRichTextStyle.Quote -> Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.5f))
            )

            Text(
                text = annotated,
                style = style,
                color = color
            )
        }

        MessageRichTextStyle.Preformatted -> Text(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = if (isOutgoing) 0.12f else 0.28f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            text = annotated,
            style = style,
            color = color
        )

        else -> Text(
            text = annotated,
            style = style,
            color = color
        )
    }
}

private fun buildRichBlockText(
    block: MessageRichBlock.Text,
    accent: Color,
    onMentionClick: ((String) -> Unit)?,
    onPhoneClick: ((String) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?
): AnnotatedString = buildAnnotatedString {
    val offset = block.bullet?.let { bullet ->
        append(bullet)
        append("  ")
        length
    } ?: 0

    appendTextWithLinks(block.text, onMentionClick, onPhoneClick, onHashtagClick)

    block.spans.forEach { span ->
        val start = (span.start + offset).coerceIn(0, length)
        val end = (span.end + offset).coerceIn(start, length)
        if (start >= end) return@forEach

        addStyle(
            SpanStyle(
                color = if (span.url != null) accent else Color.Unspecified,

                fontWeight = if (span.isBold) {
                    FontWeight.Bold
                } else {
                    null
                },

                fontStyle = if (span.isItalic) {
                    FontStyle.Italic
                } else {
                    null
                },

                fontFamily = if (span.isMonospace) {
                    FontFamily.Monospace
                } else {
                    null
                },

                textDecoration = when {
                    span.isUnderlined && span.isStrikethrough -> TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )

                    span.isUnderlined -> TextDecoration.Underline
                    span.isStrikethrough -> TextDecoration.LineThrough
                    else -> null
                }
            ),
            start = start,
            end = end
        )
    }
}

@Composable
private fun accentColor(isOutgoing: Boolean): Color {
    return if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun secondaryColor(isOutgoing: Boolean): Color {
    return if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.secondary
    }
}
