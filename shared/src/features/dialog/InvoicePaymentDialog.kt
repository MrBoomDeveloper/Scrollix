package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.PaymentAddress
import com.mrboomdev.scrollix.data.model.PaymentFormInfo
import com.mrboomdev.scrollix.data.model.PaymentOrder
import com.mrboomdev.scrollix.data.model.PaymentOutcome
import com.mrboomdev.scrollix.data.model.ShippingOptionInfo
import com.mrboomdev.scrollix.data.model.formatPrice
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.ui.notice.AppNotice
import com.mrboomdev.scrollix.ui.notice.NoticeCenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pays an invoice a bot attached to a message. Stars are charged in one tap;
 * card payments use a card the account already saved, because the app has no
 * card form of its own. A seller that asks for delivery details gets them
 * before the payment is validated.
 */
@Composable
internal fun InvoicePaymentDialog(
    session: AccountSession,
    providerDialogId: String,
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val invoice = message.invoice ?: return
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var form by remember(message.messageId) { mutableStateOf<PaymentFormInfo?>(null) }
    var error by remember(message.messageId) { mutableStateOf<String?>(null) }
    var isLoading by remember(message.messageId) { mutableStateOf(true) }
    var isPaying by remember(message.messageId) { mutableStateOf(false) }
    var shippingOptions by remember(message.messageId) {
        mutableStateOf<List<ShippingOptionInfo>>(emptyList())
    }
    var orderInfoId by remember(message.messageId) { mutableStateOf("") }
    var selectedOption by remember(message.messageId) { mutableStateOf("") }

    var name by remember(message.messageId) { mutableStateOf("") }
    var phone by remember(message.messageId) { mutableStateOf("") }
    var email by remember(message.messageId) { mutableStateOf("") }
    var country by remember(message.messageId) { mutableStateOf("") }
    var city by remember(message.messageId) { mutableStateOf("") }
    var street by remember(message.messageId) { mutableStateOf("") }
    var postal by remember(message.messageId) { mutableStateOf("") }
    var selectedCard by remember(message.messageId) { mutableStateOf<String?>(null) }
    var saveCard by remember(message.messageId) { mutableStateOf(false) }

    LaunchedEffect(message.messageId, session) {
        isLoading = true

        session.loadPaymentForm(providerDialogId, message.messageId)
            .onSuccess { loaded ->
                form = loaded
                selectedCard = loaded.savedCards.firstOrNull()?.id
            }
            .onFailure { failure ->
                error = failure.message ?: "The invoice could not be loaded"
            }

        isLoading = false
    }

    fun currentOrder() = PaymentOrder(
        name = name.trim(),
        phoneNumber = phone.trim(),
        emailAddress = email.trim(),
        address = if (form?.needsShippingAddress == true) {
            PaymentAddress(
                countryCode = country.trim(),
                city = city.trim(),
                streetLine1 = street.trim(),
                postalCode = postal.trim()
            )
        } else {
            null
        },
        cardId = selectedCard,
        saveCard = saveCard
    )

    fun pay(optionId: String) {
        val loaded = form ?: return

        scope.launch {
            isPaying = true
            error = null

            session.payInvoice(
                providerDialogId = providerDialogId,
                messageId = message.messageId,
                formId = loaded.formId,
                orderInfoId = orderInfoId,
                shippingOptionId = optionId,
                order = currentOrder()
            )
                .onSuccess { outcome ->
                    when (outcome) {
                        PaymentOutcome.Paid -> {
                            NoticeCenter.post(AppNotice(message = "Payment sent"))
                            onDismiss()
                        }

                        is PaymentOutcome.Verification -> {
                            runCatching { uriHandler.openUri(outcome.url) }
                            onDismiss()
                        }
                    }
                }
                .onFailure { failure ->
                    error = failure.message ?: "The payment was declined"
                }

            isPaying = false
        }
    }

    fun validateThenPay() {
        val loaded = form ?: return

        if (loaded.isStars) {
            pay(optionId = "")
            return
        }

        scope.launch {
            isPaying = true
            error = null

            session.validatePaymentOrder(
                providerDialogId = providerDialogId,
                messageId = message.messageId,
                order = currentOrder()
            )
                .onSuccess { validation ->
                    orderInfoId = validation.orderInfoId
                    shippingOptions = validation.shippingOptions

                    when {
                        // Nothing to choose: pay right away.
                        validation.shippingOptions.isEmpty() -> pay(optionId = "")
                        validation.shippingOptions.size == 1 -> {
                            selectedOption = validation.shippingOptions.first().id
                            pay(optionId = selectedOption)
                        }

                        // The seller offers several deliveries; the user picks
                        // one on the second step.
                        else -> selectedOption = validation.shippingOptions.first().id
                    }
                }
                .onFailure { failure ->
                    error = failure.message ?: "The order could not be validated"
                }

            isPaying = false
        }
    }

    val loaded = form

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = invoice.title.ifBlank { "Invoice" },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (invoice.description.isNotBlank()) {
                    Text(
                        text = invoice.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = formatPrice(
                        amount = loaded?.starCount?.takeIf { it > 0 }
                            ?: invoice.totalAmount,
                        currency = loaded?.currency?.ifBlank { invoice.currency }
                            ?: invoice.currency
                    ),
                    style = MaterialTheme.typography.titleLarge
                )

                if (invoice.isTest) {
                    Text(
                        text = "Test invoice: no real money is charged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                when {
                    isLoading -> CircularProgressIndicator()

                    loaded == null -> Text(
                        text = error ?: "The invoice could not be loaded",
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> {
                        if (loaded.needsName) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                singleLine = true
                            )
                        }

                        if (loaded.needsPhone) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone") },
                                singleLine = true
                            )
                        }

                        if (loaded.needsEmail) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                singleLine = true
                            )
                        }

                        if (loaded.needsShippingAddress) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = country,
                                onValueChange = { country = it },
                                label = { Text("Country code, for example US") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = street,
                                onValueChange = { street = it },
                                label = { Text("Address") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = postal,
                                onValueChange = { postal = it },
                                label = { Text("Postal code") },
                                singleLine = true
                            )
                        }

                        if (shippingOptions.isNotEmpty()) {
                            HorizontalDivider()

                            Text("Delivery")

                            shippingOptions.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedOption == option.id,
                                            onClick = { selectedOption = option.id }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedOption == option.id,
                                        onClick = { selectedOption = option.id }
                                    )

                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = option.title,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (option.amount > 0) {
                                        Text(
                                            text = formatPrice(option.amount, loaded.currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        if (!loaded.isStars) {
                            HorizontalDivider()

                            if (loaded.savedCards.isEmpty()) {
                                Text(
                                    text = "No saved card. Add a payment method in " +
                                        "Telegram Settings, then come back to this invoice.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Text("Card")

                                loaded.savedCards.forEach { card ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selectedCard == card.id,
                                                onClick = { selectedCard = card.id }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedCard == card.id,
                                            onClick = { selectedCard = card.id }
                                        )

                                        Text(
                                            text = card.title,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        error?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canPay = loaded != null && !isPaying &&
                (loaded.isStars || loaded.savedCards.isNotEmpty()) &&
                (!loaded.needsShippingAddress ||
                    (country.isNotBlank() && city.isNotBlank() && street.isNotBlank()))

            Button(
                enabled = canPay,
                onClick = { validateThenPay() }
            ) {
                if (isPaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }

                val amount = loaded?.let { info ->
                    formatPrice(
                        amount = info.starCount.takeIf { it > 0 } ?: info.totalAmount,
                        currency = info.currency
                    )
                }

                Text(
                    when {
                        loaded?.isStars == true -> "Pay $amount"
                        else -> "Pay"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
