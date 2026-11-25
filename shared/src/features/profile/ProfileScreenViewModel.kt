package com.mrboomdev.scrollix.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the profile payload and every mutation the profile offers: mute, block,
 * contact editing and channel subscription. Dialog visibility and the contact
 * form fields stay in the composable, which passes the typed values in.
 */
internal class ProfileScreenViewModel(
    private val accountId: String,
    private val providerDialogId: String,
    private val userId: Long?,
    private val ownAccount: Boolean
) : ViewModel() {
    private val session = ApiManager.session(accountId)
    private var subscriptionOverride by mutableStateOf<Boolean?>(null)

    var details by mutableStateOf<UserProfileDetails?>(null)
        private set

    var isUpdatingSubscription by mutableStateOf(false)
        private set

    var actionError by mutableStateOf<String?>(null)
        private set

    var isMuted by mutableStateOf<Boolean?>(null)
        private set

    val isSubscribed: Boolean
        get() = subscriptionOverride ?: details?.isSubscribed == true

    init {
        load()

        if (!ownAccount && providerDialogId.isNotBlank()) {
            viewModelScope.launch {
                isMuted = session?.isDialogMuted(providerDialogId)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            details = if (ownAccount) {
                session?.loadOwnProfile()?.getOrNull()
            } else {
                ApiManager.loadUserProfile(
                    accountId = accountId,
                    providerDialogId = providerDialogId,
                    userId = userId
                ).getOrNull()
            }
        }
    }

    fun toggleMute() {
        val currentSession = session ?: return
        val muted = isMuted ?: return
        val next = !muted

        isMuted = next

        viewModelScope.launch {
            currentSession.setDialogMuted(providerDialogId, next).onFailure {
                isMuted = muted
                actionError = it.message ?: "Failed to update notifications"
            }
        }
    }

    fun toggleBlock() {
        val currentSession = session ?: return
        val targetUserId = details?.userId ?: return
        if (details?.canBlock == false) return

        val blocked = details?.isBlocked == true
        details = details?.copy(isBlocked = !blocked)

        viewModelScope.launch {
            currentSession.setUserBlocked(targetUserId, !blocked).onFailure {
                details = details?.copy(isBlocked = blocked)
                actionError = it.message ?: "Failed to update the block state"
            }
        }
    }

    /**
     * Saves the typed contact name. Returns false when nothing was submitted, so
     * the screen keeps the editor open instead of discarding the input.
     */
    fun saveContact(firstName: String, lastName: String): Boolean {
        val currentSession = session ?: return false
        val targetUserId = details?.userId ?: return false
        val trimmedFirst = firstName.trim()
        val trimmedLast = lastName.trim()

        if (trimmedFirst.isEmpty()) {
            actionError = "Enter a first name"
            return false
        }

        actionError = null
        details = details?.copy(
            isContact = true,
            displayName = listOf(trimmedFirst, trimmedLast)
                .filter { part -> part.isNotBlank() }
                .joinToString(" ")
        )

        viewModelScope.launch {
            currentSession.addContact(targetUserId, trimmedFirst, trimmedLast).onFailure {
                details = details?.copy(isContact = false)
                actionError = it.message ?: "Failed to update the contact"
            }
        }

        return true
    }

    fun removeContact() {
        val currentSession = session ?: return
        val targetUserId = details?.userId ?: return

        details = details?.copy(isContact = false)
        actionError = null

        viewModelScope.launch {
            currentSession.removeContact(targetUserId).onFailure {
                details = details?.copy(isContact = true)
                actionError = it.message ?: "Failed to remove the contact"
            }
        }
    }

    fun toggleSubscription() {
        val currentSession = session

        if (currentSession == null) {
            actionError = "Account session is not available"
            return
        }

        val subscribe = !isSubscribed
        val previous = isSubscribed

        // Flip the label right away and revert it if the request fails.
        subscriptionOverride = subscribe
        details = details?.copy(isSubscribed = subscribe)

        fun revert(message: String?) {
            subscriptionOverride = previous
            details = details?.copy(isSubscribed = previous)

            if (message != null) {
                actionError = message
            }
        }

        viewModelScope.launch {
            isUpdatingSubscription = true
            actionError = null

            try {
                val result = withTimeoutOrNull(15_000.milliseconds) {
                    currentSession.setChatSubscribed(providerDialogId, subscribe)
                }

                when {
                    result == null -> revert("The server took too long to respond")

                    result.isSuccess -> {
                        // Re-read the membership from the server so reopening
                        // the profile keeps showing the updated state.
                        details = ApiManager.loadUserProfile(
                            accountId = accountId,
                            providerDialogId = providerDialogId,
                            userId = userId
                        ).getOrNull() ?: details
                    }

                    else -> revert(
                        result.exceptionOrNull()?.message
                            ?: "Failed to update subscription"
                    )
                }
            } finally {
                isUpdatingSubscription = false
            }
        }
    }

    fun clearError() {
        actionError = null
    }
}
