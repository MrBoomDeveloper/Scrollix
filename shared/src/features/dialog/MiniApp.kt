package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.WebAppLaunch
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.platform.PlatformBackHandler
import com.mrboomdev.scrollix.ui.icons.close_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Injected into every mini app before its own scripts run. Telegram Mini Apps
 * detect `TelegramWebviewProxy` and send their bridge events through it; the
 * proxy forwards them to the native WebView bridge on Android and records them
 * for the desktop build, which polls them.
 */
internal val MiniAppBridgeScript = """
    (function () {
      if (window.__scrollixMiniAppBridge) return;
      window.__scrollixMiniAppBridge = true;
      window.__scrollixMiniAppEvents = window.__scrollixMiniAppEvents || [];

      function send(type, data) {
        var eventType = String(type || '');
        var eventData = data == null ? '' : String(data);

        if (eventType === 'web_app_close') {
          window.__scrollixMiniAppClose = true;
        }

        try { window.__scrollixMiniAppEvents.push([eventType, eventData]); } catch (ignored) {}

        try {
          if (window.ScrollixMiniApp && ScrollixMiniApp.postEvent) {
            ScrollixMiniApp.postEvent(eventType, eventData);
          }
        } catch (ignored) {}
      }

      window.TelegramWebviewProxy = { postEvent: send };
      window.TelegramWebviewProxyProto = window.TelegramWebviewProxy;
    })();
""".trimIndent()

/**
 * Renders a bot mini app. The page is loaded with the URL the provider
 * produced, which already carries its `tgWebAppData` init payload.
 */
@Composable
expect fun MiniAppView(
    url: String,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
)

/**
 * Renders a message's inline keyboard. Only links and mini apps are present in
 * the model; unsupported button types were filtered out while mapping.
 */
@Composable
fun MessageKeyboardButtons(
    keyboard: List<List<MessageButton>>,
    onOpenWebApp: (MessageButton) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keyboard.isEmpty()) return

    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keyboard.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { button ->
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        onClick = {
                            if (button.webAppUrl != null) {
                                onOpenWebApp(button)
                            } else {
                                button.url?.let { url -> uriHandler.openUri(url) }
                            }
                        }
                    ) {
                        Text(
                            text = button.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Keep short rows aligned with full ones.
                repeat(3 - row.size.coerceAtMost(3)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Full-screen host of a bot mini app, opened from an inline keyboard button.
 * Closing it reports the launch id back to the provider.
 */
@Composable
fun MiniAppDialog(
    session: AccountSession?,
    providerDialogId: String,
    button: MessageButton,
    onDismiss: () -> Unit
) {
    MiniAppHost(
        title = button.text,
        launchKey = button,
        session = session,
        onDismiss = onDismiss,
        open = {
            session?.openWebApp(
                providerDialogId = providerDialogId,
                button = button
            ) ?: Result.failure(IllegalStateException("Account session not found"))
        }
    )
}

/** Full-screen host of a bot's main mini app, opened from the chat header. */
@Composable
fun MainMiniAppDialog(
    session: AccountSession?,
    providerDialogId: String,
    title: String,
    onDismiss: () -> Unit
) {
    MiniAppHost(
        title = title,
        launchKey = providerDialogId,
        session = session,
        onDismiss = onDismiss,
        open = {
            session?.openMainWebApp(providerDialogId)
                ?: Result.failure(IllegalStateException("Account session not found"))
        }
    )
}

@Composable
private fun MiniAppHost(
    title: String,
    launchKey: Any,
    session: AccountSession?,
    onDismiss: () -> Unit,
    open: suspend () -> Result<WebAppLaunch>
) {
    var launch by remember(launchKey) { mutableStateOf<WebAppLaunch?>(null) }
    var error by remember(launchKey) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val close = {
        launch?.let { active -> scope.launch { session?.closeWebApp(active.id) } }
        onDismiss()
    }

    LaunchedEffect(launchKey, session) {
        open()
            .onSuccess { webApp -> launch = webApp }
            .onFailure { failure -> error = failure.message ?: "Could not open the mini app" }
    }

    PlatformBackHandler { close() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(onClick = close) {
                        Icon(
                            imageVector = close_icon,
                            contentDescription = "Close mini app"
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val active = launch

                    when {
                        active != null -> MiniAppView(
                            url = active.url,
                            modifier = Modifier.fillMaxSize(),
                            onClose = close
                        )

                        error != null -> Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )

                            Button(onClick = close) {
                                Text("Close")
                            }
                        }

                        else -> CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
