package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

/** What a button of a bot's reply keyboard asks for when it is pressed. */
@Serializable
enum class ReplyKeyboardButtonKind {
    /** A plain button: pressing it sends its [ReplyKeyboardButton.text]. */
    Text,

    /** Shares the device location instead of a text. */
    RequestLocation,

    /** Shares the account's phone number. */
    RequestPhoneNumber,

    /** Opens the poll composer. */
    RequestPoll,

    /** Opens a mini app owned by [ReplyKeyboardButton.botUserId]. */
    WebApp,

    /** A button type this client cannot act on; rendered as plain text. */
    Unsupported
}

/** One button of the reply keyboard a bot installs below the composer. */
@Serializable
data class ReplyKeyboardButton(
    val text: String,
    val kind: ReplyKeyboardButtonKind = ReplyKeyboardButtonKind.Text,
    /** URL of a mini app button, passed back to the provider untouched. */
    val webAppUrl: String? = null,
    /** Bot that owns [webAppUrl]; the provider needs it to launch. */
    val botUserId: Long? = null
)

/**
 * The reply keyboard of the chat. It belongs to the chat rather than to a
 * single message: the last bot message that carried one decides what the
 * composer shows until the bot replaces or removes it.
 */
@Serializable
data class MessageReplyKeyboard(
    val rows: List<List<ReplyKeyboardButton>> = emptyList(),
    /** Hint shown in the input field while this keyboard is active. */
    val placeholder: String = "",
    /** Keep the keyboard on screen instead of letting the user hide it. */
    val isPersistent: Boolean = false,
    /** Hide the keyboard after the account sends the next message. */
    val isOneTime: Boolean = false
) {
    val isEmpty: Boolean get() = rows.all { row -> row.isEmpty() }
}

/**
 * What a bot answered to a pressed callback button. [text] is a toast, or an
 * alert when [showAlert] is set; [url] has to be opened after it.
 */
data class BotCallbackAnswer(
    val text: String = "",
    val showAlert: Boolean = false,
    val url: String? = null
)

/**
 * One result of an inline query. Only what the picker shows is mapped; the
 * result itself stays on the provider's side and is sent by id.
 */
@Serializable
data class InlineBotResult(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnailFileId: Int? = null,
    val thumbnailUniqueId: String? = null
)

/** One page of inline results, with the id needed to send a picked one. */
data class InlineBotPage(
    val queryId: Long,
    val results: List<InlineBotResult> = emptyList(),
    /** Offset of the next page; empty when this is the last one. */
    val nextOffset: String = ""
)

/** One command a bot advertises in its menu. */
@Serializable
data class BotCommandInfo(
    val command: String,
    val description: String = "",
    /** Ephemeral commands are hidden from the regular menu. */
    val isEphemeral: Boolean = false
) {
    /** The text inserted into the composer, including the leading slash. */
    val insertion: String get() = "/$command"
}
