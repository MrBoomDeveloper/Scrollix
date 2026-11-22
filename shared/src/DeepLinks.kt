package com.mrboomdev.scrollix

import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A link waiting to be handled. [id] changes on every publish, so publishing
 * the same URL twice is observable even though the URL itself did not change.
 */
data class DeepLinkRequest(val id: Long, val url: String)

/**
 * Bridge between the platform entry point and Compose. Android publishes the
 * t.me link that launched or resumed the app; [App] consumes it and opens the
 * chat inside the app instead of handing the link to a browser.
 */
object DeepLinkHandler {
    private val _pending = MutableStateFlow<DeepLinkRequest?>(null)
    val pending: StateFlow<DeepLinkRequest?> = _pending.asStateFlow()

    private var nextId = 0L
    private var lastBrowserFallbackUrl: String? = null
    private var lastBrowserFallbackAt = 0L

    fun publish(url: String?) {
        val link = url?.trim().orEmpty()
        if (link.isEmpty()) return

        // Telegram links are resolved by the provider. Plain http(s) links are
        // accepted too so App can fall back to the system browser instead of
        // silently dropping a URL the app was asked to open.
        val isHttp = link.startsWith("http://", ignoreCase = true) ||
            link.startsWith("https://", ignoreCase = true)

        if (!isTelegramLink(link) && !isHttp) return

        nextId += 1
        _pending.value = DeepLinkRequest(nextId, link)
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

    /**
     * Guards the browser fallback against a loop when this app is also the
     * default handler for the same https link. Returns false for an immediate
     * repeat of the URL that was just sent to the browser.
     */
    fun allowBrowserFallback(url: String): Boolean {
        val now = currentTimeMillis()
        val repeated = url == lastBrowserFallbackUrl &&
            now - lastBrowserFallbackAt < BrowserFallbackCooldownMillis

        lastBrowserFallbackUrl = url
        lastBrowserFallbackAt = now

        return !repeated
    }

    private const val BrowserFallbackCooldownMillis = 5_000L
}
