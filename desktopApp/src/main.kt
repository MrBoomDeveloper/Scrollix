package com.mrboomdev.scrollix

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mrboomdev.scrollix.data.api.ApiManager
import kotlinx.coroutines.runBlocking

fun main() = application {
    Window(
        onCloseRequest = {
            runBlocking {
                ApiManager.shutdown()
            }
            exitApplication()
        },
        title = "Scrollix"
    ) {
        App()
    }
}
