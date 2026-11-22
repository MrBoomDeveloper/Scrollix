package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes the embedded Chromium (KCEF) the first time a captcha appears.
 *
 * KCEF downloads its runtime on first use, so this is deliberately lazy: a user
 * who never signs in to a provider with a captcha never pays that cost.
 */
private object CaptchaBrowser {
    private val mutex = Mutex()
    private val _downloadProgress = MutableStateFlow(-1f)
    private val installDir = File(System.getProperty("user.home"), ".scrollix/kcef")

    val downloadProgress = _downloadProgress.asStateFlow()

    @Volatile
    var isReady = false
        private set

    suspend fun ensure() {
        if (isReady) return

        mutex.withLock {
            if (isReady) return

            val initialized = CompletableDeferred<Unit>()

            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        installDir(installDir)

                        // CEF otherwise looks for jcef_helper under the running
                        // JDK, which does not ship it. The downloaded bundle
                        // contains the helper next to its libraries.
                        settings {
                            browserSubProcessPath = File(installDir, "jcef_helper").absolutePath
                        }

                        progress {
                            onDownloading { _downloadProgress.value = it }
                            onInitialized { initialized.complete(Unit) }
                        }
                    },
                    onError = { error ->
                        initialized.completeExceptionally(
                            error ?: IllegalStateException("The embedded browser failed to start")
                        )
                    },
                    onRestartRequired = {
                        initialized.completeExceptionally(
                            IllegalStateException("The embedded browser needs an application restart")
                        )
                    }
                )
            }

            withTimeout(120_000) { initialized.await() }
            isReady = true
        }
    }
}

/** Releases the embedded browser when the desktop app closes. */
fun shutdownCaptchaBrowser() {
    if (!CaptchaBrowser.isReady) return
    runCatching { KCEF.disposeBlocking() }
}

private const val CaptchaBaseUrl = "https://web.max.ru/"

private val TokenPollScript = "window.__scrollixCaptchaToken || ''"
private val ErrorPollScript = "window.__scrollixCaptchaError || ''"

@Composable
actual fun CaptchaView(
    url: String,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    var initializing by remember { mutableStateOf(!CaptchaBrowser.isReady) }
    var initError by remember { mutableStateOf<String?>(null) }
    val progress by CaptchaBrowser.downloadProgress.collectAsState()

    LaunchedEffect(url) {
        if (CaptchaBrowser.isReady) {
            initializing = false
            return@LaunchedEffect
        }

        initializing = true

        runCatching { CaptchaBrowser.ensure() }
            .onSuccess { initializing = false }
            .onFailure { exception ->
                initializing = false
                initError = exception.message
                    ?: "The embedded browser could not start"
                onError(initError!!)
            }
    }

    when {
        initializing -> {
            Card(modifier = modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        style = MaterialTheme.typography.titleSmall,
                        text = "Preparing the captcha browser"
                    )
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        text = "The first captcha downloads an embedded Chromium runtime once."
                    )
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { progress / 100f }
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        initError != null -> {
            Card(modifier = modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        style = MaterialTheme.typography.titleSmall,
                        text = "Captcha browser unavailable"
                    )
                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        text = initError.orEmpty()
                    )
                }
            }
        }

        else -> key(url) {
            CaptchaBrowserContent(
                url = url,
                enabled = enabled,
                onToken = onToken,
                onError = onError,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CaptchaBrowserContent(
    url: String,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    val state = rememberWebViewStateWithHTMLData(
        data = captchaPage(url),
        baseUrl = CaptchaBaseUrl
    )
    val navigator = rememberWebViewNavigator()
    val delivered = remember(url) { AtomicBoolean(false) }

    LaunchedEffect(state) {
        state.webSettings.isJavaScriptEnabled = true
        state.webSettings.supportZoom = true
    }

    LaunchedEffect(url) {
        while (isActive && !delivered.get()) {
            delay(400)

            runCatching {
                navigator.evaluateJavaScript(TokenPollScript) { raw ->
                    val token = raw.trim().trim('"')

                    if (token.isNotBlank() && token != "null" &&
                        delivered.compareAndSet(false, true)
                    ) {
                        onToken(token)
                    }
                }
            }

            if (delivered.get()) break

            runCatching {
                navigator.evaluateJavaScript(ErrorPollScript) { raw ->
                    val message = raw.trim().trim('"')

                    if (message.isNotBlank() && message != "null") {
                        onError(message)
                    }
                }
            }
        }
    }

    WebView(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(RoundedCornerShape(16.dp)),
        navigator = navigator
    )
}
