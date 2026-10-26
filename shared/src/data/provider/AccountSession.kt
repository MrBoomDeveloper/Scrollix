package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import kotlinx.coroutines.flow.StateFlow

interface AccountSession {
    val accountId: String
    val providerId: String

    val authState: StateFlow<AuthState>
    val profile: StateFlow<AccountProfile?>
    val dialogs: StateFlow<List<Dialog>>

    fun messages(providerDialogId: String): StateFlow<List<ChatMessage>>

    suspend fun start()
    suspend fun submitAuth(values: Map<String, String>)
    suspend fun performAuthAction(action: AuthAction)
    suspend fun loadMessages(providerDialogId: String, limit: Int = 50): Result<Unit>
    suspend fun sendMessage(providerDialogId: String, text: String): Result<Unit>
    suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray)
    suspend fun logout()
    suspend fun close()
}
