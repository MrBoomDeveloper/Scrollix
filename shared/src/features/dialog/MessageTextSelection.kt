package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shows one message's text in a selectable block. The custom emoji in a bubble
 * are drawn as stickers, which cannot be copied, so this dialog shows the plain
 * text where a long-press selects single words and phrases.
 *
 * [onReply] receives the selected passage and its UTF-16 offset in the message,
 * which is what the provider needs to quote the same words for everyone.
 */
@Composable
internal fun MessageTextSelectionDialog(
    text: String,
    onDismiss: () -> Unit,
    onReply: ((text: String, position: Int) -> Unit)? = null
) {
    // A read-only text field exposes its selection, unlike SelectionContainer
    // which only draws the handles.
    val state = rememberTextFieldState(initialText = text)
    val selection = state.selection
    val selectedText = if (selection.collapsed) {
        ""
    } else {
        text.substring(selection.min, selection.max)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Select text") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (selectedText.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = selectedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (onReply != null) {
                    TextButton(
                        enabled = selectedText.isNotBlank(),
                        onClick = {
                            onReply(selectedText, selection.min)
                            onDismiss()
                        }
                    ) {
                        Text("Reply with quote")
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
