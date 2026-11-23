package com.mrboomdev.scrollix.data

/**
 * Process-wide mode that has to be decided before any store, database or
 * session is created.
 *
 * Entry points call `TelegramIntegration.init()` first; it copies the
 * build-generated flag here, and every later `init()` (MAX, VK) and every
 * lazily created storage reads it. That keeps test-server builds fully
 * isolated from a normal install on the same device.
 */
object AppMode {
    /** True when the app was built for the Telegram test server. */
    var isTestServerMode: Boolean = false
        private set

    /**
     * Directory name of the storage profile. Test builds use their own root,
     * so settings, accounts, databases, media cache and TDLib directories of a
     * test run never mix with a regular one.
     */
    val storageProfileName: String
        get() = if (isTestServerMode) "scrollix-test" else "scrollix"

    fun setTestServerMode(enabled: Boolean) {
        isTestServerMode = enabled
    }

    /**
     * Providers without a publicly available test server are left out of the
     * registry in this mode, so they cannot be added or contacted at all.
     */
    fun isProviderEnabled(providerId: String): Boolean {
        return !isTestServerMode || providerId == "telegram"
    }
}
