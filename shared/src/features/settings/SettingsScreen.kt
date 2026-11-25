package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.backup_icon
import com.mrboomdev.scrollix.ui.icons.bug_report_icon
import com.mrboomdev.scrollix.ui.icons.campaign_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.icons.data_usage_icon
import com.mrboomdev.scrollix.ui.icons.devices_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.info_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.storage_icon
import com.mrboomdev.scrollix.ui.icons.touch_app_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mrboomdev.scrollix.isTwoPaneLayout

/**
 * A settings section. [keywords] name things the section owns that its title
 * does not say, so a category-level hit still works for a broad word.
 */
internal enum class SettingsCategory(
    val title: String,
    val description: String,
    val keywords: List<String>
) {
    ChatList(
        "Chat list",
        "Folders, filters and chat sorting",
        listOf(
            "wallpaper", "folders", "filters", "tabs", "merge duplicate chats",
            "sticker packs", "emoji packs", "recent emoji", "system emoji",
            "quick reactions"
        )
    ),
    Accessibility(
        "Accessibility",
        "Motion and readability",
        listOf("reduce motion", "animation", "accessibility")
    ),
    Storage(
        "Storage",
        "Cached data and disk usage",
        listOf("cache", "media cache limit", "clear cache", "keep media", "disk usage")
    ),
    DataUsage(
        "Data and network",
        "Traffic, data saver and auto-download",
        listOf("traffic", "reset statistics", "auto-download", "network", "roaming", "data saver")
    ),
    Backup(
        "Backup",
        "Export or import your settings",
        listOf("export", "import", "restore", "settings file")
    ),
    Notifications(
        "Notifications",
        "Mutes, alerts and double-tap reaction",
        listOf("mute", "muted scopes", "exceptions", "alerts")
    ),
    Sessions(
        "Sessions",
        "Devices signed in to your account",
        listOf("devices", "terminate", "sign out", "active sessions")
    ),
    About(
        "About",
        "Application information",
        listOf("version", "licenses", "source code", "github", "edition")
    ),
    Diagnostics(
        "Diagnostics",
        "Debug log and environment details",
        listOf("log", "crash reports", "protocol dumps", "errors", "environment")
    )
}

/**
 * One searchable setting inside a section. The index covers every row of every
 * section, including rows of screens that have not been opened in this session,
 * so the search field can find a single switch instead of only its section.
 */
internal data class SettingsEntry(
    val category: SettingsCategory,
    val title: String,
    val keywords: List<String> = emptyList()
)

internal val SettingsEntries: List<SettingsEntry> = listOf(
    // Chat list
    SettingsEntry(SettingsCategory.ChatList, "Chat wallpaper", listOf("wallpaper", "background", "chat background")),
    SettingsEntry(SettingsCategory.ChatList, "Show the \"All\" tab", listOf("tabs", "chips")),
    SettingsEntry(SettingsCategory.ChatList, "Single row filters", listOf("tabs", "folders", "chips")),
    SettingsEntry(SettingsCategory.ChatList, "Merge duplicate chats", listOf("duplicates", "accounts")),
    SettingsEntry(SettingsCategory.ChatList, "Use Telegram folders", listOf("folders", "filters", "native")),
    SettingsEntry(SettingsCategory.ChatList, "Recent emoji and stickers", listOf("recent")),
    SettingsEntry(SettingsCategory.ChatList, "System emoji", listOf("emoji", "picker")),
    SettingsEntry(SettingsCategory.ChatList, "Choose emoji", listOf("disabled emoji", "hide emoji")),
    SettingsEntry(SettingsCategory.ChatList, "Quick reactions", listOf("reactions", "action menu")),
    SettingsEntry(SettingsCategory.ChatList, "Pinned reactions", listOf("reactions", "custom emoji")),
    SettingsEntry(SettingsCategory.ChatList, "Double-tap reaction", listOf("reaction", "gesture")),
    SettingsEntry(SettingsCategory.ChatList, "Default home tab", listOf("tabs", "start")),
    SettingsEntry(SettingsCategory.ChatList, "Reorder sticker packs", listOf("stickers", "order")),
    SettingsEntry(SettingsCategory.ChatList, "Reorder emoji packs", listOf("emoji", "order")),

    // Accessibility
    SettingsEntry(
        SettingsCategory.Accessibility,
        "Reduce motion",
        listOf("animation", "transition", "motion", "accessibility")
    ),

    // Notifications
    SettingsEntry(SettingsCategory.Notifications, "Account notifications", listOf("mute", "alerts", "account")),
    SettingsEntry(SettingsCategory.Notifications, "Muted scopes", listOf("private chats", "groups", "channels", "mute")),
    SettingsEntry(SettingsCategory.Notifications, "Notification exceptions", listOf("exceptions", "muted chats")),

    // Backup
    SettingsEntry(SettingsCategory.Backup, "Export settings", listOf("backup", "save", "file")),
    SettingsEntry(SettingsCategory.Backup, "Import settings", listOf("restore", "backup", "file")),

    // Storage
    SettingsEntry(SettingsCategory.Storage, "Media cache limit", listOf("cache", "size", "limit", "budget")),
    SettingsEntry(SettingsCategory.Storage, "Keep media", listOf("keep media", "kept chats", "protection")),
    SettingsEntry(SettingsCategory.Storage, "Clear cache", listOf("clear", "delete", "disk usage")),
    SettingsEntry(SettingsCategory.Storage, "Clear all", listOf("clear", "delete", "everything")),

    // Sessions
    SettingsEntry(SettingsCategory.Sessions, "Active sessions", listOf("devices", "sign out", "terminate")),
    SettingsEntry(SettingsCategory.Sessions, "Terminate sessions", listOf("sign out", "devices")),

    // Data and network
    SettingsEntry(SettingsCategory.DataUsage, "Traffic statistics", listOf("data usage", "sent", "received")),
    SettingsEntry(SettingsCategory.DataUsage, "Reset statistics", listOf("traffic", "reset")),
    SettingsEntry(SettingsCategory.DataUsage, "Data saver presets", listOf("auto-download", "low", "medium", "high")),
    SettingsEntry(SettingsCategory.DataUsage, "Auto-play media", listOf("animations", "gifs", "video notes", "data saver")),

    // Diagnostics
    SettingsEntry(SettingsCategory.Diagnostics, "Save crash reports", listOf("crash", "report", "errors")),
    SettingsEntry(SettingsCategory.Diagnostics, "Open diagnostics", listOf("log", "debug", "protocol dumps")),
    SettingsEntry(SettingsCategory.Diagnostics, "Protocol dumps", listOf("log", "debug", "network")),

    // About
    SettingsEntry(SettingsCategory.About, "Version", listOf("build", "edition")),
    SettingsEntry(SettingsCategory.About, "Source code", listOf("github", "repository", "licenses")),
    SettingsEntry(SettingsCategory.About, "Storage profile", listOf("diagnostics", "test server"))
)

/**
 * Finds the rows matching a query. Every whitespace-separated token has to occur
 * in the row title, its keywords or its section, so a second word narrows the
 * result instead of widening it. A section whose own title matches but has no
 * matching row is returned as a single result, so "storage" still finds the
 * section. An empty query returns nothing: the category list is shown instead.
 */
internal fun searchSettings(
    query: String,
    entries: List<SettingsEntry> = SettingsEntries
): List<SettingsEntry> {
    val tokens = query.trim().lowercase()
        .split(Regex("\\s+"))
        .filter { token -> token.isNotEmpty() }

    if (tokens.isEmpty()) return emptyList()

    fun String.containsAllTokens() = tokens.all { token -> lowercase().contains(token) }

    val rows = entries.filter { entry ->
        val haystack = buildString {
            append(entry.title)

            entry.keywords.forEach { keyword ->
                append(' ')
                append(keyword)
            }
        }

        haystack.containsAllTokens()
    }

    val covered = rows.mapTo(mutableSetOf()) { entry -> entry.category }

    val sections = SettingsCategory.entries
        .filter { category -> category !in covered }
        .filter { category ->
            // Rows carry the descriptions, so the fallback only needs the
            // section's own title and keywords here.
            val haystack = buildString {
                append(category.title)

                category.keywords.forEach { keyword ->
                    append(' ')
                    append(keyword)
                }
            }

            haystack.containsAllTokens()
        }
        .map { category -> SettingsEntry(category, category.title) }

    // Rows keep the order of the index, which is grouped by section already.
    return rows + sections
}

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit
) {
    val isTwoPane = isTwoPaneLayout()
    val settings by AppSettingsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    var highlightTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberFilePicker(
        onImagesPicked = { picked ->
            picked.firstOrNull()?.let { file ->
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        readFileBytes(file.path)
                    }

                    if (bytes != null) {
                        AppSettingsStore.saveWallpaper(bytes)
                    }
                }
            }
        },
        onFilesPicked = {}
    )

    if (isTwoPane) {
        // rememberSaveable keeps the open section across navigation and
        // activity recreation; the enum itself is stored by name so the value
        // is a plain string.
        var selectedName by rememberSaveable { mutableStateOf(SettingsCategory.ChatList.name) }
        val selected = SettingsCategory.entries
            .firstOrNull { category -> category.name == selectedName }
            ?: SettingsCategory.ChatList

        Row(Modifier.fillMaxSize()) {
            SettingsCategories(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                selected = selected,
                query = categoryQuery,
                onQueryChange = { categoryQuery = it },
                onSelect = { category, entry ->
                    selectedName = category.name
                    highlightTitle = entry?.title
                }
            )

            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

            SettingsCategoryContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                category = selected,
                settings = settings,
                highlightTitle = highlightTitle,
                onHighlightDismiss = { highlightTitle = null },
                onPickWallpaper = { filePicker.pickImages() },
                onOpenDiagnostics = onOpenDiagnostics
            )
        }

        return
    }

    var openedName by rememberSaveable { mutableStateOf("") }
    val category = SettingsCategory.entries.firstOrNull { it.name == openedName }

    if (category == null) {
        SettingsCategories(
            modifier = Modifier.fillMaxSize(),
            selected = null,
            query = categoryQuery,
            onQueryChange = { categoryQuery = it },
            onSelect = { category, entry ->
                openedName = category.name
                highlightTitle = entry?.title
            }
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedName = "" }) {
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
                highlightTitle = highlightTitle,
                onHighlightDismiss = { highlightTitle = null },
                onPickWallpaper = { filePicker.pickImages() },
                onOpenDiagnostics = onOpenDiagnostics
            )
        }
    }
}

@Composable
internal fun SettingsCategories(
    modifier: Modifier = Modifier,
    selected: SettingsCategory?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsCategory, SettingsEntry?) -> Unit
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = "Settings",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search settings") },
            leadingIcon = {
                Icon(
                    imageVector = search_icon,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = close_icon,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )

        if (query.isBlank()) {
            SettingsCategory.entries.forEach { category ->
                SettingsNavRow(
                    icon = category.icon,
                    title = category.title,
                    subtitle = category.description,
                    selected = selected == category,
                    onClick = { onSelect(category, null) }
                )
            }
        } else {
            // A query searches every row of every section, including sections
            // that were never opened, so a specific switch can be found by name.
            val results = searchSettings(query)

            if (results.isEmpty()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "No settings match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            results.forEach { entry ->
                SettingsNavRow(
                    icon = entry.category.icon,
                    title = entry.title,
                    subtitle = entry.category.title,
                    selected = false,
                    onClick = { onSelect(entry.category, entry) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else Color.Transparent,
        
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if(selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = icon,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentDescription = null
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if(selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

internal val SettingsCategory.icon: ImageVector
    get() = when (this) {
        SettingsCategory.ChatList -> chat_bubble_icon
        SettingsCategory.Accessibility -> touch_app_icon
        SettingsCategory.Notifications -> notifications_icon
        SettingsCategory.Backup -> backup_icon
        SettingsCategory.Storage -> storage_icon
        SettingsCategory.Sessions -> devices_icon
        SettingsCategory.DataUsage -> data_usage_icon
        SettingsCategory.Diagnostics -> bug_report_icon
        SettingsCategory.About -> info_icon
    }

internal val NotificationScope.icon: ImageVector
    get() = when (this) {
        NotificationScope.PrivateChats -> chat_bubble_icon
        NotificationScope.Groups -> group_icon
        NotificationScope.Channels -> campaign_icon
    }

/** Section heading with the icon of the setting it introduces. */
@Composable
internal fun SettingsSectionTitle(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.settingsAnchor(title),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
internal fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsAnchor(title),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = icon,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )
            }
        }

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
