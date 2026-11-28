package com.mrboomdev.scrollix.features.search

import com.mrboomdev.scrollix.features.dialog.search.DialogSearchFilter
import com.mrboomdev.scrollix.features.dialog.search.SearchDateFilter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone

/**
 * Every filter of the global message search in one dialog: the account, the
 * media type and the date window. They narrow the page the providers already
 * returned, so applying them never starts another search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchFiltersDialog(
    filter: DialogSearchFilter,
    range: MessageDateRange,
    accounts: List<Pair<String, String>>,
    accountId: String?,
    onDismiss: () -> Unit,
    onApply: (DialogSearchFilter, MessageDateRange, String?) -> Unit
) {
    var media by remember(filter) { mutableStateOf(filter) }
    var preset by remember(range) { mutableStateOf(range.preset) }
    var fromText by remember(range) { mutableStateOf(range.fromDate) }
    var toText by remember(range) { mutableStateOf(range.toDate) }
    var account by remember(accountId) { mutableStateOf(accountId) }

    val zone = remember { TimeZone.currentSystemDefault() }

    val fromMillis = parseDateBoundMillis(fromText, endOfDay = false, zone)
    val toMillis = parseDateBoundMillis(toText, endOfDay = true, zone)
    val customRange = fromText.isNotBlank() || toText.isNotBlank()

    val invalidRange = isInvalidDateBound(fromText, endOfDay = false, zone) ||
        isInvalidDateBound(toText, endOfDay = true, zone) ||
        (fromMillis != null && toMillis != null && fromMillis > toMillis)

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text("Search filters") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (accounts.size > 1) {
                    FilterSectionTitle("Accounts")

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = account == null,
                            onClick = { account = null },
                            label = { Text("All accounts") }
                        )

                        accounts.forEach { option ->
                            FilterChip(
                                selected = account == option.first,
                                onClick = { account = option.first },
                                label = { Text(option.second, maxLines = 1) }
                            )
                        }
                    }
                }

                FilterSectionTitle("Type")

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogSearchFilter.entries.forEach { entry ->
                        FilterChip(
                            selected = media == entry,
                            onClick = { media = entry },
                            label = { Text(entry.label) }
                        )
                    }
                }

                FilterSectionTitle("Date")

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchDateFilter.entries.forEach { entry ->
                        FilterChip(
                            selected = !customRange && preset == entry,
                            onClick = {
                                preset = entry
                                fromText = ""
                                toText = ""
                            },
                            label = { Text(entry.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = fromText,
                        onValueChange = { value ->
                            fromText = value
                            preset = SearchDateFilter.AnyTime
                        },
                        label = { Text("From") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        isError = isInvalidDateBound(fromText, endOfDay = false, zone)
                    )

                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = toText,
                        onValueChange = { value ->
                            toText = value
                            preset = SearchDateFilter.AnyTime
                        },
                        label = { Text("To") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        isError = isInvalidDateBound(toText, endOfDay = true, zone)
                    )
                }

                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = if (invalidRange) {
                        "Use YYYY-MM-DD, and keep From not later than To."
                    } else {
                        "Leave a field empty for an open-ended range."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalidRange) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !invalidRange,
                onClick = {
                    onApply(
                        media,
                        MessageDateRange(
                            preset = if (customRange) {
                                SearchDateFilter.AnyTime
                            } else {
                                preset
                            },
                            fromDate = fromText.trim(),
                            toDate = toText.trim()
                        ),
                        account
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    media = DialogSearchFilter.All
                    preset = SearchDateFilter.AnyTime
                    fromText = ""
                    toText = ""
                    account = null
                }
            ) {
                Text("Reset")
            }
        }
    )
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
