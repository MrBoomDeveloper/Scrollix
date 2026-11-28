package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.PaymentFormInfo
import com.mrboomdev.scrollix.data.model.PaymentOrder
import com.mrboomdev.scrollix.data.model.PaymentOutcome
import com.mrboomdev.scrollix.data.model.PaymentValidation
import com.mrboomdev.scrollix.data.model.SessionErrorCode
import com.mrboomdev.scrollix.data.model.SessionException

/**
 * Invoices a bot attaches to a message. Providers without payments keep the
 * defaults, so an invoice button simply never appears there.
 */
interface PaymentSession {
    /**
     * What the invoice of [messageId] needs: product, price, saved cards and
     * the contact fields the seller asked for.
     */
    suspend fun loadPaymentForm(
        providerDialogId: String,
        messageId: Long
    ): Result<PaymentFormInfo> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Payments are not supported by this provider"
        )
    )

    /**
     * Checks the filled order with the seller and returns the shipping
     * options it offers, plus the id the payment needs.
     */
    suspend fun validatePaymentOrder(
        providerDialogId: String,
        messageId: Long,
        order: PaymentOrder
    ): Result<PaymentValidation> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Payments are not supported by this provider"
        )
    )

    /** Charges the invoice. [orderInfoId] comes from [validatePaymentOrder]. */
    suspend fun payInvoice(
        providerDialogId: String,
        messageId: Long,
        formId: Long,
        orderInfoId: String,
        shippingOptionId: String,
        order: PaymentOrder
    ): Result<PaymentOutcome> = Result.failure(
        SessionException(
            SessionErrorCode.Unsupported,
            "Payments are not supported by this provider"
        )
    )
}
