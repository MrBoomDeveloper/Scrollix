package com.mrboomdev.scrollix.data.provider

import com.mrboomdev.scrollix.data.model.AccountRecord
import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogAggregatorTest {
    @Test
    fun `sorts pinned first then activity and marks duplicates`() {
        val first = account("first")
        val second = account("second")

        val entries = DialogAggregator.aggregate(
            accounts = listOf(first, second),
            dialogsByAccount = mapOf(
                first.id to listOf(
                    dialog(first.id, "A", "user:1", timestamp = 100),
                    dialog(first.id, "B", "group:1", timestamp = 300, pinned = true)
                ),
                second.id to listOf(
                    dialog(second.id, "C", "user:1", timestamp = 200),
                    dialog(second.id, "D", "user:2", timestamp = 400)
                )
            )
        )

        assertEquals(
            listOf("B", "D", "C", "A"),
            entries.map { it.dialog.title }
        )

        assertTrue(entries.first { it.dialog.title == "A" }.showAccountBadge)
        assertTrue(entries.first { it.dialog.title == "C" }.showAccountBadge)
        assertFalse(entries.first { it.dialog.title == "B" }.showAccountBadge)
        assertFalse(entries.first { it.dialog.title == "D" }.showAccountBadge)
    }

    @Test
    fun `same identity in one account is not a duplicate`() {
        val account = account("first")

        val entries = DialogAggregator.aggregate(
            accounts = listOf(account),
            dialogsByAccount = mapOf(
                account.id to listOf(
                    dialog(account.id, "A", "user:1", timestamp = 100)
                )
            )
        )

        assertFalse(entries.single().showAccountBadge)
    }

    private fun account(id: String): AccountRecord {
        return AccountRecord(
            id = id,
            providerId = "telegram",
            displayName = id,
            isPending = false,
            addedAt = 0,
            lastUsedAt = 0
        )
    }

    private fun dialog(
        accountId: String,
        title: String,
        remoteId: String,
        timestamp: Long,
        pinned: Boolean = false
    ): Dialog {
        return Dialog(
            providerId = "telegram",
            accountId = accountId,
            providerDialogId = "$accountId:$title",
            identity = DialogIdentity(
                providerId = "telegram",
                remoteId = remoteId
            ),
            kind = DialogKind.Direct,
            title = title,
            preview = "",
            timestamp = timestamp,
            isPinned = pinned
        )
    }
}
