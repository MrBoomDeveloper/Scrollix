package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage

/**
 * Row the unread jump should land on. The boundary is stored as the raw id of
 * the first unread message, which may be any member of a media album, so the
 * row that owns the id is looked up instead of assuming a plain message row.
 * Returns null when the anchor is unknown or has not been loaded yet.
 */
internal fun unreadJumpRowId(messages: List<ChatMessage>, anchorMessageId: Long?): String? =
    anchorMessageId?.let { id ->
        messages.firstOrNull { message -> id in message.allMessageIds }?.rowId
    }
