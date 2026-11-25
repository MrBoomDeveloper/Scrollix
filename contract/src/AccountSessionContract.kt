package com.mrboomdev.scrollix.data.contract

import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.provider.MessengerProvider
import com.mrboomdev.scrollix.data.storage.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The behavior every [AccountSession] implementation promises to the app
 * shell, independent of the messenger protocol.
 *
 * Provider test source sets subclass this and call [verifyProvider] and
 * [verifySession]. The checks are offline on purpose: they construct a real
 * session and inspect the state it exposes before any network work starts, so
 * one suite can run for Telegram, MAX and VK without credentials.
 */
abstract class AccountSessionContract {
    protected abstract val provider: MessengerProvider

    /**
     * An unsaved account; sessions only use its id and provider id here.
     * Lazy, because [provider] is initialized by the subclass constructor and
     * is still null while this base class is being constructed.
     */
    protected open val account: AccountRecord by lazy {
        AccountRecord(
            id = "contract-account",
            providerId = provider.id,
            displayName = "Contract Account",
            isPending = true,
            addedAt = 0L,
            lastUsedAt = 0L
        )
    }

    protected abstract fun createSession(
        storage: PlatformStorage,
        scope: CoroutineScope
    ): AccountSession

    /**
     * Metadata the app shell uses to build provider pickers and gate
     * features. An empty or malformed id breaks account storage.
     */
    fun verifyProvider() {
        check(provider.id.isNotBlank()) { "Provider id must not be blank" }
        check(provider.id.none { it.isWhitespace() }) {
            "Provider id '${provider.id}' must not contain whitespace"
        }
        check(provider.displayName.isNotBlank()) { "Provider display name must not be blank" }
        check(provider.capabilities.dialogs) {
            "Every registered provider must support dialogs"
        }
    }

    /**
     * Session identity, the empty initial state and a clean lifecycle.
     *
     * A fresh session must not require network or auth before exposing these
     * values; screens render it immediately after [createSession].
     */
    suspend fun verifySession() {
        val storage = ContractPlatformStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = createSession(storage, scope)

        try {
            check(session.accountId == account.id) {
                "Session accountId '${session.accountId}' does not match '${account.id}'"
            }
            check(session.providerId == provider.id) {
                "Session providerId '${session.providerId}' does not match '${provider.id}'"
            }
            check(session.authState.value == AuthState.Initializing) {
                "A new session must start in Initializing, was ${session.authState.value}"
            }
            check(session.profile.value == null) {
                "A new session must not expose a profile before authentication"
            }
            check(session.dialogs.value.isEmpty()) {
                "A new session must start without loaded dialogs"
            }
            check(session.folders.value.isEmpty()) {
                "A new session must start without loaded folders"
            }
            check(session.downloads.value.isEmpty()) {
                "A new session must start without downloads"
            }

            val history = session.messageHistory(ContractDialogId)
            check(history === session.messageHistory(ContractDialogId)) {
                "messageHistory must return the same flow for a dialog on every call"
            }
            check(history.value.messages.isEmpty()) {
                "A new dialog history must start empty"
            }
            check(!history.value.hasMoreOlder) {
                "A new dialog history must not claim older messages before loading"
            }

            check(session.loadCachedMedia(ContractMediaId) == null) {
                "An empty storage must not report cached media"
            }
            check(session.loadMediaBytes(null, null) == null) {
                "Media without a file id or unique id must resolve to null"
            }
        } finally {
            session.close()
            scope.cancel()
        }

        check(session.authState.value == AuthState.Closed) {
            "close() must move the session to Closed, was ${session.authState.value}"
        }

        // Screens and account removal may close a session twice.
        session.close()
    }

    private companion object {
        const val ContractDialogId = "contract:missing-dialog"
        const val ContractMediaId = "contract:missing-media"
    }
}
