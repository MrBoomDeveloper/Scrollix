package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.formatPrice
import com.mrboomdev.scrollix.ui.icons.reply_icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The rows a bubble adds under its content: the auto-delete countdown, the
 * bot's inline keyboard and the channel's comment link. They stay in their own
 * file so the bubble composable itself stays readable.
 */
@Composable
internal fun MessageBubbleExtras(
    message: ChatMessage,
    dialogKind: DialogKind,
    onOpenWebApp: ((MessageButton) -> Unit)?,
    onBotButton: ((MessageButton) -> Unit)?,
    onOpenThread: (() -> Unit)?,
    onOpenInvoice: (() -> Unit)? = null
) {
    // A bot invoice is a product card with its price, not plain text.
    message.invoice?.let { invoice ->
        if (onOpenInvoice != null) {
            InvoiceCard(
                title = invoice.title,
                description = invoice.description,
                price = formatPrice(invoice.totalAmount, invoice.currency),
                onOpen = onOpenInvoice
            )
        }
    }

    if (message.autoDeleteAt > 0) {
        AutoDeleteCountdown(
            modifier = Modifier.padding(top = 4.dp),
            autoDeleteAt = message.autoDeleteAt,
            isOutgoing = message.isOutgoing
        )
    }

    if (message.keyboard.isNotEmpty() && onOpenWebApp != null) {
        MessageKeyboardButtons(
            modifier = Modifier.padding(top = 6.dp),
            keyboard = message.keyboard,
            onOpenWebApp = onOpenWebApp,
            onButton = onBotButton
        )
    }

    // Channel posts expose their discussion thread right under the post
    // instead of hiding it in the action menu, where most readers never
    // found it.
    if (dialogKind == DialogKind.Channel && message.replyCount > 0 && onOpenThread != null) {
        TextButton(
            modifier = Modifier.padding(top = 2.dp),
            onClick = onOpenThread
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = reply_icon,
                contentDescription = null
            )

            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = if (message.replyCount == 1) {
                    "1 comment"
                } else {
                    "${message.replyCount} comments"
                }
            )
        }
    }
}

/** The invoice a bot attached: product, price and one payment action. */
@Composable
private fun InvoiceCard(
    title: String,
    description: String,
    price: String,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title.ifBlank { "Invoice" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            modifier = Modifier.padding(top = 2.dp),
            onClick = onOpen
        ) {
            Text("Pay $price")
        }
    }
}
