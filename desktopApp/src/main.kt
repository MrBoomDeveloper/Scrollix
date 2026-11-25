package com.mrboomdev.scrollix

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import com.mrboomdev.scrollix.ui.components.ScrollixContextMenuRepresentation
import com.mrboomdev.scrollix.features.login.shutdownCaptchaBrowser
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.diagnostics.CrashReports
import com.mrboomdev.scrollix.data.max.MaxIntegration
import com.mrboomdev.scrollix.data.telegram.TelegramIntegration
import com.mrboomdev.scrollix.data.vk.VkIntegration

fun main() {
    TelegramIntegration.init()
    MaxIntegration.init()
    VkIntegration.init()
    CrashReports.install()
    installHorizontalScrollSupport()

    application {
        val scope = rememberCoroutineScope()

        Window(
            // Escape is the desktop equivalent of a back press: App closes the
            // open overlay or pops the current route.
            onPreviewKeyEvent = { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false

                    event.key == Key.Escape -> {
                        BackNavigation.requestBack()
                        true
                    }

                    KeyboardShortcuts.handle(
                        key = event.key,
                        ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed,
                        shift = event.isShiftPressed,
                        alt = event.isAltPressed
                    ) -> true

                    else -> false
                }
            },
            onCloseRequest = {
                runBlocking {
                    ApiManager.shutdown()
                }
                shutdownCaptchaBrowser()
                DesktopTray.remove()
                exitApplication()
            },
            title = "Scrollix"
        ) {
            // The tray mirrors the unread total and can reopen the window or
            // quit the process when the window is hidden behind other apps.
            LaunchedEffect(Unit) {
                DesktopTray.install(
                    onOpen = {
                        window.isVisible = true
                        window.toFront()
                    },
                    onQuit = {
                        scope.launch {
                            ApiManager.shutdown()
                            shutdownCaptchaBrowser()
                            DesktopTray.remove()
                            exitApplication()
                        }
                    }
                )
            }

            LaunchedEffect(Unit) {
                ApiManager.unreadCount.collect { count ->
                    DesktopTray.setUnread(count)
                }
            }

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
