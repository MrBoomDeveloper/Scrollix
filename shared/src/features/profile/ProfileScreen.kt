package com.mrboomdev.scrollix.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon

@Composable
private fun ProfileDetails(profile: UserProfileDetails) {
    val rows = buildList {
        profile.username?.takeIf { it.isNotBlank() }?.let { username ->
            add("Username" to "@${username.removePrefix("@")}")
        }

        profile.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
            add("Phone" to formatPhoneNumber(phone))
        }

        profile.birthdate?.takeIf { it.isNotBlank() }?.let { birthdate ->
            add("Birthday" to birthdate)
        }

        profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            add("Bio" to bio)
        }

        profile.status?.takeIf { it.isNotBlank() }?.let { status ->
            add("Status" to status)
        }
    }

    if (rows.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        for ((label, value) in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    modifier = Modifier.width(96.dp),
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    modifier = Modifier.weight(1f),
                    text = value,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PersonalChannelCard(
    channel: com.mrboomdev.scrollix.data.model.PersonalChannel,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                avatar = channel.avatar ?: Avatar.fromName(channel.title),
                size = 44.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = channel.subscriberCount?.let { count ->
                        "${formatCount(count)} " +
                            if (count == 1) "subscriber" else "subscribers"
                    } ?: "Channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun CommonGroupsSection(
    groups: List<CommonGroup>,
    onOpenGroup: (CommonGroup) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Groups in common",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        for (group in groups) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenGroup(group) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        avatar = group.avatar ?: Avatar.fromName(group.title),
                        size = 44.dp
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    accountId: String,
    providerDialogId: String,
    userId: Long? = null,
    onOpenChannelProfile: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val entries by ApiManager.dialogs.collectAsState()
    val entry = entries.firstOrNull { entry ->
        entry.account.id == accountId &&
            entry.dialog.providerDialogId == providerDialogId
    }
    var details by remember(accountId, providerDialogId) {
        mutableStateOf<UserProfileDetails?>(null)
    }

    LaunchedEffect(accountId, providerDialogId) {
        details = ApiManager.loadUserProfile(
            accountId = accountId,
            providerDialogId = providerDialogId,
            userId = userId
        ).getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = arrow_back_icon,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        val dialog = entry?.dialog
        val profile = details

        if (entry == null && profile == null) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = "This dialog is no longer available.",
                color = MaterialTheme.colorScheme.secondary
            )

            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val displayName = profile?.displayName ?: dialog?.title ?: "Profile"
            val avatar = profile?.avatar
                ?: dialog?.avatar
                ?: Avatar.fromName(displayName)
            val kind = profile?.kind ?: dialog?.kind

            AvatarImage(
                avatar = avatar,
                size = 96.dp
            )

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (userId != null) {
                    "Telegram user"
                } else when (kind) {
                    DialogKind.Direct -> "Direct message"
                    DialogKind.Group -> profile?.memberCount?.let { count ->
                        "${formatCount(count)} ${if (count == 1) "member" else "members"}"
                    } ?: "Group"

                    DialogKind.Channel -> profile?.memberCount?.let { count ->
                        "${formatCount(count)} " +
                            if (count == 1) "subscriber" else "subscribers"
                    } ?: "Channel"

                    DialogKind.Bot -> "Bot"
                    DialogKind.Secret -> "Secret chat"
                    null -> "Profile"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            profile?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            profile?.personalChannel?.let { channel ->
                PersonalChannelCard(
                    channel = channel,
                    onClick = { onOpenChannelProfile(channel.providerDialogId) }
                )
            }

            val commonGroups = profile?.commonGroups.orEmpty()

            if (commonGroups.isNotEmpty()) {
                CommonGroupsSection(
                    groups = commonGroups,
                    onOpenGroup = { group ->
                        onOpenChannelProfile(group.providerDialogId)
                    }
                )
            }

            profile?.let { data -> ProfileDetails(data) }

            entry?.let { accountEntry ->
                Text(
                    text = "Account: ${accountEntry.account.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (entry != null && userId == null && entry.dialog.identity.remoteId.isNotBlank()) {
                Text(
                    text = entry.dialog.identity.remoteId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
