package com.mrboomdev.scrollix

import com.mrboomdev.scrollix.features.home.key

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A desktop keyboard command. The desktop entry point maps key presses onto
 * these with [shortcutForKey] and publishes them; [App] applies the navigation.
 * Mobile builds have no keyboard handling, but the cheat sheet lists the same
 * table so the feature stays documented in one place.
 */
enum class AppShortcut(val title: String, val keys: String) {
    Search("Open search", "Ctrl/⌘ K"),
    Settings("Open settings", "Ctrl/⌘ ,"),
    ShortcutCheatSheet("Show keyboard shortcuts", "Ctrl/⌘ /"),
    NextChat("Next chat", "Alt ↓"),
    PreviousChat("Previous chat", "Alt ↑"),
    JumpToUnread("Jump to the first unread chat", "Alt U")
}

/** One published command; [id] distinguishes repeated presses of the same key. */
data class AppShortcutRequest(val shortcut: AppShortcut, val id: Long)

/**
 * Carries keyboard commands from the desktop window to [App], the same way
 * [BackNavigation] carries Escape and the Android back gesture.
 */
object KeyboardShortcuts {
    private val _requests = MutableStateFlow<AppShortcutRequest?>(null)
    val requests: StateFlow<AppShortcutRequest?> = _requests.asStateFlow()

    private var nextId = 0L

    fun request(shortcut: AppShortcut) {
        _requests.value = AppShortcutRequest(shortcut, nextId++)
    }

    /**
     * Publishes the command for a key press. Returns true when the press was a
     * shortcut, so the window can mark the event as handled.
     */
    fun handle(
        key: Key,
        ctrlOrMeta: Boolean,
        shift: Boolean = false,
        alt: Boolean = false
    ): Boolean {
        val shortcut = shortcutForKey(key, ctrlOrMeta, shift, alt) ?: return false
        request(shortcut)
        return true
    }

    fun consume() {
        _requests.value = null
    }
}

/**
 * Maps a desktop key press to a command. Plain typing and modifier
 * combinations reserved for text editing or other actions are ignored, so a
 * chat composer keeps its usual Ctrl+C/Ctrl+V behavior. Chat navigation uses
 * Alt+arrows because Ctrl+arrows move through words and paragraphs in a text
 * field.
 */
fun shortcutForKey(
    key: Key,
    ctrlOrMeta: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false
): AppShortcut? {
    if (shift) return null

    if (alt && !ctrlOrMeta) {
        return when (key) {
            Key.DirectionDown -> AppShortcut.NextChat
            Key.DirectionUp -> AppShortcut.PreviousChat
            Key.U -> AppShortcut.JumpToUnread
            else -> null
        }
    }

    if (!ctrlOrMeta || alt) return null

    return when (key) {
        Key.K -> AppShortcut.Search
        Key.Comma -> AppShortcut.Settings
        Key.Slash -> AppShortcut.ShortcutCheatSheet
        else -> null
    }
}
