package com.mrboomdev.scrollix.platform.clipboard

import com.mrboomdev.scrollix.data.storage.AndroidAppContext

import android.content.ClipboardManager

actual fun readClipboardRichText(): ClipboardRichText? {
    val context = runCatching { AndroidAppContext.context }.getOrNull() ?: return null

    val clip = runCatching {
        context.getSystemService(ClipboardManager::class.java)?.primaryClip
    }.getOrNull() ?: return null

    if (clip.itemCount == 0) return null

    var plain = ""
    var html: String? = null

    for (index in 0 until clip.itemCount) {
        val item = clip.getItemAt(index)

        if (html == null) {
            html = item.htmlText?.takeIf { value -> value.isNotBlank() }
        }

        if (plain.isEmpty()) {
            plain = item.text?.toString().orEmpty()
        }
    }

    if (plain.isEmpty() && html == null) return null

    return ClipboardRichText(plain = plain, html = html)
}
