package com.mrboomdev.scrollix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide recording flag. Navigation is frozen while a voice or video
 * note is being recorded: leaving the chat would release the recorder and
 * silently drop the note the user is still holding.
 */
object DialogRecordingState {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    val isActive: Boolean
        get() = _isRecording.value

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }
}
