package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.DeepLinkHandler
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.WebAppLaunch
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.platform.PlatformBackHandler
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.close_icon
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Renders a bot mini app. The page is loaded with the URL the provider
 * produced, which already carries its `tgWebAppData` init payload.
 *
 * [command] is JavaScript run once in the page; the host uses it to answer
 * main/back button presses. [onEvent] receives every bridge event the page
 * emits, as `(type, data)`.
 */
@Composable
expect fun MiniAppView(
    url: String,
    modifier: Modifier = Modifier,
    command: String? = null,
    onEvent: (String, String) -> Unit = { _, _ -> },
    onClose: () -> Unit
)

/**
 * Renders a message's inline keyboard. Mini apps open through
 * [onOpenWebApp]; callback, copy and profile buttons go to [onButton] so the
 * chat can run the provider call. A disabled button stays visible as a hint
 * that the bot turned it off, but does nothing.
 */
@Composable
fun MessageKeyboardButtons(
    keyboard: List<List<MessageButton>>,
    onOpenWebApp: (MessageButton) -> Unit,
    modifier: Modifier = Modifier,
    onButton: ((MessageButton) -> Unit)? = null
) {
    if (keyboard.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

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
                        enabled = !button.isDisabled,
                        onClick = {
                            when {
                                button.isDisabled -> Unit
                                button.webAppUrl != null -> onOpenWebApp(button)

                                // A callback needs the provider; without a
                                // handler the button must not pretend to work.
                                button.callbackData != null ||
                                    button.userId != null -> onButton?.invoke(button)

                                button.copyText != null -> clipboard.setText(
                                    AnnotatedString(button.copyText.orEmpty())
                                )

                                button.url != null -> {
                                    val url = button.url.orEmpty()

                                    // t.me links stay inside the app; the
                                    // handler opens chats, packs and bots.
                                    if (DeepLinkHandler.isTelegramLink(url)) {
                                        DeepLinkHandler.publish(url)
                                    } else {
                                        uriHandler.openUri(url)
                                    }
                                }
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
        providerDialogId = providerDialogId,
        botUserId = button.botUserId,
        onDismiss = onDismiss,
        open = {
            session?.openWebApp(
                providerDialogId = providerDialogId,
                button = button
            ) ?: Result.failure(IllegalStateException("Account session not found"))
        }
    )
}

/**
 * Full-screen host of a mini app resolved from a `t.me` link. The provider
 * already produced the URL with its init data, so there is nothing to launch
 * on the server side.
 */
@Composable
fun WebAppLinkDialog(
    session: AccountSession?,
    title: String,
    url: String,
    onDismiss: () -> Unit
) {
    MiniAppHost(
        title = title,
        launchKey = url,
        session = session,
        providerDialogId = "",
        botUserId = null,
        onDismiss = onDismiss,
        open = { Result.success(WebAppLaunch(id = 0L, url = url)) }
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
        providerDialogId = providerDialogId,
        botUserId = null,
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
    providerDialogId: String,
    botUserId: Long?,
    onDismiss: () -> Unit,
    open: suspend () -> Result<WebAppLaunch>
) {
    var launch by remember(launchKey) { mutableStateOf<WebAppLaunch?>(null) }
    var error by remember(launchKey) { mutableStateOf<String?>(null) }
    var mainButton by remember(launchKey) { mutableStateOf(MiniAppMainButton()) }
    var backVisible by remember(launchKey) { mutableStateOf(false) }
    var closingConfirmation by remember(launchKey) { mutableStateOf(false) }
    var confirmClose by remember(launchKey) { mutableStateOf(false) }
    var command by remember(launchKey) { mutableStateOf<String?>(null) }
    var shareRequest by remember(launchKey) { mutableStateOf<MiniAppShareRequest?>(null) }
    var shareQuery by remember(launchKey) { mutableStateOf("") }
    var sharing by remember(launchKey) { mutableStateOf(false) }
    val shareTargets by session?.dialogs?.collectAsState()
        ?: remember { mutableStateOf(emptyList<com.mrboomdev.scrollix.data.model.Dialog>()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val closeNow = {
        // A link-opened mini app has no provider launch id to report back.
        launch?.takeIf { active -> active.id != 0L }?.let { active ->
            scope.launch { session?.closeWebApp(active.id) }
        }

        onDismiss()
    }

    // A page that enabled closing confirmation asked to be protected from a
    // stray back press, so the first close only raises the question.
    val close = {
        if (closingConfirmation) confirmClose = true else closeNow()
    }

    val dispatch = { name: String ->
        command = "window.__scrollixMiniAppDispatch&&window.__scrollixMiniAppDispatch(" +
            "'$name')"
    }

    // A share either finishes or is cancelled; the page always gets its
    // callback, so a mini app never waits forever.
    val finishShare = { request: MiniAppShareRequest, success: Boolean ->
        if (request.callbackId.isNotBlank()) {
            command = "window.__scrollixMiniAppShareResult&&window.__scrollixMiniAppShareResult(" +
                "'${request.callbackId}', $success)"
        }
    }

    LaunchedEffect(launchKey, session) {
        open()
            .onSuccess { webApp -> launch = webApp }
            .onFailure { failure -> error = failure.message ?: "Could not open the mini app" }
    }

    PlatformBackHandler {
        // A visible back button belongs to the page, not to the window.
        if (backVisible) dispatch("backButtonClicked") else close()
    }

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
                    if (backVisible) {
                        IconButton(onClick = { dispatch("backButtonClicked") }) {
                            Icon(
                                imageVector = arrow_back_icon,
                                contentDescription = "Back in the mini app"
                            )
                        }
                    }

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
                            command = command,
                            onEvent = { type, data ->
                                when (type) {
                                    "web_app_setup_main_button" -> {
                                        mainButton = parseMiniAppMainButton(data)
                                    }

                                    "web_app_setup_back_button" -> {
                                        parseMiniAppVisible(data)?.let { visible ->
                                            backVisible = visible
                                        }
                                    }

                                    "web_app_setup_closing_behavior" -> {
                                        closingConfirmation =
                                            parseMiniAppClosingConfirmation(data)
                                    }

                                    "web_app_open_link" -> {
                                        extractMiniAppUrl(data)?.let { url ->
                                            uriHandler.openUri(url)
                                        }
                                    }

                                    "web_app_open_tg_link" -> {
                                        extractMiniAppUrl(data)?.let(DeepLinkHandler::publish)
                                    }

                                    "web_app_share_url" -> {
                                        extractMiniAppUrl(data)?.let { url ->
                                            clipboard.setText(AnnotatedString(url))
                                            NoticeCenter.post(
                                                AppNotice(message = "Link copied")
                                            )
                                        }
                                    }

                                    "web_app_share_message" -> {
                                        parseMiniAppShareRequest(data)?.let { request ->
                                            shareQuery = ""
                                            shareRequest = request
                                        }
                                    }
                                }
                            },
                            onClose = closeNow
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

                // The page owns this bar: it appears only when the mini app
                // asked for a main button and reports its own label.
                if (mainButton.isVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mainButton.isProgressVisible) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        Button(
                            enabled = mainButton.isActive,
                            onClick = { dispatch("mainButtonClicked") }
                        ) {
                            Text(mainButton.text.ifBlank { "Continue" })
                        }
                    }
                }
            }
        }

        shareRequest?.let { request ->
            val targets = filterForwardTargets(shareTargets, shareQuery)

            AlertDialog(
                onDismissRequest = {
                    shareRequest = null
                    finishShare(request, false)
                },
                title = { Text("Share to") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            shareRequest = null
                            finishShare(request, false)
                        }
                    ) {
                        Text("Cancel")
                    }
                },
                text = {
                    Column {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            value = shareQuery,
                            onValueChange = { value -> shareQuery = value },
                            singleLine = true,
                            placeholder = { Text("Search chats") }
                        )

                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            targets.forEach { target ->
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !sharing,
                                    onClick = {
                                        val currentSession = session

                                        if (currentSession == null) {
                                            shareRequest = null
                                            finishShare(request, false)
                                            return@TextButton
                                        }

                                        sharing = true
                                        shareRequest = null

                                        scope.launch {
                                            currentSession.sharePreparedMessage(
                                                providerDialogId = providerDialogId,
                                                botUserId = botUserId,
                                                preparedMessageId = request.messageId,
                                                targetDialogId = target.providerDialogId
                                            )
                                                .onSuccess {
                                                    NoticeCenter.post(
                                                        AppNotice("Shared with ${target.title}")
                                                    )
                                                    finishShare(request, true)
                                                }
                                                .onFailure { failure ->
                                                    NoticeCenter.error(
                                                        failure.message ?: "The message was not shared"
                                                    )
                                                    finishShare(request, false)
                                                }

                                            sharing = false
                                        }
                                    }
                                ) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = target.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }

                            if (targets.isEmpty()) {
                                Text(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    text = "No chats to share with",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            )
        }

        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClose = false
                        closeNow()
                    }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClose = false }) {
                        Text("Stay")
                    }
                },
                text = { Text("Close the mini app? Unsaved input in it will be lost.") }
            )
        }
    }
}
