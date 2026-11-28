package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.BusinessQuickReply
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.InlineBotResult
import com.mrboomdev.scrollix.data.model.MessageReplyKeyboard
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButton
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButtonKind
import com.mrboomdev.scrollix.ui.icons.location_on_icon
import com.mrboomdev.scrollix.ui.icons.poll_icon
import com.mrboomdev.scrollix.ui.icons.send_icon
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The reply keyboard still in effect for the chat. TDLib attaches the markup to
 * a message, but only the newest one counts: a bot that removes its keyboard
 * sends a message with an empty one, which hides it here too.
 */
internal fun replyKeyboardOf(messages: List<ChatMessage>): MessageReplyKeyboard? {
    return messages
        .lastOrNull { message -> message.replyKeyboard != null }
        ?.replyKeyboard
        ?.takeUnless { keyboard -> keyboard.isEmpty }
}

/**
 * Commands matching what the user typed after the slash. An empty query lists
 * the whole menu; ephemeral commands stay hidden until they are typed.
 */
internal fun filterBotCommands(
    commands: List<BotCommandInfo>,
    input: String
): List<BotCommandInfo> {
    if (!input.startsWith("/")) return emptyList()
    if (input.contains(' ') && !input.startsWith("/ ")) return emptyList()

    val token = input.removePrefix("/").trim()
    if (token.isEmpty()) return commands.filterNot { command -> command.isEphemeral }

    return commands.filter { command ->
        command.command.startsWith(token, ignoreCase = true)
    }
}

/** An inline bot mentioned at the start of the draft. */
internal data class InlineBotQuery(val username: String, val query: String)

/**
 * Reads `@bot some query` from the draft. The query only starts once the
 * username is followed by a space, which is what opens the picker in Telegram
 * and keeps a plain mention from firing a request on every keystroke.
 */
internal fun parseInlineBotQuery(input: String): InlineBotQuery? {
    if (!input.startsWith("@")) return null

    val space = input.indexOf(' ')
    if (space <= 1) return null

    val username = input.substring(1, space)
    if (!username.matches(Regex("[A-Za-z][A-Za-z0-9_]{1,31}"))) return null

    return InlineBotQuery(username = username, query = input.substring(space + 1))
}

/**
 * The persistent keyboard a bot installs under the composer. Text buttons send
 * their label; the request buttons open the matching composer.
 */
@Composable
internal fun ReplyKeyboardBar(
    keyboard: MessageReplyKeyboard,
    onText: (String) -> Unit,
    onLocation: () -> Unit,
    onPhoneNumber: () -> Unit,
    onPoll: () -> Unit,
    onWebApp: (ReplyKeyboardButton) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keyboard.isEmpty) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keyboard.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                row.forEach { button ->
                    TextButton(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = {
                            when (button.kind) {
                                ReplyKeyboardButtonKind.RequestLocation -> onLocation()
                                ReplyKeyboardButtonKind.RequestPhoneNumber -> onPhoneNumber()
                                ReplyKeyboardButtonKind.RequestPoll -> onPoll()
                                ReplyKeyboardButtonKind.WebApp -> onWebApp(button)
                                else -> onText(button.text)
                            }
                        }
                    ) {
                        val icon = when (button.kind) {
                            ReplyKeyboardButtonKind.RequestLocation -> location_on_icon
                            ReplyKeyboardButtonKind.RequestPoll -> poll_icon
                            else -> null
                        }

                        if (icon != null) {
                            Icon(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .widthIn(max = 16.dp),
                                imageVector = icon,
                                contentDescription = null
                            )
                        }

                        Text(
                            text = button.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Results of the inline query named by the draft. Each card sends one result;
 * thumbnails load through the session like any other media preview.
 */
@Composable
internal fun InlineBotResultStrip(
    results: List<InlineBotResult>,
    session: AccountSession?,
    isLoading: Boolean,
    sendingId: String?,
    onPick: (InlineBotResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty() && !isLoading) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (results.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Searching...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        items(results, key = { result -> result.id }) { result ->
            InlineBotResultCard(
                result = result,
                session = session,
                isSending = sendingId == result.id,
                onClick = { onPick(result) }
            )
        }
    }
}

@Composable
private fun InlineBotResultCard(
    result: InlineBotResult,
    session: AccountSession?,
    isSending: Boolean,
    onClick: () -> Unit
) {
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = result.thumbnailFileId,
        key2 = session
    ) {
        val fileId = result.thumbnailFileId
        if (session == null || fileId == null) return@produceState

        value = session.loadMediaBytes(fileId, result.thumbnailUniqueId)
            ?.let(::decodeImageBitmap)
    }

    Surface(
        modifier = Modifier
            .width(168.dp)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isSending, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                val image = thumbnail

                when {
                    isSending -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )

                    image != null -> Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    else -> Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = send_icon,
                        tint = MaterialTheme.colorScheme.secondary,
                        contentDescription = null
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (result.description.isNotBlank()) {
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Picker of the account's quick replies. Picking one sends every message of
 * the shortcut, so the rows show what will actually go out.
 */
@Composable
internal fun QuickReplyPickerDialog(
    replies: List<BusinessQuickReply>,
    onDismiss: () -> Unit,
    onPick: (BusinessQuickReply) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick replies") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
            ) {
                replies.forEach { reply ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(reply) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = reply.name,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (reply.preview.isNotBlank()) {
                            Text(
                                text = reply.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Command menu shown above the composer while the draft starts with a slash.
 * Picking a row leaves the caret after the command so arguments can be typed.
 */
@Composable
internal fun BotCommandSuggestions(
    commands: List<BotCommandInfo>,
    onPick: (BotCommandInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (commands.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            commands.take(6).forEach { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(command) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = command.insertion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (command.description.isNotBlank()) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = command.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
