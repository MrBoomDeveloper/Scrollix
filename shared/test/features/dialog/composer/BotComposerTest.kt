package com.mrboomdev.scrollix.features.dialog.composer

import com.mrboomdev.scrollix.data.model.BotCommandInfo
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.MessageButton
import com.mrboomdev.scrollix.data.model.MessageReplyKeyboard
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButton
import com.mrboomdev.scrollix.data.model.ReplyKeyboardButtonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotComposerTest {
    private fun message(
        id: Long,
        keyboard: MessageReplyKeyboard?
    ) = ChatMessage(
        providerId = "telegram",
        accountId = "account",
        providerDialogId = "1",
        messageId = id,
        senderId = 1,
        text = "text",
        timestamp = 0,
        isOutgoing = false,
        replyKeyboard = keyboard
    )

    private val keyboard = MessageReplyKeyboard(
        rows = listOf(
            listOf(
                ReplyKeyboardButton(text = "Yes"),
                ReplyKeyboardButton(text = "No")
            )
        )
    )

    @Test
    fun `the newest keyboard wins over an older one`() {
        val messages = listOf(
            message(1, keyboard),
            message(2, null),
            message(3, MessageReplyKeyboard(rows = listOf(listOf(ReplyKeyboardButton("Later")))))
        )

        assertEquals("Later", replyKeyboardOf(messages)?.rows?.first()?.first()?.text)
    }

    @Test
    fun `a message without a keyboard keeps the previous one`() {
        val messages = listOf(message(1, keyboard), message(2, null))

        assertEquals(keyboard, replyKeyboardOf(messages))
    }

    @Test
    fun `a removed keyboard hides the reply keyboard`() {
        val messages = listOf(message(1, keyboard), message(2, MessageReplyKeyboard()))

        assertNull(replyKeyboardOf(messages))
    }

    @Test
    fun `no bot message means no keyboard`() {
        assertNull(replyKeyboardOf(listOf(message(1, null))))
        assertNull(replyKeyboardOf(emptyList()))
    }

    @Test
    fun `a plain draft does not open the command menu`() {
        val commands = listOf(BotCommandInfo("start", "Start the bot"))

        assertTrue(filterBotCommands(commands, "hello").isEmpty())
        assertTrue(filterBotCommands(commands, "").isEmpty())
    }

    @Test
    fun `an empty slash lists the regular commands only`() {
        val commands = listOf(
            BotCommandInfo("start", "Start"),
            BotCommandInfo("debug", "Hidden", isEphemeral = true)
        )

        assertEquals(listOf("start"), filterBotCommands(commands, "/").map { it.command })
    }

    @Test
    fun `typing narrows the menu and reveals ephemeral commands`() {
        val commands = listOf(
            BotCommandInfo("start", "Start"),
            BotCommandInfo("settings", "Settings"),
            BotCommandInfo("debug", "Hidden", isEphemeral = true)
        )

        assertEquals(
            listOf("start", "settings"),
            filterBotCommands(commands, "/s").map { it.command }
        )

        assertEquals(listOf("debug"), filterBotCommands(commands, "/de").map { it.command })

        // Arguments after the command close the menu instead of filtering it.
        assertTrue(filterBotCommands(commands, "/start payload").isEmpty())
    }

    @Test
    fun `reply keyboard buttons describe what they ask for`() {
        val button = ReplyKeyboardButton(
            text = "Share location",
            kind = ReplyKeyboardButtonKind.RequestLocation
        )

        assertEquals(ReplyKeyboardButtonKind.RequestLocation, button.kind)
    }

    @Test
    fun `an inline mention only opens the picker after a space`() {
        assertNull(parseInlineBotQuery("@gif"))
        assertNull(parseInlineBotQuery("hello @gif cat"))
        assertNull(parseInlineBotQuery("@"))

        assertEquals(
            InlineBotQuery(username = "gif", query = "cat"),
            parseInlineBotQuery("@gif cat")
        )

        // An empty query still asks the bot, which is how its default
        // suggestions appear.
        assertEquals(InlineBotQuery("gif", ""), parseInlineBotQuery("@gif "))

        // Everything after the first space belongs to the query.
        assertEquals(
            InlineBotQuery("gif", "two words"),
            parseInlineBotQuery("@gif two words")
        )
    }

    @Test
    fun `an invalid bot name is not a query`() {
        assertNull(parseInlineBotQuery("@1abc text"))
        assertNull(parseInlineBotQuery("@a b"))
    }

    @Test
    fun `a callback button is recognised as needing the provider`() {
        assertTrue(MessageButton(text = "Yes", callbackData = "yes").isCallback)
        assertFalse(MessageButton(text = "Link", url = "https://example.com").isCallback)
    }
}
