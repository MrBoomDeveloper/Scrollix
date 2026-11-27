package com.mrboomdev.scrollix.features.drafts

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Every typed-but-unsent message, grouped by the account it belongs to. The
 * drafts come from the dialog list, so they reflect what the provider reported
 * with the latest chat refresh; opening a row returns to that chat.
 */
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onOpenDialog: (accountId: String, providerDialogId: String) -> Unit
) {
    val entries by ApiManager.dialogs.collectAsState()
    val drafts = entries.filter { entry -> entry.dialog.draftText.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = arrow_back_icon,
                    contentDescription = "Back"
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = "Drafts",
                style = MaterialTheme.typography.titleLarge
            )

            if (drafts.isNotEmpty()) {
                Text(
                    text = drafts.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        if (drafts.isEmpty()) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "No unfinished drafts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            drafts.groupBy { entry -> entry.account.id }.values.forEach { accountDrafts ->
                val account = accountDrafts.first().account

                item(key = "account:${account.id}") {
                    Text(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 6.dp
                        ),
                        text = account.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(
                    items = accountDrafts,
                    key = { entry -> "${entry.account.id}:${entry.dialog.providerDialogId}" }
                ) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenDialog(
                                    entry.account.id,
                                    entry.dialog.providerDialogId
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = entry.dialog.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = entry.dialog.draftText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
            }
        }
    }
}
