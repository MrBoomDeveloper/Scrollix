package com.mrboomdev.scrollix.data.model

/** The server's auto-download profiles, from least to most data hungry. */
enum class DataUsagePreset(val title: String) {
    Low("Low"),
    Medium("Medium"),
    High("High")
}

/**
 * Traffic counted for one account's session since [sinceMillis]. The counter is
 * kept by the provider; resetting it only clears local traffic statistics.
 */
data class DataUsageSummary(
    val sinceMillis: Long = 0L,
    val sentBytes: Long = 0L,
    val receivedBytes: Long = 0L,
    val callsDurationSeconds: Long = 0L
) {
    val totalBytes: Long get() = sentBytes + receivedBytes
}
