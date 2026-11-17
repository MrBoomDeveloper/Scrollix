package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import kotlinx.coroutines.flow.StateFlow

interface AccountSession {
    val accountId: String
    val providerId: String

    val authState: StateFlow<AuthState>
    val profile: StateFlow<AccountProfile?>
    val dialogs: StateFlow<List<Dialog>>
    val folders: StateFlow<List<DialogFolder>>
    val isLoadingDialogs: StateFlow<Boolean>

    fun messages(providerDialogId: String): StateFlow<List<ChatMessage>>

    suspend fun start()
    suspend fun submitAuth(values: Map<String, String>)
    suspend fun performAuthAction(action: AuthAction)
    suspend fun loadMessages(providerDialogId: String, limit: Int = 50): Result<Unit>
    suspend fun loadOlderMessages(providerDialogId: String, limit: Int = 50): Result<Boolean>
    suspend fun loadFile(fileId: Int): Result<ByteArray>
    suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long? = null
    ): Result<Unit>
    suspend fun sendFile(
        providerDialogId: String,
        path: String,
        asPhoto: Boolean
    ): Result<Unit>
    suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray)
    suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int? = null
    ): Result<Unit>
    suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long? = null
    ): Result<UserProfileDetails>
    suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int = 50
    ): Result<List<ChatMessage>>
    suspend fun loadMessage(
        providerDialogId: String,
        messageId: Long
    ): Result<ChatMessage?>
    suspend fun loadCustomEmoji(emojiId: Long): ByteArray?
    suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String
    ): Result<Unit>
    suspend fun forwardMessages(
        providerDialogId: String,
        messageIds: LongArray,
        targetDialogId: String
    ): Result<Unit>
    suspend fun deleteMessages(
        providerDialogId: String,
        messageIds: LongArray
    ): Result<Unit>
    suspend fun setMessagePinned(
        providerDialogId: String,
        messageId: Long,
        isPinned: Boolean
    ): Result<Unit>
    suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?>
    suspend fun logout()
    suspend fun close()
}
