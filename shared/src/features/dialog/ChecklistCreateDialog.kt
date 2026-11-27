package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What the checklist editor hands back to the chat screen. */
internal data class ChecklistDraft(
    val title: String,
    val tasks: List<String>,
    val othersCanAddTasks: Boolean = false,
    val othersCanMarkTasksAsDone: Boolean = true
)

/**
 * Composes a new checklist, or collects the tasks to append to an existing
 * one. Blank rows are dropped on confirm, so the provider only sees tasks that
 * were actually typed.
 */
@Composable
internal fun ChecklistCreateDialog(
    header: String,
    confirmLabel: String,
    onConfirm: (ChecklistDraft) -> Unit,
    onDismiss: () -> Unit,
    /** False when adding to an existing list, whose title is already fixed. */
    allowTitleEdit: Boolean = true,
    showOptions: Boolean = true
) {
    var title by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf("") }
    var othersCanAddTasks by remember { mutableStateOf(false) }
    var othersCanMarkTasksAsDone by remember { mutableStateOf(true) }

    val cleanTasks = tasks.map(String::trim).filter(String::isNotEmpty)
    val canConfirm = cleanTasks.isNotEmpty() &&
        (!allowTitleEdit || title.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(header) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allowTitleEdit) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true
                    )
                }

                tasks.forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = task,
                            onValueChange = { value -> tasks[index] = value },
                            placeholder = { Text("Task ${index + 1}") },
                            singleLine = true
                        )

                        // A list needs at least one row, so the last one stays.
                        if (tasks.size > 1) {
                            TextButton(onClick = { tasks.removeAt(index) }) {
                                Text("Remove")
                            }
                        }
                    }
                }

                TextButton(onClick = { tasks.add("") }) {
                    Text("Add task")
                }

                if (showOptions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = othersCanAddTasks,
                            onCheckedChange = { checked -> othersCanAddTasks = checked }
                        )

                        Text(
                            modifier = Modifier.padding(start = 4.dp),
                            text = "Others can add tasks",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = othersCanMarkTasksAsDone,
                            onCheckedChange = { checked -> othersCanMarkTasksAsDone = checked }
                        )

                        Text(
                            modifier = Modifier.padding(start = 4.dp),
                            text = "Others can mark tasks done",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        ChecklistDraft(
                            title = title.trim(),
                            tasks = cleanTasks,
                            othersCanAddTasks = othersCanAddTasks,
                            othersCanMarkTasksAsDone = othersCanMarkTasksAsDone
                        )
                    )
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
