package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DialogRecordingState
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.platform.files.PickedFile
import com.mrboomdev.scrollix.platform.media.MediaRecordingController
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
    if (value.isEmpty()) {
        // A programmatic reset never reaches onValueChange, so its spans are
        // dropped here instead of pointing past the end of the text.
        if (inputCustomEmojis.isNotEmpty()) {
            inputCustomEmojis = emptyList()
        }
    } else if (value != input) {
        inputCustomEmojis = adjustCustomEmojiSpans(
            oldText = input,
            newText = value,
            spans = inputCustomEmojis
        )
    }

    input = value
    inputSelection = selection

    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

/**
 * Inserts text at the caret and leaves the caret after it. Custom emoji runs are
 * tracked, so a sticker inserted mid-sentence stays where it was put while the
 * user keeps typing after it.
 */
internal fun DialogScreenViewModel.insertIntoComposer(text: String, emojiId: Long? = null) {
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

    input = updated
    inputSelection = TextRange(start + text.length)

    ensureInputEmojiLoaded()
    scheduleDraftSave()
}

/** Appends to the end of the draft, as the attachment caption field does. */
internal fun DialogScreenViewModel.appendToComposer(text: String, emojiId: Long?) {
    val start = input.length

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
            hadServerDraft = draft.text.isNotEmpty() || draft.customEmojis.isNotEmpty()

            if (input.isEmpty() && editingMessage == null) {
                input = draft.text
                inputCustomEmojis = draft.customEmojis
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

    if (input.isEmpty() && inputCustomEmojis.isEmpty() && !hadServerDraft) {
        return
    }

    draftJob?.cancel()
    draftJob = viewModelScope.launch {
        delay(600.milliseconds)

        val currentSession = session ?: return@launch
        currentSession.saveDraft(providerDialogId, input, inputCustomEmojis)
        hadServerDraft = input.isNotEmpty() || inputCustomEmojis.isNotEmpty()
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

internal fun DialogScreenViewModel.beginRecording(kind: MediaRecordingKind) {
    if (isRecording) return

    recordingError = null
    recordedMedia = null
    recordingKind = kind
    recordingStartedAt = System.currentTimeMillis()
    recordingElapsedMs = 0L
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
    DialogRecordingState.setRecording(false)
}

internal fun DialogScreenViewModel.cancelRecording() {
    if (!isRecording) return

    isRecording = false
    recordingActive = false
    activeRecorder?.cancel()
    recordedMedia = null
    recordingElapsedMs = 0L
    DialogRecordingState.setRecording(false)
}

/** Releases a reviewed note that will not be sent. */
internal fun DialogScreenViewModel.discardRecording() {
    recordedMedia?.let { recording -> activeRecorder?.release(recording) }
    recordedMedia = null
    recordingError = null
}
