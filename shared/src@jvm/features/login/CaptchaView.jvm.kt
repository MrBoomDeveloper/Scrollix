package com.mrboomdev.scrollix.features.login

import com.mrboomdev.scrollix.features.home.key

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.callback.CefCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
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
internal object EmbeddedBrowser {
    private val mutex = Mutex()
    private val _downloadProgress = MutableStateFlow(-1f)

    // The runtime has to match the JCEF Java classes bundled with KCEF. KCEF
    // otherwise downloads the latest JBR release, whose newer libjcef expects
    // callbacks this library's Java glue does not have, so resource loading
    // fails with NoSuchMethodError/ClassNotFoundException. This is the release
    // KCEF 2025.03.23 is built against. The install directory is versioned so
    // an incompatible bundle from an older build is never reused.
    private const val RuntimeRelease = "jbr-release-17.0.14b1367.22"
    private val installDir = File(
        File(System.getProperty("user.home"), ".scrollix"),
        "kcef-$RuntimeRelease"
    )

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
                        download {
                            github {
                                release(RuntimeRelease)
                            }
                        }

                        // CEF otherwise looks for its helper, resources and
                        // locales under the running JDK. General-purpose JVMs
                        // (including the toolchain's JBR) do not ship them, so
                        // Chromium aborts during CefInitialize. Everything it
                        // needs lives in the downloaded bundle instead.
                        settings {
                            browserSubProcessPath = File(installDir, "jcef_helper").absolutePath
                            resourcesDirPath = installDir.absolutePath
                            localesDirPath = File(installDir, "locales").absolutePath
                            cachePath = File(installDir, "cache").absolutePath

                            // The bundle's chrome-sandbox is not setuid, so the
                            // sandbox cannot start and Chromium would abort.
                            noSandbox = true
                        }

                        args(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",

                            // Chromium 121+ blocks third-party cookies by
                            // default. The captcha lives in an iframe and keeps
                            // its session in cookies, so without this it keeps
                            // retrying and never reports the solved token.
                            "--disable-features=TrackingProtection3pcd"
                        )

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
    if (!EmbeddedBrowser.isReady) return
    runCatching { KCEF.disposeBlocking() }
}

private const val CaptchaOrigin = "https://web.max.ru/"

// The captcha widget refuses to be framed by a file:// document, so the
// wrapper is served from this page under the real provider origin. The browser
// answers the request locally instead of hitting the network.
private const val CaptchaWrapperUrl = "${CaptchaOrigin}__scrollix_captcha"

private val TokenPollScript = "window.__scrollixCaptchaToken || ''"
private val ErrorPollScript = "window.__scrollixCaptchaError || ''"
private val MessagesPollScript =
    "JSON.stringify(window.__scrollixCaptchaMessages || [])"

@Composable
actual fun CaptchaView(
    url: String,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier
) {
    var initializing by remember { mutableStateOf(!EmbeddedBrowser.isReady) }
    var initError by remember { mutableStateOf<String?>(null) }
    val progress by EmbeddedBrowser.downloadProgress.collectAsState()

    LaunchedEffect(url) {
        if (EmbeddedBrowser.isReady) {
            initializing = false
            return@LaunchedEffect
        }

        initializing = true

        runCatching { EmbeddedBrowser.ensure() }
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
    val latestOnToken by rememberUpdatedState(onToken)
    val latestOnError by rememberUpdatedState(onError)
    val delivered = remember(url) { AtomicBoolean(false) }

    val session = remember(url) {
        runCatching {
            createCaptchaBrowser(captchaPage(url)) { message -> latestOnError(message) }
        }.getOrNull()
    }

    LaunchedEffect(session) {
        val browser = session?.browser ?: return@LaunchedEffect
        var lastMessages = ""

        while (isActive && !delivered.get()) {
            delay(400)

            runCatching {
                browser.evaluateJavaScript(TokenPollScript) { raw ->
                    val token = raw?.trim()?.trim('"')

                    if (!token.isNullOrBlank() && token != "null" &&
                        delivered.compareAndSet(false, true)
                    ) {
                        latestOnToken(token)
                    }
                }
            }

            if (delivered.get()) break

            runCatching {
                browser.evaluateJavaScript(ErrorPollScript) { raw ->
                    val message = raw?.trim()?.trim('"')

                    if (!message.isNullOrBlank() && message != "null") {
                        latestOnError(message)
                    }
                }
            }

            // The widget only talks to its embedder through postMessage. Log
            // the frames it sends so a silent failure is diagnosable.
            runCatching {
                browser.evaluateJavaScript(MessagesPollScript) { raw ->
                    val messages = raw?.trim()?.trim('"')

                    if (!messages.isNullOrBlank() && messages != "null" &&
                        messages != "[]" && messages != lastMessages
                    ) {
                        lastMessages = messages
                        println("SCROLLIX_CAPTCHA_MESSAGES=$messages")
                    }
                }
            }
        }
    }

    DisposableEffect(session) {
        onDispose { session?.dispose() }
    }

    if (session == null) {
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
                    text = "The captcha page could not be created."
                )
            }
        }
    } else {
        SwingPanel(
            factory = { session.browser.uiComponent },
            modifier = modifier
                .fillMaxWidth()
                .height(480.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

private class CaptchaBrowserSession(
    private val client: KCEFClient,
    val browser: KCEFBrowser
) {
    fun dispose() {
        runCatching { browser.dispose() }
        runCatching { client.dispose() }
    }
}

private fun createCaptchaBrowser(
    html: String,
    onError: (String) -> Unit
): CaptchaBrowserSession {
    val client = KCEF.newClientBlocking()
    val body = html.toByteArray(Charsets.UTF_8)

    client.addRequestHandler(CaptchaRequestHandler(body))
    client.addLoadHandler(
        object : CefLoadHandlerAdapter() {
            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                if (frame?.isMain != true) return

                onError(
                    errorText?.takeIf { it.isNotBlank() }
                        ?: "The captcha could not be loaded"
                )
            }
        }
    )

    val browser = client.createBrowser(CaptchaWrapperUrl, CefRendering.DEFAULT, false)
    return CaptchaBrowserSession(client, browser)
}

private class CaptchaRequestHandler(
    private val body: ByteArray
) : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?
    ): CefResourceRequestHandler? {
        if (request?.url != CaptchaWrapperUrl) return null

        disableDefaultHandling?.set(true)
        return CaptchaResourceRequestHandler(body)
    }
}

private class CaptchaResourceRequestHandler(
    private val body: ByteArray
) : CefResourceRequestHandlerAdapter() {
    override fun getResourceHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?
    ): CefResourceHandler = CaptchaHtmlResourceHandler(body)
}

private class CaptchaHtmlResourceHandler(
    private val body: ByteArray
) : CefResourceHandlerAdapter() {
    private var offset = 0

    override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
        callback?.Continue()
        return true
    }

    override fun getResponseHeaders(
        response: CefResponse?,
        responseLength: IntRef?,
        redirectUrl: StringRef?
    ) {
        // Chromium only recognizes a bare MIME type here: a parameter such as
        // `; charset=utf-8` makes it fall back to the plain-text viewer and the
        // wrapper's markup is shown as source. The charset belongs in the
        // Content-Type header instead.
        response?.mimeType = "text/html"
        response?.status = 200
        response?.statusText = "OK"
        response?.setHeaderByName("Content-Type", "text/html; charset=utf-8", true)
        // The page carries a one-time captcha link; never serve it from cache.
        response?.setHeaderByName("Cache-Control", "no-store", true)
        responseLength?.set(body.size)
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback?
    ): Boolean {
        if (offset >= body.size) return false

        val count = minOf(bytesToRead, body.size - offset)
        System.arraycopy(body, offset, dataOut, 0, count)
        offset += count
        bytesRead.set(count)

        return true
    }

    override fun cancel() {
        offset = body.size
    }
}
