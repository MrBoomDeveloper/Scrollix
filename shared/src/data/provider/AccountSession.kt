package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
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
    val downloads: StateFlow<List<DownloadedFile>>

    /**
     * The message window of a dialog together with its pagination state.
     * The returned flow is stable for the lifetime of the dialog, so the UI
     * can remember it and never lose the loaded range on recomposition.
     */
    fun messageHistory(providerDialogId: String): StateFlow<MessageHistory>

    suspend fun start()
    suspend fun submitAuth(values: Map<String, String>)
    suspend fun performAuthAction(action: AuthAction)

    /**
     * Approves a login on another device by passing the link encoded in the
     * QR code that device shows. Only works for an authorized session.
     */
    suspend fun confirmQrCodeAuthentication(link: String): Result<Unit>
    suspend fun loadMessages(providerDialogId: String, limit: Int = 50): Result<Unit>
    suspend fun loadOlderMessages(providerDialogId: String, limit: Int = 50): Result<Boolean>
    suspend fun loadMessagesUntil(providerDialogId: String, messageId: Long): Result<Boolean>
    suspend fun loadFile(fileId: Int): Result<ByteArray>
    suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList()
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
    suspend fun setChatSubscribed(providerDialogId: String, subscribed: Boolean): Result<Unit>
    suspend fun votePoll(
        providerDialogId: String,
        messageId: Long,
        optionIds: List<String>
    ): Result<Unit>
    suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int = 50
    ): Result<List<ChatMessage>>
    suspend fun searchGlobalMessages(
        query: String,
        limit: Int = 50
    ): Result<List<ChatMessage>>
    suspend fun searchChats(
        query: String,
        limit: Int = 50
    ): Result<List<Dialog>>
    suspend fun loadMessage(
        providerDialogId: String,
        messageId: Long
    ): Result<ChatMessage?>
    suspend fun loadCustomEmoji(emojiId: Long): ByteArray?
    suspend fun resolveUsername(username: String): Result<Long>
    suspend fun loadStickerSet(setId: Long): Result<StickerSetInfo>
    suspend fun setStickerSetInstalled(setId: Long, installed: Boolean): Result<Unit>
    suspend fun loadStickerPacks(kind: StickerPackKind): Result<List<StickerPack>>
    suspend fun loadRecentStickers(kind: StickerPackKind): Result<List<MessageMedia>>
    suspend fun removeRecentSticker(sticker: MessageMedia): Result<Unit>
    suspend fun clearRecentStickers(): Result<Unit>
    suspend fun loadSavedAnimations(): Result<List<MessageMedia>>
    suspend fun removeSavedAnimation(animation: MessageMedia): Result<Unit>
    suspend fun clearSavedAnimations(): Result<Unit>
    suspend fun sendSticker(providerDialogId: String, sticker: MessageMedia): Result<Unit>
    suspend fun sendAnimation(providerDialogId: String, animation: MessageMedia): Result<Unit>
    suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList()
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
    suspend fun addMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long? = null
    ): Result<Unit>
    suspend fun removeMessageReaction(
        providerDialogId: String,
        messageId: Long,
        emoji: String,
        customEmojiId: Long? = null
    ): Result<Unit>
    suspend fun loadAvailableReactions(
        providerDialogId: String,
        messageId: Long
    ): Result<List<MessageReactionOption>>
    suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?>
    suspend fun logout()
    suspend fun close()
}
