package com.mrboomdev.scrollix.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.DialogSceneStrategy.Companion.dialog
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.api.TelegramProvider.displayName
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.isWideLayout
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.icons.arrow_back_icon
import com.mrboomdev.scrollix.ui.icons.cake_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.campaign_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.schedule_icon
import java.awt.AWTEventMulticaster.add
import java.awt.SystemColor.text

@Composable
private fun PersonalChannelCard(
    modifier: Modifier = Modifier,
    channel: com.mrboomdev.scrollix.data.model.PersonalChannel,
    onClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
            modifier = modifier
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
                                    if(count == 1) "subscriber" else "subscribers"
                        } ?: "Channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
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
private fun ProfileScreenActions(
    dialog: Dialog?,
    stretch: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val actionModifier = if(stretch) {
            Modifier.weight(1f)
        } else Modifier
        
        if(dialog?.kind != DialogKind.Channel) {
            OutlinedButton(
                onClick = {

                }
            ) {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    imageVector = chat_bubble_icon,
                    contentDescription = null
                )

                Spacer(Modifier.width(ButtonDefaults.IconSpacing))

                Text("Message")
            }
        } else {
            Button(
                onClick = {

                }
            ) {
                Text(
                    "Follow"
                )
            }
        }

        if(dialog?.kind == DialogKind.Direct) {
            OutlinedButton(
                modifier = actionModifier,
                contentPadding = PaddingValues(0.dp),
                onClick = {

                }
            ) {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    imageVector = call_icon,
                    contentDescription = null
                )
            }
        }

        OutlinedButton(
            modifier = actionModifier,
            contentPadding = PaddingValues(0.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                imageVector = notifications_icon,
                contentDescription = null
            )
        }

        OutlinedButton(
            modifier = actionModifier,
            contentPadding = PaddingValues(0.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                imageVector = more_vertical_icon,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun ProfileScreenInfo(
    profile: UserProfileDetails?
) {
    Column {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.secondary,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium
        ) {
            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = bio,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            profile?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = description,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
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

                profile?.status?.takeIf { it.isNotBlank() }?.let { status ->
                    Text(
                        inlineContent = remember(textStyle) {
                            mapOf("icon" to InlineTextContent(inlinePlaceholder) {
                                Icon(
                                    imageVector = schedule_icon,
                                    contentDescription = null
                                )
                            })
                        },

                        text = buildAnnotatedString {
                            appendInlineContent("icon")
                            append(" ")
                            append(status)
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
                            append(phone)
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
    onOpenChannelProfile: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val isWide = isWideLayout()
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
                .padding(24.dp)
        ) {
            val displayName = profile?.displayName ?: dialog?.title ?: "Profile"
            val avatar = profile?.avatar
                ?: dialog?.avatar
                ?: Avatar.fromName(displayName)
            val kind = profile?.kind ?: dialog?.kind
            
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    AvatarImage(
                        avatar = avatar,
                        size = 96.dp
                    )

                    AnimatedVisibility(isWide) {
                        ProfileScreenActions(
                            dialog = dialog,
                            stretch = false
                        )
                    }

                    AnimatedVisibility(isWide) {
                        profile?.personalChannel?.let { channel ->
                            PersonalChannelCard(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                channel = channel,
                                onClick = { onOpenChannelProfile(channel.providerDialogId) }
                            )
                        }
                    }
                }

                Row {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // TODO: Add provider icon
                }

                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.secondary,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium
                ) {
                    profile?.username?.takeIf { it.isNotBlank() }?.let { username ->
                        Text("@${username.removePrefix("@")}",)
                    }
                }
            }
            
            AnimatedVisibility(!isWide) {
                ProfileScreenActions(
                    dialog = dialog,
                    stretch = true
                )
            }
            
            ProfileScreenInfo(
                profile = profile
            )

            AnimatedVisibility(!isWide) {
                profile?.personalChannel?.let { channel ->
                    PersonalChannelCard(
                        modifier = Modifier.fillMaxWidth(),
                        channel = channel,
                        onClick = { onOpenChannelProfile(channel.providerDialogId) }
                    )
                }   
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
