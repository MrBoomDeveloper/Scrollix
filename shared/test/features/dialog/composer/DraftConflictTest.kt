package com.mrboomdev.scrollix.features.dialog.composer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftConflictTest {
    @Test
    fun `a remote draft replaces an untouched composer`() {
        assertTrue(
            shouldApplyRemoteDraft(
                localText = "draft",
                knownServerText = "draft",
                remoteText = "draft from the phone"
            )
        )
    }

    @Test
    fun `local typing wins over a remote draft`() {
        assertFalse(
            shouldApplyRemoteDraft(
                localText = "half a sentence",
                knownServerText = "draft",
                remoteText = "draft from the phone"
            )
        )
    }

    @Test
    fun `the same remote text is not applied twice`() {
        assertFalse(
            shouldApplyRemoteDraft(
                localText = "draft",
                knownServerText = "draft from the phone",
                remoteText = "draft from the phone"
            )
        )
    }

    @Test
    fun `a remote clear replaces an untouched draft`() {
        assertTrue(
            shouldApplyRemoteDraft(
                localText = "draft",
                knownServerText = "draft",
                remoteText = ""
            )
        )
    }
}
