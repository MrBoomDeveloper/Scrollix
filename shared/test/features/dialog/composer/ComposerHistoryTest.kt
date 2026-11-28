package com.mrboomdev.scrollix.features.dialog.composer

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerHistoryTest {
    private fun snapshot(text: String) = ComposerSnapshot(
        text = text,
        selection = TextRange(text.length)
    )

    @Test
    fun `typing run is undone in one step`() {
        val history = ComposerHistory(typingMergeWindowMs = 1_000L)

        history.record(snapshot(""), ComposerEditKind.Typing, now = 0L)
        history.record(snapshot("h"), ComposerEditKind.Typing, now = 100L)
        history.record(snapshot("he"), ComposerEditKind.Typing, now = 200L)

        assertEquals("", history.undo(snapshot("hel"))?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun `a pause starts a new step`() {
        val history = ComposerHistory(typingMergeWindowMs = 1_000L)

        history.record(snapshot(""), ComposerEditKind.Typing, now = 0L)
        history.record(snapshot("hello"), ComposerEditKind.Typing, now = 5_000L)

        assertEquals("hello", history.undo(snapshot("hello world"))?.text)
        assertEquals("", history.undo(snapshot("hello"))?.text)
    }

    @Test
    fun `a discrete edit never merges with typing`() {
        val history = ComposerHistory(typingMergeWindowMs = 1_000L)

        history.record(snapshot(""), ComposerEditKind.Typing, now = 0L)
        history.record(snapshot("ab"), ComposerEditKind.Discrete, now = 100L)

        // Undo drops the paste, and the next undo drops the typing run.
        assertEquals("ab", history.undo(snapshot("abcd"))?.text)
        assertEquals("", history.undo(snapshot("ab"))?.text)
    }

    @Test
    fun `redo restores the undone state and undo returns to it`() {
        val history = ComposerHistory()

        history.record(snapshot(""), ComposerEditKind.Discrete, now = 0L)

        assertEquals("", history.undo(snapshot("typed"))?.text)
        assertTrue(history.canRedo)

        assertEquals("typed", history.redo(snapshot(""))?.text)
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)

        // The redone state is undoable again.
        assertEquals("", history.undo(snapshot("typed"))?.text)
    }

    @Test
    fun `a new edit drops the redo branch`() {
        val history = ComposerHistory()

        history.record(snapshot(""), ComposerEditKind.Discrete, now = 0L)
        history.undo(snapshot("typed"))
        assertTrue(history.canRedo)

        history.record(snapshot(""), ComposerEditKind.Discrete, now = 10L)

        assertFalse(history.canRedo)
        assertNull(history.redo(snapshot("other")))
    }

    @Test
    fun `the oldest step is dropped past the limit`() {
        val history = ComposerHistory(limit = 2)

        history.record(snapshot("one"), ComposerEditKind.Discrete, now = 0L)
        history.record(snapshot("two"), ComposerEditKind.Discrete, now = 1L)
        history.record(snapshot("three"), ComposerEditKind.Discrete, now = 2L)

        assertEquals("three", history.undo(snapshot("four"))?.text)
        assertEquals("two", history.undo(snapshot("three"))?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun `clear forgets both directions`() {
        val history = ComposerHistory()

        history.record(snapshot(""), ComposerEditKind.Discrete, now = 0L)
        history.undo(snapshot("text"))
        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo on an empty history returns nothing`() {
        val history = ComposerHistory()

        assertNull(history.undo(snapshot("text")))
        assertNull(history.redo(snapshot("text")))
    }
}
