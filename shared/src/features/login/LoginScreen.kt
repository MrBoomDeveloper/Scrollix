package com.mrboomdev.scrollix.features.login

import com.mrboomdev.scrollix.data.model.AuthAction
import com.mrboomdev.scrollix.data.model.AuthChallenge
import com.mrboomdev.scrollix.data.model.AuthField
import com.mrboomdev.scrollix.data.model.AuthFieldType
import com.mrboomdev.scrollix.data.model.AuthState
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
internal fun LoginScreen(
    accountId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: LoginScreenViewModel = viewModel(key = accountId) {
        LoginScreenViewModel(accountId = accountId)
    }
) {
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Ready) {
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        IconButton(
            modifier = Modifier.align(Alignment.Start),
            onClick = onBack
        ) {
            Icon(
                tint = MaterialTheme.colorScheme.secondary,
                imageVector = arrow_back_icon,
                contentDescription = null
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val contentModifier = Modifier
                .widthIn(max = 450.dp)
                .fillMaxWidth()
            
            when (val state = authState) {
                is AuthState.Challenge -> {
                    AuthChallengeContent(
                        modifier = contentModifier,
                        challenge = state.challenge,
                        viewModel = viewModel
                    )
                }

                is AuthState.Initializing -> {
                    LoadingContent(
                        modifier = contentModifier,
                        text = "Connecting…"
                    )
                }

                is AuthState.Error -> {
                    ErrorContent(
                        modifier = contentModifier,
                        message = state.message,
                        onBack = onBack
                    )
                }

                is AuthState.Closed -> {
                    ErrorContent(
                        modifier = contentModifier,
                        message = "This account session is closed.",
                        onBack = onBack
                    )
                }

                is AuthState.Ready -> {
                    LoadingContent(
                        modifier = contentModifier,
                        text = "Signed in"
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthChallengeContent(
    modifier: Modifier,
    challenge: AuthChallenge,
    viewModel: LoginScreenViewModel
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    val values = viewModel.values
    val pendingAction = viewModel.pendingAction
    val captchaError = viewModel.captchaError
    val resendDelay = viewModel.resendDelay
    val resendCooldown = viewModel.resendCooldown
    val isBusy = viewModel.isBusy
    val canSubmit = viewModel.canSubmit


    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            style = MaterialTheme.typography.headlineMedium,
            text = challenge.title
        )

        challenge.description?.let { description ->
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                text = description
            )
        }

        challenge.captchaUrl?.let { captchaUrl ->
            CaptchaView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                url = captchaUrl,
                enabled = !isBusy && viewModel.hasSession,
                onToken = { token -> viewModel.submitCaptcha(token) },
                onError = { message -> viewModel.reportCaptchaError(message) }
            )

            if (challenge.isSubmitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        text = "Checking the captcha…"
                    )
                }
            }
        }

        captchaError?.let { message ->
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                text = message
            )
        }

        challenge.qrLink?.let { link ->
            QrCodeImage(link)
        }

        challenge.fields.forEach { field ->
            AuthFieldInput(
                field = field,
                value = values[field.id].orEmpty(),
                enabled = !isBusy,
                onValueChange = { value ->
                    viewModel.updateValue(field.id, value)
                }
            )
        }

        if (challenge.id == "code" && values["code"].isNullOrBlank()) {
            Text(
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                text = when {
                    pendingAction == AuthAction.ResendCode ->
                        "Requesting a new code…"

                    // The provider only lists ResendCode when the server has
                    // another delivery channel. Without it the button is absent
                    // and pointing at it would be a dead end.
                    challenge.actions.none { it is AuthAction.ResendCode } ->
                        "Telegram has no other delivery method for this code. Terminate any " +
                            "former Scrollix session in Telegram → Settings → Devices so it " +
                            "is sent by SMS, or use QR login from a signed-in phone."

                    resendCooldown > 0 ->
                        "Waiting for the code. You can request a new one in ${resendCooldown}s."

                    else ->
                        "The code hasn't arrived. Tap Resend code to request a new one."
                }
            )
        }

        challenge.error?.let { error ->
            Text(
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                text = error
            )
        }

        if (challenge.fields.isNotEmpty()) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                enabled = canSubmit && !isBusy && viewModel.hasSession,
                onClick = {
                    keyboardController?.hide()
                    viewModel.submit()
                }
            ) {
                if (challenge.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Continue")
                }
            }
        }

        challenge.actions
            .filterNot { it is AuthAction.Submit }
            .forEach { action ->
                when (action) {
                    is AuthAction.ResendCode -> {
                        AuthActionButton(
                            text = if (resendCooldown > 0) {
                                "Resend code in ${resendCooldown}s"
                            } else {
                                "Resend code"
                            },
                            isBusy = pendingAction == action,
                            enabled = !isBusy && viewModel.hasSession && resendCooldown == 0,
                            onClick = { viewModel.resendCode(action) }
                        )
                    }

                    is AuthAction.RecoverPassword -> {
                        AuthActionButton(
                            text = "Recover password",
                            isBusy = pendingAction == action,
                            enabled = !isBusy && viewModel.hasSession,
                            onClick = { viewModel.performAction(action) }
                        )
                    }

                    is AuthAction.UseQrCode -> {
                        AuthActionButton(
                            text = "Log in with QR code",
                            isBusy = pendingAction == action,
                            enabled = !isBusy && viewModel.hasSession,
                            onClick = { viewModel.performAction(action) }
                        )
                    }

                    is AuthAction.RefreshCaptcha -> {
                        AuthActionButton(
                            text = "Reload captcha",
                            isBusy = pendingAction == action,
                            enabled = !isBusy && viewModel.hasSession,
                            onClick = {
                                viewModel.clearCaptchaError()
                                viewModel.performAction(action)
                            }
                        )
                    }

                    is AuthAction.OpenUrl -> {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !challenge.isSubmitting,
                            onClick = {
                                runCatching {
                                    uriHandler.openUri(action.url)
                                }
                            }
                        ) {
                            Text("Open in Telegram")
                        }
                    }

                    AuthAction.Submit -> Unit
                }
            }
    }
}

@Composable
private fun AuthActionButton(
    text: String,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(text)
        }
    }
}

@Composable
private fun QrCodeImage(link: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier
                .size(240.dp)
                .background(Color.White)
                .padding(12.dp),
            painter = rememberQrCodePainter(
                data = link,
                backgroundFill = SolidColor(Color.White),
                scale = 0.9f
            ),
            contentDescription = "Telegram login QR code"
        )
    }
}

@Composable
private fun AuthFieldInput(
    field: AuthField,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(field.label) },
        placeholder = field.placeholder?.let { hint ->
            { Text(hint) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = field.type.keyboardType()
        ),
        visualTransformation = if (field.type == AuthFieldType.Password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }
    )
}

@Composable
private fun ErrorContent(
    modifier: Modifier,
    message: String,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "Unable to sign in",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = message,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium
        )

        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

private fun AuthFieldType.keyboardType(): KeyboardType {
    return when (this) {
        AuthFieldType.Phone -> KeyboardType.Phone
        AuthFieldType.Code -> KeyboardType.Number
        AuthFieldType.Password -> KeyboardType.Password
        AuthFieldType.Email -> KeyboardType.Email
        AuthFieldType.Text,
        AuthFieldType.FirstName,
        AuthFieldType.LastName -> KeyboardType.Text
    }
}
