package com.mrboomdev.scrollix.platform.clipboard

/**
 * The current clipboard contents. Rich sources (browsers, editors) usually
 * offer HTML next to the plain text, which keeps the original formatting.
 */
data class ClipboardRichText(
    val plain: String,
    val html: String? = null
)

/** Reads the clipboard, or null when nothing text-like is there. */
expect fun readClipboardRichText(): ClipboardRichText?
