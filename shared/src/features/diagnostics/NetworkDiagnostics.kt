package com.mrboomdev.scrollix.features.diagnostics

/**
 * Connection state for one account as diagnostics present it. An account
 * without a live session is still signing in, not offline; a session whose
 * flag has not emitted yet is treated as connected rather than flashing a
 * false "Offline".
 */
internal fun networkConnectionLabel(hasSession: Boolean, isConnected: Boolean?): String = when {
    !hasSession -> "Signing in"
    isConnected == false -> "Offline"
    else -> "Connected"
}

/** One line for the outgoing queue; zero counters read as "Nothing queued". */
internal fun outboxSummary(waiting: Int, sending: Int, failed: Int): String {
    val parts = buildList {
        if (waiting > 0) add("$waiting waiting")
        if (sending > 0) add("$sending sending")
        if (failed > 0) add("$failed failed")
    }

    return if (parts.isEmpty()) "Nothing queued" else parts.joinToString(", ")
}
