package com.mrboomdev.scrollix

/**
 * Installs a process-wide handler for uncaught exceptions. [onCrash] runs on the
 * crashing thread and must not block; the previous handler is still invoked so
 * the crash is reported to the OS instead of being swallowed.
 */
expect fun installPlatformCrashHandler(
    onCrash: (threadName: String, throwable: Throwable) -> Unit
)
