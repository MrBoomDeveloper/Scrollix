package com.mrboomdev.scrollix.data.api

/**
 * A t.me link that points into a chat. Private links
 * (`t.me/c/<internal-id>/<message>`) carry a numeric id instead of a username,
 * so exactly one of [providerDialogId] and [username] is set. [messageId] is
 * null when the link only opens the chat.
 */
internal data class TelegramMessageLink(
    val username: String? = null,
    val providerDialogId: String? = null,
    val messageId: Long? = null
)

/**
 * Extracts the path of a t.me/telegram.me link without the scheme, query or
 * fragment. Returns null for other links, including `tg://` ones, which the
 * provider classifies separately.
 */
internal fun telegramLinkPath(link: String): String? {
    val lower = link.lowercase()
    val marker = when {
        lower.contains("t.me/") -> "t.me/"
        lower.contains("telegram.me/") -> "telegram.me/"
        else -> return null
    }

    return link.substringAfter(marker, "")
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
        .takeIf { path -> path.isNotBlank() }
}

/**
 * Parses a t.me link into the chat and message it addresses. Invite links,
 * sticker-set links, proxy links and `tg://` links return null so the caller
 * keeps its existing classification.
 */
internal fun parseTelegramMessageLink(link: String): TelegramMessageLink? {
    val path = telegramLinkPath(link) ?: return null
    val segments = path.split('/').filter { segment -> segment.isNotBlank() }
    val first = segments.firstOrNull() ?: return null

    // A private link is the only form with a numeric chat id; a username can
    // never be the literal "c".
    if (first == "c") {
        val internalId = segments.getOrNull(1)?.toLongOrNull() ?: return null
        if (internalId <= 0) return null

        return TelegramMessageLink(
            providerDialogId = supergroupIdToDialogId(internalId),
            messageId = segments.getOrNull(2)?.toLongOrNull()
        )
    }

    if (first.startsWith("+") ||
        first == "joinchat" ||
        first == "addstickers" ||
        first == "addemoji" ||
        first == "proxy" ||
        first == "socks" ||
        first == "login"
    ) {
        return null
    }

    // A topic link is username/<topic-id>/<message-id>; a plain message link is
    // username/<message-id>. Only the last numeric segment is the message.
    val messageId = segments.drop(1)
        .lastOrNull { segment -> segment.toLongOrNull() != null }
        ?.toLongOrNull()

    return TelegramMessageLink(username = first, messageId = messageId)
}

/**
 * Converts the numeric id Telegram puts into private links into the TDLib chat
 * id of the same supergroup or channel: `-(1000000000000 + id)`.
 */
internal fun supergroupIdToDialogId(internalId: Long): String {
    return (-1_000_000_000_000L - internalId).toString()
}
