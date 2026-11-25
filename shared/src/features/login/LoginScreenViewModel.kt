package com.mrboomdev.scrollix.features.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthCaptchaValueId
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Delay before the code can be requested again. */
internal const val CodeResendDelaySeconds = 60

/**
 * Owns the sign-in form: the current challenge, typed field values, the
 * in-flight action, the captcha error and the resend cooldown. The session is
 * resolved once, so the screen only renders the state.
 */
internal class LoginScreenViewModel(accountId: String) : ViewModel() {
    private val session = ApiManager.session(accountId)

    val authState: StateFlow<AuthState> = session?.authState
        ?: MutableStateFlow<AuthState>(AuthState.Error("Account not found"))

    var challenge by mutableStateOf<AuthChallenge?>(null)
        private set

    var values by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var pendingAction by mutableStateOf<AuthAction?>(null)
        private set

    var captchaError by mutableStateOf<String?>(null)
        private set

    var resendCooldown by mutableStateOf(0)
        private set

    private var cooldownJob: Job? = null

    val hasSession: Boolean
        get() = session != null

    /**
     * Telegram blocks repeated code requests for a while, so the cooldown comes
     * from the server (`codeInfo.timeout`) when it is known.
     */
    val resendDelay: Int
        get() = challenge?.resendAfterSeconds ?: CodeResendDelaySeconds

    val isBusy: Boolean
        get() = challenge?.isSubmitting == true || pendingAction != null

    val canSubmit: Boolean
        get() = challenge?.fields?.let { fields ->
            fields.isNotEmpty() && fields.all { field ->
                field.type == AuthFieldType.LastName || values[field.id].orEmpty().isNotBlank()
            }
        } ?: false

    init {
        viewModelScope.launch {
            authState.collect { state -> onAuthState(state) }
        }
    }

    fun updateValue(fieldId: String, value: String) {
        values = values + (fieldId to value)
    }

    fun submit() {
        if (session == null) return

        viewModelScope.launch {
            session.submitAuth(values)
        }
    }

    fun performAction(action: AuthAction) {
        if (session == null || pendingAction != null) return

        viewModelScope.launch {
            pendingAction = action

            try {
                session.performAuthAction(action)
            } finally {
                pendingAction = null
            }
        }
    }

    fun resendCode(action: AuthAction) {
        resendCooldown = CodeResendDelaySeconds
        performAction(action)
    }

    fun submitCaptcha(token: String) {
        if (session == null || isBusy) return

        viewModelScope.launch {
            session.submitAuth(mapOf(AuthCaptchaValueId to token))
        }
    }

    fun reportCaptchaError(message: String) {
        captchaError = message
    }

    fun clearCaptchaError() {
        captchaError = null
    }

    private fun onAuthState(state: AuthState) {
        val next = (state as? AuthState.Challenge)?.challenge
        val isNewChallenge = next?.id != challenge?.id

        challenge = next

        if (isNewChallenge) {
            values = next?.fields?.associate { field -> field.id to "" } ?: emptyMap()
            pendingAction = null
            captchaError = null
            startCooldown(next)
        }
    }

    private fun startCooldown(challenge: AuthChallenge?) {
        cooldownJob?.cancel()

        val delaySeconds = challenge?.resendAfterSeconds ?: CodeResendDelaySeconds
        resendCooldown = if (challenge?.id == "code" || challenge?.id == "email_code") {
            delaySeconds
        } else {
            0
        }

        if (resendCooldown <= 0) return

        cooldownJob = viewModelScope.launch {
            while (resendCooldown > 0) {
                delay(1_000)
                resendCooldown--
            }
        }
    }
}
