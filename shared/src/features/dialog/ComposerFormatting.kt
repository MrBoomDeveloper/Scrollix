package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageTextBlock
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.ui.icons.link_icon

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * The formatting a composer toolbar can apply to a selection. Every entry maps
 * to exactly one [MessageTextFormat] flag, so toggling one style never changes
 * another.
 */
internal enum class ComposerTextFormat(val label: String, val description: String) {
    Bold("B", "Bold"),
    Italic("I", "Italic"),
    Monospace("M", "Monospace"),
    Underline("U", "Underline"),
    Strikethrough("S", "Strikethrough"),
    Spoiler("Spoiler", "Spoiler"),
    Quote("Quote", "Quote block"),
    Code("Code", "Code block")
}

private fun MessageTextFormat.matches(style: ComposerTextFormat): Boolean {
    return when (style) {
        ComposerTextFormat.Bold -> bold
        ComposerTextFormat.Italic -> italic
        ComposerTextFormat.Monospace -> monospace
        ComposerTextFormat.Underline -> underline
        ComposerTextFormat.Strikethrough -> strikethrough
        ComposerTextFormat.Spoiler -> spoiler
        ComposerTextFormat.Quote -> block == MessageTextBlock.Quote
        ComposerTextFormat.Code -> block == MessageTextBlock.Code
    }
}

private fun formatFor(range: TextRange, style: ComposerTextFormat): MessageTextFormat {
    return MessageTextFormat(
        start = range.min,
        end = range.max,
        bold = style == ComposerTextFormat.Bold,
        italic = style == ComposerTextFormat.Italic,
        monospace = style == ComposerTextFormat.Monospace,
        underline = style == ComposerTextFormat.Underline,
        strikethrough = style == ComposerTextFormat.Strikethrough,
        spoiler = style == ComposerTextFormat.Spoiler,
        block = when (style) {
            ComposerTextFormat.Quote -> MessageTextBlock.Quote
            ComposerTextFormat.Code -> MessageTextBlock.Code
            else -> null
        }
    )
}

/** Whether [spans] together cover every character of [range]. */
private fun isCovered(spans: List<MessageTextFormat>, range: TextRange): Boolean {
    if (range.collapsed) return false

    var cursor = range.min

    spans.sortedBy { span -> span.start }.forEach { span ->
        if (span.start > cursor) return false
        if (span.end > cursor) cursor = span.end
        if (cursor >= range.max) return true
    }

    return cursor >= range.max
}

/** Removes [range] from the matching spans, keeping the parts around it. */
private fun List<MessageTextFormat>.subtract(
    range: TextRange,
    style: ComposerTextFormat
): List<MessageTextFormat> {
    return flatMap { span ->
        if (!span.matches(style) || span.end <= range.min || span.start >= range.max) {
            return@flatMap listOf(span)
        }

        buildList {
            if (span.start < range.min) {
                add(span.copy(end = range.min))
            }

            if (span.end > range.max) {
                add(span.copy(start = range.max))
            }
        }
    }
}

/**
 * Applies or removes [style] over the selection. A selection that is already
 * fully styled loses the style; anything else gains it, matching how the
 * Telegram composer treats a second tap on a format button.
 */
internal fun List<MessageTextFormat>.toggleFormatting(
    selection: TextRange,
    style: ComposerTextFormat
): List<MessageTextFormat> {
    if (selection.collapsed) return this

    val range = TextRange(selection.min, selection.max)
    val matching = filter { span -> span.matches(style) }

    return if (isCovered(matching, range)) {
        subtract(range, style)
    } else {
        this + formatFor(range, style)
    }.mergedFormatting()
}

/** Links the selection to [url], or removes the link when it is blank. */
internal fun List<MessageTextFormat>.applyLink(
    selection: TextRange,
    url: String
): List<MessageTextFormat> {
    if (selection.collapsed) return this

    val cleaned = url.trim()
    val unlinked = filterNot { span ->
        span.url != null &&
            span.end > selection.min &&
            span.start < selection.max
    }

    if (cleaned.isEmpty()) return unlinked.mergedFormatting()

    return (unlinked + MessageTextFormat(
        start = selection.min,
        end = selection.max,
        url = cleaned
    )).mergedFormatting()
}

/** The link that covers [selection], so the dialog can preselect it. */
internal fun List<MessageTextFormat>.linkAt(selection: TextRange): String? {
    val position = selection.min

    return firstOrNull { span ->
        span.url != null && span.covers(if (selection.collapsed) position else selection.min)
    }?.url
}

/** Whether the whole selection already uses [style]. */
internal fun List<MessageTextFormat>.isStyleActive(
    selection: TextRange,
    style: ComposerTextFormat
): Boolean {
    val matching = filter { span -> span.matches(style) }

    return if (selection.collapsed) {
        matching.any { span -> span.covers(selection.min) }
    } else {
        isCovered(matching, TextRange(selection.min, selection.max))
    }
}

/** Merges touching spans of the same style so toggling does not pile them up. */
internal fun List<MessageTextFormat>.mergedFormatting(): List<MessageTextFormat> {
    val result = mutableListOf<MessageTextFormat>()

    filterNot { span -> span.isEmpty || span.isPlain }
        .sortedWith(compareBy({ span -> span.start }, { span -> span.end }))
        .forEach { span ->
            val last = result.lastOrNull()

            if (last != null && last.sameStyle(span) && span.start <= last.end) {
                result[result.lastIndex] = last.copy(end = maxOf(last.end, span.end))
            } else {
                result.add(span)
            }
        }

    return result
}

/**
 * Shifts formatting after the composer text changed. A style that contains the
 * edit point grows with inserted text and shrinks with removed text, so typing
 * inside a bold word keeps it bold instead of dropping the style.
 */
internal fun List<MessageTextFormat>.adjustFormatting(
    oldText: String,
    newText: String
): List<MessageTextFormat> {
    if (isEmpty() || oldText == newText) return this

    var prefix = 0
    while (
        prefix < oldText.length &&
        prefix < newText.length &&
        oldText[prefix] == newText[prefix]
    ) {
        prefix++
    }

    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    val removed = oldText.length - prefix - suffix
    val inserted = newText.length - prefix - suffix
    val delta = inserted - removed
    val editEnd = prefix + removed

    return mapNotNull { span ->
        when {
            span.end <= prefix -> span

            span.start >= editEnd -> span.copy(
                start = span.start + delta,
                end = span.end + delta
            )

            else -> {
                val start = if (span.start <= prefix) span.start else prefix
                val end = if (span.end >= editEnd) {
                    span.end + delta
                } else {
                    prefix + inserted
                }

                span.copy(
                    start = start.coerceIn(0, newText.length),
                    end = end.coerceIn(start, newText.length)
                )
            }
        }
    }.filterNot { span -> span.isEmpty }
}

/**
 * Turns [text] and its formatting into a styled string for the composer. The
 * spoiler text stays readable here, because it is still being edited.
 */
internal fun List<MessageTextFormat>.toComposerText(
    text: String,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        forEach { format ->
            val start = format.start.coerceIn(0, text.length)
            val end = format.end.coerceIn(start, text.length)

            if (start >= end) return@forEach

            addStyle(
                style = format.spanStyle(linkColor = linkColor, spoilerHidden = false),
                start = start,
                end = end
            )
        }
    }
}

/**
 * The visual style of one formatting range. [spoilerHidden] hides the glyphs of
 * a spoiler behind a solid block until the reader taps it.
 */
internal fun MessageTextFormat.spanStyle(
    linkColor: Color? = null,
    spoilerColor: Color = Color.Gray,
    spoilerHidden: Boolean = false,
    blockColor: Color? = null
): SpanStyle {
    val decorations = buildList {
        if (underline || url != null) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }

    val decoration = when (decorations.size) {
        0 -> null
        1 -> decorations.single()
        else -> TextDecoration.combine(decorations)
    }

    return SpanStyle(
        color = when {
            spoilerHidden -> Color.Transparent
            url != null -> linkColor ?: Color.Unspecified
            else -> Color.Unspecified
        },
        background = when {
            spoilerHidden -> spoilerColor
            spoiler -> spoilerColor.copy(alpha = 0.25f)
            block != null -> blockColor ?: Color.Unspecified
            else -> Color.Unspecified
        },
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = if (monospace || block == MessageTextBlock.Code) {
            FontFamily.Monospace
        } else {
            null
        },
        textDecoration = decoration
    )
}

/**
 * The formatting toolbar of the composer. Buttons reflect the style of the
 * selection, and a tap on one applies or removes that single style.
 */
@Composable
internal fun ComposerFormattingBar(
    formatting: List<MessageTextFormat>,
    selection: TextRange,
    onToggle: (ComposerTextFormat) -> Unit,
    onEditLink: () -> Unit,
    onClearFormatting: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComposerTextFormat.entries.forEach { style ->
            val active = formatting.isStyleActive(selection, style)

            FilterChip(
                selected = active,
                onClick = { onToggle(style) },
                label = {
                    Text(
                        text = style.label,
                        fontWeight = if (style == ComposerTextFormat.Bold) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        fontStyle = if (style == ComposerTextFormat.Italic) {
                            FontStyle.Italic
                        } else {
                            FontStyle.Normal
                        },
                        fontFamily = if (style == ComposerTextFormat.Monospace) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        },
                        textDecoration = when (style) {
                            ComposerTextFormat.Underline -> TextDecoration.Underline
                            ComposerTextFormat.Strikethrough -> TextDecoration.LineThrough
                            else -> null
                        }
                    )
                }
            )
        }

        FilterChip(
            selected = formatting.linkAt(selection) != null,
            onClick = onEditLink,
            label = { Text("Link") },
            leadingIcon = {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = link_icon,
                    contentDescription = null
                )
            }
        )

        // "Send without formatting": the text stays, the styles are dropped
        // right before the message is queued.
        if (formatting.isNotEmpty()) {
            FilterChip(
                selected = false,
                onClick = onClearFormatting,
                label = { Text("Plain text") }
            )
        }
    }
}

/**
 * Asks for the target of a link on the current selection. The field starts
 * with the existing link, so opening the dialog on a linked range edits it.
 */
@Composable
internal fun ComposerLinkDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Link") },
        text = {
            Column(Modifier.width(360.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { value -> url = value },
                    singleLine = true,
                    label = { Text("URL") },
                    placeholder = { Text("https://") }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onConfirm(url.trim()) }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onConfirm("") }) {
                    Text("Remove")
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
