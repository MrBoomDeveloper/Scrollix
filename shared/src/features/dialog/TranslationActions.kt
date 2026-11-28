package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.features.dialog.viewmodel.DialogScreenViewModel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Persists the target language of the chat translator. Translations already on
 * screen were made for the previous language, so they are dropped: keeping them
 * would mix two languages under one "Show original" toggle.
 */
internal fun DialogScreenViewModel.setTranslationLanguage(code: String?) {
    translationJob?.cancel()
    isTranslatingChat = false
    translationProgress = null
    isChatTranslationEnabled = false

    translations = emptyMap()
    hiddenTranslations = emptySet()
    translatingMessageIds = emptySet()

    val normalized = normalizeTranslationLanguage(code)

    AppSettingsStore.update { settings ->
        settings.copy(translationLanguage = normalized)
    }
}

/**
 * Translates every loaded message that has text and no translation yet, then
 * keeps translating messages as older pages or new arrivals show up.
 */
internal fun DialogScreenViewModel.translateLoadedMessages() {
    val currentSession = session ?: return
    if (!currentSession.supportsTranslation || isTranslatingChat) return

    isChatTranslationEnabled = true

    startTranslation(messagesToTranslate(displayMessages, translations.keys))
}

/**
 * Called whenever the history changes: a conversation translated once keeps
 * up with paging and incoming messages instead of only covering the window
 * that existed when the action ran.
 */
internal fun DialogScreenViewModel.translateNewMessages() {
    if (!isChatTranslationEnabled || isTranslatingChat) return

    startTranslation(messagesToTranslate(displayMessages, translations.keys))
}

private fun DialogScreenViewModel.startTranslation(targets: List<ChatMessage>) {
    val currentSession = session ?: return
    if (targets.isEmpty()) return

    val language = translationLanguage

    isTranslatingChat = true
    translationProgress = TranslationProgress(done = 0, total = targets.size)

    translationJob = viewModelScope.launch {
        var failures = 0

        targets.forEachIndexed { index, message ->
            currentSession.translateText(message.text, language)
                .onSuccess { translated ->
                    translations = translations + (message.messageId to translated)
                    hiddenTranslations = hiddenTranslations - message.messageId
                }
                .onFailure { failures++ }

            translationProgress = TranslationProgress(
                done = index + 1,
                total = targets.size
            )
        }

        isTranslatingChat = false
        translationProgress = null

        if (failures > 0) {
            error = if (failures == targets.size) {
                "Couldn't translate the chat"
            } else {
                "Translated ${targets.size - failures} of ${targets.size} messages"
            }
        }
    }
}

internal fun DialogScreenViewModel.cancelChatTranslation() {
    translationJob?.cancel()
    translationJob = null
    isTranslatingChat = false
    isChatTranslationEnabled = false
    translationProgress = null
}

/** Shows or hides every translation this screen has loaded with one action. */
internal fun DialogScreenViewModel.setTranslationsVisible(visible: Boolean) {
    hiddenTranslations = if (visible) {
        emptySet()
    } else {
        translations.keys
    }
}
