package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.features.home.key
import com.mrboomdev.scrollix.features.login.EmbeddedBrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.cef.browser.CefRendering
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

@Composable
actual fun MiniAppView(
    url: String,
    modifier: Modifier,
    command: String?,
    onEvent: (String, String) -> Unit,
    onClose: () -> Unit
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
                initError = exception.message ?: "The embedded browser could not start"
            }
    }

    when {
        initializing -> Card(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    style = MaterialTheme.typography.titleSmall,
                    text = "Preparing the mini app browser"
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

        initError != null -> Card(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    style = MaterialTheme.typography.titleSmall,
                    text = "Mini app browser unavailable"
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    text = initError.orEmpty()
                )
            }
        }

        else -> key(url) {
            MiniAppBrowserContent(
                url = url,
                command = command,
                onEvent = onEvent,
                onClose = onClose,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MiniAppBrowserContent(
    url: String,
    command: String?,
    onEvent: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnEvent by rememberUpdatedState(onEvent)
    val closed = remember(url) { AtomicBoolean(false) }
    val session = remember(url) {
        runCatching { createMiniAppBrowser(url) }.getOrNull()
    }

    LaunchedEffect(session) {
        val browser = session?.browser ?: return@LaunchedEffect

        while (isActive && !closed.get()) {
            delay(350)

            // Re-injecting is cheap: the script guards itself with a global.
            runCatching { browser.evaluateJavaScript(MiniAppBridgeScript) {} }

            drainMiniAppEvents(browser) { type, data -> latestOnEvent(type, data) }

            runCatching {
                browser.evaluateJavaScript(ClosePollScript) { raw ->
                    val requested = raw?.trim()?.trim('"') == "true"

                    if (requested && closed.compareAndSet(false, true)) {
                        latestOnClose()
                    }
                }
            }

            if (closed.get()) break
        }
    }

    // Host commands answer a main/back button press inside the page.
    LaunchedEffect(session, command) {
        val browser = session?.browser ?: return@LaunchedEffect
        val target = command ?: return@LaunchedEffect

        runCatching { browser.evaluateJavaScript(target) {} }
    }

    DisposableEffect(session) {
        onDispose { session?.dispose() }
    }

    if (session == null) {
        Card(modifier = modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                text = "The mini app could not be opened."
            )
        }
    } else {
        SwingPanel(
            factory = { session.browser.uiComponent },
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

private const val ClosePollScript = "!!window.__scrollixMiniAppClose"

/**
 * Empties the event queue the page recorded and replays it as callbacks. The
 * queue is populated by [MiniAppBridgeScript] on pages whose WebView has no
 * native JS bridge.
 */
private fun drainMiniAppEvents(
    browser: KCEFBrowser,
    onEvent: (String, String) -> Unit
) {
    runCatching {
        browser.evaluateJavaScript(MiniAppDrainScript) { raw ->
            val json = raw
                ?.trim()
                ?.removeSurrounding("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: return@evaluateJavaScript

            parseMiniAppEvents(json).forEach { (type, data) -> onEvent(type, data) }
        }
    }
}

private class MiniAppBrowserSession(
    private val client: KCEFClient,
    val browser: KCEFBrowser
) {
    fun dispose() {
        runCatching { browser.dispose() }
        runCatching { client.dispose() }
    }
}

private fun createMiniAppBrowser(url: String): MiniAppBrowserSession {
    val client = KCEF.newClientBlocking()
    val browser = client.createBrowser(url, CefRendering.DEFAULT, false)
    return MiniAppBrowserSession(client, browser)
}
