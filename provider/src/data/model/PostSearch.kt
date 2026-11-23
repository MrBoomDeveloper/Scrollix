package com.mrboomdev.scrollix.data.model

/** Which public chats a provider should return for a global chat search. */
enum class PublicChatSearchKind {
    Any,
    Channels,
    Bots
}

/** Free public post search quota reported by the messenger server. */
data class PostSearchQuota(
    val dailyFreeQueryCount: Int,
    val remainingFreeQueryCount: Int,
    val nextFreeQueryIn: Int,
    val starCount: Long,
    val isCurrentQueryFree: Boolean
)

/**
 * One page of a global public post search. [dialogs] carries the channels the
 * posts belong to, including channels the account has not joined, so results
 * can be rendered without them being added to the chat list.
 */
data class PostSearchPage(
    val messages: List<ChatMessage> = emptyList(),
    val dialogs: List<Dialog> = emptyList(),
    val nextOffset: String = "",
    val quota: PostSearchQuota? = null,
    val areLimitsExceeded: Boolean = false
)
