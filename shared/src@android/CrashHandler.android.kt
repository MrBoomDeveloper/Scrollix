package com.mrboomdev.scrollix

private var crashHandlerInstalled = false

actual fun installPlatformCrashHandler(
    onCrash: (threadName: String, throwable: Throwable) -> Unit
) {
    if (crashHandlerInstalled) return
    crashHandlerInstalled = true

    val previous = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { onCrash(thread.name, throwable) }

        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            throwable.printStackTrace()
            Runtime.getRuntime().halt(2)
        }
    }
}
