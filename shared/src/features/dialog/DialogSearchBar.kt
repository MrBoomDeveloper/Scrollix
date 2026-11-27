package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.ui.icons.close_icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The in-chat search field and its filters. The state lives in
 * [DialogScreenViewModel], so the query, the filter and the results survive a
 * recomposition and a jump.
 */
@Composable
internal fun DialogSearchBar(ui: DialogScreenViewModel) = with(ui) {
    AnimatedVisibility(visible = searchOpen) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = Dp.Unspecified),
                    state = searchQuery,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    contentPadding = PaddingValues(),
                    placeholder = { Text("Search in chat") }
                )

                if (searchQuery.text.isNotBlank()) {
                    Text(
                        text = if (isSearchPending) {
                            "Searching..."
                        } else {
                            "${visibleMessages.size}\nresults"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(
                    onClick = {
                        searchOpen = false
                        searchQuery.clearText()
                    }
                ) {
                    Icon(
                        imageVector = close_icon,
                        contentDescription = null
                    )
                }
            }

            if (searchQuery.text.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DialogSearchFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = searchFilter == filter,
                            onClick = { setSearchFilter(filter) },
                            label = { Text(filter.label, maxLines = 1) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SearchDateFilter.entries.forEach { dateFilter ->
                        FilterChip(
                            selected = searchDateFilter == dateFilter,
                            onClick = { setSearchDateFilter(dateFilter) },
                            label = { Text(dateFilter.label, maxLines = 1) }
                        )
                    }
                }
            }

            // Only providers that can page past the first page offer the
            // action; the others keep returning the same page.
            if (
                searchQuery.text.isNotBlank() &&
                session?.supportsMessageSearchPaging == true &&
                rawSearchResults.isNotEmpty()
            ) {
                TextButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enabled = !isSearchPending && !isLoadingMoreSearch && !searchReachedEnd,
                    onClick = { loadMoreSearchResults() }
                ) {
                    Text(
                        text = when {
                            searchReachedEnd -> "End of results"
                            isLoadingMoreSearch -> "Loading more…"
                            else -> "Load more results"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
