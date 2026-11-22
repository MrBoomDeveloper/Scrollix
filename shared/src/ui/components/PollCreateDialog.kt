package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PollCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowsMultipleAnswers: Boolean
    ) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowsMultipleAnswers by remember { mutableStateOf(false) }

    val canCreate = question.isNotBlank() &&
        options.count { it.isNotBlank() } >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create poll") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    maxLines = 3
                )

                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = option,
                            onValueChange = { value ->
                                options = options.toMutableList().apply {
                                    this[index] = value
                                }
                            },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true
                        )

                        if (options.size > 2) {
                            TextButton(
                                onClick = {
                                    options = options.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    }
                                }
                            ) {
                                Text("✕")
                            }
                        }
                    }
                }

                TextButton(
                    enabled = options.size < 10,
                    onClick = { options = options + "" }
                ) {
                    Text("Add option")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Anonymous",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "Multiple answers",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = allowsMultipleAnswers,
                        onCheckedChange = { allowsMultipleAnswers = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canCreate,
                onClick = {
                    onCreate(
                        question.trim(),
                        options.map(String::trim).filter(String::isNotEmpty),
                        isAnonymous,
                        allowsMultipleAnswers
                    )
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
