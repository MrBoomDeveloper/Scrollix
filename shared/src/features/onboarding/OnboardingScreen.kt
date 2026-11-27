package com.mrboomdev.scrollix.features.onboarding

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.provider.ProviderRegistry
import com.mrboomdev.scrollix.ui.components.LoadingContent
import com.mrboomdev.scrollix.ui.icons.ProviderLogoIcon
import com.mrboomdev.scrollix.ui.icons.image_icon
import com.mrboomdev.scrollix.ui.icons.microphone_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.storage_icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The non-blocking first run: it can be skipped at any point, and choosing a
 * provider is the only action that starts a sign-in. The permission list is
 * explanatory only — every permission is still requested when the feature that
 * needs it is used, never from here.
 */
@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onAccountCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var addingProvider by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val providers = remember { ProviderRegistry.all() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = "Welcome to Scrollix",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "One chat list for every messenger you use. Sign in now, " +
                "or skip this screen and add accounts later from the Accounts tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            text = "Choose a messenger",
            style = MaterialTheme.typography.titleMedium
        )

        providers.forEach { provider ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    if (addingProvider != null) return@Surface

                    addingProvider = provider.id
                    error = null

                    scope.launch {
                        runCatching { ApiManager.addAccount(provider.id) }
                            .onSuccess { accountId ->
                                addingProvider = null
                                onAccountCreated(accountId)
                            }
                            .onFailure { failure ->
                                addingProvider = null
                                error = failure.message ?: "Could not start sign-in"
                            }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProviderLogoIcon(providerId = provider.id, size = 24.dp)

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            text = if (addingProvider == provider.id) {
                                "Starting sign-in…"
                            } else {
                                "Sign in to your ${provider.displayName} account"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (addingProvider != null) {
            LoadingContent(
                modifier = Modifier.fillMaxWidth(),
                text = "Preparing sign-in…"
            )
        }

        error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            text = "What Scrollix may ask for",
            style = MaterialTheme.typography.titleMedium
        )

        PermissionRow(
            icon = notifications_icon,
            title = "Notifications",
            description = "Only new messages of unmuted chats. Android asks once " +
                "when the first notification is due."
        )

        PermissionRow(
            icon = microphone_icon,
            title = "Microphone and camera",
            description = "Only while you record a voice or video message, or take " +
                "a photo."
        )

        PermissionRow(
            icon = image_icon,
            title = "Photos and files",
            description = "Only when you attach a file or save one from a chat."
        )

        PermissionRow(
            icon = storage_icon,
            title = "Location and storage",
            description = "The location picker asks for access on first use and " +
                "closes if you decline; cached media stays on this device."
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onSkip
            ) {
                Text("Skip for now")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = onSkip
            ) {
                Text("Continue")
            }
        }

        Text(
            text = "Every permission is requested only when the feature that " +
                "needs it is used. You can review them at any time in the " +
                "system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = icon,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
