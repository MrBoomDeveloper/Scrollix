package com.mrboomdev.scrollix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between the platform entry point and Compose. Android publishes the
 * t.me link that launched or resumed the app; [App] consumes it and opens the
 * chat inside the app instead of handing the link to a browser.
 */
object DeepLinkHandler {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun publish(url: String?) {
        val link = url?.trim().orEmpty()
        if (link.isEmpty()) return
        if (!isTelegramLink(link)) return
        _pending.value = link
    }

    fun consume() {
        _pending.value = null
    }

    fun isTelegramLink(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("tg://") ||
            lower.contains("t.me/") ||
            lower.contains("telegram.me/")
    }
}
