package com.mrboomdev.scrollix.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun MiniAppView(
    url: String,
    modifier: Modifier,
    onClose: () -> Unit
) {
    val latestOnClose by rememberUpdatedState(onClose)

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
                        MiniAppBridge { type ->
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
                }
            }
        )
    }
}

private class MiniAppBridge(
    private val onEvent: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // The JS bridge runs on its own thread, so callbacks go back to main.
    @JavascriptInterface
    fun postEvent(type: String, data: String) {
        mainHandler.post { onEvent(type) }
    }
}
