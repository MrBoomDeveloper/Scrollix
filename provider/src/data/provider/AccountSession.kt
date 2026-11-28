package com.mrboomdev.scrollix.data.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.data.model.AccountProfile
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolder
import com.mrboomdev.scrollix.data.model.DownloadedFile
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.ChatTopic
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.MessageDraft
import com.mrboomdev.scrollix.data.model.NotificationSettings
import com.mrboomdev.scrollix.data.model.NotificationScope
import com.mrboomdev.scrollix.data.model.NotificationException
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.model.PublicChatSearchKind
import com.mrboomdev.scrollix.data.model.PostSearchPage
import com.mrboomdev.scrollix.data.model.PostSearchQuota
import com.mrboomdev.scrollix.data.model.CustomEmojiAsset
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.SharedMediaKind
import com.mrboomdev.scrollix.data.model.StickerPackKind
import com.mrboomdev.scrollix.data.model.StickerSetInfo
import com.mrboomdev.scrollix.data.model.StickerPack
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReplyQuote
import com.mrboomdev.scrollix.data.model.ActiveSessionInfo
import com.mrboomdev.scrollix.data.model.DataUsageSummary
import com.mrboomdev.scrollix.data.model.DataUsagePreset
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessageReportResult
import com.mrboomdev.scrollix.data.model.LastSeenVisibility

/** Sessions without a live transport are always considered connected. */
private val AlwaysConnected = MutableStateFlow(true)

/** Providers that do not report a star balance own none of it. */
private val NoStars = MutableStateFlow(0L)

interface AccountSession : ChecklistAuthoringSession, BotSession, PaymentSession, BusinessSession {
    val accountId: String
    val providerId: String

    val authState: StateFlow<AuthState>
    /**
     * False while the provider lost its server connection. The dialog header
     * shows "Connecting..." instead of a stale online status until it heals.
     * Providers without a live connection keep the default `true`.
     */
    val isConnected: StateFlow<Boolean> get() = AlwaysConnected
    /**
     * Stars the account owns, kept current by provider updates. Providers
     * without stars keep the default zero, so the balance row simply hides.
     */
    val starBalance: StateFlow<Long> get() = NoStars
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

    /**
     * Jumps to the true beginning of a chat, keeping the previously loaded
     * window as a far-end preview behind an undiscovered gap.
     */
    suspend fun jumpToBeginning(
        providerDialogId: String,
        limit: Int = 50
    ): Result<Boolean> = Result.success(false)

    /** Fills one page of the undiscovered gap after [jumpToBeginning]. */
    suspend fun loadNewerMessages(
        providerDialogId: String,
        limit: Int = 50
    ): Result<Boolean> = Result.success(false)

    /**
     * Forum topics of a chat, newest activity first, optionally narrowed by
     * [query] on the provider's side. Empty for non-forums.
     */
    suspend fun loadTopics(
        providerDialogId: String,
        query: String = ""
    ): Result<List<ChatTopic>> =
        Result.failure(UnsupportedOperationException("Topics are not supported"))

    /** Newest page of one topic's history, oldest first. */
    suspend fun loadTopicMessages(
        providerDialogId: String,
        topicId: Long,
        limit: Int = 50
    ): Result<List<ChatMessage>> = Result.failure(
        UnsupportedOperationException("Topics are not supported")
    )

    /** Sends a message into a forum topic. */
    suspend fun sendTopicMessage(
        providerDialogId: String,
        topicId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList()
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Topics are not supported")
    )

    /** Whether a topic can be created, renamed, closed and pinned. */
    val supportsTopicManagement: Boolean
        get() = false

    /** Creates a topic with [title] and returns its provider id. */
    suspend fun createForumTopic(
        providerDialogId: String,
        title: String
    ): Result<Long> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Creating topics is not supported"
        )
    )

    /** Renames an existing topic. */
    suspend fun editForumTopicTitle(
        providerDialogId: String,
        topicId: Long,
        title: String
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Renaming topics is not supported"
        )
    )

    /** Closes a topic to new messages, or reopens it. */
    suspend fun setForumTopicClosed(
        providerDialogId: String,
        topicId: Long,
        closed: Boolean
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Closing topics is not supported"
        )
    )

    /** Pins a topic above the unpinned ones, or unpins it. */
    suspend fun setForumTopicPinned(
        providerDialogId: String,
        topicId: Long,
        pinned: Boolean
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Pinning topics is not supported"
        )
    )

    /** Whether a single topic can be muted and the General topic hidden. */
    val supportsTopicNotifications: Boolean
        get() = false

    /** Mutes or unmutes notifications of one topic on the server. */
    suspend fun setForumTopicMuted(
        providerDialogId: String,
        topicId: Long,
        muted: Boolean
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Topic notifications are not supported"
        )
    )

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

    /**
     * Fraction of [fileId] already downloaded, 0..1, or null when the provider
     * cannot report it. The UI polls this only while a download it started is
     * still running.
     */
    fun mediaDownloadProgress(fileId: Int): Float? = null

    /** Stops an in-flight download. Unknown or already finished ids are ignored. */
    suspend fun cancelMediaDownload(fileId: Int) = Unit
    /**
     * Loads the replies of a message thread. For channel comments the replies
     * live in a different chat, which [ChatMessageThread.chatId] exposes.
     */
    suspend fun loadMessageThread(
        providerDialogId: String,
        messageId: Long,
        limit: Int = 100
    ): Result<com.mrboomdev.scrollix.data.model.ChatMessageThread> = Result.failure(
        UnsupportedOperationException("Message threads are not supported")
    )

    /** Sends a reply into the thread rooted at [rootMessageId]. */
    suspend fun sendMessageToThread(
        threadChatId: String,
        rootMessageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList()
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Message threads are not supported")
    )

    /** True when the provider can deliver a message without a notification. */
    open val supportsSilentSend: Boolean get() = false

    /**
     * True when pausing stops the bytes on the provider side. Without it the
     * queue still pauses between attempts, and the UI must not promise more.
     */
    open val supportsUploadPause: Boolean get() = false

    /** Stops an in-flight upload; a no-op for providers without pause support. */
    suspend fun pauseUpload(path: String): Result<Unit> = Result.success(Unit)

    /** Lets a paused upload continue; a no-op for providers without pause support. */
    suspend fun resumeUpload(path: String): Result<Unit> = Result.success(Unit)

    /**
     * True when this account can approve a QR login shown by another device.
     * Telegram has the flow; MAX and VK do not, so the QR dialog offers only
     * the accounts that can actually finish it.
     */
    open val supportsQrLogin: Boolean get() = false

    /**
     * Sends text. [formatting] carries the rich text ranges of [text]; providers
     * without entity support may deliver the plain text instead.
     * [sendSilently] asks the provider to skip the recipient's notification;
     * providers without [supportsSilentSend] ignore it.
     */
    suspend fun sendMessage(
        providerDialogId: String,
        text: String,
        replyToMessageId: Long? = null,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        sendSilently: Boolean = false,
        replyQuote: MessageReplyQuote? = null
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
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        scheduling: MessageScheduling? = null,
        sendSilently: Boolean = false
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

    /**
     * Restores the server-side draft of a chat, if it has one. [threadId] is
     * the provider's topic/thread id, or 0 for the chat itself.
     */
    suspend fun loadDraft(
        providerDialogId: String,
        threadId: Long = 0L
    ): Result<MessageDraft> = Result.success(MessageDraft())

    /**
     * Stores the composer text as a server-side draft so other devices see it.
     * [threadId] scopes it to a topic/thread, or 0 for the chat itself.
     */
    suspend fun saveDraft(
        providerDialogId: String,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList(),
        threadId: Long = 0L
    ): Result<Unit> = Result.success(Unit)

    /** Whether this account can change a chat's auto-delete timer. */
    val supportsMessageAutoDelete: Boolean
        get() = false

    /** The chat's auto-delete timer in seconds; 0 turns it off. */
    suspend fun loadMessageAutoDeleteTime(providerDialogId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("Auto-delete timers are not supported"))

    /** Sets the chat's auto-delete timer for new messages; 0 turns it off. */
    suspend fun setMessageAutoDeleteTime(providerDialogId: String, seconds: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Auto-delete timers are not supported"))

    /** Whether this account can schedule messages and reschedule them. */
    val supportsScheduling: Boolean
        get() = false

    /** Messages waiting in the chat's schedule queue, newest first. */
    suspend fun loadScheduledMessages(providerDialogId: String): Result<List<ChatMessage>> =
        Result.failure(UnsupportedOperationException("Scheduling is not supported"))

    /**
     * Changes when a scheduled message is sent; null sends it immediately.
     * Providers that cannot schedule return an unsupported failure.
     */
    suspend fun rescheduleMessage(
        providerDialogId: String,
        messageId: Long,
        scheduling: MessageScheduling?
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Scheduling is not supported")
    )

    /** Whether the account has server-side notification settings to edit. */
    val supportsNotificationSettings: Boolean
        get() = false

    /** Scope-wide notification settings kept by the server. */
    suspend fun loadNotificationSettings(): Result<NotificationSettings> =
        Result.success(NotificationSettings())

    suspend fun setScopeMuted(scope: NotificationScope, muted: Boolean): Result<Unit> =
        Result.success(Unit)

    /**
     * Chats whose notification settings differ from [scope]'s defaults. These
     * are the per-chat exceptions listed under the category in settings.
     */
    suspend fun loadNotificationExceptions(
        scope: NotificationScope
    ): Result<List<NotificationException>> = Result.success(emptyList())

    /** Restores a chat's notification settings to its scope's defaults. */
    suspend fun resetDialogNotificationSettings(providerDialogId: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Notification settings are not supported")
        )

    /** Whether [loadLastSeenVisibility] and [setLastSeenVisibility] work here. */
    val supportsLastSeenPrivacy: Boolean
        get() = false

    /**
     * Who may see this account's last seen time. Providers without a server-side
     * privacy setting keep the default, and the settings screen hides the row.
     */
    suspend fun loadLastSeenVisibility(): Result<LastSeenVisibility> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Last seen privacy is not supported"
        )
    )

    /**
     * Replaces the last seen rule with [visibility]. The three values the app
     * offers are the whole rule list, so finer provider exceptions ("always
     * share with this contact") are not preserved by a change.
     */
    suspend fun setLastSeenVisibility(visibility: LastSeenVisibility): Result<Unit> =
        Result.failure(
            com.mrboomdev.scrollix.data.model.SessionException(
                com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
                "Last seen privacy is not supported"
            )
        )

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

    /**
     * Adds a user to the account's contact list, or renames an existing entry.
     * [firstName] and [lastName] replace the stored name.
     */
    suspend fun addContact(
        userId: Long,
        firstName: String,
        lastName: String
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Managing contacts is not supported")
    )

    /** Removes a user from the account's contact list. */
    suspend fun removeContact(userId: Long): Result<Unit> = Result.failure(
        UnsupportedOperationException("Managing contacts is not supported")
    )

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

    /** Whether [stopPoll] can close an open poll. */
    val supportsPollClosing: Boolean
        get() = false

    /**
     * Closes the poll in [messageId] so no more votes are accepted. The already
     * collected results stay visible. Only the poll's author may close it, and
     * the provider is the one that enforces that.
     */
    suspend fun stopPoll(
        providerDialogId: String,
        messageId: Long
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Closing polls is not supported"
        )
    )

    /** Whether [translateText] works for this provider. */
    val supportsTranslation: Boolean
        get() = false

    /**
     * Translates [text] into the two-letter language [languageCode] (for
     * example "en"), or into a language the provider picks when it is null.
     * The result is plain text: formatting and custom emoji are not carried
     * over, so the UI renders it without the original's ranges.
     */
    suspend fun translateText(
        text: String,
        languageCode: String? = null
    ): Result<String> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Translation is not supported"
        )
    )

    /** Whether [searchMessages] can return pages older than the first one. */
    val supportsMessageSearchPaging: Boolean
        get() = false

    /**
     * Searches inside one chat. [fromMessageId] is the newest message the page
     * may return (the cursor of the previous page); null starts at the newest
     * match. Providers that cannot page ignore it and always return one page.
     */
    suspend fun searchMessages(
        providerDialogId: String,
        query: String,
        limit: Int = 50,
        fromMessageId: Long? = null
    ): Result<List<ChatMessage>>
    /**
     * Global message search. A non-zero [minTimestamp]/[maxTimestamp] is a date
     * range in epoch milliseconds, inclusive; the provider may apply it on the
     * server instead of returning matches the caller would drop anyway.
     */
    suspend fun searchGlobalMessages(
        query: String,
        limit: Int = 50,
        minTimestamp: Long = 0L,
        maxTimestamp: Long = 0L
    ): Result<List<ChatMessage>>
    suspend fun searchChats(
        query: String,
        limit: Int = 50
    ): Result<List<Dialog>>

    /**
     * Searches public chats the account has not joined yet. [kind] narrows the
     * server filter so the channels and bots tabs can reuse this call.
     */
    suspend fun searchPublicChats(
        query: String,
        limit: Int = 50,
        kind: PublicChatSearchKind = PublicChatSearchKind.Any
    ): Result<List<Dialog>> = Result.success(emptyList())

    /**
     * One page of the messenger's global public post search. Providers without
     * a public post index keep the default failure. Results usually cost one
     * free query per page, so callers must only invoke this for a user action.
     */
    suspend fun searchPublicPosts(
        query: String,
        offset: String = "",
        limit: Int = 30,
        starCount: Long = 0
    ): Result<PostSearchPage> = Result.failure(
        UnsupportedOperationException("Public post search is not supported by this provider")
    )

    /** Free public post search quota for [query], when the provider exposes it. */
    suspend fun loadPublicPostSearchQuota(query: String): Result<PostSearchQuota> =
        Result.failure(
            UnsupportedOperationException("Public post search is not supported by this provider")
        )

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

    /**
     * Sends an animation that came from another provider as raw bytes.
     * Providers convert it into their own attachment format, because animation
     * file ids are not portable between messengers.
     */
    suspend fun sendAnimationBytes(
        providerDialogId: String,
        bytes: ByteArray,
        mimeType: String?,
        width: Int,
        height: Int,
        durationSeconds: Int
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Cross-provider animations are not supported")
    )
    /** Replaces the text of a message that has no media. */
    suspend fun editMessage(
        providerDialogId: String,
        messageId: Long,
        text: String,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList()
    ): Result<Unit>

    /**
     * Whether [editMessageCaption] can replace the caption of a media message.
     * Providers that only edit plain text keep this false, and the message menu
     * does not offer caption editing. Telegram needs a separate call for it,
     * because its text edit rejects media messages.
     */
    val supportsCaptionEditing: Boolean
        get() = false

    /**
     * Replaces the caption of a media message. An empty [caption] removes it,
     * which plain text editing never allows.
     */
    suspend fun editMessageCaption(
        providerDialogId: String,
        messageId: Long,
        caption: String,
        customEmojis: List<CustomEmojiSpan> = emptyList(),
        formatting: List<MessageTextFormat> = emptyList()
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Editing captions is not supported"
        )
    )

    /** Whether this provider can report a message to its moderators. */
    val supportsReporting: Boolean
        get() = false

    /**
     * Reports [messageIds] of one chat. A null [optionId] asks the provider
     * what it requires: the result is either [MessageReportResult.Reported],
     * a list of reasons to choose from, or a request for a comment.
     */
    suspend fun reportMessage(
        providerDialogId: String,
        messageIds: LongArray,
        optionId: String? = null,
        comment: String = ""
    ): Result<MessageReportResult> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Reporting messages is not supported"
        )
    )

    /** Whether [setChecklistTasksDone] can toggle tasks of a checklist message. */
    val supportsChecklists: Boolean
        get() = false

    /**
     * Marks [taskIds] of a checklist message done ([done] = true) or not done.
     * Whether this account is allowed to do it comes from the message itself
     * ([com.mrboomdev.scrollix.data.model.MessageChecklistInfo.canMarkTasksAsDone]).
     */
    suspend fun setChecklistTasksDone(
        providerDialogId: String,
        messageId: Long,
        taskIds: List<Int>,
        done: Boolean
    ): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Checklists are not supported"
        )
    )

    /** Whether [forwardMessages] honors [hideSender] and [removeCaption]. */
    val supportsForwardOptions: Boolean
        get() = false

    /**
     * Forwards [messageIds] from [providerDialogId] into [targetDialogId].
     *
     * [hideSender] re-sends the content without the forward header, so the
     * message looks authored by this account, and [removeCaption] drops media
     * captions. Providers that cannot do either keep
     * [supportsForwardOptions] false, and the picker does not offer them.
     */
    /**
     * Forwards [messageIds] into [targetDialogId]. For a forum, [targetTopicId]
     * names the topic the messages land in; null means the general topic or a
     * provider without topics.
     */
    suspend fun forwardMessages(
        providerDialogId: String,
        messageIds: LongArray,
        targetDialogId: String,
        hideSender: Boolean = false,
        removeCaption: Boolean = false,
        targetTopicId: Long? = null
    ): Result<Unit>
    /**
     * Deletes [messageIds]. With [forEveryone] the deletion reaches the other
     * side and retries through the outbox; without it only this account's copy
     * is removed, and a failure is reported instead of queued.
     */
    suspend fun deleteMessages(
        providerDialogId: String,
        messageIds: LongArray,
        forEveryone: Boolean = true
    ): Result<Unit>

    /** Whether [markAllChatsRead] can clear the unread counters at once. */
    val supportsMarkAllRead: Boolean
        get() = false

    /**
     * Marks every unread chat of this account as read. Providers that can only
     * read one chat at a time keep this false, and the chat list hides the
     * action.
     */
    suspend fun markAllChatsRead(): Result<Unit> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "Marking all chats read is not supported"
        )
    )

    /** Whether [setDialogUnread] can mark a chat unread. */
    val supportsMarkAsUnread: Boolean
        get() = false

    /**
     * Marks the chat as unread ([unread] = true) or clears the mark. Marking
     * read itself happens automatically when the chat is opened with
     * [markMessagesRead].
     */
    suspend fun setDialogUnread(providerDialogId: String, unread: Boolean): Result<Unit> =
        Result.failure(
            com.mrboomdev.scrollix.data.model.SessionException(
                com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
                "Marking chats unread is not supported"
            )
        )

    /** Whether [messageLink] can produce links for this provider. */
    val supportsMessageLinks: Boolean
        get() = false

    /**
     * A shareable link to [messageId], when the provider can produce one.
     * Providers without message links fail with
     * [com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported], and the
     * message menu hides the action.
     */

    suspend fun messageLink(providerDialogId: String, messageId: Long): Result<String> =
        Result.failure(
            com.mrboomdev.scrollix.data.model.SessionException(
                com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
                "Message links are not supported"
            )
        )
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

    /**
     * The reactors of one reaction on a message, paged with [offset]. A message
     * only carries a recent window, so this fills in the rest of the list.
     */
    suspend fun loadMessageReactionReactors(
        providerDialogId: String,
        messageId: Long,
        reaction: MessageReactionOption,
        offset: String = "",
        limit: Int = 50
    ): Result<com.mrboomdev.scrollix.data.model.MessageReactionReactorsPage> = Result.failure(
        com.mrboomdev.scrollix.data.model.SessionException(
            com.mrboomdev.scrollix.data.model.SessionErrorCode.Unsupported,
            "The full reaction list is not supported"
        )
    )
    suspend fun loadPinnedMessageId(providerDialogId: String): Result<Long?>
    /** Whether the account has a server-side device list to manage. */
    val supportsSessionManagement: Boolean
        get() = false

    /**
     * Devices currently signed in to this account. Telegram calls these active
     * sessions; providers without a server-side device list keep the failure.
     */
    suspend fun loadActiveSessions(): Result<List<ActiveSessionInfo>> =
        Result.failure(UnsupportedOperationException("Session management is not supported"))

    /** Signs one other device out of this account. */
    suspend fun terminateSession(sessionId: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Session management is not supported"))

    /** Signs every device except the current one out of this account. */
    suspend fun terminateOtherSessions(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Session management is not supported"))

    /** Traffic counted by the provider for this account's session. */
    suspend fun loadDataUsage(): Result<DataUsageSummary> =
        Result.failure(UnsupportedOperationException("Data usage is not supported by this provider"))

    /** Clears the provider's local traffic counters, not any server-side data. */
    suspend fun resetDataUsageStatistics(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Data usage is not supported by this provider"))

    /** Applies a low/medium/high auto-download profile to every network type. */
    suspend fun applyDataUsagePreset(preset: DataUsagePreset): Result<Unit> =
        Result.failure(
            UnsupportedOperationException("Data usage presets are not supported by this provider")
        )

    suspend fun logout()
    suspend fun close()
}
