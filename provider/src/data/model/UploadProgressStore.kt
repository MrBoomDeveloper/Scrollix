package com.mrboomdev.scrollix.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Upload progress of outgoing files, keyed by the local path they are sent
 * from. Providers report it while the bytes are still on their way; the
 * pending message row reads the fraction so a large file shows how far it got
 * instead of an endless "sending" mark.
 */
object UploadProgressStore {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun report(path: String, fraction: Float) {
        if (path.isBlank()) return

        val value = fraction.coerceIn(0f, 1f)

        _progress.update { current -> current + (path to value) }
    }

    fun clear(path: String) {
        if (path.isBlank()) return

        _progress.update { current -> current - path }
    }
}
