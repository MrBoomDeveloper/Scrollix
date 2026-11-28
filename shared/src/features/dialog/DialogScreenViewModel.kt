package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.BotCallbackAnswer
import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.InlineBotResult
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageTextFormat
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReplyQuote
import com.mrboomdev.scrollix.data.outbox.OutboxEntry
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettings
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.platform.media.MediaRecordingController
import com.mrboomdev.scrollix.platform.media.MediaRecordingKind
import com.mrboomdev.scrollix.platform.media.RecordedMedia

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job

/**
 * Owns everything the chat screen works with: the loaded dialog and its
 * history, the composer and its draft, recording, selection, search and every
 * mutation a message offers. The composables only lay the state out and forward
 * gestures; see [observe] for the data plumbing, [DialogViewModelComposerKt]
 * for the composer and [DialogViewModelActionsKt] for message actions.
 */
internal class DialogScreenViewModel(
    val accountId: String,
    val providerDialogId: String
) : ViewModel() {
    val session: AccountSession? = ApiManager.session(accountId)

    /** Queued text and file sends, rendered as rows while they wait. */
    var pendingMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    /** Outbox entries of this chat, kept so upload progress can refresh them. */
    internal var pendingOutboxEntries by mutableStateOf<List<OutboxEntry>>(emptyList())

    /** Messages grouped for the list, with media albums merged into one row. */
    var displayMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    /** Messages actually shown: the history, or the search results. */
    var visibleMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    var threadRailItems by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    var messagesById by mutableStateOf<Map<Long, ChatMessage>>(emptyMap())
        internal set

    var gapRowId by mutableStateOf<String?>(null)
        internal set

    var dialogs by mutableStateOf<List<Dialog>>(emptyList())
        internal set

    var folders by mutableStateOf<List<AccountDialogFolder>>(emptyList())
        internal set

    var allSessions by mutableStateOf<Map<String, AccountSession>>(emptyMap())
        internal set

    var isConnected by mutableStateOf(true)
        internal set

    var settings by mutableStateOf(AppSettingsStore.state.value)
        internal set

    var history by mutableStateOf(
        MessageHistory(
            hasMoreOlder = false,
            isLoadingInitial = false
        )
    )
        internal set

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    var appWallpaper by mutableStateOf<ImageBitmap?>(null)
        internal set

    var wallpaperImage by mutableStateOf(WallpaperImageCache.last)
        internal set

    var doubleTapReaction by mutableStateOf<MessageReactionOption?>(null)
        internal set

    /** The chat itself; null until the dialog list has loaded. */
    val dialog: Dialog?
        get() = dialogs.firstOrNull { entry -> entry.providerDialogId == providerDialogId }

    /** The in-chat search field; the results are exposed through [searchResults]. */
    val searchQuery = TextFieldState()

    var isLoadingGap by mutableStateOf(false)
    var showTopics by mutableStateOf(false)
    var input by mutableStateOf("")
    var inputSelection by mutableStateOf(TextRange.Zero)
    var inputCustomEmojis by mutableStateOf<List<CustomEmojiSpan>>(emptyList())
    /** Rich text ranges of [input], kept in sync by every composer edit. */
    var inputFormatting by mutableStateOf<List<MessageTextFormat>>(emptyList())
    /** Undo/redo steps of the draft; see [ComposerHistory]. */
    internal val composerHistory = ComposerHistory()
    var canUndoComposer by mutableStateOf(false)
        internal set
    var canRedoComposer by mutableStateOf(false)
        internal set
    var showLinkDialog by mutableStateOf(false)
    var inputEmojiData by mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    var inputEmojiAdaptive by mutableStateOf<Set<Long>>(emptySet())
    var pendingAttachments by mutableStateOf<List<PendingAttachment>>(emptyList())
    /** Send photos as photos (compressed) or as files for the next send. */
    var attachmentCompressPhotos by mutableStateOf(AppSettingsStore.state.value.compressPhotos)
        internal set

    /** Index of the row the photo editor is open for, if any. */
    var editingAttachmentIndex by mutableStateOf<Int?>(null)
    /** Index of the row the video trimmer is open for, if any. */
    var trimmingAttachmentIndex by mutableStateOf<Int?>(null)
    /** Outbox entry behind the attachment send the review sheet is showing. */
    var attachmentSendEntryId by mutableStateOf<String?>(null)
        internal set
    /** True when that send was paused from the review sheet. */
    var attachmentSendPaused by mutableStateOf(false)
        internal set
    /** Permanent failure of that send, shown inside the review sheet. */
    var attachmentSendError by mutableStateOf<String?>(null)
        internal set
    /** The in-flight attachment send; pausing cancels this job. */
    internal var attachmentSendJob: kotlinx.coroutines.Job? = null
    var showAttachmentMenu by mutableStateOf(false)
    var showPollDialog by mutableStateOf(false)
    /** Picker of the account's saved quick replies. */
    var showQuickReplies by mutableStateOf(false)
    var showLocationDialog by mutableStateOf(false)
    /** The composer's scheduling picker; true while it is open. */
    var showSchedulePicker by mutableStateOf(false)
    /** The list of messages waiting in the chat's schedule queue. */
    var showScheduledMessages by mutableStateOf(false)
    /** The chat's auto-delete timer picker. */
    var showAutoDeleteTime by mutableStateOf(false)
    var videoMessageMode by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var recordingActive by mutableStateOf(false)
    var recordingKind by mutableStateOf(MediaRecordingKind.Voice)
    var recordingStartedAt by mutableLongStateOf(0L)
    var recordingElapsedMs by mutableLongStateOf(0L)
    /** Recent microphone levels while recording, oldest first. */
    var recordingWaveform by mutableStateOf<List<Float>>(emptyList())
    var recordedMedia by mutableStateOf<RecordedMedia?>(null)
    var threadRoot by mutableStateOf<ChatMessage?>(null)
    /** Message whose text is open in the selectable-text dialog. */
    var selectTextMessage by mutableStateOf<ChatMessage?>(null)
    /** Message whose metadata sheet is open. */
    var detailsMessage by mutableStateOf<ChatMessage?>(null)
    /** Message whose bot invoice is being paid. */
    var paymentMessage by mutableStateOf<ChatMessage?>(null)
    /** Message whose reactor list is open, and the reaction that opened it. */
    var reactionListMessage by mutableStateOf<ChatMessage?>(null)
    var reactionListFocusKey by mutableStateOf<String?>(null)
    var isSendingRecording by mutableStateOf(false)
    var recordingError by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var isSending by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
    var editingMessage by mutableStateOf<ChatMessage?>(null)
    var replyingTo by mutableStateOf<ChatMessage?>(null)
    /** Chosen passage of [replyingTo], when the reply quotes one. */
    var replyQuote by mutableStateOf<MessageReplyQuote?>(null)
    var showChecklistDialog by mutableStateOf(false)
    /** Checklist message the "add tasks" editor appends to. */
    var addChecklistTasksTarget by mutableStateOf<ChatMessage?>(null)
    var locallySentMessageIds by mutableStateOf<Set<Long>>(emptySet())
    var composerHeightPx by mutableStateOf(0)
    var draftRestored by mutableStateOf(false)
    var hadServerDraft by mutableStateOf(false)
    /** Draft text the server last acknowledged; remote edits are diffed against it. */
    var serverDraftText by mutableStateOf("")
    var forwardingMessageIds by mutableStateOf<LongArray?>(null)
    var deleteConfirmationIds by mutableStateOf<LongArray?>(null)
    var showUnpinConfirmation by mutableStateOf(false)
    var loadedPinnedMessage by mutableStateOf<ChatMessage?>(null)
    var attemptedPinnedMessageId by mutableStateOf<Long?>(null)
    var loadedReplies by mutableStateOf<Map<Long, ChatMessage>>(emptyMap())
    var showMoreMenu by mutableStateOf(false)
    var openedStickerSetId by mutableStateOf<Long?>(null)
    var webAppButton by mutableStateOf<MessageButton?>(null)
    /** Alert a bot returned for a callback button; null when there is none. */
    var callbackAlert by mutableStateOf<BotCallbackAnswer?>(null)
    /** Command menu of the chat's bot, empty when the chat has no bot. */
    var botCommands by mutableStateOf<List<BotCommandInfo>>(emptyList())
        internal set
    /** "What can this bot do" line, shown while a bot chat is still empty. */
    var botDescription by mutableStateOf("")
        internal set
    /** Inline bot named by the draft, null when the picker should be hidden. */
    internal var inlineBotQuery by mutableStateOf<InlineBotQuery?>(null)
    internal var inlineBotJob by mutableStateOf<Job?>(null)
    /** Results of the current inline query; stale pages are dropped. */
    var inlineBotResults by mutableStateOf<List<InlineBotResult>>(emptyList())
        internal set
    var inlineBotQueryId by mutableStateOf<Long?>(null)
        internal set
    var inlineBotLoading by mutableStateOf(false)
        internal set
    /** Id of the result being sent, so its row can show progress. */
    var inlineBotSending by mutableStateOf<String?>(null)
        internal set
    var showMainWebApp by mutableStateOf(false)
    var showEmojiPicker by mutableStateOf(false)
    var unreadAtOpen by mutableStateOf<Int?>(null)
    var unreadAnchorMessageId by mutableStateOf<Long?>(null)
    var unreadAnchorResolved by mutableStateOf(false)
    /** Hides the unread jump after it was used once on this screen. */
    var unreadJumpDismissed by mutableStateOf(false)

    /** Translated text per message id; lives only while this screen is open. */
    var translations by mutableStateOf<Map<Long, String>>(emptyMap())
    var hiddenTranslations by mutableStateOf<Set<Long>>(emptySet())
    var translatingMessageIds by mutableStateOf<Set<Long>>(emptySet())

    /** Language translations target: the saved choice or the device one. */
    val translationLanguage: String
        get() = translationTargetLanguage(settings.translationLanguage, Locale.current.language)

    /** True while every translated bubble hides its translation. */
    val allTranslationsHidden: Boolean
        get() = translations.isNotEmpty() &&
            translations.keys.all { messageId -> messageId in hiddenTranslations }

    /** True when at least one translation is loaded on this screen. */
    val hasTranslations: Boolean
        get() = translations.isNotEmpty()

    var showTranslationLanguage by mutableStateOf(false)
    var isTranslatingChat by mutableStateOf(false)
    var translationProgress by mutableStateOf<TranslationProgress?>(null)

    /** True while messages paged in later are translated as they load. */
    var isChatTranslationEnabled by mutableStateOf(false)
        internal set
    var isSearchPending by mutableStateOf(false)
    var customEmojiData by mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    var customEmojiAdaptive by mutableStateOf<Set<Long>>(emptySet())
    var selectedMessageIds by mutableStateOf<Set<Long>>(emptySet())
    var animatedMessageIds by mutableStateOf<Set<Long>>(emptySet())
    var attemptedReplyIds by mutableStateOf<Set<Long>>(emptySet())
    var searchResults by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    /** Raw page the server returned for the current query, before filters. */
    var rawSearchResults by mutableStateOf<List<ChatMessage>>(emptyList())
        internal set

    var isLoadingMoreSearch by mutableStateOf(false)
        internal set

    /** True once an older page came back empty, so paging stops asking. */
    var searchReachedEnd by mutableStateOf(false)
        internal set

    var searchFilter by mutableStateOf(DialogSearchFilter.All)
        internal set

    /** Date window applied to the loaded in-chat search page. */
    var searchDateFilter by mutableStateOf(SearchDateFilter.AnyTime)
        internal set

    var headerStatus by mutableStateOf<DialogHeaderStatus?>(null)
        internal set

    var forwardTargets by mutableStateOf<List<Dialog>>(emptyList())
        internal set

    var filteredForwardTargets by mutableStateOf<List<Dialog>>(emptyList())
        internal set

    var forwardFolderOptions by mutableStateOf<List<Pair<String, String>>>(emptyList())
        internal set

    var forwardQuery by mutableStateOf("")
    var forwardFolderKey by mutableStateOf("all")
    var forwardHideSender by mutableStateOf(false)
    var forwardRemoveCaption by mutableStateOf(false)

    /** Only providers that can re-send content without the forward header offer it. */
    val supportsForwardOptions: Boolean
        get() = session?.supportsForwardOptions == true

    /** Only providers that can flag a chat unread offer the menu entry. */
    val supportsMarkAsUnread: Boolean
        get() = session?.supportsMarkAsUnread == true

    internal var activeRecorder: MediaRecordingController? = null
    internal var draftJob: Job? = null
    internal var translationJob: Job? = null
    internal var wallpaperJob: Job? = null
    internal var lastMarkedReadId: Long? = null
    internal val inFlightEmojiIds = mutableSetOf<Long>()

    init {
        observe()
        loadBotCommands()
    }

    override fun onCleared() {
        draftJob?.cancel()
        translationJob?.cancel()
        wallpaperJob?.cancel()
        DialogRecordingState.setRecording(false)
    }
}
