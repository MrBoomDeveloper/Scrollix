package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.flow.map
import com.mrboomdev.scrollix.data.model.Avatar
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageLinkPreview
import com.mrboomdev.scrollix.data.model.MessageLocationInfo
import com.mrboomdev.scrollix.data.model.MessageMedia
import com.mrboomdev.scrollix.data.model.MessageMediaKind
import com.mrboomdev.scrollix.data.model.MessageGiftInfo
import com.mrboomdev.scrollix.data.model.MessageGiftKind
import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionReactor
import com.mrboomdev.scrollix.data.model.MessageReply
import com.mrboomdev.scrollix.data.model.MessageRichBlock
import com.mrboomdev.scrollix.data.model.MessageRichTextStyle
import com.mrboomdev.scrollix.data.model.MessageScheduling
import com.mrboomdev.scrollix.data.model.MessageSendState
import com.mrboomdev.scrollix.data.model.MessageReplyKeyboard
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButton
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButtonKind
import com.mrboomdev.scrollix.data.model.RichTextSpan

/** A non-null result marks the message as waiting in the schedule queue. */
internal fun mapMessageScheduling(state: MessageSchedulingState?): MessageScheduling? {
    return when (state) {
        is MessageSchedulingStateSendAtDate -> MessageScheduling.At(
            sendDateMillis = state.sendDate.toLong() * 1000L
        )

        is MessageSchedulingStateSendWhenOnline -> MessageScheduling.WhenOnline
        else -> null
    }
}

/** Maps a shared point, live shares included, into the UI model. */
internal fun TelegramSession.mapLocation(content: MessageContent): MessageLocationInfo? {
    return when (content) {
        is MessageLocation -> MessageLocationInfo(
            latitude = content.location.latitude,
            longitude = content.location.longitude
        )

        is MessageLiveLocation -> MessageLocationInfo(
            latitude = content.location.location.latitude,
            longitude = content.location.location.longitude,
            isLive = true,
            expiresInSeconds = content.expiresIn
        )

        else -> null
    }
}

internal fun TelegramSession.mapMessage(message: Message): ChatMessage {
    val senderUser = if (message.isOutgoing) {
        null
    } else {
        (message.senderId as? MessageSenderUser)?.let { sender ->
            _users.value[sender.userId]
        }
    }

    val senderName = if (message.isOutgoing) {
        null
    } else {
        when (val sender = message.senderId) {
            is MessageSenderUser -> senderUser?.let(::userDisplayName)
            is MessageSenderChat -> _chats.value[sender.chatId]?.title
        }
    }
    val isChannel = (_chats.value[message.chatId]?.type as? ChatTypeSupergroup)
        ?.isChannel == true
    val (forwardedFromName, forwardedFromAvatar) = forwardOrigin(
        message.forwardInfo?.origin
    )

    return ChatMessage(
        providerId = providerId,
        accountId = accountId,
        providerDialogId = message.chatId.toString(),
        messageId = message.id,
        senderName = senderName,
        senderAvatar = senderUser?.let(::userAvatar),
        senderEmojiStatusId = emojiStatusId(senderUser?.emojiStatus),
        senderId = when(val sender = message.senderId) {
            is MessageSenderUser -> sender.userId
            is MessageSenderChat -> sender.chatId
        },
        forwardedFromName = forwardedFromName,
        forwardedFromAvatar = forwardedFromAvatar,
        text = messageText(
            content = message.content,
            isOutgoing = message.isOutgoing,
            senderName = senderName,
            isChannel = isChannel
        ),
        timestamp = message.date.toLong() * 1000L,
        editDate = message.editDate.toLong() * 1000L,
        // TDLib reports the time left before the chat's auto-delete timer
        // removes the message, not a deadline, so the deadline is fixed here.
        autoDeleteAt = if (message.autoDeleteIn > 0) {
            com.mrboomdev.scrollix.data.storage.currentTimeMillis() +
                message.autoDeleteIn.toLong() * 1000L
        } else {
            0L
        },
        isOutgoing = message.isOutgoing,
        isService = isServiceContent(message.content),
        media = mapMedia(message.content),
        mediaAlbumId = message.mediaAlbumId.takeIf { it != 0L },
        poll = mapPoll(message.content),
        checklist = mapChecklistWithNames(message.content),
        gift = mapGift(message.content, message.isOutgoing),
        invoice = mapInvoice(message.content),
        customEmojis = messageCustomEmojis(message.content),
        formatting = messageFormatting(message.content),
        scheduling = mapMessageScheduling(message.schedulingState),
        richBlocks = mapRichBlocks(message.content),
        linkPreview = mapLinkPreview(message.content),
        location = mapLocation(message.content),
        replyTo = (message.replyTo as? MessageReplyToMessage)?.let { reply ->
            MessageReply(
                messageId = reply.messageId,
                text = reply.quote?.text?.text.orEmpty()
            )
        },
        // Forum topics carry their id on the message itself, so the dialog
        // can scope history and sending to the topic it belongs to.
        messageThreadId = when (val topic = message.topicId) {
            is MessageTopicForum -> topic.forumTopicId.toLong()
            else -> 0L
        },
        replyCount = message.interactionInfo?.replyInfo?.replyCount ?: 0,
        keyboard = mapKeyboard(message.replyMarkup, message.senderId),
        replyKeyboard = mapReplyKeyboard(message.replyMarkup, message.senderId),
        reactions = mapReactions(message.interactionInfo),
        viewCount = message.interactionInfo?.viewCount ?: 0,
        shareCount = message.interactionInfo?.forwardCount ?: 0,
        // This TDLib build exposes chat permissions instead of per-message
        // rights: the author deletes own messages, moderators delete any,
        // and protected content disables forwarding for everyone.
        canBeDeleted = message.isOutgoing,
        canBeDeletedForEveryone = message.isOutgoing,
        canBeForwarded = message.canBeSaved,
        sendState = when (message.sendingState) {
            is MessageSendingStatePending -> MessageSendState.Sending
            is MessageSendingStateFailed -> MessageSendState.Failed
            else -> MessageSendState.Sent
        },
        isRead = message.isOutgoing && message.id <= (
            _chats.value[message.chatId]?.lastReadOutboxMessageId ?: 0L
        )
    )
}

/**
 * Inline keyboards keep every button this client can act on: links, bot mini
 * apps, callback buttons, copy-to-clipboard buttons and user profiles.
 * Payment, game and login buttons are skipped instead of rendering dead
 * buttons, and a disabled button is kept as a hint that the bot turned it off.
 */
internal fun TelegramSession.mapKeyboard(
    markup: ReplyMarkup?,
    senderId: MessageSender
): List<List<MessageButton>> {
    val inline = markup as? ReplyMarkupInlineKeyboard ?: return emptyList()
    val botUserId = (senderId as? MessageSenderUser)?.userId

    return inline.rows
        .map { row ->
            row.mapNotNull { button ->
                when (val type = button.type) {
                    is InlineKeyboardButtonTypeUrl -> MessageButton(
                        text = button.text,
                        url = type.url
                    )

                    is InlineKeyboardButtonTypeWebApp -> MessageButton(
                        text = button.text,
                        webAppUrl = type.url,
                        botUserId = botUserId
                    )

                    is InlineKeyboardButtonTypeCallback -> MessageButton(
                        text = button.text,
                        callbackData = type.data.decodeToString(),
                        botUserId = botUserId
                    )

                    // A password callback still reaches the bot; the password
                    // prompt itself is out of scope, so it presses as a plain
                    // callback and the bot answers with its own error.
                    is InlineKeyboardButtonTypeCallbackWithPassword -> MessageButton(
                        text = button.text,
                        callbackData = type.data.decodeToString(),
                        botUserId = botUserId
                    )

                    is InlineKeyboardButtonTypeCopyText -> MessageButton(
                        text = button.text,
                        copyText = type.text
                    )

                    is InlineKeyboardButtonTypeUser -> MessageButton(
                        text = button.text,
                        userId = type.userId
                    )

                    is InlineKeyboardButtonTypeDisabled -> MessageButton(
                        text = button.text,
                        isDisabled = true
                    )

                    else -> null
                }
            }
        }
        .filter { row -> row.isNotEmpty() }
}

/**
 * The reply keyboard a bot installs below the composer. Buttons that ask for
 * a chat, users or a managed bot cannot be answered by this client, so they
 * keep their label and behave like plain text buttons.
 */
internal fun TelegramSession.mapReplyKeyboard(
    markup: ReplyMarkup?,
    senderId: MessageSender
): MessageReplyKeyboard? {
    // A removed keyboard has to be reported as an empty one: the newest markup
    // of the chat decides, so a message without a keyboard leaves the previous
    // one in place while an explicit removal hides it.
    if (markup is ReplyMarkupRemoveKeyboard) return MessageReplyKeyboard()

    val keyboard = markup as? ReplyMarkupShowKeyboard ?: return null
    val botUserId = (senderId as? MessageSenderUser)?.userId

    val rows = keyboard.rows
        .map { row ->
            row.map { button ->
                when (val type = button.type) {
                    is KeyboardButtonTypeText -> ReplyKeyboardButton(text = button.text)

                    is KeyboardButtonTypeRequestLocation -> ReplyKeyboardButton(
                        text = button.text,
                        kind = ReplyKeyboardButtonKind.RequestLocation
                    )

                    is KeyboardButtonTypeRequestPhoneNumber -> ReplyKeyboardButton(
                        text = button.text,
                        kind = ReplyKeyboardButtonKind.RequestPhoneNumber
                    )

                    is KeyboardButtonTypeRequestPoll -> ReplyKeyboardButton(
                        text = button.text,
                        kind = ReplyKeyboardButtonKind.RequestPoll
                    )

                    is KeyboardButtonTypeWebApp -> ReplyKeyboardButton(
                        text = button.text,
                        kind = ReplyKeyboardButtonKind.WebApp,
                        webAppUrl = type.url,
                        botUserId = botUserId
                    )

                    else -> ReplyKeyboardButton(
                        text = button.text,
                        kind = ReplyKeyboardButtonKind.Unsupported
                    )
                }
            }
        }
        .filter { row -> row.isNotEmpty() }

    return MessageReplyKeyboard(
        rows = rows,
        placeholder = keyboard.inputFieldPlaceholder,
        isPersistent = keyboard.isPersistent,
        isOneTime = keyboard.oneTime
    )
}

/** Resolves the chat or user a forwarded message originally came from. */
internal fun TelegramSession.forwardOrigin(origin: MessageOrigin?): Pair<String?, Avatar?> {
    return when (origin) {
        is MessageOriginUser -> {
            val user = _users.value[origin.senderUserId]
            if (user == null) requestUser(origin.senderUserId)

            val name = user?.let(::userDisplayName) ?: "Telegram user"
            name to (user?.let(::userAvatar) ?: Avatar.fromName(name))
        }

        is MessageOriginHiddenUser -> {
            val name = origin.senderName.ifBlank { "Telegram user" }
            name to Avatar.fromName(name)
        }

        is MessageOriginChat -> {
            val entry = _chats.value[origin.senderChatId]
            val name = entry?.title?.takeIf { it.isNotBlank() } ?: "Chat"
            name to (entry?.let { chatAvatar(it.title, it.photo) } ?: Avatar.fromName(name))
        }

        is MessageOriginChannel -> {
            val entry = _chats.value[origin.chatId]
            val name = entry?.title?.takeIf { it.isNotBlank() } ?: "Channel"
            name to (entry?.let { chatAvatar(it.title, it.photo) } ?: Avatar.fromName(name))
        }

        else -> null to null
    }
}

/** Maps the server-generated link preview of a text message. */
internal fun TelegramSession.mapLinkPreview(content: MessageContent): MessageLinkPreview? {
    val preview = (content as? MessageText)?.linkPreview ?: return null
    if (preview.url.isBlank()) return null

    return MessageLinkPreview(
        url = preview.url,
        displayUrl = preview.displayUrl,
        siteName = preview.siteName,
        title = preview.title,
        description = preview.description.text,
        showAboveText = preview.showAboveText
    )
}

internal fun TelegramSession.mapRichBlocks(content: MessageContent): List<MessageRichBlock> {
    val rich = (content as? MessageRichMessage)?.message ?: return emptyList()
    return rich.blocks.flatMap { block -> mapRichBlock(block) }
}

internal fun TelegramSession.mapRichBlock(block: PageBlock): List<MessageRichBlock> {
    return when (block) {
        is PageBlockTitle -> listOf(richTextBlock(block.title, MessageRichTextStyle.Title))
        is PageBlockSubtitle -> listOf(richTextBlock(block.subtitle, MessageRichTextStyle.Subheader))
        is PageBlockHeader -> listOf(richTextBlock(block.header, MessageRichTextStyle.Header))
        is PageBlockSubheader -> listOf(richTextBlock(block.subheader, MessageRichTextStyle.Subheader))
        is PageBlockParagraph -> listOf(richTextBlock(block.text, MessageRichTextStyle.Paragraph))
        is PageBlockPreformatted -> listOf(richTextBlock(block.text, MessageRichTextStyle.Preformatted))
        is PageBlockFooter -> listOf(richTextBlock(block.footer, MessageRichTextStyle.Footer))
        is PageBlockAuthorDate -> listOf(richTextBlock(block.author, MessageRichTextStyle.Footer))
        is PageBlockDivider -> listOf(MessageRichBlock.Divider)

        is PageBlockBlockQuote -> buildList {
            block.blocks.flatMap { child -> mapRichBlock(child) }.forEach { child ->
                add(
                    if (child is MessageRichBlock.Text) {
                        child.copy(style = MessageRichTextStyle.Quote)
                    } else {
                        child
                    }
                )
            }

            val credit = richTextBlock(block.credit, MessageRichTextStyle.Footer)
            if (credit.text.isNotBlank()) add(credit)
        }

        is PageBlockDetails -> buildList {
            add(richTextBlock(block.header, MessageRichTextStyle.Subheader))
            block.blocks.flatMap { child -> mapRichBlock(child) }.forEach(::add)
        }

        is PageBlockList -> block.items.flatMap { item ->
            val prefix = if (item.hasCheckbox) {
                if (item.value != 0) "☑" else "☐"
            } else {
                item.label.ifBlank { "•" }
            }

            val children = item.blocks.flatMap { child -> mapRichBlock(child) }
            val firstTextIndex = children.indexOfFirst { child -> child is MessageRichBlock.Text }

            when {
                children.isEmpty() -> listOf(MessageRichBlock.Text(text = "", bullet = prefix))
                firstTextIndex < 0 -> children
                else -> children.mapIndexed { index, child ->
                    if (index == firstTextIndex && child is MessageRichBlock.Text) {
                        child.copy(bullet = prefix)
                    } else {
                        child
                    }
                }
            }
        }

        is PageBlockCollage -> block.blocks.flatMap { child -> mapRichBlock(child) }
        is PageBlockSlideshow -> block.blocks.flatMap { child -> mapRichBlock(child) }
        is PageBlockCover -> mapRichBlock(block.cover)

        is PageBlockPhoto -> block.photo
            ?.let { photo -> listOf(richMediaBlock(block.caption, richPhotoMedia(photo))) }
            .orEmpty()

        is PageBlockVideo -> block.video
            ?.let { video -> listOf(richMediaBlock(block.caption, richVideoMedia(video))) }
            .orEmpty()

        is PageBlockAnimation -> block.animation
            ?.let { animation -> listOf(richMediaBlock(block.caption, richAnimationMedia(animation))) }
            .orEmpty()

        else -> emptyList()
    }
}

internal fun TelegramSession.richMediaBlock(caption: PageBlockCaption?, media: MessageMedia): MessageRichBlock {
    return MessageRichBlock.Media(
        media = media,
        caption = richTextPlain(caption?.text).trim()
    )
}

internal fun TelegramSession.richPhotoMedia(photo: Photo): MessageMedia {
    val size = photo.previewSize()

    return MessageMedia(
        kind = MessageMediaKind.Photo,
        previewFileId = size?.photo?.id,
        fileId = photo.sizes.maxByOrNull { it.photo.size }?.photo?.id,
        fileName = "Photo",
        mimeType = "image/jpeg",
        size = size?.photo?.size ?: 0,
        width = size?.width ?: 0,
        height = size?.height ?: 0,
        minithumbnail = photo.minithumbnail?.data
    )
}

internal fun TelegramSession.richVideoMedia(video: Video): MessageMedia {
    return MessageMedia(
        kind = MessageMediaKind.Video,
        previewFileId = video.thumbnail?.file?.id,
        fileId = video.video.id,
        fileName = video.fileName.ifBlank { "Video" },
        mimeType = video.mimeType,
        size = video.video.size,
        width = video.width,
        height = video.height,
        durationSeconds = video.duration,
        minithumbnail = video.minithumbnail?.data
    )
}

internal fun TelegramSession.richAnimationMedia(animation: Animation): MessageMedia {
    return MessageMedia(
        kind = MessageMediaKind.Animation,
        previewFileId = animation.thumbnail?.file?.id,
        fileId = animation.animation.id,
        fileName = animation.fileName.ifBlank { "GIF" },
        mimeType = animation.mimeType,
        size = animation.animation.size,
        width = animation.width,
        height = animation.height,
        minithumbnail = animation.minithumbnail?.data
    )
}

internal fun TelegramSession.richTextBlock(text: RichText?, style: MessageRichTextStyle): MessageRichBlock.Text {
    val out = StringBuilder()
    val spans = mutableListOf<RichTextSpan>()

    collectRichText(text, out, spans)

    return MessageRichBlock.Text(text = out.toString(), style = style, spans = spans)
}

internal fun TelegramSession.collectRichText(
    text: RichText?,
    out: StringBuilder,
    spans: MutableList<RichTextSpan>,
    style: RichTextStyleState = RichTextStyleState()
) {
    when (text) {
        null -> return

        is RichTexts -> {
            text.texts.forEach { child -> collectRichText(child, out, spans, style) }
            return
        }

        is RichTextBold -> {
            collectRichText(text.text, out, spans, style.copy(bold = true))
            return
        }

        is RichTextItalic -> {
            collectRichText(text.text, out, spans, style.copy(italic = true))
            return
        }

        is RichTextUnderline -> {
            collectRichText(text.text, out, spans, style.copy(underline = true))
            return
        }

        is RichTextStrikethrough -> {
            collectRichText(text.text, out, spans, style.copy(strikethrough = true))
            return
        }

        is RichTextFixed -> {
            collectRichText(text.text, out, spans, style.copy(monospace = true))
            return
        }

        is RichTextSpoiler -> {
            collectRichText(text.text, out, spans, style.copy(spoiler = true))
            return
        }

        is RichTextMarked -> {
            collectRichText(text.text, out, spans, style.copy(bold = true))
            return
        }

        is RichTextSubscript -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextSuperscript -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextUrl -> {
            collectRichText(text.text, out, spans, style.copy(url = text.url))
            return
        }

        is RichTextAnchorLink -> {
            collectRichText(text.text, out, spans, style.copy(url = text.url))
            return
        }

        is RichTextReferenceLink -> {
            collectRichText(text.text, out, spans, style.copy(url = text.url))
            return
        }

        is RichTextReference -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextDiff -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextDateTime -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextMentionName -> {
            collectRichText(text.text, out, spans, style)
            return
        }

        is RichTextAnchor -> return
        is RichTextIcon -> return

        is RichTextCustomEmoji -> {
            val start = out.length
            out.append(text.alternativeText.ifBlank { " " })

            if (out.length > start) {
                spans += RichTextSpan(
                    start = start,
                    end = out.length,
                    isBold = style.bold,
                    isItalic = style.italic,
                    isUnderlined = style.underline,
                    isStrikethrough = style.strikethrough,
                    isMonospace = style.monospace,
                    isSpoiler = style.spoiler,
                    url = style.url
                )
            }

            return
        }

        is RichTextButton -> {
            val start = out.length
            out.append(richTextPlain(text.button.text))

            if (out.length > start) {
                spans += RichTextSpan(
                    start = start,
                    end = out.length,
                    isBold = true,
                    url = style.url
                )
            }

            return
        }

        is RichTextMathematicalExpression -> {
            val start = out.length
            out.append(text.expression)

            if (out.length > start) {
                spans += RichTextSpan(
                    start = start,
                    end = out.length,
                    isMonospace = true,
                    url = style.url
                )
            }

            return
        }

        else -> Unit
    }

    val start = out.length
    out.append(richTextPlain(text))
    val end = out.length

    if (end <= start) return

    if (!style.bold && !style.italic && !style.underline && !style.strikethrough &&
        !style.monospace && !style.spoiler && style.url == null
    ) {
        return
    }

    spans += RichTextSpan(
        start = start,
        end = end,
        isBold = style.bold,
        isItalic = style.italic,
        isUnderlined = style.underline,
        isStrikethrough = style.strikethrough,
        isMonospace = style.monospace,
        isSpoiler = style.spoiler,
        url = style.url
    )
}

internal fun TelegramSession.richTextPlain(text: RichText?): String {
    return when (text) {
        null -> ""
        is RichTextPlain -> text.text
        is RichTexts -> text.texts.joinToString("") { child -> richTextPlain(child) }
        is RichTextBankCardNumber -> richTextPlain(text.text)
        is RichTextBotCommand -> richTextPlain(text.text)
        is RichTextCashtag -> richTextPlain(text.text)
        is RichTextEmailAddress -> richTextPlain(text.text)
        is RichTextHashtag -> richTextPlain(text.text)
        is RichTextMention -> richTextPlain(text.text)
        is RichTextMentionName -> richTextPlain(text.text)
        is RichTextPhoneNumber -> richTextPlain(text.text)
        is RichTextTonAddress -> richTextPlain(text.text)
        is RichTextDateTime -> richTextPlain(text.text)
        is RichTextMathematicalExpression -> text.expression
        is RichTextButton -> richTextPlain(text.button.text)
        is RichTextCustomEmoji -> text.alternativeText
        is RichTextReference -> richTextPlain(text.text)
        is RichTextReferenceLink -> richTextPlain(text.text)
        is RichTextAnchorLink -> richTextPlain(text.text)
        is RichTextUrl -> richTextPlain(text.text)
        is RichTextBold -> richTextPlain(text.text)
        is RichTextItalic -> richTextPlain(text.text)
        is RichTextUnderline -> richTextPlain(text.text)
        is RichTextStrikethrough -> richTextPlain(text.text)
        is RichTextFixed -> richTextPlain(text.text)
        is RichTextMarked -> richTextPlain(text.text)
        is RichTextSpoiler -> richTextPlain(text.text)
        is RichTextSubscript -> richTextPlain(text.text)
        is RichTextSuperscript -> richTextPlain(text.text)
        is RichTextDiff -> richTextPlain(text.text)
        else -> ""
    }
}

internal fun TelegramSession.richPlainText(rich: RichMessage?): String {
    return rich?.blocks.orEmpty()
        .joinToString("\n") { block -> pageBlockPlainText(block) }
        .trim()
}

internal fun TelegramSession.pageBlockPlainText(block: PageBlock): String {
    return when (block) {
        is PageBlockTitle -> richTextPlain(block.title)
        is PageBlockSubtitle -> richTextPlain(block.subtitle)
        is PageBlockHeader -> richTextPlain(block.header)
        is PageBlockSubheader -> richTextPlain(block.subheader)
        is PageBlockParagraph -> richTextPlain(block.text)
        is PageBlockPreformatted -> richTextPlain(block.text)
        is PageBlockFooter -> richTextPlain(block.footer)
        is PageBlockAuthorDate -> richTextPlain(block.author)
        is PageBlockCover -> pageBlockPlainText(block.cover)

        is PageBlockBlockQuote -> block.blocks
            .joinToString("\n") { child -> pageBlockPlainText(child) }

        is PageBlockDetails -> block.blocks
            .joinToString("\n") { child -> pageBlockPlainText(child) }

        is PageBlockCollage -> block.blocks
            .joinToString("\n") { child -> pageBlockPlainText(child) }

        is PageBlockSlideshow -> block.blocks
            .joinToString("\n") { child -> pageBlockPlainText(child) }

        is PageBlockList -> block.items.joinToString("\n") { item ->
            val prefix = if (item.hasCheckbox) {
                if (item.value != 0) "☑" else "☐"
            } else {
                item.label.ifBlank { "•" }
            }

            val text = item.blocks
                .joinToString(" ") { child -> pageBlockPlainText(child) }
                .trim()

            "$prefix $text".trim()
        }

        is PageBlockPhoto -> richTextPlain(block.caption?.text)
        is PageBlockVideo -> richTextPlain(block.caption?.text)
        is PageBlockAnimation -> richTextPlain(block.caption?.text)
        else -> ""
    }
}

internal fun TelegramSession.supergroupChatCreateText(content: MessageSupergroupChatCreate): String {
    val title = content.title.trim()

    return if (title.isEmpty()) {
        "Supergroup created"
    } else {
        "Supergroup \"$title\" created"
    }
}

/**
 * Gift and giveaway messages keep their text for the chat list, while the
 * bubble renders a card from this data instead.
 */
internal fun TelegramSession.mapGift(
    content: MessageContent,
    isOutgoing: Boolean
): MessageGiftInfo? {
    return when (content) {
        is MessageGift -> MessageGiftInfo(
            kind = MessageGiftKind.Gift,
            title = "Gift",
            subtitle = content.text.text.ifBlank {
                if (isOutgoing) "You sent a gift" else "You've got a gift"
            },
            starCount = content.gift.starCount,
            isUpgraded = content.wasUpgraded,
            isRefunded = content.wasRefunded
        )

        is dev.g000sha256.tdl.dto.MessageGiveaway -> MessageGiftInfo(
            kind = MessageGiftKind.Giveaway,
            title = "Giveaway",
            subtitle = giveawayPrizeText(content.prize)
                .ifBlank { "${content.winnerCount} winners" },
            starCount = giveawayPrizeStars(content.prize),
            isRefunded = (content.prize as? dev.g000sha256.tdl.dto.GiveawayPrizeStars) == null &&
                content.winnerCount == 0
        )

        is dev.g000sha256.tdl.dto.MessageGiveawayWinners -> MessageGiftInfo(
            kind = MessageGiftKind.GiveawayWinners,
            title = "Giveaway results",
            subtitle = content.prizeDescription.ifBlank {
                "${content.winnerCount} winners"
            },
            starCount = giveawayPrizeStars(content.prize),
            isRefunded = content.wasRefunded
        )

        is dev.g000sha256.tdl.dto.MessageGiveawayCompleted -> MessageGiftInfo(
            kind = MessageGiftKind.GiveawayCompleted,
            title = "Giveaway completed",
            subtitle = "${content.winnerCount} winners",
            isRefunded = content.unclaimedPrizeCount == content.winnerCount
        )

        else -> null
    }
}

private fun giveawayPrizeStars(prize: dev.g000sha256.tdl.dto.GiveawayPrize?): Long {
    return (prize as? dev.g000sha256.tdl.dto.GiveawayPrizeStars)?.starCount ?: 0L
}

private fun giveawayPrizeText(prize: dev.g000sha256.tdl.dto.GiveawayPrize?): String {
    return when (prize) {
        is dev.g000sha256.tdl.dto.GiveawayPrizeStars -> "${prize.starCount} stars"
        is dev.g000sha256.tdl.dto.GiveawayPrizePremium -> "${prize.monthCount} months of Premium"
        else -> ""
    }
}

internal fun TelegramSession.messageText(
    content: MessageContent,
    isOutgoing: Boolean = false,
    senderName: String? = null,
    isChannel: Boolean = false
): String {
    return when (content) {
        is MessageText -> content.text.text
        is MessagePhoto -> content.caption.text
        is MessageDocument -> content.caption.text
        is MessageVideo -> content.caption.text
        is MessageSticker -> ""
        is MessageAnimatedEmoji -> ""
        is MessageGift -> serviceMessageText(
            content = content,
            isOutgoing = isOutgoing,
            senderName = senderName,
            isChannel = isChannel
        )
        is MessageVideoNote -> ""
        is MessageVoiceNote -> ""
        is MessageAudio -> content.caption.text
        is MessageAnimation -> content.caption.text
        is MessagePoll -> ""
        // The title and tasks render as a card, not as message text.
        is MessageChecklist -> ""
        is MessageLocation, is MessageLiveLocation -> ""
        is MessageSupergroupChatCreate -> supergroupChatCreateText(content)
        is MessageRichMessage -> richPlainText(content.message)
        else -> serviceMessageText(
            content = content,
            isOutgoing = isOutgoing,
            senderName = senderName,
            isChannel = isChannel
        )
    }
}

internal fun TelegramSession.isServiceContent(content: MessageContent): Boolean {
    return when (content) {
        is MessageText,
        is MessagePhoto,
        is MessageDocument,
        is MessageVideo,
        is MessageSticker,
        is MessageAnimatedEmoji,
        is MessageVideoNote,
        is MessageVoiceNote,
        is MessageAudio,
        is MessageAnimation,
        is MessagePoll,
        is MessageLocation,
        is MessageLiveLocation,
        is MessageRichMessage -> false

        else -> true
    }
}

/**
 * Human-readable text for service messages. TDLib leaves many of these
 * without a text field, so the chat would otherwise show an empty bubble.
 */
internal fun TelegramSession.serviceMessageText(
    content: MessageContent,
    isOutgoing: Boolean = false,
    senderName: String? = null,
    isChannel: Boolean = false
): String {
    return when (content) {
        is MessageGift -> content.text.text.ifBlank {
            if (isOutgoing) "You sent a gift" else "You've got a gift"
        }

        is MessageChatSetBackground -> chatSetBackgroundText(
            content = content,
            isOutgoing = isOutgoing,
            senderName = senderName,
            isChannel = isChannel
        )

        is MessageChatChangePhoto -> "Chat photo changed"
        is MessageChatDeletePhoto -> "Chat photo deleted"

        is MessageChatChangeTitle -> content.title
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { title -> "Chat renamed to \"$title\"" }
            ?: "Chat title changed"

        is MessageChatAddMembers -> "Members added"
        is MessageChatDeleteMember -> "Member removed"
        is MessageChatJoinByLink,
        is MessageChatJoinByRequest -> "Member joined"

        is MessageScreenshotTaken -> if (isOutgoing) {
            "You took a screenshot"
        } else {
            "Screenshot taken"
        }

        is MessageContactRegistered -> "Contact joined Telegram"
        is MessagePinMessage -> "Message pinned"
        is MessageChatSetMessageAutoDeleteTime -> "Auto-delete time changed"
        is MessageChatSetTheme -> "Chat theme changed"

        is MessageBasicGroupChatCreate -> content.title
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { title -> "Group \"$title\" created" }
            ?: "Group created"

        is MessageChatUpgradeTo,
        is MessageChatUpgradeFrom -> "Group upgraded to supergroup"

        is MessageCustomServiceAction -> content.text.ifBlank {
            fallbackServiceText(content)
        }

        else -> fallbackServiceText(content)
    }
}

internal fun TelegramSession.chatSetBackgroundText(
    content: MessageChatSetBackground,
    isOutgoing: Boolean,
    senderName: String?,
    isChannel: Boolean
): String {
    if (content.onlyForSelf) {
        return if (isOutgoing) {
            "You set this wallpaper for yourself"
        } else {
            "Wallpaper set for you"
        }
    }

    val actor = when {
        isOutgoing -> "You"
        isChannel -> "This channel"
        !senderName.isNullOrBlank() -> senderName
        else -> "This chat"
    }

    return "$actor set the wallpaper"
}

/** Last-resort label for a service type not handled explicitly. */
internal fun TelegramSession.fallbackServiceText(content: MessageContent): String {
    val name = content::class.simpleName
        ?.removePrefix("Message")
        ?.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        ?.trim()
        .orEmpty()

    return name.ifBlank { "Service message" }
}

internal fun TelegramSession.mapReactions(interactionInfo: MessageInteractionInfo?): List<MessageReactionInfo> {
    return mapReactions(interactionInfo?.reactions?.reactions.orEmpty())
}

internal fun TelegramSession.mapReactions(reactions: Array<out MessageReaction>): List<MessageReactionInfo> {
    val users = _users.value

    return reactions.mapNotNull { reaction ->
        val emoji = when (val type = reaction.type) {
            is ReactionTypeEmoji -> type.emoji
            is ReactionTypePaid -> "⭐"
            else -> ""
        }
        val customEmojiId = (reaction.type as? ReactionTypeCustomEmoji)?.customEmojiId

        if (emoji.isBlank() && customEmojiId == null) return@mapNotNull null

        val reactorIds = reaction.recentSenderIds
            .mapNotNull { sender -> (sender as? MessageSenderUser)?.userId }

        reactorIds.forEach(::requestUser)

        MessageReactionInfo(
            emoji = emoji,
            customEmojiId = customEmojiId,
            count = reaction.totalCount,
            isChosen = reaction.isChosen,
            reactorIds = reactorIds,
            reactorAvatars = reactorIds.mapNotNull { userId ->
                users[userId]?.let(::userAvatar)
            },
            reactors = reactorIds.map { userId ->
                val user = users[userId]

                MessageReactionReactor(
                    id = userId,
                    name = user?.let(::userDisplayName).orEmpty(),
                    avatar = user?.let(::userAvatar)
                )
            }
        )
    }
}

/** Returns the signed in user id, asking TDLib if it isn't cached yet. */
internal suspend fun TelegramSession.resolveCurrentUserId(): Long? {
    currentUserId?.let { return it }

    return when (val result = client.getMe()) {
        is TdlResult.Success -> result.result.id.also { currentUserId = it }
        is TdlResult.Failure -> null
    }
}

internal fun ChatMemberStatus.isSubscribed(): Boolean = when (this) {
    is ChatMemberStatusMember,
    is ChatMemberStatusCreator,
    is ChatMemberStatusAdministrator -> true

    is ChatMemberStatusRestricted -> isMember

    else -> false
}
