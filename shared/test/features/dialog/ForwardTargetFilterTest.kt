package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.Dialog
import com.mrboomdev.scrollix.data.model.DialogIdentity
import com.mrboomdev.scrollix.data.model.DialogKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ForwardTargetFilterTest {
    private val work = dialog(id = "1", title = "Work chat")
    private val family = dialog(id = "2", title = "Family")
    private val workNews = dialog(id = "3", title = "Work news")

    private val targets = listOf(work, family, workNews)

    @Test
    fun `an empty query keeps the activity order`() {
        assertSame(targets, filterForwardTargets(targets, ""))
        assertSame(targets, filterForwardTargets(targets, "   "))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(
            listOf(work, workNews),
            filterForwardTargets(targets, "WORK")
        )
    }

    @Test
    fun `every token has to match so a second word narrows the list`() {
        assertEquals(listOf(workNews), filterForwardTargets(targets, "work news"))
        assertEquals(emptyList(), filterForwardTargets(targets, "work family"))
    }

    @Test
    fun `no match returns an empty list`() {
        assertEquals(emptyList(), filterForwardTargets(targets, "telepathy"))
    }

    private fun dialog(id: String, title: String) = Dialog(
        providerId = "telegram",
        accountId = "account",
        providerDialogId = id,
        identity = DialogIdentity(providerId = "telegram", remoteId = id),
        kind = DialogKind.Direct,
        title = title,
        preview = "",
        timestamp = 0L
    )
}
