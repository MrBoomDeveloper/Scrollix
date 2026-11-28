package com.mrboomdev.scrollix.features.dialog.bots

import com.mrboomdev.scrollix.features.dialog.bots.MiniAppBridgeScript
import com.mrboomdev.scrollix.features.dialog.bots.MiniAppView
import com.mrboomdev.scrollix.features.home.key

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun MiniAppView(
    url: String,
    modifier: Modifier,
    command: String?,
    onEvent: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnEvent by rememberUpdatedState(onEvent)
    var view by remember(url) { mutableStateOf<WebView?>(null) }

    key(url) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportMultipleWindows(false)

                    addJavascriptInterface(
                        MiniAppBridge { type, data ->
                            latestOnEvent(type, data)
                            if (type == "web_app_close") latestOnClose()
                        },
                        "ScrollixMiniApp"
                    )

                    webViewClient = object : WebViewClient() {
                        // The proxy has to exist before telegram-web-app.js
                        // runs, so inject at the earliest document hooks.
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                        ) {
                            super.onPageStarted(view, url, favicon)
                            view?.evaluateJavascript(MiniAppBridgeScript, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(MiniAppBridgeScript, null)
                        }
                    }

                    loadUrl(url)
                    view = this
                }
            }
        )
    }

    // Host commands answer a main/back button press inside the page.
    LaunchedEffect(command) {
        val target = command ?: return@LaunchedEffect
        view?.evaluateJavascript(target, null)
    }
}

private class MiniAppBridge(
    private val onEvent: (String, String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // The JS bridge runs on its own thread, so callbacks go back to main.
    @JavascriptInterface
    fun postEvent(type: String, data: String) {
        mainHandler.post { onEvent(type, data) }
    }
}
