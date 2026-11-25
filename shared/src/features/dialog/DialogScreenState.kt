package com.mrboomdev.scrollix.features.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageButton

internal class DialogScreenState {
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
}

@Composable
internal fun rememberDialogScreenState(providerDialogId: String): DialogScreenState {
    return remember(providerDialogId) { DialogScreenState() }
}