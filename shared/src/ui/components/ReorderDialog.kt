package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.features.home.key

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class ReorderItem(
    val key: String,
    val title: String
)

/**
 * A compact list whose rows can be dragged after a long press.
 *
 * The gesture handling, item animation and edge auto-scroll come from
 * Reorderable (klibs.io); this composable only owns the order and the dialog
 * chrome, so behavior fixes arrive with the library instead of this file.
 */
@Composable
fun ReorderDialog(
    title: String,
    items: List<ReorderItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var order by remember(items) { mutableStateOf(items) }
    val listState = rememberLazyListState()

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                state = listState
            ) {
                items(order, key = { item -> item.key }) { item ->
                    ReorderableItem(reorderState, key = item.key) { isDragging ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .longPressDraggableHandle()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                text = "≡",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order.map { item -> item.key }) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
