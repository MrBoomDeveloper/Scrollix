package com.mrboomdev.scrollix.features.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.storage.AppSettings
import kotlinx.coroutines.launch

private const val HOME_LOG_TAG = "home"

/** Unread counters for the chip row, split the way the chips consume them. */
internal data class HomeUnreadCounts(
    val all: Int = 0,
    val tabs: Map<Int, Int> = emptyMap()
)

/**
 * Owns the chat-list screen state: the selected chip, the pinned-chat reorder
 * dialog and the counters derived from the loaded dialogs. Pager geometry
 * stays in the composable because it only describes the layout.
 */
internal class HomeScreenViewModel : ViewModel() {
    var selectedTab by mutableStateOf(TAB_DIRECT)
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

    fun selectAll() {
        selectedTab = TAB_ALL
    }

    fun selectTab(index: Int) {
        selectedTab = index
    }

    fun openPinnedOrder() {
        showPinnedOrderDialog = true
    }

    fun closePinnedOrder() {
        showPinnedOrderDialog = false
    }

    /**
     * Clears the unread counters of every account whose provider can do it in
     * bulk. A failure is only logged: the counters that remain already show
     * what is left, so there is nothing to revert in the UI.
     */
    fun markAllChatsRead() {
        viewModelScope.launch {
            ApiManager.markAllChatsRead().onFailure { cause ->
                AppLog.warning(HOME_LOG_TAG, "Mark all as read failed", cause)
            }
        }
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

/** Unread chips are counted per built-in tab. */
internal fun homeUnreadCounts(entries: List<AccountDialogEntry>): HomeUnreadCounts {
    var all = 0
    val tabs = mutableMapOf<Int, Int>()

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
    }

    return HomeUnreadCounts(all = all, tabs = tabs)
}
