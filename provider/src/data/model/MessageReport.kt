package com.mrboomdev.scrollix.data.model

/**
 * One reason the provider offers for reporting a message. [id] is an opaque
 * provider identifier encoded as hex so it can travel through UI state and be
 * handed back unchanged; the app never interprets it.
 */
data class MessageReportOption(
    val id: String,
    val title: String
)

/** The provider's answer to a report request. */
sealed interface MessageReportResult {
    /** The report was accepted. */
    data object Reported : MessageReportResult

    /** The provider wants a reason before it accepts the report. */
    data class OptionsRequired(
        val title: String,
        val options: List<MessageReportOption>
    ) : MessageReportResult

    /** The provider wants a free-form comment, optionally after a reason. */
    data class CommentRequired(
        val optionId: String,
        val isOptional: Boolean
    ) : MessageReportResult

    /** The provider needs the concrete messages, which this call did not give. */
    data object MessagesRequired : MessageReportResult
}
