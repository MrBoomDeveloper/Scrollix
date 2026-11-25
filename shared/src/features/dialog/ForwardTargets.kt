package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog

/**
 * Filters the forwarding picker by chat title. Every whitespace-separated token
 * has to occur in the title, so a second word narrows the list instead of
 * widening it; an empty query keeps every target in activity order.
 */
internal fun filterForwardTargets(
    targets: List<Dialog>,
    query: String
): List<Dialog> {
    val tokens = query.trim().lowercase()
        .split(Regex("\\s+"))
        .filter { token -> token.isNotEmpty() }

    if (tokens.isEmpty()) return targets

    return targets.filter { target ->
        val title = target.title.lowercase()
        tokens.all { token -> title.contains(token) }
    }
}
