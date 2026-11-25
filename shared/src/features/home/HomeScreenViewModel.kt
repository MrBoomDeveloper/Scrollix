package com.mrboomdev.scrollix.features.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import kotlinx.coroutines.launch

/** Unread counters for the chip row, split the way the chips consume them. */
internal data class HomeUnreadCounts(
    val all: Int = 0,
    val tabs: Map<Int, Int> = emptyMap(),
    val folders: Map<String, Int> = emptyMap()
)

/**
 * Owns the chat-list screen state: the selected chip, the two reorder dialogs
 * and the counters derived from the loaded dialogs. Pager geometry and chip
 * dragging stay in the composable because they only describe the layout.
 */
internal class HomeScreenViewModel : ViewModel() {
    var selectedTab by mutableStateOf(TAB_DIRECT)
        private set

    var selectedNativeFolderKey by mutableStateOf<String?>(null)
        private set

    var showFolderOrderDialog by mutableStateOf(false)
        private set

    var showPinnedOrderDialog by mutableStateOf(false)
        private set

    private var defaultTabApplied = false

    /** Applies the configured start tab once, after the settings are loaded. */
    fun applyDefaultTab(settings: AppSettings) {
        if (defaultTabApplied) return
        defaultTabApplied = true

        selectedTab = when (settings.defaultHomeTab) {
            "direct" -> TAB_DIRECT
            "groups" -> TAB_GROUPS
            "channels" -> TAB_CHANNELS
            "bots" -> TAB_BOTS
            else -> if (settings.showAllChatsTab) TAB_ALL else TAB_DIRECT
        }
    }

    fun selectAll(isUsingNativeFolders: Boolean) {
        if (isUsingNativeFolders) {
            selectedNativeFolderKey = null
        } else {
            selectedTab = TAB_ALL
        }
    }

    fun selectTab(index: Int) {
        selectedTab = index
    }

    fun selectFolder(key: String) {
        selectedNativeFolderKey = key
    }

    fun openFolderOrder() {
        showFolderOrderDialog = true
    }

    fun closeFolderOrder() {
        showFolderOrderDialog = false
    }

    fun openPinnedOrder() {
        showPinnedOrderDialog = true
    }

    fun closePinnedOrder() {
        showPinnedOrderDialog = false
    }

    fun applyFolderOrder(keys: List<String>) {
        showFolderOrderDialog = false
        AppSettingsStore.update { current -> current.copy(folderOrder = keys) }
    }

    /**
     * Telegram has no reorder call, so every pinned chat of an account is
     * re-pinned in the requested order. Keys are `accountId:providerDialogId`.
     */
    fun applyPinnedOrder(keys: List<String>) {
        showPinnedOrderDialog = false

        val byAccount = keys.mapNotNull { key ->
            val separator = key.indexOf(':')
            if (separator <= 0) return@mapNotNull null

            key.substring(0, separator) to key.substring(separator + 1)
        }.groupBy({ it.first }, { it.second })

        viewModelScope.launch {
            byAccount.forEach { (accountId, dialogIds) ->
                ApiManager.session(accountId)?.reorderPinnedDialogs(dialogIds)
            }
        }
    }
}

/** Unread chips are counted per native folder and per built-in tab. */
internal fun homeUnreadCounts(entries: List<AccountDialogEntry>): HomeUnreadCounts {
    var all = 0
    val tabs = mutableMapOf<Int, Int>()
    val folders = mutableMapOf<String, Int>()

    entries.forEach { entry ->
        if (!entry.dialog.hasUnreadIndicator()) return@forEach
        all++

        val tab = when (entry.dialog.kind) {
            DialogKind.Direct, DialogKind.Secret -> TAB_DIRECT
            DialogKind.Group -> TAB_GROUPS
            DialogKind.Channel -> TAB_CHANNELS
            DialogKind.Bot -> TAB_BOTS
        }

        tabs[tab] = (tabs[tab] ?: 0) + 1

        entry.dialog.folderIds.forEach { folderId ->
            val key = "${entry.account.id}:$folderId"
            folders[key] = (folders[key] ?: 0) + 1
        }
    }

    return HomeUnreadCounts(all = all, tabs = tabs, folders = folders)
}
