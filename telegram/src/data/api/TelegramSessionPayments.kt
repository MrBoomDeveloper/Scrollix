package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import com.mrboomdev.scrollix.data.model.MessageInvoiceInfo
import com.mrboomdev.scrollix.data.model.PaymentAddress
import com.mrboomdev.scrollix.data.model.PaymentFormInfo
import com.mrboomdev.scrollix.data.model.PaymentOrder
import com.mrboomdev.scrollix.data.model.PaymentOutcome
import com.mrboomdev.scrollix.data.model.PaymentValidation
import com.mrboomdev.scrollix.data.model.SavedPaymentCard
import com.mrboomdev.scrollix.data.model.ShippingOptionInfo
import kotlinx.coroutines.CancellationException

/** Maps the invoice a bot attached to a message. */
internal fun mapInvoice(content: MessageContent): MessageInvoiceInfo? {
    val invoice = content as? MessageInvoice ?: return null

    return MessageInvoiceInfo(
        title = invoice.productInfo.title,
        description = invoice.productInfo.description.text,
        currency = invoice.currency,
        totalAmount = invoice.totalAmount,
        isTest = invoice.isTest
    )
}

internal suspend fun TelegramSession.loadPaymentFormImpl(
    providerDialogId: String,
    messageId: Long
): Result<PaymentFormInfo> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.getPaymentForm(
            inputInvoice = InputInvoiceMessage(chatId = chatId, messageId = messageId),
            theme = null
        )) {
            is TdlResult.Success -> Result.success(mapPaymentForm(result.result))
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.validatePaymentOrderImpl(
    providerDialogId: String,
    messageId: Long,
    order: PaymentOrder
): Result<PaymentValidation> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.validateOrderInfo(
            inputInvoice = InputInvoiceMessage(chatId = chatId, messageId = messageId),
            orderInfo = order.toOrderInfo(),
            allowSave = order.saveCard
        )) {
            is TdlResult.Success -> Result.success(
                PaymentValidation(
                    orderInfoId = result.result.orderInfoId,
                    shippingOptions = result.result.shippingOptions.map { option ->
                        ShippingOptionInfo(
                            id = option.id,
                            title = option.title,
                            amount = option.priceParts.sumOf { part -> part.amount }
                        )
                    }
                )
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.payInvoiceImpl(
    providerDialogId: String,
    messageId: Long,
    formId: Long,
    orderInfoId: String,
    shippingOptionId: String,
    order: PaymentOrder
): Result<PaymentOutcome> {
    val chatId = providerDialogId.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("Invalid dialog id"))

    return try {
        when (val result = client.sendPaymentForm(
            inputInvoice = InputInvoiceMessage(chatId = chatId, messageId = messageId),
            paymentFormId = formId,
            orderInfoId = orderInfoId,
            shippingOptionId = shippingOptionId,
            // Without a saved card the provider has to collect the card
            // itself; the account is told to finish the payment elsewhere.
            credentials = order.cardId?.let { id -> InputCredentialsSaved(savedCredentialsId = id) },
            tipAmount = 0
        )) {
            is TdlResult.Success -> {
                val payment = result.result

                Result.success(
                    if (payment.success) {
                        PaymentOutcome.Paid
                    } else {
                        PaymentOutcome.Verification(payment.verificationUrl)
                    }
                )
            }

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

private fun mapPaymentForm(form: PaymentForm): PaymentFormInfo {
    val type = form.type

    return when (type) {
        is PaymentFormTypeStars -> PaymentFormInfo(
            formId = form.id,
            currency = "XTR",
            // Stars invoices report the invoice total through the product;
            // the star count is what the button charges.
            totalAmount = type.starCount,
            starCount = type.starCount
        )

        is PaymentFormTypeRegular -> PaymentFormInfo(
            formId = form.id,
            currency = type.invoice.currency,
            totalAmount = type.invoice.priceParts.sumOf { part -> part.amount },
            savedCards = type.savedCredentials.map { card ->
                SavedPaymentCard(id = card.id, title = card.title)
            },
            canSaveCredentials = type.canSaveCredentials,
            needsName = type.invoice.needName,
            needsPhone = type.invoice.needPhoneNumber,
            needsEmail = type.invoice.needEmailAddress,
            needsShippingAddress = type.invoice.needShippingAddress
        )

        // A saved order only needs the credentials the account already has.
        else -> PaymentFormInfo(formId = form.id)
    }
}

private fun PaymentOrder.toOrderInfo(): OrderInfo? {
    val address = address

    if (
        name.isBlank() && phoneNumber.isBlank() && emailAddress.isBlank() &&
        (address == null || address.isEmpty)
    ) {
        return null
    }

    return OrderInfo(
        name = name,
        phoneNumber = phoneNumber,
        emailAddress = emailAddress,
        shippingAddress = address?.takeUnless { it.isEmpty }?.let { value ->
            Address(
                countryCode = value.countryCode.uppercase(),
                state = value.state,
                city = value.city,
                streetLine1 = value.streetLine1,
                streetLine2 = value.streetLine2,
                postalCode = value.postalCode
            )
        }
    )
}
