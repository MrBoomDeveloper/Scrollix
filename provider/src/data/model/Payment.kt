package com.mrboomdev.scrollix.data.model

import kotlinx.serialization.Serializable

/**
 * The invoice a bot attached to a message. [totalAmount] is in the currency's
 * smallest unit, except for currencies without one (stars included).
 */
@Serializable
data class MessageInvoiceInfo(
    val title: String,
    val description: String = "",
    val currency: String = "",
    val totalAmount: Long = 0,
    val isTest: Boolean = false
)

/** A card the account already saved with the payment provider. */
data class SavedPaymentCard(
    val id: String,
    val title: String
)

data class ShippingOptionInfo(
    val id: String,
    val title: String,
    val amount: Long = 0
)

/** What the provider needs before an invoice can be paid. */
data class PaymentFormInfo(
    val formId: Long,
    val currency: String = "",
    val totalAmount: Long = 0,
    /** Amount in Telegram stars; 0 for a regular card payment. */
    val starCount: Long = 0,
    val savedCards: List<SavedPaymentCard> = emptyList(),
    val canSaveCredentials: Boolean = false,
    val needsName: Boolean = false,
    val needsPhone: Boolean = false,
    val needsEmail: Boolean = false,
    val needsShippingAddress: Boolean = false
) {
    /** True when the invoice is paid with stars instead of a card. */
    val isStars: Boolean get() = starCount > 0 || currency.equals("XTR", ignoreCase = true)

    /** True when the invoice asks for contact or delivery details. */
    val needsDetails: Boolean
        get() = needsName || needsPhone || needsEmail || needsShippingAddress
}

data class PaymentAddress(
    val countryCode: String,
    val state: String = "",
    val city: String = "",
    val streetLine1: String = "",
    val streetLine2: String = "",
    val postalCode: String = ""
) {
    val isEmpty: Boolean
        get() = countryCode.isBlank() || city.isBlank() || streetLine1.isBlank()
}

/** Everything the user filled in before paying. */
data class PaymentOrder(
    val name: String = "",
    val phoneNumber: String = "",
    val emailAddress: String = "",
    val address: PaymentAddress? = null,
    /** Saved card to charge; null asks the provider for a new card. */
    val cardId: String? = null,
    /** Store the card for the next invoice. */
    val saveCard: Boolean = false
)

/** Result of validating the order: what can be shipped and how. */
data class PaymentValidation(
    val orderInfoId: String = "",
    val shippingOptions: List<ShippingOptionInfo> = emptyList()
)

sealed interface PaymentOutcome {
    /** The provider accepted the payment. */
    data object Paid : PaymentOutcome

    /** The provider wants the user to finish the payment on its own page. */
    data class Verification(val url: String) : PaymentOutcome
}

/** Currencies whose smallest unit is the whole unit. */
private val ZeroDecimalCurrencies = setOf(
    "XTR", "JPY", "KRW", "VND", "CLP", "ISK", "UGX", "RWF", "VUV",
    "XAF", "XOF", "XPF", "KMF", "DJF", "GNF", "MGA", "PYG", "UZS"
)

/**
 * Formats an amount the provider reports in minor units. Stars and the
 * zero-decimal currencies are printed as they are.
 */
fun formatPrice(amount: Long, currency: String): String {
    val code = currency.uppercase()

    if (code.isEmpty()) return amount.toString()

    val value = if (code in ZeroDecimalCurrencies) {
        amount.toString()
    } else {
        val sign = if (amount < 0) "-" else ""
        val absolute = if (amount < 0) -amount else amount
        val fraction = (absolute % 100).toString().padStart(2, '0')

        "$sign${absolute / 100}.$fraction"
    }

    return "$value $code"
}
