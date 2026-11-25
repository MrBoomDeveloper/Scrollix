package com.mrboomdev.scrollix.features.home

import com.mrboomdev.scrollix.data.model.AccountDialogEntry
import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DialogNavigationTest {
    private val entries = listOf(
        entry("account", "first"),
        entry("account", "second"),
        entry("account", "third")
    )

    @Test
    fun `next moves down and previous moves up`() {
        assertEquals(
            "second",
            adjacentDialogEntry(entries, "account", "first", step = 1)?.dialog?.providerDialogId
        )

        assertEquals(
            "second",
            adjacentDialogEntry(entries, "account", "third", step = -1)?.dialog?.providerDialogId
        )
    }

    @Test
    fun `the walk stops at both ends`() {
        assertNull(adjacentDialogEntry(entries, "account", "first", step = -1))
        assertNull(adjacentDialogEntry(entries, "account", "third", step = 1))
    }

    @Test
    fun `nothing selected opens the first or last chat`() {
        assertEquals(
            "first",
            adjacentDialogEntry(entries, null, null, step = 1)?.dialog?.providerDialogId
        )

        assertEquals(
            "third",
            adjacentDialogEntry(entries, null, null, step = -1)?.dialog?.providerDialogId
        )
    }

    @Test
    fun `a selection from another account does not match`() {
        assertEquals(
            "first",
            adjacentDialogEntry(entries, "other-account", "second", step = 1)
                ?.dialog
                ?.providerDialogId
        )
    }

    @Test
    fun `an empty list and a zero step produce nothing`() {
        assertNull(adjacentDialogEntry(emptyList(), "account", "first", step = 1))
        assertNull(adjacentDialogEntry(entries, "account", "first", step = 0))
    }

    @Test
    fun `jump to unread picks the highest unread chat`() {
        val list = listOf(
            entry("account", "read"),
            entry("account", "unread", unreadCount = 3),
            entry("account", "later-unread", unreadCount = 1)
        )

        assertEquals(
            "unread",
            firstUnreadEntry(list)?.dialog?.providerDialogId
        )
    }

    @Test
    fun `an explicitly marked chat counts as unread`() {
        val list = listOf(
            entry("account", "read"),
            entry("account", "marked", markedAsUnread = true)
        )

        assertEquals("marked", firstUnreadEntry(list)?.dialog?.providerDialogId)
    }

    @Test
    fun `saved messages are skipped even when they carry a counter`() {
        val list = listOf(
            entry("account", "saved", unreadCount = 2, savedMessages = true),
            entry("account", "chat", unreadCount = 1)
        )

        assertEquals("chat", firstUnreadEntry(list)?.dialog?.providerDialogId)
    }

    @Test
    fun `no unread chats produce nothing`() {
        assertNull(firstUnreadEntry(entries))
        assertNull(firstUnreadEntry(emptyList()))
    }

    private fun entry(
        accountId: String,
        dialogId: String,
        unreadCount: Int = 0,
        markedAsUnread: Boolean = false,
        savedMessages: Boolean = false
    ): AccountDialogEntry {
        val account = AccountRecord(
            id = accountId,
            providerId = "telegram",
            displayName = accountId,
            isPending = false,
            addedAt = 0,
            lastUsedAt = 0
        )

        return AccountDialogEntry(
            account = account,
            dialog = Dialog(
                providerId = "telegram",
                accountId = accountId,
                providerDialogId = dialogId,
                identity = DialogIdentity("telegram", dialogId),
                kind = DialogKind.Direct,
                title = dialogId,
                preview = "",
                timestamp = 0,
                unreadCount = unreadCount,
                isMarkedAsUnread = markedAsUnread,
                isSavedMessages = savedMessages
            )
        )
    }
}
