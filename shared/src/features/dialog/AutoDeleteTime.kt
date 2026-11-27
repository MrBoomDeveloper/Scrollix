package com.mrboomdev.scrollix.features.dialog

/** One tappable choice of the chat auto-delete picker. */
internal data class AutoDeletePreset(
    val label: String,
    val seconds: Int
)

private const val Minute = 60
private const val Hour = 3_600
private const val Day = 86_400
private const val Week = 604_800
private const val Month = 2_592_000

private val presets = listOf(
    AutoDeletePreset("Off", 0),
    AutoDeletePreset("1 hour", Hour),
    AutoDeletePreset("1 day", Day),
    AutoDeletePreset("1 week", Week),
    AutoDeletePreset("1 month", Month)
)

/**
 * The picker's choices. A timer set by another client that is not one of the
 * presets is kept as an extra row, so opening the picker never silently offers
 * a different value than the chat actually has.
 */
internal fun autoDeletePresets(currentSeconds: Int): List<AutoDeletePreset> {
    if (currentSeconds <= 0 || presets.any { it.seconds == currentSeconds }) {
        return presets
    }

    return presets + AutoDeletePreset(autoDeleteLabel(currentSeconds), currentSeconds)
}

/** Human label for any timer value, including custom ones. */
internal fun autoDeleteLabel(seconds: Int): String {
    if (seconds <= 0) return "Off"

    // Larger units first: a week and a month are also whole numbers of days,
    // so checking days first would label them "7 days" and "30 days".
    return when {
        seconds == Month -> "1 month"
        seconds % Week == 0 && seconds >= Week -> count(seconds / Week, "week")
        seconds % Day == 0 -> count(seconds / Day, "day")
        seconds % Hour == 0 -> count(seconds / Hour, "hour")
        seconds % Minute == 0 -> count(seconds / Minute, "minute")
        else -> "${seconds}s"
    }
}

private fun count(value: Int, unit: String): String {
    return "$value $unit${if (value == 1) "" else "s"}"
}
