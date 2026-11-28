package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.BusinessHoursInterval
import com.mrboomdev.scrollix.data.model.BusinessQuickReply
import com.mrboomdev.scrollix.data.model.BusinessSettings
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NoBusinessQuickReplies = MutableStateFlow<List<BusinessQuickReply>>(emptyList())

/**
 * Telegram Business features: greeting and away messages, opening hours and
 * quick replies. Providers without them keep the defaults, so the settings
 * section simply reports that the account cannot use them.
 */
interface BusinessSession {
    /**
     * Whether this provider can read and edit business settings at all. The
     * account still needs its own subscription; a provider without support
     * must not show the section instead of showing a disabled one.
     */
    val supportsBusinessSettings: Boolean
        get() = false

    /**
     * Quick reply shortcuts of the account, kept current by provider updates.
     * The list is empty for a non-business account.
     */
    val businessQuickReplies: StateFlow<List<BusinessQuickReply>>
        get() = NoBusinessQuickReplies

    /** Everything the business settings editor shows. */
    suspend fun loadBusinessSettings(): Result<BusinessSettings> =
        Result.success(BusinessSettings())

    /** Sets the greeting, or turns it off when [text] is null or blank. */
    suspend fun setBusinessGreeting(text: String?): Result<Unit> =
        businessUnsupported()

    /** Sets the away message, or turns it off when [text] is null or blank. */
    suspend fun setBusinessAway(
        text: String?,
        outsideOpeningHours: Boolean = false
    ): Result<Unit> = businessUnsupported()

    /** Replaces the opening hours; an empty list removes them. */
    suspend fun setBusinessOpeningHours(
        timeZoneId: String,
        intervals: List<BusinessHoursInterval>
    ): Result<Unit> = businessUnsupported()

    /** Saves a new quick reply shortcut with a single text message. */
    suspend fun createBusinessQuickReply(name: String, text: String): Result<Unit> =
        businessUnsupported()

    /** Deletes a shortcut, including its messages. */
    suspend fun deleteBusinessQuickReply(shortcutId: Int): Result<Unit> =
        businessUnsupported()

    /** Sends every message of a shortcut into the chat. */
    suspend fun sendBusinessQuickReply(
        providerDialogId: String,
        shortcutId: Int
    ): Result<Unit> = businessUnsupported()
}

private fun <T> businessUnsupported(): Result<T> = Result.failure(
    SessionException(
        SessionErrorCode.Unsupported,
        "Business features are not supported by this provider"
    )
)
