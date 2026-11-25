package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.provider.AccountSession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Lets the user approve a login on another device by scanning the QR code that
 * device shows. The camera implementation is platform specific; a manual link
 * field covers devices without one.
 */
@Composable
fun QrScannerDialog(
    session: AccountSession?,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isConfirming by remember { mutableStateOf(false) }
    var manualLink by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun confirm(link: String) {
        if (session == null || isConfirming) return
        if (link.isBlank()) return

        isConfirming = true
        isError = false
        status = "Authorizing the other device…"

        scope.launch {
            session.confirmQrCodeAuthentication(link)
                .onSuccess {
                    isError = false
                    status = "Device authorized. It should finish logging in now."
                }
                .onFailure { throwable ->
                    isError = true
                    status = throwable.message ?: "Failed to authorize the device"
                }

            isConfirming = false
        }
    }

    LaunchedEffect(session) {
        if (session == null) {
            isError = true
            status = "Sign in to an account first, then scan the QR code with it."
        }
    }

    Dialog(onDismissRequest = { if (!isConfirming) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Scan QR code",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Open Telegram on the device you want to authorize, go to " +
                        "Settings → Devices → Link Desktop Device, and point your camera " +
                        "at the QR code it shows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (session == null) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = "Log in to an account to authorize other devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        PlatformQrCamera(
                            modifier = Modifier.fillMaxSize(),
                            onQrCode = { link -> confirm(link) },
                            onError = { message ->
                                isError = true
                                status = message
                            }
                        )
                    }
                }

                status?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                if (session != null) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = manualLink,
                        onValueChange = { manualLink = it },
                        enabled = !isConfirming,
                        singleLine = true,
                        label = { Text("Login link") },
                        placeholder = { Text("tg://login?token=…") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isConfirming && manualLink.isNotBlank(),
                            onClick = { confirm(manualLink.trim()) }
                        ) {
                            if (isConfirming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(2.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Authorize")
                            }
                        }

                        TextButton(
                            enabled = !isConfirming,
                            onClick = onDismiss
                        ) {
                            Text("Close")
                        }
                    }
                } else {
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = onDismiss
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/** Camera preview that reports the first QR code it recognizes. */
@Composable
expect fun PlatformQrCamera(
    modifier: Modifier,
    onQrCode: (String) -> Unit,
    onError: (String) -> Unit
)
