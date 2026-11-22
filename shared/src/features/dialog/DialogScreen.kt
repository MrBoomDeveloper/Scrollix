package com.mrboomdev.scrollix.features.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.mrboomdev.scrollix.ui.components.StickerSetDialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.test.microphone_icon
import com.mrboomdev.scrollix.ui.components.MediaRecordingKind
import com.mrboomdev.scrollix.ui.components.RecordedMedia
import com.mrboomdev.scrollix.ui.components.PollCreateDialog
import com.mrboomdev.scrollix.ui.components.LocationPickerDialog
import com.mrboomdev.scrollix.ui.components.RecordedMediaDialog
import com.mrboomdev.scrollix.ui.components.rememberMediaRecorder
import com.mrboomdev.scrollix.ui.icons.videocam_icon
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.ChatWallpaper
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogFolderEmoji
import com.mrboomdev.scrollix.data.model.DialogFolderEmojiFormat
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageHistory
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessagePollInfo
import com.mrboomdev.scrollix.data.model.MessagePollOptionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionOption
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageStickerFormat
import com.mrboomdev.scrollix.data.model.OutgoingAttachment
import com.mrboomdev.scrollix.data.model.groupMessageAlbums
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DialogScrollPositionStore
import com.mrboomdev.scrollix.data.storage.readFileBytes
import com.mrboomdev.scrollix.data.storage.saveFileToDevice
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.wallpaper
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.ChatAnimationPreview
import com.mrboomdev.scrollix.ui.components.ChatSticker
import com.mrboomdev.scrollix.ui.components.ChatVideoPreview
import com.mrboomdev.scrollix.ui.components.CustomEmojiMessageText
import com.mrboomdev.scrollix.ui.components.EmojiPicker
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.CustomEmojiMessageText
import com.mrboomdev.scrollix.ui.components.FolderEmojiImage
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.components.loadingBlur
import com.mrboomdev.scrollix.ui.components.SavedMessagesAvatar
import com.mrboomdev.scrollix.ui.components.MediaViewer
import com.mrboomdev.scrollix.ui.components.MessageGallery
import com.mrboomdev.scrollix.ui.components.MessageDatePill
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import com.mrboomdev.scrollix.ui.components.PickedFile
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.components.VoiceMessagePlayer
import com.mrboomdev.scrollix.ui.components.messageDate
import com.mrboomdev.scrollix.ui.components.rememberFilePicker
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.isTwoPaneLayout
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.close_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val MaxAlbumAttachments = 10


/**
 * Keeps decoded wallpapers outside the composition: switching dialogs (or
 * recreating the dialog screen) can show the previous image immediately
 * instead of flashing the default wallpaper.
 */
private object WallpaperImageCache {
    private val byDialog = mutableMapOf<String, ImageBitmap>()

    var last: ImageBitmap? = null
        private set

    fun exact(key: String): ImageBitmap? = byDialog[key]

    fun put(key: String, image: ImageBitmap) {
        byDialog[key] = image
        last = image
    }
}

@Composable
private fun PickerPopupAnimation(
    visibleState: MutableTransitionState<Boolean>,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(160)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(160)
        ),
        exit = fadeOut(tween(120)) + scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(120)
        )
    ) {
        content()
    }
}

/**
 * Shifts or drops custom emoji spans after the draft was edited, so the
 * entities keep pointing at the text they were inserted with.
 */
private fun adjustCustomEmojiSpans(
    oldText: String,
    newText: String,
    spans: List<CustomEmojiSpan>
): List<CustomEmojiSpan> {
    if (spans.isEmpty() || oldText == newText) return spans

    var prefix = 0
    while (
        prefix < oldText.length &&
        prefix < newText.length &&
        oldText[prefix] == newText[prefix]
    ) {
        prefix++
    }

    var suffix = 0
    while (
        suffix < oldText.length - prefix &&
        suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    val removed = oldText.length - prefix - suffix
    val inserted = newText.length - prefix - suffix
    val delta = inserted - removed
    val editEnd = prefix + removed

    return spans.mapNotNull { span ->
        when {
            span.end <= prefix -> span

            span.start >= editEnd -> span.copy(
                start = span.start + delta,
                end = span.end + delta
            )

            else -> null
        }
    }
}

private fun wallpaperBrush(wallpaper: ChatWallpaper): Brush {
    val colors = wallpaper.colors.map { color ->
        Color(color or (0xFF shl 24))
    }

    return when (colors.size) {
        0 -> SolidColor(Color.Transparent)
        1 -> SolidColor(colors.first())
        else -> Brush.verticalGradient(colors)
    }
}

@Composable
fun DialogScreen(
    accountId: String,
    providerDialogId: String,
    initialMessageId: Long? = null,
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (Long) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val isTwoPaneWindow = isTwoPaneLayout()
    val session = remember(accountId) { ApiManager.session(accountId) }
    val fallbackDialogs = remember { MutableStateFlow<List<Dialog>>(emptyList()) }
    val fallbackHistory = remember {
        MutableStateFlow(
            MessageHistory(
                hasMoreOlder = false,
                isLoadingInitial = false
            )
        )
    }

    val dialogs by (session?.dialogs ?: fallbackDialogs).collectAsState()
    val historyFlow = remember(session, providerDialogId) {
        session?.messageHistory(providerDialogId) ?: fallbackHistory
    }
    val history by historyFlow.collectAsState()
    val messages = history.messages
    val displayMessages = remember(messages) { messages.groupMessageAlbums() }
    val dialog = dialogs.firstOrNull { it.providerDialogId == providerDialogId }
    val settings by AppSettingsStore.state.collectAsState()
    var appWallpaper by remember(settings.wallpaperUpdatedAt) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(settings.hasCustomWallpaper, settings.wallpaperUpdatedAt) {
        appWallpaper = if (settings.hasCustomWallpaper) {
            val bytes = withContext(Dispatchers.IO) {
                AppSettingsStore.readWallpaper()
            }

            bytes?.let { data ->
                withContext(Dispatchers.Default) {
                    decodeImageBitmap(data)
                }
            }
        } else {
            null
        }
    }
    // Starts with the last decoded wallpaper, so even a fresh composition for
    // a new dialog doesn't flash the default one while the image loads.
    var wallpaperImage by remember { mutableStateOf(WallpaperImageCache.last) }

    LaunchedEffect(dialog?.providerDialogId, dialog?.wallpaper, session) {
        val currentDialog = dialog ?: return@LaunchedEffect
        val wallpaper = currentDialog.wallpaper

        if (wallpaper == null) {
            wallpaperImage = null
            return@LaunchedEffect
        }

        val cacheKey = buildString {
            append(currentDialog.providerDialogId)
            append(':')
            append(wallpaper.fileUniqueId ?: wallpaper.fileId)
            append(':')
            append(wallpaper.thumbnailUniqueId ?: wallpaper.thumbnailFileId)
        }

        WallpaperImageCache.exact(cacheKey)?.let { cached ->
            wallpaperImage = cached
            return@LaunchedEffect
        }

        var image: ImageBitmap? = null
        val thumbnailFileId = wallpaper.thumbnailFileId

        // Cached media is addressed by the durable remote unique id, so a
        // custom wallpaper is restored right away instead of waiting for TDLib
        // to replay the chat: the file ids stored in a cached dialog are only
        // valid for the run that produced them.
        val thumbnailBytes = wallpaper.thumbnailUniqueId
            ?.let { uniqueId -> session?.loadCachedMedia(uniqueId) }
            ?: thumbnailFileId?.let { fileId -> session?.loadFile(fileId)?.getOrNull() }

        if (thumbnailBytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(thumbnailBytes)
            }
        }

        val fileId = wallpaper.fileId

        if (image == null && fileId != null && fileId != thumbnailFileId) {
            val bytes = wallpaper.fileUniqueId
                ?.let { uniqueId -> session?.loadCachedMedia(uniqueId) }
                ?: session?.loadFile(fileId)?.getOrNull()

            if (bytes != null) {
                // Animated wallpapers (video/TGS) can't be decoded as a bitmap,
                // in that case the thumbnail stays as the wallpaper.
                image = withContext(Dispatchers.Default) {
                    decodeImageBitmap(bytes)
                }
            }
        }

        image?.let { decoded ->
            WallpaperImageCache.put(cacheKey, decoded)
            wallpaperImage = decoded
        }
    }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var input by remember(providerDialogId) { mutableStateOf("") }
    var inputCustomEmojis by remember(providerDialogId) {
        mutableStateOf<List<CustomEmojiSpan>>(emptyList())
    }
    var inputEmojiData by remember(providerDialogId) {
        mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    }
    var inputEmojiAdaptive by remember(providerDialogId) {
        mutableStateOf<Set<Long>>(emptySet())
    }
    var pendingAttachments by remember(providerDialogId) {
        mutableStateOf<List<PendingAttachment>>(emptyList())
    }
    var showAttachmentMenu by remember(providerDialogId) { mutableStateOf(false) }
    var showPollDialog by remember(providerDialogId) { mutableStateOf(false) }
    var showLocationDialog by remember(providerDialogId) { mutableStateOf(false) }
    val mediaRecorder = rememberMediaRecorder()
    var videoMessageMode by remember(providerDialogId) { mutableStateOf(false) }
    var isRecording by remember(providerDialogId) { mutableStateOf(false) }
    var recordingKind by remember(providerDialogId) {
        mutableStateOf(MediaRecordingKind.Voice)
    }
    var recordingStartedAt by remember(providerDialogId) { mutableLongStateOf(0L) }
    var recordingElapsedMs by remember(providerDialogId) { mutableLongStateOf(0L) }
    var recordedMedia by remember(providerDialogId) { mutableStateOf<RecordedMedia?>(null) }
    var isSendingRecording by remember(providerDialogId) { mutableStateOf(false) }
    var recordingError by remember(providerDialogId) { mutableStateOf<String?>(null) }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordedMedia = mediaRecorder.stop()
        recordingElapsedMs = 0L
    }

    LaunchedEffect(isRecording, recordingKind, recordingStartedAt) {
        if (!isRecording) return@LaunchedEffect

        while (isRecording) {
            recordingElapsedMs = System.currentTimeMillis() - recordingStartedAt

            if (recordingKind == MediaRecordingKind.VideoNote &&
                recordingElapsedMs >= 60_000L
            ) {
                stopRecording()
                break
            }

            delay(100L)
        }
    }

    // Drafts reset to an empty string programmatically, which never reaches
    // onValueChange, so their custom emoji spans are dropped here.
    LaunchedEffect(input) {
        if (input.isEmpty() && inputCustomEmojis.isNotEmpty()) {
            inputCustomEmojis = emptyList()
        }
    }

    // The composer renders custom emoji as inline stickers, so their assets
    // have to be loaded as soon as they are inserted into the draft.
    LaunchedEffect(session, inputCustomEmojis) {
        val currentSession = session ?: return@LaunchedEffect
        val missing = inputCustomEmojis
            .map { span -> span.emojiId }
            .distinct()
            .filter { id -> id !in inputEmojiData }

        if (missing.isEmpty()) return@LaunchedEffect

        val loaded = missing.mapNotNull { id ->
            currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
        }.toMap()

        if (loaded.isNotEmpty()) {
            inputEmojiData = inputEmojiData + loaded.mapValues { (_, asset) -> asset.data }
            inputEmojiAdaptive = inputEmojiAdaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }
    var error by remember(providerDialogId) { mutableStateOf<String?>(null) }
    var isSending by remember(providerDialogId) { mutableStateOf(false) }
    var searchOpen by remember(providerDialogId) { mutableStateOf(false) }
    var scrollRequest by remember(providerDialogId) { mutableStateOf<ScrollRequest?>(null) }
    val searchQuery = rememberTextFieldState()
    var editingMessage by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var replyingTo by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageIds by remember(providerDialogId) { mutableStateOf<Set<Long>>(emptySet()) }
    var composerHeightPx by remember(providerDialogId) { mutableStateOf(0) }
    val composerInset = with(LocalDensity.current) { composerHeightPx.toDp() }

    // The composer text is stored on Telegram's servers as a draft, so it is
    // restored when the chat is reopened on this or any other device.
    var draftRestored by remember(providerDialogId) { mutableStateOf(false) }
    var hadServerDraft by remember(providerDialogId) { mutableStateOf(false) }

    LaunchedEffect(session, providerDialogId) {
        val currentSession = session ?: return@LaunchedEffect

        currentSession.loadDraft(providerDialogId).onSuccess { draft ->
            hadServerDraft = draft.text.isNotEmpty() || draft.customEmojis.isNotEmpty()

            if (input.isEmpty() && editingMessage == null) {
                input = draft.text
                inputCustomEmojis = draft.customEmojis
            }
        }

        draftRestored = true
    }

    LaunchedEffect(input, inputCustomEmojis, session, providerDialogId, editingMessage, draftRestored) {
        if (!draftRestored || editingMessage != null) return@LaunchedEffect

        // Never clear an unreadable draft just because the composer is empty.
        if (input.isEmpty() && inputCustomEmojis.isEmpty() && !hadServerDraft) {
            return@LaunchedEffect
        }

        // Debounce so a draft write doesn't race every keystroke.
        delay(600.milliseconds)
        session?.saveDraft(providerDialogId, input, inputCustomEmojis)
        hadServerDraft = input.isNotEmpty() || inputCustomEmojis.isNotEmpty()
    }
    var forwardingMessageIds by remember(providerDialogId) {
        mutableStateOf<LongArray?>(null)
    }
    var showUnpinConfirmation by remember(providerDialogId) { mutableStateOf(false) }
    var loadedPinnedMessage by remember(providerDialogId) { mutableStateOf<ChatMessage?>(null) }
    var attemptedPinnedMessageId by remember(providerDialogId) { mutableStateOf<Long?>(null) }
    var loadedReplies by remember(providerDialogId) {
        mutableStateOf<Map<Long, ChatMessage>>(emptyMap())
    }
    var attemptedReplyIds by remember(providerDialogId) { mutableStateOf<Set<Long>>(emptySet()) }
    var showMoreMenu by remember(providerDialogId) { mutableStateOf(false) }
    var openedStickerSetId by remember(providerDialogId) { mutableStateOf<Long?>(null) }
    var showEmojiPicker by remember(providerDialogId) { mutableStateOf(false) }
    var emojiPickerAnchor by remember { mutableStateOf<Rect?>(null) }
    var showHoverPicker by remember { mutableStateOf(false) }
    val emojiButtonInteraction = remember { MutableInteractionSource() }
    val emojiPickerInteraction = remember { MutableInteractionSource() }
    val isEmojiButtonHovered by emojiButtonInteraction.collectIsHoveredAsState()
    val isEmojiPickerHovered by emojiPickerInteraction.collectIsHoveredAsState()

    LaunchedEffect(isTwoPaneWindow, isEmojiButtonHovered, isEmojiPickerHovered) {
        if (!isTwoPaneWindow) {
            showHoverPicker = false
            return@LaunchedEffect
        }

        if (isEmojiButtonHovered || isEmojiPickerHovered) {
            showHoverPicker = true
        } else {
            // Give the pointer time to travel from the button into the popup.
            delay(200.milliseconds)
            showHoverPicker = false
        }
    }

    // The unread boundary is captured once per opened dialog. Marking the
    // messages read must not remove the header while the dialog stays open.
    var unreadAtOpen by remember(providerDialogId) { mutableStateOf<Int?>(null) }
    var unreadAnchorMessageId by remember(providerDialogId) {
        mutableStateOf<Long?>(null)
    }
    var unreadAnchorResolved by remember(providerDialogId) {
        mutableStateOf(false)
    }

    LaunchedEffect(providerDialogId, dialog?.unreadCount) {
        if (unreadAtOpen == null && dialog != null) {
            unreadAtOpen = dialog.unreadCount
        }
    }

    LaunchedEffect(providerDialogId, unreadAtOpen, messages.size) {
        if (unreadAnchorResolved) return@LaunchedEffect

        val unread = unreadAtOpen ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect

        unreadAnchorResolved = true
        if (unread <= 0) return@LaunchedEffect

        // The unread tail starts this many messages before the newest one.
        val start = (messages.size - unread).coerceIn(0, messages.lastIndex)
        val anchorIndex = (start until messages.size).firstOrNull { index ->
            !messages[index].isOutgoing
        } ?: start

        val anchorId = messages[anchorIndex].messageId
        unreadAnchorMessageId = displayMessages
            .firstOrNull { message -> anchorId in message.allMessageIds }
            ?.messageId
            ?: anchorId
    }

    val messageListState = rememberDialogMessageListState(
        providerDialogId = providerDialogId,
        history = history,
        displayMessages = displayMessages,
        initialMessageId = initialMessageId,
        savedPosition = remember(accountId, providerDialogId) {
            DialogScrollPositionStore.get(accountId, providerDialogId)
        },
        trackPosition = searchQuery.text.isBlank(),
        loadOlder = {
            session?.loadOlderMessages(providerDialogId)
                ?: Result.failure(IllegalStateException("Account session not found"))
        },
        loadUntilLoaded = { messageId ->
            session?.loadMessagesUntil(providerDialogId, messageId)
                ?: Result.failure(IllegalStateException("Account session not found"))
        },
        loadLatest = {
            session?.loadMessages(providerDialogId)
                ?: Result.failure(IllegalStateException("Account session not found"))
        },
        onPositionChanged = { position ->
            DialogScrollPositionStore.save(accountId, providerDialogId, position)
        },
        onError = { throwable ->
            error = throwable.message ?: "Failed to load messages"
        }
    )
    val listState = messageListState.listState

    fun addAttachments(picked: List<PickedFile>, asPhoto: Boolean) {
        if (picked.isEmpty() || editingMessage != null) return

        val free = (MaxAlbumAttachments - pendingAttachments.size).coerceAtLeast(0)
        if (free == 0) {
            error = "You can attach up to $MaxAlbumAttachments files"
            return
        }

        pendingAttachments = pendingAttachments + picked
            .take(free)
            .map { file -> PendingAttachment(file = file, asPhoto = asPhoto) }

        if (picked.size > free) {
            error = "Only $MaxAlbumAttachments files can be sent at once"
        }
    }

    val filePicker = rememberFilePicker(
        onImagesPicked = { picked -> addAttachments(picked, asPhoto = true) },
        onFilesPicked = { picked -> addAttachments(picked, asPhoto = false) }
    )

    fun deleteMessages(messageIds: LongArray) {
        if (session == null || messageIds.isEmpty()) return

        scope.launch {
            session.deleteMessages(providerDialogId, messageIds)
                .onSuccess {
                    selectedMessageIds = selectedMessageIds - messageIds.toSet()
                }
                .onFailure { error = it.message ?: "Failed to delete messages" }
        }
    }

    fun forwardMessages(messageIds: LongArray, targetDialogId: String) {
        val currentSession = session ?: return

        scope.launch {
            currentSession.forwardMessages(
                providerDialogId = providerDialogId,
                messageIds = messageIds,
                targetDialogId = targetDialogId
            ).onSuccess {
                selectedMessageIds = emptySet()
            }.onFailure {
                error = it.message ?: "Failed to forward messages"
            }
        }
    }

    fun pinMessage(message: ChatMessage, isPinned: Boolean) {
        if (session == null) return

        scope.launch {
            session.setMessagePinned(providerDialogId, message.messageId, isPinned)
                .onFailure { error = it.message ?: "Failed to pin message" }
        }
    }

    LaunchedEffect(session, providerDialogId) {
        error = null

        if (session == null) {
            error = "Account session not found"
            return@LaunchedEffect
        }

        session.loadMessages(providerDialogId)
            .onFailure { error = it.message ?: "Failed to load messages" }
    }

    LaunchedEffect(session, providerDialogId) {
        session?.loadPinnedMessageId(providerDialogId)
    }

    LaunchedEffect(session, providerDialogId, dialog?.pinnedMessageId, messages) {
        val pinnedId = dialog?.pinnedMessageId

        if (pinnedId == null) {
            loadedPinnedMessage = null
            attemptedPinnedMessageId = null
            return@LaunchedEffect
        }

        if (messages.any { message -> pinnedId in message.allMessageIds }) {
            return@LaunchedEffect
        }
        if (loadedPinnedMessage?.messageId == pinnedId) return@LaunchedEffect
        if (attemptedPinnedMessageId == pinnedId) return@LaunchedEffect

        attemptedPinnedMessageId = pinnedId
        loadedPinnedMessage = session?.loadMessage(providerDialogId, pinnedId)?.getOrNull()
    }

    val lastMessageId = messages.lastOrNull()?.messageId

    var searchResults by remember(providerDialogId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isSearchPending by remember(providerDialogId) { mutableStateOf(false) }
    var customEmojiData by remember(providerDialogId) {
        mutableStateOf<Map<Long, ByteArray>>(emptyMap())
    }
    var customEmojiAdaptive by remember(providerDialogId) {
        mutableStateOf<Set<Long>>(emptySet())
    }

    LaunchedEffect(session, messages) {
        if (session == null) return@LaunchedEffect

        val ids = messages
            .flatMap { message ->
                message.customEmojis.map { span -> span.emojiId } +
                    message.reactions.mapNotNull { reaction -> reaction.customEmojiId } +
                    message.poll?.options.orEmpty().flatMap { option ->
                        option.customEmojis.map { span -> span.emojiId }
                    }
            }
            .distinct()
            .filter { id -> id !in customEmojiData }

        if (ids.isEmpty()) return@LaunchedEffect

        val loaded = ids.mapNotNull { id ->
            session.loadCustomEmoji(id)?.let { asset -> id to asset }
        }.toMap()

        if (loaded.isNotEmpty()) {
            customEmojiData = customEmojiData + loaded.mapValues { (_, asset) -> asset.data }
            customEmojiAdaptive = customEmojiAdaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }

    LaunchedEffect(session, providerDialogId, searchQuery.text) {
        val query = searchQuery.text.trim()

        if (query.isEmpty() || session == null) {
            searchResults = emptyList()
            isSearchPending = false
            return@LaunchedEffect
        }

        isSearchPending = true
        delay(300.milliseconds)
        searchResults = session.searchMessages(providerDialogId, query.toString())
            .getOrDefault(emptyList())
        isSearchPending = false
    }

    val isMobileLayout = currentAdaptiveLayoutInfo().size < AdaptiveLayoutSize.EXPANDED
    val doubleTapReaction = remember(
        settings.doubleTapReaction,
        settings.quickReactions,
        settings.pinnedCustomReactions
    ) {
        val preferred = settings.doubleTapReaction

        if (preferred != null) {
            MessageReactionOption(
                emoji = preferred.emoji,
                customEmojiId = preferred.customEmojiId
            )
        } else {
            settings.quickReactions.firstOrNull()?.let { emoji ->
                MessageReactionOption(emoji)
            }
        }
    }

    val visibleMessages = remember(displayMessages, searchResults, searchQuery.text) {
        if (searchQuery.text.isBlank()) displayMessages else searchResults.groupMessageAlbums()
    }

    LaunchedEffect(session, providerDialogId, visibleMessages, messages, loadedReplies) {
        val currentSession = session ?: return@LaunchedEffect

        val missingIds = visibleMessages
            .mapNotNull { message -> message.replyTo?.messageId }
            .filter { id ->
                messages.none { it.messageId == id } &&
                    id !in loadedReplies &&
                    id !in attemptedReplyIds
            }
            .distinct()

        if (missingIds.isEmpty()) return@LaunchedEffect

        attemptedReplyIds = attemptedReplyIds + missingIds

        val loaded = missingIds.mapNotNull { id ->
            currentSession.loadMessage(providerDialogId, id).getOrNull()
                ?.let { message -> id to message }
        }.toMap()

        if (loaded.isNotEmpty()) {
            loadedReplies = loadedReplies + loaded
        }
    }

    // All scrolling intents go through one queue. Waiting for the search
    // field to be cleared here guarantees that the LazyColumn already shows
    // the full history again before a jump starts animating.
    //
    // The jump runs in the screen scope on purpose: clearing the request below
    // restarts this effect, and a restart cancels its body — which would stop
    // the jump animation on its first frame and make the buttons do nothing.
    LaunchedEffect(scrollRequest, searchQuery.text) {
        val request = scrollRequest ?: return@LaunchedEffect
        if (searchQuery.text.isNotBlank()) return@LaunchedEffect

        scrollRequest = null

        scope.launch {
            when (request) {
                is ScrollRequest.Message -> {
                    if (!messageListState.jumpToMessage(request.messageId)) {
                        error = "Couldn't load the message"
                    }
                }

                ScrollRequest.Latest -> messageListState.jumpToLatest()
                ScrollRequest.First -> messageListState.jumpToFirstMessage()
            }
        }
    }

    LaunchedEffect(lastMessageId) {
        if (messages.isEmpty()) return@LaunchedEffect

        session?.markMessagesRead(
            providerDialogId = providerDialogId,
            messageIds = messages
                .takeLast(50)
                .map { it.messageId }
                .toLongArray()
        )
    }

    fun applyReaction(message: ChatMessage, option: MessageReactionOption) {
        val currentSession = session ?: return
        val isChosen = message.reactions.any { reaction ->
            reaction.isChosen && if (option.customEmojiId != null) {
                reaction.customEmojiId == option.customEmojiId
            } else {
                reaction.customEmojiId == null && reaction.emoji == option.emoji
            }
        }

        scope.launch {
            if (isChosen) {
                currentSession.removeMessageReaction(
                    providerDialogId = providerDialogId,
                    messageId = message.messageId,
                    emoji = option.emoji,
                    customEmojiId = option.customEmojiId
                )
            } else {
                currentSession.addMessageReaction(
                    providerDialogId = providerDialogId,
                    messageId = message.messageId,
                    emoji = option.emoji,
                    customEmojiId = option.customEmojiId
                )
            }
        }
    }

    fun send() {
        val text = input.trim()
        val attachments = pendingAttachments
        val currentSession = session ?: return
        if (text.isEmpty() && attachments.isEmpty()) return
        if (editingMessage != null && attachments.isNotEmpty()) return

        keyboardController?.hide()
        scope.launch {
            isSending = true
            error = null

            val editing = editingMessage

            if (editing != null) {
                currentSession.editMessage(
                    providerDialogId = providerDialogId,
                    messageId = editing.messageId,
                    text = text,
                    customEmojis = inputCustomEmojis
                )
                    .onSuccess {
                        input = ""
                        editingMessage = null
                    }
                    .onFailure { error = it.message ?: "Failed to edit message" }
            } else if (attachments.isNotEmpty()) {
                currentSession.sendFiles(
                    providerDialogId = providerDialogId,
                    attachments = attachments.map { attachment ->
                        OutgoingAttachment(
                            path = attachment.file.path,
                            asPhoto = attachment.asPhoto
                        )
                    },
                    caption = text,
                    replyToMessageId = replyingTo?.messageId,
                    customEmojis = inputCustomEmojis
                )
                    .onSuccess {
                        input = ""
                        inputCustomEmojis = emptyList()
                        pendingAttachments = emptyList()
                        replyingTo = null
                        scrollRequest = ScrollRequest.Latest
                    }
                    .onFailure { error = it.message ?: "Failed to send attachments" }
            } else {
                currentSession.sendMessage(
                    providerDialogId = providerDialogId,
                    text = text,
                    replyToMessageId = replyingTo?.messageId,
                    customEmojis = inputCustomEmojis
                )
                    .onSuccess {
                        input = ""
                        replyingTo = null
                        scrollRequest = ScrollRequest.Latest
                    }
                    .onFailure { error = it.message ?: "Failed to send message" }
            }

            isSending = false
        }
    }

    forwardingMessageIds?.let { messageIds ->
        AlertDialog(
            onDismissRequest = { forwardingMessageIds = null },
            title = { Text("Forward to") },
            text = {
                // Only chats that accept messages can be a forwarding target.
                val forwardTargets = dialogs.filter { target ->
                    target.canSendMessages && target.providerDialogId != providerDialogId
                }

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (forwardTargets.isEmpty()) {
                        item {
                            Text(
                                text = "No chats to forward to",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    itemsIndexed(forwardTargets) { _, target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    forwardingMessageIds = null
                                    forwardMessages(messageIds, target.providerDialogId)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (target.isSavedMessages) {
                                SavedMessagesAvatar(size = 36.dp)
                            } else {
                                AvatarImage(
                                    avatar = target.avatar ?: Avatar.fromName(target.title),
                                    size = 36.dp
                                )
                            }

                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                text = target.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardingMessageIds = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnpinConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnpinConfirmation = false },
            text = { Text("Unpin this message?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpinConfirmation = false
                        val messageId = dialog?.pinnedMessageId

                        if (session != null && messageId != null) {
                            scope.launch {
                                session.setMessagePinned(
                                    providerDialogId = providerDialogId,
                                    messageId = messageId,
                                    isPinned = false
                                ).onFailure {
                                    error = it.message ?: "Failed to unpin message"
                                }
                            }
                        }
                    }
                ) {
                    Text("Unpin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpinConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val stickerSetId = openedStickerSetId

    if (stickerSetId != null && session != null) {
        StickerSetDialog(
            setId = stickerSetId,
            session = session,
            onDismiss = { openedStickerSetId = null }
        )
    }

    if (showPollDialog) {
        PollCreateDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { question, options, anonymous, multiple ->
                val currentSession = session
                showPollDialog = false

                if (currentSession != null) {
                    scope.launch {
                        currentSession.sendPoll(
                            providerDialogId = providerDialogId,
                            question = question,
                            options = options,
                            isAnonymous = anonymous,
                            allowsMultipleAnswers = multiple
                        ).onSuccess {
                            scrollRequest = ScrollRequest.Latest
                        }.onFailure { failure ->
                            error = failure.message ?: "Failed to create the poll"
                        }
                    }
                }
            }
        )
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            onDismiss = { showLocationDialog = false },
            onSend = { latitude, longitude, livePeriodSeconds ->
                val currentSession = session
                showLocationDialog = false

                if (currentSession != null) {
                    scope.launch {
                        currentSession.sendLocation(
                            providerDialogId = providerDialogId,
                            latitude = latitude,
                            longitude = longitude,
                            livePeriodSeconds = livePeriodSeconds
                        ).onSuccess {
                            scrollRequest = ScrollRequest.Latest
                        }.onFailure { failure ->
                            error = failure.message ?: "Failed to share the location"
                        }
                    }
                }
            }
        )
    }

    recordedMedia?.let { recording ->
        RecordedMediaDialog(
            recording = recording,
            isSending = isSendingRecording,
            error = recordingError,
            onDiscard = {
                mediaRecorder.release(recording)
                recordedMedia = null
                recordingError = null
            },
            onSend = {
                val currentSession = session ?: return@RecordedMediaDialog
                isSendingRecording = true
                recordingError = null

                scope.launch {
                    val durationSeconds = (recording.durationMs / 1000L)
                        .toInt()
                        .coerceAtLeast(1)

                    val result = when (recording.kind) {
                        MediaRecordingKind.Voice -> currentSession.sendVoiceNote(
                            providerDialogId = providerDialogId,
                            path = recording.path,
                            durationSeconds = durationSeconds
                        )

                        MediaRecordingKind.VideoNote -> currentSession.sendVideoNote(
                            providerDialogId = providerDialogId,
                            path = recording.path,
                            durationSeconds = durationSeconds
                        )
                    }

                    isSendingRecording = false

                    result.onSuccess {
                        mediaRecorder.release(recording)
                        recordedMedia = null
                        scrollRequest = ScrollRequest.Latest
                    }.onFailure { failure ->
                        recordingError = failure.message ?: "Failed to send the recording"
                    }
                }
            }
        )
    }

    // Attachments are reviewed in a blocking modal: the chat stays dimmed and
    // the composer can't be used until the message is sent or discarded.
    if (pendingAttachments.isNotEmpty()) {
        AttachmentReviewDialog(
            attachments = pendingAttachments,
            caption = input,
            isSending = isSending,
            onCaptionChange = { value ->
                inputCustomEmojis = adjustCustomEmojiSpans(
                    oldText = input,
                    newText = value,
                    spans = inputCustomEmojis
                )

                input = value
            },
            onRemove = { index ->
                pendingAttachments = pendingAttachments.filterIndexed {
                        itemIndex,
                        _ -> itemIndex != index
                }
            },
            onDiscard = {
                pendingAttachments = emptyList()
                input = ""
                inputCustomEmojis = emptyList()
                showAttachmentMenu = false
            },
            onSend = ::send
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenProfile)
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if(!isTwoPaneWindow) {
                IconButton(onClick = onBack) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = arrow_back_icon,
                        contentDescription = "Back"
                    )
                }
            }

            if (dialog?.isSavedMessages == true) {
                SavedMessagesAvatar(size = 40.dp)
            } else {
                SharedProfileElement(
                    key = "profile:$accountId:$providerDialogId"
                ) {
                    AvatarImage(
                        avatar = dialog?.avatar ?: Avatar.fromName(dialog?.title ?: "Dialog"),
                        size = 40.dp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SharedProfileElement(
                        key = "profile-name:$accountId:$providerDialogId",
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = dialog?.title ?: "Dialog",
                            style = MaterialTheme.typography.titleMedium,
                            lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    dialog?.emojiStatusId?.let { emojiId ->
                        EmojiStatusIcon(
                            modifier = Modifier.padding(start = 4.dp),
                            emojiId = emojiId,
                            session = session
                        )
                    }
                }

                dialog?.let {
                    if(it.isSavedMessages) {
                        return@let
                    }
                    
                    val typingText = it.typingText
                    var statusText = typingText ?: it.statusText
                    val memberCount = it.memberCount
                    
                    if(it.kind == DialogKind.Bot && typingText == null) {
                        statusText = null
                    }

                    Text(
                        text = statusText ?: when (it.kind) {
                            DialogKind.Direct -> if (it.isSavedMessages) {
                                "Saved Messages"
                            } else {
                                "Direct message"
                            }
                            DialogKind.Group -> memberCount?.let { count ->
                                "${formatCount(count)} ${if (count == 1) "member" else "members"}"
                            } ?: "Group"

                            DialogKind.Channel -> memberCount?.let { count ->
                                "${formatCount(count)} ${if (count == 1) "subscriber" else "subscribers"}"
                            } ?: "Channel"

                            DialogKind.Bot -> "Bot"
                            DialogKind.Secret -> "Secret chat"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                        color = if (typingText != null || statusText == "online") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }

            IconButton(
                onClick = {
                    searchOpen = !searchOpen

                    if (!searchOpen) {
                        searchQuery.clearText()
                    }
                }
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = search_icon,
                    contentDescription = "Search in chat"
                )
            }

            if(dialog?.kind == DialogKind.Direct) {
                IconButton(
                    onClick = {

                    }
                ) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = call_icon,
                        contentDescription = null
                    )
                }
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = more_vertical_icon,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Scroll to first message") },
                        onClick = {
                            showMoreMenu = false
                            scrollRequest = ScrollRequest.First
                        }
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainer
        )

        val activeLiveLocation = displayMessages.lastOrNull { message ->
            message.isOutgoing && message.location?.isLive == true
        }

        activeLiveLocation?.let { liveMessage ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable {
                        searchOpen = false
                        searchQuery.clearText()
                        scrollRequest = ScrollRequest.Message(liveMessage.messageId)
                    }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sharing live location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Tap to open the message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                TextButton(
                    onClick = {
                        val currentSession = session

                        if (currentSession != null) {
                            scope.launch {
                                currentSession.stopLiveLocation(
                                    providerDialogId = providerDialogId,
                                    messageId = liveMessage.messageId
                                ).onFailure { failure ->
                                    error = failure.message
                                        ?: "Failed to stop sharing the location"
                                }
                            }
                        }
                    }
                ) {
                    Text("Stop")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        }

        dialog?.pinnedMessageId?.let { pinnedId ->
            val pinnedMessage = messages.firstOrNull { it.messageId == pinnedId }
                ?: loadedPinnedMessage?.takeIf { it.messageId == pinnedId }
            val preview = pinnedMessage?.text?.takeIf { it.isNotBlank() }
                ?: pinnedMessage?.media?.fileName
                ?: "Pinned message"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable {
                        searchOpen = false
                        searchQuery.clearText()
                        scrollRequest = ScrollRequest.Message(pinnedId)
                    }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pinned message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showUnpinConfirmation = true }) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = close_icon,
                        contentDescription = "Unpin message"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        }

        AnimatedVisibility(visible = searchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = Dp.Unspecified),
                    state = searchQuery,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    
                    contentPadding = PaddingValues(),
                    
                    placeholder = {
                        Text("Search in chat")
                    }
                )

                if (searchQuery.text.isNotBlank()) {
                    Text(
                        text = if (isSearchPending) {
                            "Searching..."
                        } else {
                            "${visibleMessages.size}\nresults"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                IconButton(
                    onClick = {
                        searchOpen = false
                        searchQuery.clearText()
                    }
                ) { 
                    Icon(
                        imageVector = close_icon,
                        contentDescription = null
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val wallpaper = dialog?.wallpaper
            val customWallpaper = wallpaperImage ?: appWallpaper
            val hasFill = wallpaper != null && wallpaper.colors.isNotEmpty()

            // Pattern wallpapers are drawn over the gradient fill with the
            // intensity from the server, photo wallpapers are drawn as is.
            if (hasFill) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(wallpaperBrush(wallpaper))
                )
            } else if (customWallpaper == null) {
                Image(
                    modifier = Modifier.matchParentSize(),
                    painter = painterResource(Res.drawable.wallpaper),
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            }

            if (customWallpaper != null) {
                val isPattern = wallpaper?.isPattern == true

                Image(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(
                            if (isPattern) {
                                (wallpaper.intensity / 100f).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                        ),
                    bitmap = customWallpaper,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            }
            
            Column(Modifier.fillMaxSize()) {
                val selectedMessageCount = displayMessages.count { message ->
                    message.allMessageIds.any { id -> id in selectedMessageIds }
                }

                AnimatedVisibility(visible = selectedMessageIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "$selectedMessageCount selected",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        TextButton(
                            onClick = {
                                forwardingMessageIds = selectedMessageIds.toLongArray()
                            }
                        ) {
                            Text("Forward")
                        }

                        TextButton(
                            onClick = { deleteMessages(selectedMessageIds.toLongArray()) }
                        ) {
                            Text("Delete")
                        }

                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(
                                imageVector = close_icon,
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        history.isLoadingInitial -> {
                            LoadingContent(
                                modifier = Modifier.align(Alignment.Center),
                                text = "Loading messages..."
                            )
                        }

                        messages.isEmpty() -> {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = "No messages yet",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // The list is revealed only after it has been scrolled
                        // to its restored anchor, so it never flashes the wrong
                        // part of the history while switching dialogs.
                        !messageListState.isReady -> {
                            LoadingContent(
                                modifier = Modifier.align(Alignment.Center),
                                text = "Restoring position..."
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = composerInset + 8.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = visibleMessages,
                                    key = { _, it -> it.messageId }
                                ) { index, message ->
                                    if (message.messageId == unreadAnchorMessageId) {
                                        UnreadMessagesHeader()
                                    }

                                    val previousMessage = if (index > 0) {
                                        visibleMessages[index - 1]
                                    } else {
                                        null
                                    }
                                    val nextMessage = if (index < visibleMessages.size - 1) {
                                        visibleMessages[index + 1]
                                    } else {
                                        null
                                    }
                                    val date = messageDate(message.timestamp)
                                    val previousDate = previousMessage?.let {
                                        messageDate(it.timestamp)
                                    }
                                    val nextDate = nextMessage?.let {
                                        messageDate(it.timestamp)
                                    }
                                    val isNewestMessage =
                                        message.messageId == visibleMessages.lastOrNull()?.messageId
                                    val mediaItems = message.allMedia
                                    val media = mediaItems.firstOrNull()
                                    val reply = replyFor(message, messages, loadedReplies)
                                    val replyMedia = reply?.media
                                        ?.takeIf { it.kind == MessageMediaKind.Photo }
                                    val replyThumbnail: (@Composable () -> Unit)? =
                                        replyMedia?.let { replyImage ->
                                            { ReplyImageThumbnail(replyImage, session) }
                                        }

                                    val poll = message.poll
                                    val mediaContent: (@Composable () -> Unit)? = when {
                                        poll != null -> {
                                            {
                                                MessagePollCard(
                                                    poll = poll,
                                                    isOutgoing = message.isOutgoing,
                                                    session = session,
                                                    customEmojiData = customEmojiData,
                                                    customEmojiAdaptive = customEmojiAdaptive,
                                                    onOptionClick = { option ->
                                                        if (session != null) {
                                                            scope.launch {
                                                                val optionIds = if (poll.allowsMultipleAnswers) {
                                                                    val chosen = poll.options
                                                                        .filter { it.isChosen }
                                                                        .map { it.id }

                                                                    if (option.isChosen) {
                                                                        chosen - option.id
                                                                    } else {
                                                                        chosen + option.id
                                                                    }
                                                                } else {
                                                                    listOf(option.id)
                                                                }

                                                                session.votePoll(
                                                                    providerDialogId = providerDialogId,
                                                                    messageId = message.messageId,
                                                                    optionIds = optionIds
                                                                ).onFailure { throwable ->
                                                                    error = throwable.message ?: "Failed to vote"
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onRetractVote = {
                                                        if (session != null) {
                                                            scope.launch {
                                                                session.votePoll(
                                                                    providerDialogId = providerDialogId,
                                                                    messageId = message.messageId,
                                                                    optionIds = emptyList()
                                                                ).onFailure { throwable ->
                                                                    error = throwable.message ?: "Failed to retract vote"
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        mediaItems.size > 1 &&
                                            mediaItems.all { item -> item.isGalleryMedia() } -> {
                                            {
                                                MessageGallery(
                                                    media = mediaItems,
                                                    session = session
                                                ) { index ->
                                                    MediaViewer.showGallery(
                                                        media = mediaItems,
                                                        initialIndex = index,
                                                        session = session
                                                    )
                                                }
                                            }
                                        }
                                        mediaItems.size > 1 -> {
                                            {
                                                // A media album can contain documents.
                                                // Those must render as file cards, not
                                                // be sent through the image gallery.
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    for (item in mediaItems) {
                                                        when (item.kind) {
                                                            MessageMediaKind.Photo -> MessageImagePreview(
                                                                media = item,
                                                                session = session,
                                                                fillWidth = false
                                                            ) {
                                                                MediaViewer.showImage(item, session)
                                                            }

                                                            MessageMediaKind.Video -> ChatVideoPreview(
                                                                media = item,
                                                                session = session
                                                            ) {
                                                                MediaViewer.showVideo(item, session)
                                                            }

                                                            MessageMediaKind.Animation -> ChatAnimationPreview(
                                                                media = item,
                                                                session = session
                                                            ) {
                                                                MediaViewer.showVideo(item, session)
                                                            }

                                                            MessageMediaKind.VideoNote -> ChatVideoPreview(
                                                                media = item,
                                                                session = session,
                                                                isRound = true
                                                            ) {
                                                                MediaViewer.showVideo(item, session)
                                                            }

                                                            MessageMediaKind.VoiceNote,
                                                            MessageMediaKind.Audio -> VoiceMessagePlayer(
                                                                media = item,
                                                                session = session
                                                            )

                                                            MessageMediaKind.File -> MessageFileCard(
                                                                media = item,
                                                                session = session,
                                                                isOutgoing = message.isOutgoing
                                                            )

                                                            MessageMediaKind.Sticker -> ChatSticker(
                                                                media = item,
                                                                session = session,
                                                                onClick = {
                                                                    item.stickerSetId?.let { setId ->
                                                                        openedStickerSetId = setId
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        media == null -> null
                                        media.kind == MessageMediaKind.Photo -> {
                                            {
                                                MessageImagePreview(
                                                    media = media,
                                                    session = session,
                                                    fillWidth = message.text.isNotBlank()
                                                ) {
                                                    MediaViewer.showImage(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Sticker -> {
                                            {
                                                ChatSticker(
                                                    media = media,
                                                    session = session,
                                                    onClick = {
                                                        media.stickerSetId?.let { setId ->
                                                            openedStickerSetId = setId
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        media.kind == MessageMediaKind.Animation -> {
                                            {
                                                ChatAnimationPreview(media, session) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.Video -> {
                                            {
                                                ChatVideoPreview(media, session) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.VideoNote -> {
                                            {
                                                ChatVideoPreview(media, session, isRound = true) {
                                                    MediaViewer.showVideo(media, session)
                                                }
                                            }
                                        }
                                        media.kind == MessageMediaKind.VoiceNote ||
                                            media.kind == MessageMediaKind.Audio -> {
                                            { VoiceMessagePlayer(media, session) }
                                        }
                                        media.kind == MessageMediaKind.File -> {
                                            { MessageFileCard(media, session, message.isOutgoing) }
                                        }
                                        else -> null
                                    }

                                    Column(
                                        // Deleted rows fade out and the rest move
                                        // into place instead of jumping.
                                        modifier = Modifier.animateItem(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (previousDate != date) {
                                            MessageDatePill(date)
                                        }

                                        // Only the newest message plays the
                                        // entrance; older rows would otherwise
                                        // slide again while scrolling history.
                                        val appearanceState = remember(message.messageId) {
                                            MutableTransitionState(!isNewestMessage)
                                                .apply { targetState = true }
                                        }

                                        AnimatedVisibility(
                                            visibleState = appearanceState,
                                            enter = slideInVertically(
                                                initialOffsetY = { height -> height }
                                            ) + fadeIn()
                                        ) {
                                        MessageBubble(
                                            dialogKind = dialog?.kind ?: DialogKind.Direct,
                                            message = message,
                                            mediaContent = mediaContent,
                                            richMedia = { richBlockMedia ->
                                                RichBlockMediaPreview(richBlockMedia, session)
                                            },
                                            customEmojiData = customEmojiData,
                                            customEmojiAdaptive = customEmojiAdaptive,
                                            session = session,
                                            reply = reply,
                                            replyThumbnail = replyThumbnail,
                                            isSelectionMode = selectedMessageIds.isNotEmpty(),
                                            isSelected = message.messageId in selectedMessageIds,
                                            isPinned = dialog?.pinnedMessageId == message.messageId,
                                            onOpenStickerSet = { setId ->
                                                openedStickerSetId = setId
                                            },
                                            onDoubleTap = if (
                                                isMobileLayout && doubleTapReaction != null
                                            ) {
                                                {
                                                    applyReaction(message, doubleTapReaction)
                                                }
                                            } else {
                                                null
                                            },
                                            onToggleSelection = {
                                                val ids = message.allMessageIds.toSet()
                                                selectedMessageIds =
                                                    if (message.messageId in selectedMessageIds) {
                                                        selectedMessageIds - ids
                                                    } else {
                                                        selectedMessageIds + ids
                                                    }
                                            },
                                            onReply = { replyingTo = message },
                                            onForward = {
                                                forwardingMessageIds = message.allMessageIds.toLongArray()
                                            },
                                            onDelete = {
                                                deleteMessages(message.allMessageIds.toLongArray())
                                            },
                                            onPin = {
                                                pinMessage(
                                                    message = message,
                                                    isPinned = dialog?.pinnedMessageId != message.messageId
                                                )
                                            },
                                            onSelect = {
                                                selectedMessageIds = message.allMessageIds.toSet()
                                            },
                                            onCancelSend = {
                                                deleteMessages(message.allMessageIds.toLongArray())
                                            },
                                            onOpenSender = if (
                                                !message.isOutgoing &&
                                                !(dialog?.kind ?: DialogKind.Direct).isPeer &&
                                                message.senderId > 0
                                            ) {
                                                { onOpenUserProfile(message.senderId) }
                                            } else {
                                                null
                                            },

                                            onEdit = if (
                                                message.isOutgoing &&
                                                message.text.isNotBlank() &&
                                                !message.isService
                                            ) {
                                                {
                                                    editingMessage = message
                                                    pendingAttachments = emptyList()
                                                    input = message.text; inputCustomEmojis = message.customEmojis
                                                }
                                            } else {
                                                null
                                            },

                                            onReaction = { option ->
                                                val isChosen = message.reactions.any { reaction ->
                                                    reaction.isChosen && if (
                                                        option.customEmojiId != null
                                                    ) {
                                                        reaction.customEmojiId == option.customEmojiId
                                                    } else {
                                                        reaction.customEmojiId == null &&
                                                            reaction.emoji == option.emoji
                                                    }
                                                }

                                                scope.launch {
                                                    if (isChosen) {
                                                        session?.removeMessageReaction(
                                                            providerDialogId = providerDialogId,
                                                            messageId = message.messageId,
                                                            emoji = option.emoji,
                                                            customEmojiId = option.customEmojiId
                                                        )
                                                    } else {
                                                        session?.addMessageReaction(
                                                            providerDialogId = providerDialogId,
                                                            messageId = message.messageId,
                                                            emoji = option.emoji,
                                                            customEmojiId = option.customEmojiId
                                                        )
                                                    }
                                                }
                                            },

                                            onHashtagClick = onOpenHashtag,
                                            onMentionClick = { username ->
                                                scope.launch {
                                                    session?.resolveUsername(username)
                                                        ?.onSuccess { targetUserId ->
                                                            onOpenUserProfile(targetUserId)
                                                        }
                                                }
                                            },

                                            onReplyClick = { replyMessageId ->
                                                searchOpen = false
                                                searchQuery.clearText()
                                                scrollRequest =
                                                    ScrollRequest.Message(replyMessageId)
                                            },

                                            connection = run {
                                                val previousSameSender =
                                                    previousDate == date &&
                                                        previousMessage.senderId == message.senderId
                                                val nextSameSender =
                                                    nextDate == date &&
                                                        nextMessage.senderId == message.senderId

                                                when {
                                                    previousSameSender && nextSameSender ->
                                                        MessageBubbleConnection.BOTH

                                                    previousSameSender ->
                                                        MessageBubbleConnection.BOTTOM

                                                    nextSameSender ->
                                                        MessageBubbleConnection.TOP

                                                    else -> MessageBubbleConnection.NONE
                                                }
                                            }
                                        )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MessageListOverlays(
                        isLoadingMore = history.isLoadingOlder,
                        showNewMessages = messageListState.hasNewMessages,
                        isAtBottom = messageListState.isAtBottom,
                        bottomInset = composerInset,
                        onScrollToLatest = { scrollRequest = ScrollRequest.Latest }
                    )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { composerHeightPx = it.height }
                    ) {
                error?.let { message ->
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                editingMessage?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "Editing: ${message.text.take(40)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        TextButton(
                            onClick = {
                                editingMessage = null
                                input = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                replyingTo?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "Replying to: " +
                                message.text.ifBlank { "message" }.take(40),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        TextButton(onClick = { replyingTo = null }) {
                            Text("Cancel")
                        }
                    }
                }

                val emojiPickerContent: @Composable (Modifier) -> Unit = { pickerModifier ->
                    if (session != null) {
                        EmojiPicker(
                            modifier = pickerModifier,
                            session = session,
                            onEmojiSelected = { emoji -> input += emoji },
                            onCustomEmojiSelected = { emojiId, fallback ->
                                val start = input.length

                                input += fallback
                                inputCustomEmojis = inputCustomEmojis + CustomEmojiSpan(
                                    start = start,
                                    end = start + fallback.length,
                                    emojiId = emojiId
                                )
                            },
                            onStickerSelected = { sticker ->
                                showEmojiPicker = false
                                showHoverPicker = false

                                scope.launch {
                                    session.sendSticker(providerDialogId, sticker)
                                        .onSuccess {
                                            scrollRequest = ScrollRequest.Latest
                                        }
                                        .onFailure {
                                            error = it.message ?: "Failed to send the sticker"
                                        }
                                }
                            },
                            onAnimationSelected = { animation ->
                                showEmojiPicker = false
                                showHoverPicker = false

                                scope.launch {
                                    session.sendAnimation(providerDialogId, animation)
                                        .onSuccess {
                                            scrollRequest = ScrollRequest.Latest
                                        }
                                        .onFailure {
                                            error = it.message ?: "Failed to send the GIF"
                                        }
                                }
                            }
                        )
                    }
                }

                // On large screens the picker floats above the composer and is
                // driven by the pointer hovering the emoji button.
                if (isTwoPaneWindow && session != null) {
                    val pickerTransition = remember { MutableTransitionState(false) }
                    pickerTransition.targetState = showEmojiPicker || showHoverPicker

                    // Keep the popup mounted until the exit animation finishes.
                    if (pickerTransition.currentState || !pickerTransition.isIdle) {
                        val density = LocalDensity.current
                        val pickerWidth = 340.dp
                        val pickerWidthPx = with(density) { pickerWidth.roundToPx() }
                        val pickerHeightPx = with(density) { 750.dp.roundToPx() }
                        val gapPx = with(density) { 8.dp.roundToPx() }
                        val anchor = emojiPickerAnchor

                        if (anchor != null) {
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = IntOffset(
                                    x = (anchor.left + anchor.width - pickerWidthPx).roundToInt(),
                                    y = (anchor.top - pickerHeightPx - gapPx)
                                        .coerceAtLeast(0f)
                                        .roundToInt()
                                ),
                                properties = PopupProperties(focusable = false),
                                onDismissRequest = { showHoverPicker = false }
                            ) {
                                Box(modifier = Modifier.hoverable(emojiPickerInteraction)) {
                                    PickerPopupAnimation(pickerTransition) {
                                        emojiPickerContent(Modifier.width(pickerWidth).height(700.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (dialog?.canSendMessages == false) {
                    NotSendableNotice()
                } else Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            // The screen already applies imePadding(), so only the
                            // navigation bar may add bottom inset here. Using
                            // safeDrawing would add the keyboard height twice.
                            .windowInsetsPadding(WindowInsets.navigationBars.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )).clip(RoundedCornerShape(32.dp))
                            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .weight(1f)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box {
                            IconButton(
                                enabled = editingMessage == null,
                                onClick = { showAttachmentMenu = true }
                            ) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.secondary,
                                    imageVector = add_icon,
                                    contentDescription = "Attach photos or files"
                                )
                            }

                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Photos") },
                                    onClick = {
                                        showAttachmentMenu = false
                                        filePicker.pickImages()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Files") },
                                    onClick = {
                                        showAttachmentMenu = false
                                        filePicker.pickFiles()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Poll") },
                                    onClick = {
                                        showAttachmentMenu = false
                                        showPollDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Location") },
                                    onClick = {
                                        showAttachmentMenu = false
                                        showLocationDialog = true
                                    }
                                )
                            }
                        }

                        BasicTextField(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 35.dp)
                                .onPreviewKeyEvent { event ->
                                    if(
                                        event.type == KeyEventType.KeyDown &&
                                        (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                        !event.isShiftPressed
                                    ) {
                                        send()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            value = input,
                            onValueChange = { value ->
                                inputCustomEmojis = adjustCustomEmojiSpans(
                                    oldText = input,
                                    newText = value,
                                    spans = inputCustomEmojis
                                )

                                input = value
                            },
                            enabled = !isSending,
                            maxLines = 10,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),

                            // With custom emoji in the draft the field text
                            // is drawn by the overlay below, which can render
                            // them as stickers; the field keeps the caret and
                            // selection.
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if(inputCustomEmojis.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.Transparent
                                }
                            ),

                            decorationBox = { innerTextField ->
                                Box {
                                    if(inputCustomEmojis.isNotEmpty()) {
                                        CustomEmojiMessageText(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = input,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            emojis = inputCustomEmojis,
                                            data = inputEmojiData,
                                            adaptiveEmojis = inputEmojiAdaptive
                                        )
                                    }

                                    innerTextField()

                                    if(input.isEmpty()) {
                                        Text(
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.secondary,
                                            text = if (pendingAttachments.isEmpty()) {
                                                "Write a message..."
                                            } else {
                                                "Add a caption..."
                                            }
                                        )
                                    }
                                }
                            }
                        )

                        IconButton(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    emojiPickerAnchor = coordinates.boundsInWindow()
                                }
                                .hoverable(emojiButtonInteraction),
                            onClick = {
                                showEmojiPicker = !showEmojiPicker
                            }
                        ) {
                            Icon(
                                tint = if(showEmojiPicker) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                imageVector = mood_icon,
                                contentDescription = "Emoji and stickers"
                            )
                        }

                        AnimatedVisibility(input.isEmpty() && pendingAttachments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(videoMessageMode, isRecording, mediaRecorder) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)

                                            val releasedEarly = withTimeoutOrNull(350L) {
                                                waitForUpOrCancellation()
                                            }

                                            if (releasedEarly != null) {
                                                if (mediaRecorder.supportsVideo) {
                                                    videoMessageMode = !videoMessageMode
                                                }
                                                return@awaitEachGesture
                                            }

                                            val kind = if (videoMessageMode) {
                                                MediaRecordingKind.VideoNote
                                            } else {
                                                MediaRecordingKind.Voice
                                            }

                                            recordingError = null
                                            recordingKind = kind

                                            if (!mediaRecorder.start(kind)) {
                                                recordingError =
                                                    "Microphone or camera permission is required"
                                                return@awaitEachGesture
                                            }

                                            recordingStartedAt = System.currentTimeMillis()
                                            recordingElapsedMs = 0L
                                            isRecording = true

                                            waitForUpOrCancellation()

                                            if (isRecording) {
                                                stopRecording()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRecording) {
                                    Text(
                                        text = "${recordingElapsedMs / 1000}s",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Icon(
                                        tint = MaterialTheme.colorScheme.secondary,
                                        imageVector = if (videoMessageMode) {
                                            videocam_icon
                                        } else {
                                            microphone_icon
                                        },
                                        contentDescription = if (videoMessageMode) {
                                            "Hold to record a video message"
                                        } else {
                                            "Hold to record a voice message"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(input.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                        FilledIconButton(
                            enabled = (input.isNotBlank() || pendingAttachments.isNotEmpty()) &&
                                !isSending,
                            onClick = ::send
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )

                                return@FilledIconButton
                            }
                            Icon(
                                imageVector = send_icon,
                                contentDescription = null
                            )
                        }
                    }
                }

                if (!isTwoPaneWindow && showEmojiPicker && session != null) {
                    emojiPickerContent(Modifier)
                }
                    }
            }
        }
    }
}
}

@Composable
private fun AttachmentReviewDialog(
    attachments: List<PendingAttachment>,
    caption: String,
    isSending: Boolean,
    onCaptionChange: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                // Taps outside the card are swallowed, not treated as dismiss.
                .clickable {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Send ${attachments.size} " +
                            if (attachments.size == 1) "attachment" else "attachments",
                        style = MaterialTheme.typography.titleMedium
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(attachments) { index, attachment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (attachment.asPhoto) "IMG" else "FILE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = attachment.file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(
                                        imageVector = close_icon,
                                        contentDescription = "Remove attachment"
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = caption,
                        onValueChange = onCaptionChange,
                        label = { Text("Caption (optional)") },
                        minLines = 1,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = !isSending,
                            onClick = onDiscard
                        ) {
                            Text("Discard")
                        }

                        Button(
                            enabled = !isSending,
                            onClick = onSend
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentsTray(
    attachments: List<PendingAttachment>,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "${attachments.size} of $MaxAlbumAttachments attached",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(attachments) { index, attachment ->
                PendingAttachmentTile(
                    attachment = attachment,
                    onRemove = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentTile(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    var preview by remember(attachment.file.path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(attachment.file.path, attachment.asPhoto) {
        if (!attachment.asPhoto) return@LaunchedEffect

        val bytes = withContext(Dispatchers.IO) {
            readFileBytes(attachment.file.path)
        }

        preview = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
    }

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
    ) {
        val image = preview

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (attachment.asPhoto) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "PHOTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = attachment.file.extensionLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = attachment.file.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(14.dp),
                imageVector = close_icon,
                contentDescription = "Remove attachment",
                tint = Color.White
            )
        }
    }
}

private fun PickedFile.extensionLabel(): String {
    val extension = name.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 5 }
        ?: return "FILE"

    return extension.uppercase()
}

private data class PendingAttachment(
    val file: PickedFile,
    val asPhoto: Boolean
)

@Composable
private fun UnreadMessagesHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                text = "Unread messages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun BoxScope.MessageListOverlays(
    isLoadingMore: Boolean,
    showNewMessages: Boolean,
    isAtBottom: Boolean,
    bottomInset: Dp,
    onScrollToLatest: () -> Unit
) {
    AnimatedVisibility(
        visible = isLoadingMore,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }

    // Floating jump back to the newest message while the list is scrolled up.
    AnimatedVisibility(
        visible = showNewMessages || !isAtBottom,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = bottomInset + 12.dp)
    ) {
        if (showNewMessages) {
            Button(onClick = onScrollToLatest) {
                Text("New messages")
            }
        } else {
            SmallFloatingActionButton(
                onClick = onScrollToLatest,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    modifier = Modifier.rotate(-90f),
                    imageVector = arrow_back_icon,
                    contentDescription = "Scroll to the latest message"
                )
            }
        }
    }
}

private sealed interface ScrollRequest {
    data class Message(val messageId: Long) : ScrollRequest
    data object Latest : ScrollRequest
    data object First : ScrollRequest
}

private fun replyFor(
    message: ChatMessage,
    allMessages: List<ChatMessage>,
    loadedReplies: Map<Long, ChatMessage> = emptyMap()
): MessageReply? {
    val reply = message.replyTo ?: return null
    val original = allMessages.firstOrNull { it.messageId == reply.messageId }
        ?: loadedReplies[reply.messageId]

    return if (original != null) {
        reply.copy(
            senderName = if (original.isOutgoing) "You" else original.senderName,
            text = original.replySnippet(),
            media = original.media
        )
    } else {
        reply.copy(text = reply.text.ifBlank { "Message" })
    }
}

private fun ChatMessage.replySnippet(): String {
    if (text.isNotBlank()) return text
    if (poll != null) return "Poll"

    val media = media ?: return "Message"

    return when (media.kind) {
        MessageMediaKind.Photo -> "Photo"
        MessageMediaKind.Video -> "Video"
        MessageMediaKind.Animation -> "GIF"
        MessageMediaKind.Sticker -> "Sticker"
        MessageMediaKind.VideoNote -> "Video message"
        MessageMediaKind.VoiceNote -> "Voice message"
        MessageMediaKind.Audio -> media.fileName ?: "Audio"
        MessageMediaKind.File -> media.fileName ?: "File"
    }
}

@Composable
private fun ReplyImageThumbnail(
    media: MessageMedia,
    session: AccountSession?
) {
    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val bytes = session?.loadMediaBytes(
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId
        )

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    val bitmap = image

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MessageImagePreview(
    media: MessageMedia,
    session: AccountSession?,
    fillWidth: Boolean,
    onClick: () -> Unit
) {
    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var isLoading by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf(true)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        isLoading = true
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val bytes = session?.loadMediaBytes(
            fileId = media.previewFileId,
            uniqueId = media.previewUniqueId
        )

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        isLoading = false
    }

    val bitmap = image

    // The preview bitmap can be cropped, so its own ratio must never resize
    // the bubble after it decodes. The dimensions that come with the message
    // are stable, which keeps the list from shifting under the user.
    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        bitmap?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
    }

    Box(
        modifier = Modifier
            .then(
                if (fillWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(260.dp)
                }
            )
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = bitmap != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .loadingBlur(isLoading),
                contentScale = ContentScale.Fit
            )
        } else if (!isLoading) {
            Text(
                text = "Image",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RichBlockMediaPreview(
    media: MessageMedia,
    session: AccountSession?
) {
    when (media.kind) {
        MessageMediaKind.Photo -> MessageImagePreview(
            media = media,
            session = session,
            fillWidth = false
        ) {
            MediaViewer.showImage(media, session)
        }

        MessageMediaKind.Video -> ChatVideoPreview(media, session) {
            MediaViewer.showVideo(media, session)
        }

        MessageMediaKind.Animation -> ChatAnimationPreview(media, session) {
            MediaViewer.showVideo(media, session)
        }

        MessageMediaKind.Sticker -> ChatSticker(media, session)

        else -> Unit
    }
}

private fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
private fun MessagePollCard(
    poll: MessagePollInfo,
    isOutgoing: Boolean,
    session: AccountSession?,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long> = emptySet(),
    onOptionClick: (MessagePollOptionInfo) -> Unit,
    onRetractVote: () -> Unit
) {
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val hasVotes = poll.totalVoterCount > 0 || poll.options.any { option -> option.isChosen }
    val showResults = poll.canSeeResults && (hasVotes || poll.isClosed)
    val canVote = !poll.isClosed && session != null
    val hasChosenOption = poll.options.any { option -> option.isChosen }
    val canRetractVote = canVote && hasChosenOption && !poll.isQuiz

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = poll.question.ifBlank { "Poll" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )

        Text(
            text = pollTypeLabel(poll),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor
        )

        poll.options.forEachIndexed { index, option ->
            PollOptionRow(
                option = option,
                showResults = showResults,
                isCorrect = poll.isQuiz && index in poll.correctOptionIds,
                customEmojiData = customEmojiData,
                customEmojiAdaptive = customEmojiAdaptive,
                session = session,
                accent = accent,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                onClick = if (canVote && !(poll.isQuiz && hasChosenOption)) {
                    { onOptionClick(option) }
                } else {
                    null
                }
            )
        }

        if (canRetractVote) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRetractVote)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = "Retract vote",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        poll.explanation?.takeIf { text -> text.isNotBlank() }?.let { explanation ->
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }

        Text(
            text = when {
                poll.totalVoterCount == 1 -> "1 vote"
                poll.totalVoterCount > 1 -> "${formatCount(poll.totalVoterCount)} votes"
                else -> "No votes yet"
            },
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor
        )
    }
}

@Composable
private fun PollOptionRow(
    option: MessagePollOptionInfo,
    showResults: Boolean,
    isCorrect: Boolean,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long> = emptySet(),
    session: AccountSession?,
    accent: Color,
    contentColor: Color,
    secondaryColor: Color,
    onClick: (() -> Unit)? = null
) {
    val fraction = (option.votePercentage / 100f).coerceIn(0f, 1f)
    val isHighlighted = option.isChosen || isCorrect

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.06f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(
                if (showResults && fraction > 0f) {
                    Modifier.drawBehind {
                        drawRect(
                            color = accent.copy(
                                alpha = if (option.isChosen) 0.28f else 0.16f
                            ),
                            size = Size(size.width * fraction, size.height)
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            option.media?.let { optionMedia ->
                PollOptionMedia(media = optionMedia, session = session)
            }

            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal
                ),
                LocalContentColor provides if (option.isChosen) accent else contentColor
            ) {
                CustomEmojiMessageText(
                    modifier = Modifier.weight(1f),
                    text = option.text,
                    emojis = option.customEmojis,
                    data = customEmojiData,
                    adaptiveEmojis = customEmojiAdaptive
                )
            }

            if (showResults) {
                Text(
                    text = "${option.votePercentage}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (option.isChosen) accent else secondaryColor
                )
            }

            if (isCorrect) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun PollOptionMedia(
    media: MessageMedia,
    session: AccountSession?
) {
    if (media.kind == MessageMediaKind.Sticker) {
        var data by remember(media.fileId, session) { mutableStateOf<ByteArray?>(null) }

        LaunchedEffect(media.fileId, session) {
            data = session?.loadMediaBytes(
                fileId = media.fileId,
                uniqueId = media.stickerUniqueId ?: media.fileUniqueId
            )
        }

        val emoji = remember(
            media.fileId,
            data,
            media.stickerFormat,
            media.needsRepainting
        ) {
            DialogFolderEmoji(
                customEmojiId = (media.fileId ?: 0).toLong(),
                offset = 0,
                length = 0,
                fallback = "",
                needsRepainting = media.needsRepainting,
                format = when (media.stickerFormat) {
                    MessageStickerFormat.Tgs -> DialogFolderEmojiFormat.Tgs
                    MessageStickerFormat.Webm -> DialogFolderEmojiFormat.Webm
                    MessageStickerFormat.Webp -> DialogFolderEmojiFormat.Webp
                },
                data = data
            )
        }

        FolderEmojiImage(
            emoji = emoji,
            animate = true,
            size = 28.dp,
            color = Color.Unspecified,
            contentModifier = Modifier.size(28.dp)
        )

        return
    }

    var image by remember(media.previewFileId, media.minithumbnail, session) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(media.previewFileId, media.minithumbnail, session) {
        image = media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }

        val bytes = session?.loadMediaBytes(
            fileId = media.previewFileId ?: media.fileId,
            uniqueId = media.previewUniqueId ?: media.fileUniqueId
        )

        if (bytes != null) {
            image = withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun pollTypeLabel(poll: MessagePollInfo): String {
    val parts = mutableListOf<String>()

    parts += when {
        poll.isQuiz -> "Quiz"
        poll.isAnonymous -> "Anonymous poll"
        else -> "Public poll"
    }

    if (poll.allowsMultipleAnswers) parts += "Multiple answers"
    if (poll.isClosed) parts += "Final results"

    return parts.joinToString(" · ")
}

private fun MessageMedia.isGalleryMedia(): Boolean {
    return when (kind) {
        MessageMediaKind.Photo,
        MessageMediaKind.Video,
        MessageMediaKind.Animation,
        MessageMediaKind.VideoNote -> true

        else -> false
    }
}

@Composable
private fun MessageFileCard(
    media: MessageMedia,
    session: AccountSession?,
    isOutgoing: Boolean
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember(media.fileId) { mutableStateOf(false) }
    var savedPath by remember(media.fileId) { mutableStateOf<String?>(null) }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isOutgoing) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = media.fileTypeLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = media.fileName?.takeIf { it.isNotBlank() } ?: "File",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatFileSize(media.size).ifBlank { "File" },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            enabled = !isDownloading && media.fileId != null,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                disabledContentColor = secondaryColor
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            onClick = {
                val fileId = media.fileId ?: return@Button

                scope.launch {
                    isDownloading = true

                    val bytes = session?.loadMediaBytes(fileId, media.fileUniqueId)

                    if (bytes != null) {
                        savedPath = withContext(Dispatchers.IO) {
                            saveFileToDevice(media.fileName ?: "file", bytes)
                        }
                    }

                    isDownloading = false
                }
            }
        ) {
            Text(
                text = when {
                    savedPath != null -> "Saved"
                    isDownloading -> "Saving..."
                    else -> "Download"
                },
                maxLines = 1
            )
        }
    }
}

private fun MessageMedia.fileTypeLabel(): String {
    val fromName = fileName
        ?.substringAfterLast('.', "")
        ?.takeIf { extension ->
            extension.length in 1..4 && extension.all(Char::isLetterOrDigit)
        }

    val fromMime = mimeType
        ?.substringAfter('/', "")
        ?.substringBefore(';')
        ?.substringBefore('+')
        ?.takeIf { subtype ->
            subtype.length in 1..4 && subtype.all(Char::isLetterOrDigit)
        }

    return (fromName ?: fromMime ?: "FILE").uppercase()
}

@Composable
private fun NotSendableNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = "You can't send messages to this chat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
