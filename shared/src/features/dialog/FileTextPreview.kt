package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageMedia

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Text files larger than this are previewed up to the limit only. */
internal const val TextPreviewByteLimit = 256 * 1024

private val PreviewableMimeTypes = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-yaml",
    "application/yaml",
    "application/toml"
)

private val PreviewableExtensions = setOf(
    "txt", "md", "markdown", "json", "xml", "csv", "log", "ini", "yml",
    "yaml", "toml", "kt", "kts", "java", "js", "ts", "html", "css", "svg"
)

/**
 * Whether the file can be shown as text inside the app. Only the type is
 * checked: the bytes are loaded when the preview is actually opened.
 */
internal fun isTextPreviewable(media: MessageMedia): Boolean {
    val mime = media.mimeType?.trim()?.lowercase().orEmpty()

    if (mime.startsWith("text/")) return true
    if (mime in PreviewableMimeTypes) return true

    val extension = media.fileName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()

    return extension in PreviewableExtensions
}

/**
 * Decodes the beginning of a text file. The cut is byte-based, so one broken
 * multi-byte character at the edge becomes the decoder's replacement symbol
 * instead of failing the whole preview.
 */
internal fun previewText(
    bytes: ByteArray,
    limitBytes: Int = TextPreviewByteLimit
): String {
    if (bytes.isEmpty()) return ""

    val limit = limitBytes.coerceAtLeast(1)
    val slice = if (bytes.size <= limit) bytes else bytes.copyOfRange(0, limit)
    val text = slice.decodeToString()

    return if (bytes.size > limit) {
        text + "\n\n… preview truncated at ${limit / 1024} KB"
    } else {
        text
    }
}

/** Read-only text preview of a file, with copying through the system handles. */
@Composable
internal fun FileTextPreviewDialog(
    fileName: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = fileName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            SelectionContainer {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    text = text.ifEmpty { "This file is empty." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
