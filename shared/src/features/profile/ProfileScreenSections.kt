package com.mrboomdev.scrollix.features.profile

import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.CommonGroup
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.data.model.UserProfileDetails
import com.mrboomdev.scrollix.data.storage.formatPhoneNumber
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.openDialer
import com.mrboomdev.scrollix.ui.components.AvatarImage
import com.mrboomdev.scrollix.ui.currentAdaptiveLayoutInfo
import com.mrboomdev.scrollix.ui.icons.block_icon
import com.mrboomdev.scrollix.ui.icons.cake_icon
import com.mrboomdev.scrollix.ui.icons.call_icon
import com.mrboomdev.scrollix.ui.icons.campaign_icon
import com.mrboomdev.scrollix.ui.icons.chat_bubble_icon
import com.mrboomdev.scrollix.ui.icons.delete_icon
import com.mrboomdev.scrollix.ui.icons.edit_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.more_vertical_icon
import com.mrboomdev.scrollix.ui.icons.notifications_icon
import com.mrboomdev.scrollix.ui.icons.notifications_off_icon
import com.mrboomdev.scrollix.ui.icons.schedule_icon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@Composable
internal fun PersonalChannelCard(
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
internal fun CommonGroupsSection(
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
internal fun ProfileScreenActions(
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

internal val ProfileUrlPattern = Regex(
    """(?:https?://|www\.|t\.me/)[^\s]+""",
    RegexOption.IGNORE_CASE
)

/** Renders profile text, turning every URL in it into a clickable link. */
@Composable
internal fun ProfileLinkText(
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
internal fun ProfileScreenInfo(
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
