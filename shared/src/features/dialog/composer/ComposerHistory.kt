package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.model.MessageTextFormat

import androidx.compose.ui.text.TextRange

/**
 * Everything an undo step restores: the text, the caret and the rich text that
 * plain-text undo would drop, because emoji spans and formatting ranges are
 * positions inside the text and cannot be recomputed from it.
 */
internal data class ComposerSnapshot(
    val text: String = "",
    val selection: TextRange = TextRange.Zero,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val formatting: List<MessageTextFormat> = emptyList()
)

/**
 * How an edit enters the history. Consecutive [Typing] edits merge while they
 * follow each other, so one undo drops a word instead of a single character;
 * [Discrete] edits (a paste, an inserted sticker, a formatting toggle) always
 * stand on their own.
 */
internal enum class ComposerEditKind {
    Typing,
    Discrete
}

private data class ComposerHistoryEntry(
    val snapshot: ComposerSnapshot,
    val kind: ComposerEditKind,
    val at: Long
)

/**
 * Undo/redo stack of the composer. Each entry holds the state *before* an edit,
 * and [limit] keeps a long session from growing without bound — the oldest step
 * is dropped first, which is what the system text fields do too.
 */
internal class ComposerHistory(
    private val limit: Int = 100,
    private val typingMergeWindowMs: Long = 1_500L
) {
    private val entries = mutableListOf<ComposerHistoryEntry>()
    private val redoStack = mutableListOf<ComposerSnapshot>()

    val canUndo: Boolean get() = entries.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records [before] so the edit that is about to happen can be undone. */
    fun record(before: ComposerSnapshot, kind: ComposerEditKind, now: Long) {
        // Any new edit invalidates the redo branch.
        redoStack.clear()

        val last = entries.lastOrNull()

        if (
            kind == ComposerEditKind.Typing &&
            last?.kind == ComposerEditKind.Typing &&
            now - last.at <= typingMergeWindowMs
        ) {
            // Still the same typing run: keep the state from before it began
            // and only move the window forward.
            entries[entries.lastIndex] = last.copy(at = now)
            return
        }

        entries.add(ComposerHistoryEntry(before, kind, now))
        trim()
    }

    /** Returns the state to restore, leaving [current] available for redo. */
    fun undo(current: ComposerSnapshot): ComposerSnapshot? {
        val last = entries.removeLastOrNull() ?: return null

        redoStack.add(current)
        return last.snapshot
    }

    /** Re-applies the state undone last, if any. */
    fun redo(current: ComposerSnapshot): ComposerSnapshot? {
        val snapshot = redoStack.removeLastOrNull() ?: return null

        // The state we leave stays undoable, but a later keystroke must not
        // merge into it: `at = 0` puts it outside every typing window.
        entries.add(ComposerHistoryEntry(current, ComposerEditKind.Discrete, 0L))
        trim()

        return snapshot
    }

    /** Drops the whole history, for example when the draft is resent. */
    fun clear() {
        entries.clear()
        redoStack.clear()
    }

    private fun trim() {
        while (entries.size > limit) {
            entries.removeAt(0)
        }
    }
}
