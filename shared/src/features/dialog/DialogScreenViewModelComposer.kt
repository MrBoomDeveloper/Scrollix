package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.platform.media.MediaRecordingController
import com.mrboomdev.scrollix.platform.clipboard.readClipboardRichText
import com.mrboomdev.scrollix.platform.media.MediaRecordingKind

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Composer behaviour: the text, its custom emoji spans, the server draft, the
 * attachment queue and note recording. The screen keeps only the widgets; every
 * state change goes through these calls, so the draft save and the emoji loads
 * follow the text automatically.
 */
internal fun DialogScreenViewModel.updateInput(value: String, selection: TextRange) {
    if (value != input) {
        // A multi-character insertion may be a Markdown or rich HTML paste;
        // its markup is converted into plain text plus formatting ranges.
        conversionForPaste(input, value) { readClipboardRichText() }?.let { replacement ->
            recordComposerEdit(ComposerEditKind.Discrete)
            applyPasteReplacement(replacement)
            return
        }

        recordComposerEdit(ComposerEditKind.Typing)
    }

    if (value.isEmpty()) {
        // A programmatic reset never reaches onValueChange, so its spans are
        // dropped here instead of pointing past the end of the text.
        if (inputCustomEmojis.isNotEmpty()) {
            inputCustomEmojis = emptyList()
        }

        if (inputFormatting.isNotEmpty()) {
            inputFormatting = emptyList()
        }
    } else if (value != input) {
        inputCustomEmojis = adjustCustomEmojiSpans(
            oldText = input,
            newText = value,
            spans = inputCustomEmojis
        )
        inputFormatting = inputFormatting.adjustFormatting(
            oldText = input,
            newText = value
        )
    }

    input = value
    inputSelection = selection

    ensureInputEmojiLoaded()
    scheduleDraftSave()
    refreshInlineBotQuery()
}

/**
 * Inserts text at the caret and leaves the caret after it. Custom emoji runs are
 * tracked, so a sticker inserted mid-sentence stays where it was put while the
 * user keeps typing after it.
 */
internal fun DialogScreenViewModel.insertIntoComposer(text: String, emojiId: Long? = null) {
    recordComposerEdit(ComposerEditKind.Discrete)

    val start = inputSelection.start.coerceIn(0, input.length)
    val end = inputSelection.end.coerceIn(start, input.length)
    val updated = input.replaceRange(start, end, text)

    inputCustomEmojis = adjustCustomEmojiSpans(
        oldText = input,
        newText = updated,
        spans = inputCustomEmojis
    ) + listOfNotNull(
        emojiId?.let { id ->
            CustomEmojiSpan(
                start = start,
                end = start + text.length,
                emojiId = id
            )
        }
    )

    inputFormatting = inputFormatting.adjustFormatting(
        oldText = input,
        newText = updated
    )

    input = updated
    inputSelection = TextRange(start + text.length)

    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

/** Appends to the end of the draft, as the attachment caption field does. */
internal fun DialogScreenViewModel.appendToComposer(text: String, emojiId: Long?) {
    recordComposerEdit(ComposerEditKind.Discrete)

    val start = input.length

    inputFormatting = inputFormatting.adjustFormatting(
        oldText = input,
        newText = input + text
    )

    inputCustomEmojis = inputCustomEmojis + listOfNotNull(
        emojiId?.let { id ->
            CustomEmojiSpan(
                start = start,
                end = start + text.length,
                emojiId = id
            )
        }
    )

    input += text
    inputSelection = TextRange(input.length)

    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

internal fun DialogScreenViewModel.clearComposer() {
    input = ""
    inputSelection = TextRange.Zero
    inputCustomEmojis = emptyList()
    inputFormatting = emptyList()

    // A programmatic reset (sent message, cancelled edit) starts a new draft,
    // so the steps of the previous one would only restore a stale composer.
    composerHistory.clear()
    refreshComposerHistoryState()

    // The inline picker belongs to the draft that just went away.
    inlineBotJob?.cancel()
    inlineBotQuery = null
    inlineBotQueryId = null
    inlineBotResults = emptyList()
    inlineBotLoading = false

    scheduleDraftSave()
}

/** Snapshot of the composer as it is now, ready to be restored by an undo. */
internal fun DialogScreenViewModel.composerSnapshot() = ComposerSnapshot(
    text = input,
    selection = inputSelection,
    customEmojis = inputCustomEmojis,
    formatting = inputFormatting
)

/**
 * Remembers the composer state before an edit. Typing runs merge into one step,
 * so a whole word is undone at once instead of character by character, and a
 * paste or a formatting toggle stays a step of its own.
 */
internal fun DialogScreenViewModel.recordComposerEdit(kind: ComposerEditKind) {
    composerHistory.record(
        before = composerSnapshot(),
        kind = kind,
        now = System.currentTimeMillis()
    )

    refreshComposerHistoryState()
}

/** Restores the composer as it was before the last edit. */
internal fun DialogScreenViewModel.undoComposer() {
    val snapshot = composerHistory.undo(composerSnapshot()) ?: return
    restoreComposerSnapshot(snapshot)
}

/** Re-applies the edit that was undone last. */
internal fun DialogScreenViewModel.redoComposer() {
    val snapshot = composerHistory.redo(composerSnapshot()) ?: return
    restoreComposerSnapshot(snapshot)
}

private fun DialogScreenViewModel.restoreComposerSnapshot(snapshot: ComposerSnapshot) {
    input = snapshot.text
    inputCustomEmojis = snapshot.customEmojis
    inputFormatting = snapshot.formatting

    // A restored snapshot may predate a deletion, so the caret is clamped to
    // the text that actually came back.
    inputSelection = TextRange(
        start = snapshot.selection.start.coerceIn(0, snapshot.text.length),
        end = snapshot.selection.end.coerceIn(0, snapshot.text.length)
    )

    refreshComposerHistoryState()
    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

private fun DialogScreenViewModel.refreshComposerHistoryState() {
    canUndoComposer = composerHistory.canUndo
    canRedoComposer = composerHistory.canRedo
}

/** Replaces an insertion with its converted text and formatting. */
private fun DialogScreenViewModel.applyPasteReplacement(replacement: PasteReplacement) {
    val start = replacement.start.coerceIn(0, input.length)
    val end = replacement.end.coerceIn(start, input.length)
    val updated = input.replaceRange(start, end, replacement.paste.text)

    inputCustomEmojis = adjustCustomEmojiSpans(
        oldText = input,
        newText = updated,
        spans = inputCustomEmojis
    )

    inputFormatting = (
        inputFormatting.adjustFormatting(oldText = input, newText = updated) +
            replacement.paste.formatting.map { format ->
                format.copy(
                    start = format.start + start,
                    end = format.end + start
                )
            }
        ).mergedFormatting()

    input = updated
    inputSelection = TextRange(start + replacement.paste.text.length)

    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

/** Drops every rich text range, so the message goes out as plain text. */
internal fun DialogScreenViewModel.clearComposerFormatting() {
    if (inputFormatting.isEmpty()) return

    recordComposerEdit(ComposerEditKind.Discrete)
    inputFormatting = emptyList()
    scheduleDraftSave()
}

/** Applies or removes one formatting style over the current selection. */
internal fun DialogScreenViewModel.toggleComposerFormat(style: ComposerTextFormat) {
    if (inputSelection.collapsed) return

    recordComposerEdit(ComposerEditKind.Discrete)
    inputFormatting = inputFormatting.toggleFormatting(inputSelection, style)
    scheduleDraftSave()
}

/** Links the selected text, or unlinks it when the confirmed URL is blank. */
internal fun DialogScreenViewModel.applyComposerLink(url: String) {
    recordComposerEdit(ComposerEditKind.Discrete)
    inputFormatting = inputFormatting.applyLink(inputSelection, url)
    showLinkDialog = false
    scheduleDraftSave()
}

/**
 * The composer text is stored on the server as a draft, so it is restored when
 * the chat is reopened on this or any other device.
 */
internal fun DialogScreenViewModel.restoreDraft() {
    val currentSession = session

    if (currentSession == null) {
        draftRestored = true
        return
    }

    viewModelScope.launch {
        currentSession.loadDraft(providerDialogId).onSuccess { draft ->
            hadServerDraft = draft.text.isNotEmpty() ||
                draft.customEmojis.isNotEmpty() ||
                draft.formatting.isNotEmpty()
            serverDraftText = draft.text

            if (input.isEmpty() && editingMessage == null) {
                input = draft.text
                inputCustomEmojis = draft.customEmojis
                inputFormatting = draft.formatting
                inputSelection = TextRange(draft.text.length)
                ensureInputEmojiLoaded()
            }
        }

        draftRestored = true
    }
}

/**
 * Debounced draft write. An empty composer never clears an unreadable or
 * not-yet-loaded draft, and edits keep their own flow instead of the draft.
 */
internal fun DialogScreenViewModel.scheduleDraftSave() {
    if (!draftRestored || editingMessage != null) return

    if (
        input.isEmpty() &&
        inputCustomEmojis.isEmpty() &&
        inputFormatting.isEmpty() &&
        !hadServerDraft
    ) {
        return
    }

    draftJob?.cancel()
    draftJob = viewModelScope.launch {
        delay(600.milliseconds)

        val currentSession = session ?: return@launch
        // The text that is actually stored, so a keystroke that lands while the
        // request is in flight is not mistaken for the saved draft later.
        val draftText = input

        currentSession.saveDraft(
            providerDialogId = providerDialogId,
            text = draftText,
            customEmojis = inputCustomEmojis,
            formatting = inputFormatting
        ).onSuccess {
            serverDraftText = draftText

            // The chat list does not receive a draft update from the provider
            // for a locally saved draft, so the cached dialog is patched here.
            com.mrboomdev.scrollix.data.api.ApiManager.setDraftText(
                accountId = accountId,
                providerDialogId = providerDialogId,
                text = draftText
            )
        }

        hadServerDraft = draftText.isNotEmpty() ||
            inputCustomEmojis.isNotEmpty() ||
            inputFormatting.isNotEmpty()
    }
}

/**
 * Reconciles a draft edited on another device. The remote text replaces this
 * composer only while it still matches the last server draft this device saw;
 * any local edit keeps the local text and the debounced save overwrites the
 * remote one.
 */
internal fun DialogScreenViewModel.reconcileRemoteDraft() {
    if (!draftRestored || editingMessage != null) return

    val remote = dialog?.draftText ?: return
    if (!shouldApplyRemoteDraft(
            localText = input,
            knownServerText = serverDraftText,
            remoteText = remote
        )
    ) {
        // Local changes win; remember the remote text so the same update does
        // not trigger this branch again.
        serverDraftText = remote
        return
    }

    serverDraftText = remote

    val currentSession = session ?: return

    viewModelScope.launch {
        currentSession.loadDraft(providerDialogId).onSuccess { draft ->
            // A draft only replaces an empty composer: text typed while the
            // request was in flight must not be lost.
            if (input.isNotEmpty() || editingMessage != null) return@onSuccess

            input = draft.text
            inputCustomEmojis = draft.customEmojis
            inputFormatting = draft.formatting
            inputSelection = TextRange(draft.text.length)
            hadServerDraft = draft.text.isNotEmpty() ||
                draft.customEmojis.isNotEmpty() ||
                draft.formatting.isNotEmpty()
            ensureInputEmojiLoaded()
        }
    }
}

/** Loads the assets of every custom emoji in the draft as inline stickers. */
internal fun DialogScreenViewModel.ensureInputEmojiLoaded() {
    val currentSession = session ?: return

    val missing = inputCustomEmojis
        .map { span -> span.emojiId }
        .distinct()
        .filter { id -> id !in inputEmojiData && inFlightEmojiIds.add(id) }

    if (missing.isEmpty()) return

    viewModelScope.launch {
        val loaded = withContext(Dispatchers.Default) {
            missing.mapNotNull { id ->
                currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
            }.toMap()
        }

        inFlightEmojiIds.removeAll(missing)

        if (loaded.isNotEmpty()) {
            inputEmojiData = inputEmojiData + loaded.mapValues { (_, asset) -> asset.data }
            inputEmojiAdaptive = inputEmojiAdaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }
}

/** Queues picked files, capped at the album limit. */
internal fun DialogScreenViewModel.addAttachments(picked: List<PickedFile>, asPhoto: Boolean) {
    if (picked.isEmpty() || editingMessage != null) return

    // A new review starts from the saved default; the sheet can change it
    // for this send only.
    if (pendingAttachments.isEmpty()) {
        attachmentCompressPhotos = AppSettingsStore.state.value.compressPhotos
    }

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

/**
 * Puts an edited photo or a trimmed video back into the album. The row keeps
 * its position and the baked file replaces the picked one; only an image is
 * sent as a photo, a trimmed video stays a file.
 */
internal fun DialogScreenViewModel.replaceAttachment(index: Int, file: PickedFile) {
    val asPhoto = file.mimeType?.startsWith("image/") == true

    pendingAttachments = pendingAttachments.mapIndexed { itemIndex, attachment ->
        if (itemIndex == index) {
            PendingAttachment(file = file, asPhoto = asPhoto)
        } else {
            attachment
        }
    }
}

internal fun DialogScreenViewModel.beginRecording(kind: MediaRecordingKind) {
    if (isRecording) return

    recordingError = null
    recordedMedia = null
    recordingKind = kind
    recordingStartedAt = System.currentTimeMillis()
    recordingElapsedMs = 0L
    recordingWaveform = emptyList()
    isRecording = true

    // Navigation is frozen for the whole recording: leaving the chat would
    // release the recorder and silently drop the note being held.
    DialogRecordingState.setRecording(true)
}

/**
 * Starts the recorder after the preview has attached its surface. The screen
 * waits one frame first; the camera itself is opened here.
 */
internal fun DialogScreenViewModel.startRecording(recorder: MediaRecordingController) {
    if (!isRecording || recordingActive) return

    activeRecorder = recorder

    viewModelScope.launch {
        if (recordingKind == MediaRecordingKind.VideoNote) {
            delay(150L)
        }

        if (!isRecording || recordingActive) return@launch

        recordingStartedAt = System.currentTimeMillis()
        recordingElapsedMs = 0L
        recordingWaveform = emptyList()
        recordingActive = recorder.start(recordingKind)

        if (!recordingActive) {
            isRecording = false
            DialogRecordingState.setRecording(false)
            recordingError = if (recordingKind == MediaRecordingKind.VideoNote) {
                "Microphone or camera permission is required"
            } else {
                "Microphone permission is required"
            }
            return@launch
        }

        while (isRecording) {
            recordingElapsedMs = System.currentTimeMillis() - recordingStartedAt
            recordingWaveform = appendWaveformLevel(
                levels = recordingWaveform,
                level = recorder.currentAmplitude()
            )

            if (recordingKind == MediaRecordingKind.VideoNote &&
                recordingElapsedMs >= 60_000L
            ) {
                stopRecording()
                break
            }

            delay(100L)
        }
    }
}

internal fun DialogScreenViewModel.stopRecording() {
    if (!isRecording) return

    isRecording = false
    recordedMedia = if (recordingActive) activeRecorder?.stop() else null
    recordingActive = false
    recordingElapsedMs = 0L
    recordingWaveform = emptyList()
    DialogRecordingState.setRecording(false)
}

internal fun DialogScreenViewModel.cancelRecording() {
    if (!isRecording) return

    isRecording = false
    recordingActive = false
    activeRecorder?.cancel()
    recordedMedia = null
    recordingElapsedMs = 0L
    recordingWaveform = emptyList()
    DialogRecordingState.setRecording(false)
}

/** Releases a reviewed note that will not be sent. */
internal fun DialogScreenViewModel.discardRecording() {
    recordedMedia?.let { recording -> activeRecorder?.release(recording) }
    recordedMedia = null
    recordingError = null
}
