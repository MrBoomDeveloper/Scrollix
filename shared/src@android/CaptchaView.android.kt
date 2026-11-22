package com.mrboomdev.scrollix.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The captcha page talks to its embedder with `postMessage` frames of the
 * shape `{type: "vk-sak-sdk", handler: "VKCaptchaGetResult", params: {token}}`.
 * A local wrapper page carries that token across to Kotlin.
 */
@Composable
actual fun CaptchaView(
    url: String,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    val latestOnToken by rememberUpdatedState(onToken)
    val latestOnError by rememberUpdatedState(onError)
    val delivered = remember(url) { AtomicBoolean(false) }

    key(url) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(480.dp)
                .clip(RoundedCornerShape(16.dp)),
            factory = { context ->
                WebView(context).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true

                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportMultipleWindows(false)
                    setBackgroundColor(Color.TRANSPARENT)

                    addJavascriptInterface(
                        CaptchaBridge(
                            tokenCallback = { token ->
                                if (delivered.compareAndSet(false, true)) {
                                    latestOnToken(token)
                                }
                            },
                            errorCallback = { message -> latestOnError(message) }
                        ),
                        "ScrollixCaptcha"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)

                            if (request?.isForMainFrame == true) {
                                latestOnError(
                                    error?.description?.toString() ?: "The captcha could not be loaded"
                                )
                            }
                        }
                    }

                    loadDataWithBaseURL(
                        CaptchaOrigin,
                        captchaPage(url),
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            update = { webView ->
                webView.isEnabled = enabled
                webView.alpha = if (enabled) 1f else 0.85f
            }
        )
    }
}

private const val CaptchaOrigin = "https://web.max.ru/"

private class CaptchaBridge(
    private val tokenCallback: (String) -> Unit,
    private val errorCallback: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // The JS bridge runs on its own thread, so state updates go back to main.
    @JavascriptInterface
    fun onToken(token: String) {
        if (token.isBlank()) return
        mainHandler.post { tokenCallback(token) }
    }

    @JavascriptInterface
    fun onError(message: String) {
        val text = message.ifBlank { "The captcha could not be completed" }
        mainHandler.post { errorCallback(text) }
    }
}

