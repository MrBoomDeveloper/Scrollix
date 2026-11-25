package com.mrboomdev.scrollix.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.isWideLayout
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.ProfileAvatarGalleryDialog
import com.mrboomdev.scrollix.ui.components.ProfileSharedMediaSection
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.mrboomdev.scrollix.ui.icons.ProviderLogoIcon
import com.mrboomdev.scrollix.ui.icons.verified_icon

@Composable
fun ProfileScreen(
    accountId: String,
    providerDialogId: String,
    userId: Long? = null,
    ownAccount: Boolean = false,
    /** Opens a channel or group chat from the profile (personal channel, groups in common). */
    onOpenChat: (String) -> Unit = {},
    onMessage: (String) -> Unit = {},
    onOpenMessage: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val isWide = isWideLayout()
    val session = remember(accountId) { ApiManager.session(accountId) }
    val entries by ApiManager.dialogs.collectAsState()
    val entry = entries.firstOrNull { entry ->
        entry.account.id == accountId &&
            entry.dialog.providerDialogId == providerDialogId
    }
    var details by remember(accountId, providerDialogId, userId) {
        mutableStateOf<UserProfileDetails?>(null)
    }
    val scope = rememberCoroutineScope()
    var subscriptionOverride by remember(accountId, providerDialogId) {
        mutableStateOf<Boolean?>(null)
    }
    var isUpdatingSubscription by remember(accountId, providerDialogId) {
        mutableStateOf(false)
    }
    var actionError by remember(accountId, providerDialogId) {
        mutableStateOf<String?>(null)
    }
    var isMuted by remember(accountId, providerDialogId) { mutableStateOf<Boolean?>(null) }
    var showAvatarGallery by remember(accountId, providerDialogId) {
        mutableStateOf(false)
    }
    var showContactEditor by remember(accountId, providerDialogId) {
        mutableStateOf(false)
    }
    var contactFirstName by remember(accountId, providerDialogId) { mutableStateOf("") }
    var contactLastName by remember(accountId, providerDialogId) { mutableStateOf("") }

    LaunchedEffect(accountId, providerDialogId, session, ownAccount) {
        if (!ownAccount && providerDialogId.isNotBlank()) {
            isMuted = session?.isDialogMuted(providerDialogId)
        }
    }

    fun toggleMute() {
        val currentSession = session ?: return
        val muted = isMuted ?: return
        val next = !muted

        isMuted = next

        scope.launch {
            currentSession.setDialogMuted(providerDialogId, next).onFailure {
                isMuted = muted
                actionError = it.message ?: "Failed to update notifications"
            }
        }
    }

    fun toggleBlock() {
        val currentSession = session ?: return
        val targetUserId = details?.userId ?: return
        if (details?.canBlock == false) return

        val blocked = details?.isBlocked == true

        details = details?.copy(isBlocked = !blocked)

        scope.launch {
            currentSession.setUserBlocked(targetUserId, !blocked).onFailure {
                details = details?.copy(isBlocked = blocked)
                actionError = it.message ?: "Failed to update the block state"
            }
        }
    }

    fun openContactEditor() {
        val parts = details?.displayName.orEmpty().trim().split(' ', limit = 2)

        contactFirstName = parts.firstOrNull().orEmpty()
        contactLastName = parts.getOrNull(1).orEmpty()
        showContactEditor = true
    }

    fun saveContact() {
        val currentSession = session ?: return
        val targetUserId = details?.userId ?: return
        val firstName = contactFirstName.trim()
        val lastName = contactLastName.trim()

        if (firstName.isEmpty()) {
            actionError = "Enter a first name"
            return
        }

        showContactEditor = false
        actionError = null

        details = details?.copy(
            isContact = true,
            displayName = listOf(firstName, lastName)
                .filter { part -> part.isNotBlank() }
                .joinToString(" ")
        )

        scope.launch {
            currentSession.addContact(targetUserId, firstName, lastName).onFailure {
                details = details?.copy(isContact = false)
                actionError = it.message ?: "Failed to update the contact"
            }
        }
    }

    fun removeContact() {
        val currentSession = session ?: return
        val targetUserId = details?.userId ?: return

        details = details?.copy(isContact = false)
        actionError = null

        scope.launch {
            currentSession.removeContact(targetUserId).onFailure {
                details = details?.copy(isContact = true)
                actionError = it.message ?: "Failed to remove the contact"
            }
        }
    }

    LaunchedEffect(accountId, providerDialogId, userId, ownAccount) {
        details = if (ownAccount) {
            session?.loadOwnProfile()?.getOrNull()
        } else {
            ApiManager.loadUserProfile(
                accountId = accountId,
                providerDialogId = providerDialogId,
                userId = userId
            ).getOrNull()
        }
    }

    val isSubscribed = subscriptionOverride ?: details?.isSubscribed == true

    fun toggleSubscription() {
        val currentSession = session

        if (currentSession == null) {
            actionError = "Account session is not available"
            return
        }

        val subscribe = !isSubscribed
        val previous = isSubscribed

        // Flip the label right away and revert it if the request fails.
        subscriptionOverride = subscribe
        details = details?.copy(isSubscribed = subscribe)

        fun revert(message: String?) {
            subscriptionOverride = previous
            details = details?.copy(isSubscribed = previous)

            if (message != null) {
                actionError = message
            }
        }

        scope.launch {
            isUpdatingSubscription = true
            actionError = null

            try {
                val result = withTimeoutOrNull(15_000.milliseconds) {
                    currentSession.setChatSubscribed(providerDialogId, subscribe)
                }

                when {
                    result == null -> revert("The server took too long to respond")

                    result.isSuccess -> {
                        // Re-read the membership from the server so reopening
                        // the profile keeps showing the updated state.
                        details = ApiManager.loadUserProfile(
                            accountId = accountId,
                            providerDialogId = providerDialogId,
                            userId = userId
                        ).getOrNull() ?: details
                    }

                    else -> revert(
                        result.exceptionOrNull()?.message
                            ?: "Failed to update subscription"
                    )
                }
            } finally {
                isUpdatingSubscription = false
            }
        }
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
                text = if (ownAccount) "Your profile" else "Profile",
                style = MaterialTheme.typography.titleLarge
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        val dialog = entry?.dialog
        val isChannel = (details?.kind ?: dialog?.kind) == DialogKind.Channel
        val profile = details

        if (entry == null && profile == null && !ownAccount) {
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
                .padding(24.dp)
        ) {
            val displayName = profile?.displayName ?: dialog?.title ?: "Profile"
            val sharedProfileKey = if (ownAccount) {
                "own-profile:$accountId"
            } else {
                "profile:$accountId:${userId ?: providerDialogId}"
            }
            val avatar = profile?.avatar
                ?: dialog?.avatar
                ?: Avatar.fromName(displayName)
            val kind = profile?.kind ?: dialog?.kind
            val emojiStatusId = dialog?.emojiStatusId ?: profile?.emojiStatusId
            val accentColors = (
                if (isSystemInDarkTheme()) {
                    profile?.accentColorsDark
                } else {
                    profile?.accentColorsLight
                }
            ).orEmpty()
            val accentBrush = accentColors
                .takeIf { it.isNotEmpty() }
                ?.let { colors ->
                    Brush.linearGradient(
                        colors.map { color ->
                            Color(color or (0xFF shl 24)).copy(alpha = 0.32f)
                        }
                    )
                }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if(accentBrush != null) {
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(accentBrush)
                                .padding(16.dp)
                        } else {
                            Modifier
                        }
                    ),
                horizontalAlignment = if(isWide) {
                    Alignment.Start
                } else Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SharedProfileElement(key = sharedProfileKey) {
                        AvatarImage(
                            modifier = if (accentBrush != null) {
                                Modifier.border(
                                    width = 2.dp,
                                    brush = accentBrush,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                            } else {
                                Modifier
                            }.then(
                                if (profile?.userId != null) {
                                    Modifier.clickable { showAvatarGallery = true }
                                } else {
                                    Modifier
                                }
                            ),
                            avatar = avatar,
                            size = 96.dp
                        )
                    }

                    AnimatedVisibility(isWide && !ownAccount) {
                        ProfileScreenActions(
                            dialog = dialog,
                            phoneNumber = profile?.phoneNumber,
                            isChannel = isChannel,
                            isSubscribed = isSubscribed,
                            isUpdatingSubscription = isUpdatingSubscription,
                            isMuted = isMuted,
                            canBlock = dialog?.kind == DialogKind.Direct &&
                                details?.userId != null &&
                                details?.canBlock != false,
                            isBlocked = details?.isBlocked == true,
                            canManageContacts = dialog?.kind == DialogKind.Direct &&
                                details?.canManageContacts == true,
                            isContact = details?.isContact == true,
                            stretch = false,
                            onEditContact = ::openContactEditor,
                            onRemoveContact = ::removeContact,
                            onMessage = {
                                onMessage(details?.discussionChatId ?: providerDialogId)
                            },
                            onToggleSubscription = ::toggleSubscription,
                            onToggleMute = ::toggleMute,
                            onToggleBlock = ::toggleBlock
                        )
                    }

                    AnimatedVisibility(isWide) {
                        profile?.personalChannel?.let { channel ->
                            PersonalChannelCard(
                                channel = channel,
                                onClick = { onOpenChat(channel.providerDialogId) },
                                onPortrait = false
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProviderLogoIcon(
                        providerId = session?.providerId,
                        size = 18.dp
                    )

                    SharedProfileElement(key = "$sharedProfileKey:name") {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (profile?.isVerified == true || dialog?.isVerified == true) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                            imageVector = verified_icon,
                            contentDescription = "Verified"
                        )
                    }

                    emojiStatusId?.let { emojiId ->
                        EmojiStatusIcon(
                            emojiId = emojiId,
                            session = session,
                            size = 22.dp
                        )
                    }
                }

                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.secondary,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium
                ) {
                    profile?.username?.takeIf { it.isNotBlank() }?.let { username ->
                        Text("@${username.removePrefix("@")}")
                    }
                }
            }
            
            AnimatedVisibility(!isWide && !ownAccount) {
                ProfileScreenActions(
                    dialog = dialog,
                    phoneNumber = profile?.phoneNumber,
                    isChannel = isChannel,
                    isSubscribed = isSubscribed,
                    isUpdatingSubscription = isUpdatingSubscription,
                    isMuted = isMuted,
                    canBlock = dialog?.kind == DialogKind.Direct &&
                        details?.userId != null &&
                        details?.canBlock != false,
                    isBlocked = details?.isBlocked == true,
                    canManageContacts = dialog?.kind == DialogKind.Direct &&
                        details?.canManageContacts == true,
                    isContact = details?.isContact == true,
                    stretch = true,
                    onEditContact = ::openContactEditor,
                    onRemoveContact = ::removeContact,
                    onMessage = {
                        onMessage(details?.discussionChatId ?: providerDialogId)
                    },
                    onToggleSubscription = ::toggleSubscription,
                    onToggleMute = ::toggleMute,
                    onToggleBlock = ::toggleBlock
                )
            }
            
            ProfileScreenInfo(
                profile = profile,
                fallbackStatus = dialog?.statusText
            )

            actionError?.let { message ->
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            AnimatedVisibility(!isWide) {
                profile?.personalChannel?.let { channel ->
                    PersonalChannelCard(
                        channel = channel,
                        onClick = { onOpenChat(channel.providerDialogId) },
                        onPortrait = true
                    )
                }   
            }

            val tracks = profile?.tracks.orEmpty()

            if (tracks.isNotEmpty()) {
                TracksSection(
                    tracks = tracks,
                    session = session
                )
            }

            val members = profile?.members.orEmpty()

            if (members.isNotEmpty()) {
                MembersSection(
                    members = members,
                    totalCount = profile?.memberCount,
                    session = session
                )
            }

            val commonGroups = profile?.commonGroups.orEmpty()

            if (commonGroups.isNotEmpty()) {
                CommonGroupsSection(
                    groups = commonGroups,
                    onOpenGroup = { group ->
                        onOpenChat(group.providerDialogId)
                    }
                )
            }

//            entry?.let { accountEntry ->
//                Text(
//                    text = "Account: ${accountEntry.account.displayName}",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.secondary
//                )
//            }
//
//            if (entry != null && userId == null && entry.dialog.identity.remoteId.isNotBlank()) {
//                Text(
//                    text = entry.dialog.identity.remoteId,
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.secondary
//                )
//            }

            if (providerDialogId.isNotBlank() && !ownAccount) {
                ProfileSharedMediaSection(
                    providerDialogId = providerDialogId,
                    session = session,
                    onOpenMessage = onOpenMessage
                )
            }

            if (showAvatarGallery) {
                profile?.userId?.let { profileUserId ->
                    ProfileAvatarGalleryDialog(
                        userId = profileUserId,
                        session = session,
                        fallbackAvatar = avatar,
                        onDismiss = { showAvatarGallery = false }
                    )
                }
            }

            if (showContactEditor) {
                AlertDialog(
                    onDismissRequest = { showContactEditor = false },
                    title = {
                        Text(if (details?.isContact == true) "Edit contact" else "Add contact")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = contactFirstName,
                                onValueChange = { contactFirstName = it },
                                label = { Text("First name") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = contactLastName,
                                onValueChange = { contactLastName = it },
                                label = { Text("Last name") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = ::saveContact) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showContactEditor = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MembersSection(
    members: List<ChatMemberInfo>,
    totalCount: Int?,
    session: AccountSession?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = totalCount?.let { count -> "Members · ${formatCount(count)}" } ?: "Members",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        for (member in members) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    avatar = member.avatar ?: Avatar.fromName(member.name),
                    size = 40.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        member.emojiStatusId?.let { emojiId ->
                            EmojiStatusIcon(
                                emojiId = emojiId,
                                session = session,
                                size = 16.dp
                            )
                        }
                    }

                    member.username?.takeIf { it.isNotBlank() }?.let { username ->
                        Text(
                            text = "@${username.removePrefix("@")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

internal fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
