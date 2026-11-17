package com.mrboomdev.scrollix

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onSecondaryClick(action: () -> Unit): Modifier {
    return onPointerEvent(PointerEventType.Press) { event ->
        if (event.buttons.isSecondaryPressed) {
            action()
        }
    }
}
