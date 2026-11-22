package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageDraft
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.SharedMediaKind
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

    /**
     * Submits the values of the current [AuthChallenge] fields. A challenge
     * with [AuthChallenge.captchaUrl] takes its solved token under
     * [com.mrboomdev.scrollix.data.model.AuthCaptchaValueId].
     */
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

    /**
     * Loads a window around [messageId] without paging through the chat from
     * the newest message. Pinned, replied-to and linked messages use this so
     * a jump costs one page instead of the whole history. Providers that do
     * not support windowed loading fall back to backwards paging.
     */
    suspend fun loadMessagesAround(
        providerDialogId: String,
        messageId: Long,
        radius: Int = 50
    ): Result<Boolean> = loadMessagesUntil(providerDialogId, messageId)
    suspend fun loadFile(fileId: Int): Result<ByteArray>

    /**
     * Loads bytes only when the runtime file id still belongs to the durable
     * unique id. A file id restored from cache can be reassigned after a
     * restart, and blindly using it shows a different file's bytes.
     * Implementations that cannot verify the id fall back to [loadFile].
     */
    suspend fun loadFileIfUnique(fileId: Int, uniqueId: String): Result<ByteArray> =
        loadFile(fileId)

    /**
     * Reads message media, preferring the durable cached entry. When a unique
     * id is known, the file id is used only if it still resolves to it.
     */
    suspend fun loadMediaBytes(fileId: Int?, uniqueId: String?): ByteArray? {
        if (uniqueId != null) {
            loadCachedMedia(uniqueId)?.let { return it }
            fileId?.let { id ->
                loadFileIfUnique(id, uniqueId).getOrNull()?.let { return it }
            }

            return null
        }

        return fileId?.let { id -> loadFile(id).getOrNull() }
    }

    /**
     * Reads a file that was cached in a previous run, without asking the
     * provider for it. Media is addressed by its durable remote unique id, so
     * this works even when the runtime file ids of a cached dialog are stale.
     * Returns null when nothing is cached under that id.
     */
    suspend fun loadCachedMedia(remoteUniqueId: String): ByteArray? = null
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

    /** Sends one message with one or more attachments (a TDLib album). */
    suspend fun sendFiles(
        providerDialogId: String,
        attachments: List<OutgoingAttachment>,
        caption: String = "",
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList()
    ): Result<Unit>
    /** Sends a recorded voice note (server-side Telegram voice message). */
    suspend fun sendVoiceNote(
        providerDialogId: String,
        path: String,
        durationSeconds: Int
    ): Result<Unit> = sendFile(providerDialogId, path, asPhoto = false)

    /** Sends a recorded round video note. */
    suspend fun sendVideoNote(
        providerDialogId: String,
        path: String,
        durationSeconds: Int
    ): Result<Unit> = sendFile(providerDialogId, path, asPhoto = false)

    /**
     * Creates a regular poll on the server.
     *
     * [questionCustomEmojis] and [optionCustomEmojis] are parallel to the
     * question and [options]; [optionMediaPaths] is parallel to [options] and
     * may contain null for options without media.
     */
    suspend fun sendPoll(
        providerDialogId: String,
        question: String,
        options: List<String>,
        isAnonymous: Boolean = true,
        allowsMultipleAnswers: Boolean = false,
        allowsRevoting: Boolean = true,
        questionCustomEmojis: List<CustomEmojiSpan> = emptyList(),
        optionCustomEmojis: List<List<CustomEmojiSpan>> = emptyList(),
        optionMediaPaths: List<String?> = emptyList(),
        isQuiz: Boolean = false,
        /** Index of the correct answer inside [options], for quizzes. */
        correctOptionIndex: Int? = null,
        explanation: String = ""
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Polls are not available"))

    /**
     * Sends a static location, or a live location when [livePeriodSeconds] is
     * greater than zero. Live shares return the id of the sent message so the
     * caller can stop sharing it later.
     */
    suspend fun sendLocation(
        providerDialogId: String,
        latitude: Double,
        longitude: Double,
        livePeriodSeconds: Int = 0
    ): Result<Long> = Result.failure(
        UnsupportedOperationException("Location sharing is not available")
    )

    /** Stops an active live location by editing its message in place. */
    suspend fun stopLiveLocation(
        providerDialogId: String,
        messageId: Long
    ): Result<Unit> = Result.success(Unit)

    suspend fun markMessagesRead(providerDialogId: String, messageIds: LongArray)

    /** Restores the server-side draft of a chat, if it has one. */
    suspend fun loadDraft(providerDialogId: String): Result<MessageDraft> =
        Result.success(MessageDraft())

    /** Stores the composer text as a server-side draft so other devices see it. */
    suspend fun saveDraft(
        providerDialogId: String,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList()
    ): Result<Unit> = Result.success(Unit)

    /** Scope-wide notification settings kept by the server. */
    suspend fun loadNotificationSettings(): Result<NotificationSettings> =
        Result.success(NotificationSettings())

    suspend fun setScopeMuted(scope: NotificationScope, muted: Boolean): Result<Unit> =
        Result.success(Unit)

    /** Mutes or unmutes one chat (person, group or channel) on the server. */
    suspend fun setDialogMuted(providerDialogId: String, muted: Boolean): Result<Unit> =
        Result.success(Unit)

    /** Current mute state of a chat, or null when it can't be determined. */
    suspend fun isDialogMuted(providerDialogId: String): Boolean? = null
    suspend fun setDialogPinned(
        providerDialogId: String,
        isPinned: Boolean,
        folderId: Int? = null
    ): Result<Unit>

    /**
     * Applies a new server-side order to pinned chats. Telegram has no direct
     * reorder call, so pinned state is re-applied in the requested order.
     */
    suspend fun reorderPinnedDialogs(
        providerDialogIds: List<String>,
        folderId: Int? = null
    ): Result<Unit> = Result.success(Unit)
    /**
     * Deletes a chat or leaves it, depending on its type. Private chats lose
     * their history for every participant when [revoke] is true.
     */
    suspend fun deleteDialog(
        providerDialogId: String,
        revoke: Boolean = true
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Deleting chats is not supported")
    )

    /** Blocks or unblocks a user on the server. */
    suspend fun setUserBlocked(userId: Long, blocked: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Blocking users is not supported"))

    suspend fun loadUserProfile(
        providerDialogId: String,
        userId: Long? = null
    ): Result<UserProfileDetails>

    /** Profile of the signed-in user, used by the accounts screen. */
    suspend fun loadOwnProfile(): Result<UserProfileDetails> =
        Result.failure(UnsupportedOperationException("Own profile is not available"))
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

    /** Read time of a recent outgoing private message, when Telegram exposes it. */
    suspend fun loadMessageReadDate(
        providerDialogId: String,
        messageId: Long
    ): Result<Long?>
    suspend fun loadCustomEmoji(emojiId: Long): CustomEmojiAsset?

    /** Maps custom emoji ids to the sticker/emoji pack that contains them. */
    suspend fun loadCustomEmojiSetIds(emojiIds: List<Long>): Map<Long, Long> = emptyMap()
    suspend fun resolveUsername(username: String): Result<Long>

    /**
     * Resolves the avatars of user mentions in [text], keyed by the username
     * without the leading `@`. Mentions that can't be resolved are absent.
     */
    suspend fun loadMentionAvatars(text: String): Map<String, Avatar> = emptyMap()

    /** All profile photos of a user, newest first. */
    suspend fun loadProfilePhotos(userId: Long): Result<List<MessageMedia>> =
        Result.success(emptyList())

    /** One page of a media kind sent in a dialog, newest first. */
    suspend fun loadSharedMedia(
        providerDialogId: String,
        kind: SharedMediaKind,
        fromMessageId: Long = 0,
        limit: Int = 30
    ): Result<List<ChatMessage>> = Result.success(emptyList())

    /**
     * Resolves a t.me/telegram.me link to the provider dialog id so the link
     * can be opened inside the app instead of a browser.
     */
    suspend fun resolveTelegramLink(url: String): Result<String> =
        Result.failure(UnsupportedOperationException("Deep links are not supported"))

    /**
     * Resolves any Telegram link, including sticker and emoji pack links.
     * Providers that only know chat links keep the default implementation,
     * which wraps [resolveTelegramLink].
     */
    suspend fun resolveTelegramLinkTarget(url: String): Result<TelegramLinkTarget> =
        resolveTelegramLink(url).map { providerDialogId ->
            TelegramLinkTarget.Dialog(providerDialogId)
        }

    /** Loads a sticker or custom-emoji set by its public name. */
    suspend fun loadStickerSetByName(
        name: String,
        kind: StickerPackKind = StickerPackKind.Sticker
    ): Result<StickerSetInfo> = Result.failure(
        UnsupportedOperationException("Loading sticker sets by name is not supported")
    )

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

    /**
     * Sends sticker bytes that came from another provider's sticker pack.
     * Providers convert the image into their own attachment format, because
     * sticker ids and pack ids are not portable between messengers.
     */
    suspend fun sendStickerBytes(
        providerDialogId: String,
        bytes: ByteArray,
        format: com.mrboomdev.scrollix.data.model.MessageStickerFormat,
        width: Int,
        height: Int
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Cross-provider stickers are not supported")
    )

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
