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
    val qrLink: String? = null,
    /**
     * Seconds the provider wants the user to wait before another code can be
     * requested. Null means the provider has no opinion and the screen falls
     * back to its own cooldown. Zero means a resend is allowed right away.
     */
    val resendAfterSeconds: Int? = null,
    /**
     * Link of a provider captcha that has to be solved before the next step.
     * The solved token is passed to [AccountSession.submitAuth] under
     * [AuthCaptchaValueId].
     */
    val captchaUrl: String? = null
)

/** Value key used by `submitAuth` for a solved [AuthChallenge.captchaUrl]. */
const val AuthCaptchaValueId = "captcha"

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

    /** Fetches a fresh [AuthChallenge.captchaUrl] after the previous one failed. */
    data object RefreshCaptcha : AuthAction

    data class OpenUrl(val url: String) : AuthAction
}
