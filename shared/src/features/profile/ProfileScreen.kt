package com.mrboomdev.scrollix.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.columns
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMemberInfo
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.ui.navigation.SharedProfileElement
import com.mrboomdev.scrollix.data.model.ProfileTrack
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.isWideLayout
import com.mrboomdev.scrollix.ui.AdaptiveLayoutSize
import com.mrboomdev.scrollix.ui.components.AdaptiveGrid
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.components.EmojiStatusIcon
import com.mrboomdev.scrollix.ui.components.ProfileAvatarGalleryDialog
import com.mrboomdev.scrollix.ui.components.ProfileSharedMediaSection
import com.mrboomdev.scrollix.ui.components.createVoicePlayer
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.mrboomdev.scrollix.ui.icons.cake_icon
import com.mrboomdev.scrollix.ui.icons.ProviderLogoIcon
import com.mrboomdev.scrollix.ui.icons.block_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.campaign_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.edit_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.music_note_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.notifications_off_icon
import com.mrboomdev.scrollix.ui.icons.pause_icon_filled
import com.mrboomdev.scrollix.ui.icons.play_icon_filled
import com.mrboomdev.scrollix.ui.icons.schedule_icon

@Composable
private fun PersonalChannelCard(
    channel: com.mrboomdev.scrollix.data.model.PersonalChannel,
    onClick: () -> Unit,
    onPortrait: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if(onPortrait) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            )
        }
        
        val textStyle = MaterialTheme.typography.bodyMedium

        val inlinePlaceholder = remember(textStyle) {
            Placeholder(
                width = textStyle.fontSize * 1.4,
                height = textStyle.fontSize * 1.4,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        }

        Text(
            style = textStyle,
            color = MaterialTheme.colorScheme.secondary,

            inlineContent = remember(textStyle) {
                mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = campaign_icon,
                        contentDescription = null
                    )
                })
            },

            text = buildAnnotatedString {
                appendInlineContent("icon")
                append(" Personal channel")
            }
        )

        Surface(
            modifier = if(onPortrait) Modifier.fillMaxWidth() else Modifier,
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarImage(
                    avatar = channel.avatar ?: Avatar.fromName(channel.title),
                    size = 40.dp
                )

                Column {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = channel.subscriberCount?.let { count ->
                            "${formatCount(count)} " +
                                    if(count == 1) "subscriber" else "subscribers"
                        } ?: "Channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalGridApi::class)
@Composable
private fun CommonGroupsSection(
    groups: List<CommonGroup>,
    onOpenGroup: (CommonGroup) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        )

        val textStyle = MaterialTheme.typography.bodyMedium

        val inlinePlaceholder = remember(textStyle) {
            Placeholder(
                width = textStyle.fontSize * 1.4,
                height = textStyle.fontSize * 1.4,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        }

        Text(
            style = textStyle,
            color = MaterialTheme.colorScheme.secondary,

            inlineContent = remember(textStyle) {
                mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = group_icon,
                        contentDescription = null
                    )
                })
            },

            text = buildAnnotatedString {
                appendInlineContent("icon")
                append(" Groups in common")
            }
        )

        AdaptiveGrid(
            modifier = Modifier.fillMaxWidth(),
            minColumnWidth = 150.dp,
            gap = 8.dp
        ) {
            for (group in groups) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    onClick = { onOpenGroup(group) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarImage(
                            avatar = group.avatar ?: Avatar.fromName(group.title),
                            size = 48.dp
                        )

                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreenActions(
    dialog: Dialog?,
    phoneNumber: String?,
    isChannel: Boolean,
    isSubscribed: Boolean,
    isUpdatingSubscription: Boolean,
    isMuted: Boolean?,
    canBlock: Boolean,
    isBlocked: Boolean,
    canManageContacts: Boolean,
    isContact: Boolean,
    stretch: Boolean,
    onEditContact: () -> Unit,
    onRemoveContact: () -> Unit,
    onMessage: () -> Unit,
    onToggleSubscription: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Notification settings only make sense for chats the account is part of.
    // A group or channel the user has not joined has nothing to mute.
    val isMemberChat = dialog?.kind == DialogKind.Group ||
        dialog?.kind == DialogKind.Channel
    val showMute = !isMemberChat || isSubscribed

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val actionModifier = if(stretch) {
            Modifier.weight(1f)
        } else Modifier

        if(isChannel) {
            Button(
                modifier = actionModifier,
                enabled = !isUpdatingSubscription,
                onClick = onToggleSubscription
            ) {
                Text(
                    if(isSubscribed) "Unfollow" else "Follow"
                )
            }
        }

        OutlinedButton(
            modifier = actionModifier,
            onClick = onMessage
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = MaterialTheme.colorScheme.primary,
                imageVector = chat_bubble_icon,
                contentDescription = null
            )
        }

        if (dialog?.kind == DialogKind.Direct &&
            getPlatform().name.startsWith("Android")
        ) {
            OutlinedButton(
                modifier = actionModifier,
                contentPadding = PaddingValues(0.dp),
                onClick = { phoneNumber?.let(::openDialer) }
            ) {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = call_icon,
                    contentDescription = null
                )
            }
        }

        if (showMute) {
            OutlinedButton(
                modifier = actionModifier,
                contentPadding = PaddingValues(0.dp),
                enabled = isMuted != null,
                onClick = onToggleMute
            ) {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.primary,
                    imageVector = if(isMuted == true) {
                        notifications_off_icon
                    } else notifications_icon,
                    contentDescription = if (isMuted == true) {
                        "Unmute notifications"
                    } else {
                        "Mute notifications"
                    }
                )
            }
        }

        // An empty menu must not leave a dead "more" button behind, which is
        // what a service account (no blocking) used to get.
        val hasMenuOptions = canBlock || canManageContacts

        if (hasMenuOptions) {
            Box {
                OutlinedButton(
                    modifier = actionModifier,
                    contentPadding = PaddingValues(0.dp),
                    onClick = { menuExpanded = true }
                ) {
                    Icon(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        tint = MaterialTheme.colorScheme.primary,
                        imageVector = more_vertical_icon,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    if (canManageContacts) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = edit_icon,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(if (isContact) "Edit contact" else "Add contact")
                            },
                            onClick = {
                                menuExpanded = false
                                onEditContact()
                            }
                        )

                        if (isContact) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = delete_icon,
                                        contentDescription = null
                                    )
                                },
                                text = { Text("Remove contact") },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveContact()
                                }
                            )
                        }
                    }

                    if (canBlock) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = block_icon,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(if (isBlocked) "Unblock user" else "Block user")
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleBlock()
                            }
                        )
                    }
                }
            }
        }
    }
}

private val ProfileUrlPattern = Regex(
    """(?:https?://|www\.|t\.me/)[^\s]+""",
    RegexOption.IGNORE_CASE
)

/** Renders profile text, turning every URL in it into a clickable link. */
@Composable
private fun ProfileLinkText(
    text: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary

    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0

            for (match in ProfileUrlPattern.findAll(text)) {
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '{', '}')

                if (url.isEmpty()) continue

                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }

                val target = if (url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true)
                ) {
                    url
                } else {
                    "https://$url"
                }

                withLink(
                    LinkAnnotation.Url(
                        url = target,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = {
                            if (com.mrboomdev.scrollix.DeepLinkHandler.isTelegramLink(target)) {
                                com.mrboomdev.scrollix.DeepLinkHandler.publish(target)
                            } else {
                                uriHandler.openUri(target)
                            }
                        }
                    )
                ) {
                    append(url)
                }

                cursor = match.range.first + url.length
            }

            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Text(
        modifier = modifier,
        text = annotated,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ProfileScreenInfo(
    profile: UserProfileDetails?,
    fallbackStatus: String? = null
) {
    val layoutInfo = currentAdaptiveLayoutInfo()
    
    Column {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.secondary,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium
        ) {
            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                ProfileLinkText(
                    text = bio,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            profile?.description?.takeIf { it.isNotBlank() }?.let { description ->
                ProfileLinkText(
                    text = description,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            FlowRow(
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val textStyle = LocalTextStyle.current
                val inlinePlaceholder = remember(textStyle) {
                    Placeholder(
                        width = textStyle.fontSize * 1.4,
                        height = textStyle.fontSize * 1.4,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                }

                val status = fallbackStatus?.takeIf { it.isNotBlank() }
                    ?: profile?.status?.takeIf { it.isNotBlank() }
                val isOnline = profile?.isOnline == true || status == "online"

                status?.let { statusText ->
                    Text(
                        color = if (isOnline) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        inlineContent = remember(textStyle, isOnline) {
                            mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                                if (isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Icon(
                                        imageVector = schedule_icon,
                                        contentDescription = null
                                    )
                                }
                            })
                        },

                        text = buildAnnotatedString {
                            appendInlineContent("icon")
                            append(" ")
                            append(statusText)
                        }
                    )
                }

                profile?.birthdate?.takeIf { it.isNotBlank() }?.let { birthdate ->
                    Text(
                        inlineContent = remember(textStyle) {
                            mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                                Icon(
                                    imageVector = cake_icon,
                                    contentDescription = null
                                )
                            })
                        },

                        text = buildAnnotatedString {
                            appendInlineContent("icon")
                            append(" ")
                            append(birthdate)
                        }
                    )
                }

                profile?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                    Text(
                        inlineContent = remember(textStyle) {
                            mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                                Icon(
                                    imageVector = call_icon,
                                    contentDescription = null
                                )
                            })
                        },

                        text = buildAnnotatedString {
                            appendInlineContent("icon")
                            append(" ")
                            append(formatPhoneNumber(phone))
                        }
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
    ownAccount: Boolean = false,
    onOpenChannelProfile: (String) -> Unit = {},
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
                                onClick = { onOpenChannelProfile(channel.providerDialogId) },
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
                        onClick = { onOpenChannelProfile(channel.providerDialogId) },
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
                        onOpenChannelProfile(group.providerDialogId)
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

@OptIn(ExperimentalGridApi::class)
@Composable
private fun TracksSection(
    tracks: List<ProfileTrack>,
    session: AccountSession?
) {
    val player = remember { createVoicePlayer() }
    val scope = rememberCoroutineScope()
    var playingFileId by remember { mutableStateOf<Int?>(null) }
    var loadingFileId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(player) {
        onDispose { player.stop() }
    }

    fun toggle(track: ProfileTrack) {
        val fileId = track.media.fileId ?: return

        if (loadingFileId != null) return

        if (playingFileId == fileId) {
            player.stop()
            playingFileId = null
            return
        }

        player.stop()
        playingFileId = null
        loadingFileId = fileId

        scope.launch {
            val bytes = session?.loadFile(fileId)?.getOrNull()
            loadingFileId = null

            if (bytes != null) {
                playingFileId = fileId

                val started = player.play(bytes, 0L) {
                    if (playingFileId == fileId) {
                        playingFileId = null
                    }
                }

                if (!started) {
                    playingFileId = null
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        )

        val textStyle = MaterialTheme.typography.bodyMedium

        val inlinePlaceholder = remember(textStyle) {
            Placeholder(
                width = textStyle.fontSize * 1.4,
                height = textStyle.fontSize * 1.4,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        }

        Text(
            style = textStyle,
            color = MaterialTheme.colorScheme.secondary,

            inlineContent = remember(textStyle) {
                mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                    Icon(
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = music_note_icon,
                        contentDescription = null
                    )
                })
            },

            text = buildAnnotatedString {
                appendInlineContent("icon")
                append(" Pinned tracks")
            }
        )

        AdaptiveGrid(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            minColumnWidth = 250.dp,
            gap = 8.dp
        ) {
            for (track in tracks) {
                val fileId = track.media.fileId

                ProfileTrackCard(
                    track = track,
                    isPlaying = fileId != null && playingFileId == fileId,
                    isLoading = fileId != null && loadingFileId == fileId,
                    onToggle = { toggle(track) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileTrackCard(
    track: ProfileTrack,
    isPlaying: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    var cover by remember(track.media.minithumbnail) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(track.media.minithumbnail) {
        cover = track.media.minithumbnail?.let { bytes ->
            withContext(Dispatchers.Default) {
                decodeImageBitmap(bytes)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = cover

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = listOfNotNull(
                    track.performer?.takeIf { it.isNotBlank() },
                    formatTrackDuration(track.durationSeconds).takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                FilledTonalIconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onToggle
                ) {
                    if(isLoading) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = if(isPlaying) {
                                pause_icon_filled
                            } else play_icon_filled,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

private fun formatTrackDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)

    if (safeSeconds == 0) return ""

    val minutes = safeSeconds / 60
    val remaining = safeSeconds % 60

    return "$minutes:${remaining.toString().padStart(2, '0')}"
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

private fun formatCount(count: Int): String {
    return count.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}
