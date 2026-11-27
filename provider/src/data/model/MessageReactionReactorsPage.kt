package com.mrboomdev.scrollix.data.model

/**
 * One page of everybody who reacted with a given reaction. Telegram only sends
 * a recent window with a message, so the full list is fetched separately with
 * [nextOffset] paging.
 */
data class MessageReactionReactorsPage(
    val totalCount: Int,
    val reactors: List<MessageReactionReactor>,
    val nextOffset: String = ""
)
