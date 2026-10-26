package com.mrboomdev.scrollix.data.model

sealed interface AuthState {
    data object Initializing : AuthState
    data object Ready : AuthState
    data class Challenge(val challenge: AuthChallenge) : AuthState
    data class Error(val message: String) : AuthState
    data object Closed : AuthState
}

data class AuthChallenge(
    val id: String,
    val title: String,
    val description: String? = null,
    val fields: List<AuthField> = emptyList(),
    val actions: List<AuthAction> = emptyList(),
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val qrLink: String? = null
)

data class AuthField(
    val id: String,
    val label: String,
    val type: AuthFieldType,
    val placeholder: String? = null
)

enum class AuthFieldType {
    Text,
    Phone,
    Code,
    Password,
    Email,
    FirstName,
    LastName
}

sealed interface AuthAction {
    data object Submit : AuthAction
    data object ResendCode : AuthAction
    data object RecoverPassword : AuthAction
    data object UseQrCode : AuthAction
    data class OpenUrl(val url: String) : AuthAction
}
