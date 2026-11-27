package com.mrboomdev.scrollix.features.dialog

/**
 * Decides whether a draft edited on another device may replace the open
 * composer.
 *
 * The remote draft wins only while the local text still equals the last server
 * draft this device saw, so unfinished typing is never lost. When the two
 * differ, the local text stays and the debounced save pushes it to the server.
 * Repeating the same remote text changes nothing.
 */
internal fun shouldApplyRemoteDraft(
    localText: String,
    knownServerText: String,
    remoteText: String
): Boolean {
    if (remoteText == knownServerText) return false

    return localText == knownServerText
}
