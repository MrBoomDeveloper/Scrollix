package com.mrboomdev.scrollix.features.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.outbox.OutboxManager
import kotlinx.coroutines.launch

/**
 * Owns account management and the outgoing-queue actions shown on the screen.
 * The account list, session states and queue entries themselves stay in the
 * managers and are collected by the composable.
 */
internal class AccountsScreenViewModel : ViewModel() {
    var isAdding by mutableStateOf(false)
        private set

    var removingIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var providerPickerOpen by mutableStateOf(false)
        private set

    var confirmDiscard by mutableStateOf(false)
        private set

    fun openProviderPicker() {
        providerPickerOpen = true
    }

    fun closeProviderPicker() {
        providerPickerOpen = false
    }

    fun dismissError() {
        error = null
    }

    fun addAccount(providerId: String, onAdded: (String) -> Unit) {
        if (isAdding) return
        isAdding = true

        viewModelScope.launch {
            try {
                runCatching {
                    ApiManager.addAccount(providerId)
                }.onSuccess { accountId ->
                    error = null
                    onAdded(accountId)
                }.onFailure { exception ->
                    error = exception.message ?: "Failed to add account"
                }
            } finally {
                isAdding = false
            }
        }
    }

    fun removeAccount(accountId: String) {
        if (accountId in removingIds) return
        removingIds = removingIds + accountId

        viewModelScope.launch {
            try {
                runCatching {
                    ApiManager.removeAccount(accountId)
                }.onFailure { exception ->
                    error = exception.message ?: "Failed to remove account"
                }
            } finally {
                removingIds = removingIds - accountId
            }
        }
    }

    fun requestDiscardAll() {
        confirmDiscard = true
    }

    fun dismissDiscardAll() {
        confirmDiscard = false
    }

    fun retryAll() {
        OutboxManager.retryAll()
    }

    fun discardAll() {
        confirmDiscard = false
        OutboxManager.discardAll()
    }
}
