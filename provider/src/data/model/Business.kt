package com.mrboomdev.scrollix.data.model

/** One saved quick reply shortcut of a business account. */
data class BusinessQuickReply(
    val id: Int,
    val name: String,
    /** First line of the first message, for the picker. */
    val preview: String = "",
    val messageCount: Int = 1
)

/** One opening interval, as minutes since midnight. */
data class BusinessHoursInterval(
    val startMinute: Int,
    val endMinute: Int
) {
    val label: String
        get() = "${formatMinute(startMinute)} – ${formatMinute(endMinute)}"
}

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % MinutesPerDay) + MinutesPerDay) % MinutesPerDay
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

private const val MinutesPerDay = 24 * 60

/**
 * Business settings of the account. [greeting] and [away] are null when the
 * feature is off; their text is the first message of the shortcut Telegram
 * keeps for it.
 */
data class BusinessSettings(
    /** True when the account has a Telegram Business subscription. */
    val isAvailable: Boolean = false,
    val greeting: String? = null,
    val away: String? = null,
    /** Away messages only outside the opening hours instead of always. */
    val awayOutsideHours: Boolean = false,
    val timeZoneId: String = "",
    val openingHours: List<BusinessHoursInterval> = emptyList()
)
