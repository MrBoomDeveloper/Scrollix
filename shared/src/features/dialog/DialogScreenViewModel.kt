package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AccountDialogFolder
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageReactionOption
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
    var inputEmojiData by mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    var inputEmojiAdaptive by mutableStateOf<Set<Long>>(emptySet())
    var pendingAttachments by mutableStateOf<List<PendingAttachment>>(emptyList())
    var showAttachmentMenu by mutableStateOf(false)
    var showPollDialog by mutableStateOf(false)
    var showLocationDialog by mutableStateOf(false)
    var videoMessageMode by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var recordingActive by mutableStateOf(false)
    var recordingKind by mutableStateOf(MediaRecordingKind.Voice)
    var recordingStartedAt by mutableLongStateOf(0L)
    var recordingElapsedMs by mutableLongStateOf(0L)
    var recordedMedia by mutableStateOf<RecordedMedia?>(null)
    var threadRoot by mutableStateOf<ChatMessage?>(null)
    /** Message whose text is open in the selectable-text dialog. */
    var selectTextMessage by mutableStateOf<ChatMessage?>(null)
    var isSendingRecording by mutableStateOf(false)
    var recordingError by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var isSending by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
    var editingMessage by mutableStateOf<ChatMessage?>(null)
    var replyingTo by mutableStateOf<ChatMessage?>(null)
    var locallySentMessageIds by mutableStateOf<Set<Long>>(emptySet())
    var composerHeightPx by mutableStateOf(0)
    var draftRestored by mutableStateOf(false)
    var hadServerDraft by mutableStateOf(false)
    var forwardingMessageIds by mutableStateOf<LongArray?>(null)
    var deleteConfirmationIds by mutableStateOf<LongArray?>(null)
    var showUnpinConfirmation by mutableStateOf(false)
    var loadedPinnedMessage by mutableStateOf<ChatMessage?>(null)
    var attemptedPinnedMessageId by mutableStateOf<Long?>(null)
    var loadedReplies by mutableStateOf<Map<Long, ChatMessage>>(emptyMap())
    var showMoreMenu by mutableStateOf(false)
    var openedStickerSetId by mutableStateOf<Long?>(null)
    var webAppButton by mutableStateOf<MessageButton?>(null)
    var showMainWebApp by mutableStateOf(false)
    var showEmojiPicker by mutableStateOf(false)
    var unreadAtOpen by mutableStateOf<Int?>(null)
    var unreadAnchorMessageId by mutableStateOf<Long?>(null)
    var unreadAnchorResolved by mutableStateOf(false)
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

    var searchFilter by mutableStateOf(DialogSearchFilter.All)
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
    internal var wallpaperJob: Job? = null
    internal var lastMarkedReadId: Long? = null
    internal val inFlightEmojiIds = mutableSetOf<Long>()

    init {
        observe()
    }

    override fun onCleared() {
        draftJob?.cancel()
        wallpaperJob?.cancel()
        DialogRecordingState.setRecording(false)
    }
}
