package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.isTwoPaneLayout

private enum class SettingsCategory(
    val title: String,
    val description: String
) {
    ChatList("Chat list", "Folders, filters and chat sorting"),
    Accounts("Accounts", "Connected messaging accounts"),
    About("About", "Application information")
}

@Composable
fun SettingsScreen(onOpenAccountsScreen: () -> Unit) {
    val isTwoPane = isTwoPaneLayout()
    val settings by AppSettingsStore.state.collectAsState()

    if (isTwoPane) {
        var selected by remember { mutableStateOf(SettingsCategory.ChatList) }

        Row(Modifier.fillMaxSize()) {
            SettingsCategories(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                selected = selected,
                onSelect = { selected = it }
            )

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            SettingsCategoryContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                category = selected,
                settings = settings,
                onOpenAccountsScreen = onOpenAccountsScreen
            )
        }

        return
    }

    var openedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val category = openedCategory

    if (category == null) {
        SettingsCategories(
            modifier = Modifier.fillMaxSize(),
            selected = null,
            onSelect = { openedCategory = it }
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedCategory = null }) {
                    Icon(
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            SettingsCategoryContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                category = category,
                settings = settings,
                onOpenAccountsScreen = onOpenAccountsScreen
            )
        }
    }
}

@Composable
private fun SettingsCategories(
    modifier: Modifier = Modifier,
    selected: SettingsCategory?,
    onSelect: (SettingsCategory) -> Unit
) {
    Column(modifier) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "Settings",
            style = MaterialTheme.typography.titleLarge
        )

        SettingsCategory.entries.forEach { category ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (selected == category) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                onClick = { onSelect(category) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    modifier: Modifier = Modifier,
    category: SettingsCategory,
    settings: AppSettings,
    onOpenAccountsScreen: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge
        )

        when (category) {
            SettingsCategory.ChatList -> {
                SettingsSwitch(
                    title = "Show the \"All\" tab",
                    description = "Add a chip that displays every chat at once.",
                    checked = settings.showAllChatsTab,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(showAllChatsTab = value) }
                    }
                )

                SettingsSwitch(
                    title = "Single row filters",
                    description = "Lay out folder chips in one scrollable row.",
                    checked = settings.singleRowFilters,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(singleRowFilters = value) }
                    }
                )

                SettingsSwitch(
                    title = "Use Telegram folders",
                    description = "Show native server-side chat folders.",
                    checked = settings.useNativeFolders,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(useNativeFolders = value) }
                    }
                )
            }

            SettingsCategory.Accounts -> {
                Text(
                    text = "Manage the accounts Scrollix connects to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Button(onClick = onOpenAccountsScreen) {
                    Text("Manage accounts")
                }
            }

            SettingsCategory.About -> {
                Text(
                    text = "Scrollix",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Telegram client built with Compose Multiplatform.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
