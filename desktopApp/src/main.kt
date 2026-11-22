package com.mrboomdev.scrollix

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mrboomdev.scrollix.ui.components.ScrollixContextMenuRepresentation
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.telegram.TelegramIntegration
import kotlinx.coroutines.runBlocking
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

fun main() {
    TelegramIntegration.init()
    installHorizontalScrollSupport()

    application {
        Window(
            onCloseRequest = {
                runBlocking {
                    ApiManager.shutdown()
                }
                exitApplication()
            },
            title = "Scrollix"
        ) {
            CompositionLocalProvider(
                LocalContextMenuRepresentation provides ScrollixContextMenuRepresentation
            ) {
                App()
            }
        }
    }
}

private fun installHorizontalScrollSupport() {
    val toolkit = Toolkit.getDefaultToolkit()
    if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
    toolkit.systemEventQueue.push(HorizontalScrollEventQueue())
}

private class HorizontalScrollEventQueue : EventQueue() {
    override fun dispatchEvent(event: AWTEvent) {
        super.dispatchEvent(mapHorizontalScroll(event))
    }

    private fun mapHorizontalScroll(event: AWTEvent): AWTEvent {
        if (event !is MouseEvent || event.button < 4) {
            return event
        }

        return if (event.button < 6) {
            @Suppress("DEPRECATION")
            val modifiers = event.modifiers or InputEvent.SHIFT_DOWN_MASK

            MouseWheelEvent(
                event.component,
                MouseEvent.MOUSE_WHEEL,
                event.`when`,
                modifiers,
                event.x,
                event.y,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                event.clickCount,
                if (event.button == 4) -1 else 1
            )
        } else {
            @Suppress("DEPRECATION")
            val modifiers = event.modifiers or (1 shl (8 + event.button))

            MouseEvent(
                event.component,
                event.id,
                event.`when`,
                modifiers,
                event.x,
                event.y,
                1,
                event.isPopupTrigger,
                event.button - 2
            )
        }
    }
}
