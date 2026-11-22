package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

data class ReorderItem(
    val key: String,
    val title: String
)

/** A compact list whose rows can be dragged after a long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderDialog(
    title: String,
    items: List<ReorderItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var order by remember(items) { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 52.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(order, key = { item -> item.key }) { item ->
                    val isDragging = item.key == draggingKey

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                            }
                            .animateItem()
                            .pointerInput(item.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = item.key
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y

                                        val from = order.indexOfFirst {
                                            it.key == item.key
                                        }
                                        if (from == -1) return@detectDragGesturesAfterLongPress

                                        val to = (from + (dragOffset / rowHeightPx).roundToInt())
                                            .coerceIn(0, order.lastIndex)

                                        if (to != from) {
                                            order = order.toMutableList().apply {
                                                add(to, removeAt(from))
                                            }
                                            dragOffset -= (to - from) * rowHeightPx
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    }
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
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
            TextButton(onClick = { onConfirm(order.map { it.key }) }) {
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
