package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind


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

/** Target kind a folder page shows; "all" keeps every kind. */
internal fun matchesForwardFolder(target: Dialog, folderKey: String): Boolean = when (folderKey) {
    "direct" -> target.kind == DialogKind.Direct || target.kind == DialogKind.Secret
    "groups" -> target.kind == DialogKind.Group
    "channels" -> target.kind == DialogKind.Channel
    "bots" -> target.kind == DialogKind.Bot
    else -> true
}

/** Targets of one folder page after the search query is applied. */
internal fun forwardTargetsForFolder(
    targets: List<Dialog>,
    query: String,
    folderKey: String
): List<Dialog> = filterForwardTargets(targets, query)
    .filter { target -> matchesForwardFolder(target, folderKey) }
