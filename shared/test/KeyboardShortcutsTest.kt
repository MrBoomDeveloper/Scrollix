package com.mrboomdev.scrollix

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardShortcutsTest {
    @Test
    fun `ctrl or meta with K opens search`() {
        assertEquals(AppShortcut.Search, shortcutForKey(Key.K, ctrlOrMeta = true))
    }

    @Test
    fun `comma opens settings and slash opens the cheat sheet`() {
        assertEquals(AppShortcut.Settings, shortcutForKey(Key.Comma, ctrlOrMeta = true))
        assertEquals(
            AppShortcut.ShortcutCheatSheet,
            shortcutForKey(Key.Slash, ctrlOrMeta = true)
        )
    }

    @Test
    fun `plain typing is left to the focused field`() {
        assertNull(shortcutForKey(Key.K))
        assertNull(shortcutForKey(Key.Comma))
        assertNull(shortcutForKey(Key.Slash))
    }

    @Test
    fun `shift and alt combinations are not shortcuts`() {
        assertNull(shortcutForKey(Key.K, ctrlOrMeta = true, shift = true))
        assertNull(shortcutForKey(Key.K, ctrlOrMeta = true, alt = true))
    }

    @Test
    fun `alt with the arrow keys walks the chat list`() {
        assertEquals(AppShortcut.NextChat, shortcutForKey(Key.DirectionDown, alt = true))
        assertEquals(AppShortcut.PreviousChat, shortcutForKey(Key.DirectionUp, alt = true))
    }

    @Test
    fun `alt with U jumps to the first unread chat`() {
        assertEquals(AppShortcut.JumpToUnread, shortcutForKey(Key.U, alt = true))
        assertNull(shortcutForKey(Key.U, ctrlOrMeta = true, alt = true))
    }

    @Test
    fun `arrows without alt stay with text navigation`() {
        assertNull(shortcutForKey(Key.DirectionDown))
        assertNull(shortcutForKey(Key.DirectionUp, ctrlOrMeta = true))
    }

    @Test
    fun `alt with other keys is not a shortcut`() {
        assertNull(shortcutForKey(Key.K, alt = true))
        assertNull(shortcutForKey(Key.DirectionDown, ctrlOrMeta = true, alt = true))
    }

    @Test
    fun `unmapped keys are ignored`() {
        assertNull(shortcutForKey(Key.P, ctrlOrMeta = true))
        assertNull(shortcutForKey(Key.Escape, ctrlOrMeta = true))
    }

    @Test
    fun `handling a mapped press publishes a new request`() {
        KeyboardShortcuts.consume()

        val handled = KeyboardShortcuts.handle(Key.K, ctrlOrMeta = true)

        try {
            assertEquals(true, handled)
            assertEquals(
                AppShortcut.Search,
                KeyboardShortcuts.requests.value?.shortcut
            )
        } finally {
            KeyboardShortcuts.consume()
        }
    }

    @Test
    fun `unmapped presses are not published`() {
        KeyboardShortcuts.consume()

        assertEquals(false, KeyboardShortcuts.handle(Key.P, ctrlOrMeta = true))
        assertNull(KeyboardShortcuts.requests.value)
    }
}
