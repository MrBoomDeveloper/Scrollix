package com.mrboomdev.scrollix.features.dialog.message

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Selectable message text. An outgoing bubble is drawn on the primary color,
 * where the default primary selection highlight disappears into the
 * background; those bubbles get a translucent on-primary highlight instead.
 */
@Composable
internal fun BubbleSelectionContainer(
    isOutgoing: Boolean,
    content: @Composable () -> Unit
) {
    val defaultColors = LocalTextSelectionColors.current

    val colors = if (isOutgoing) {
        TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.onPrimary,
            backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
        )
    } else {
        defaultColors
    }

    CompositionLocalProvider(LocalTextSelectionColors provides colors) {
        SelectionContainer(content = content)
    }
}
