package com.mrboomdev.scrollix.data.model

data class ChatMessage(
    val providerId: String,
    val accountId: String,
    val providerDialogId: String,
    val messageId: Long,
    val senderName: String? = null,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean
)
