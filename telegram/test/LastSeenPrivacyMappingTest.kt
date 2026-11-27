package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.LastSeenVisibility
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictContacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LastSeenPrivacyMappingTest {
    @Test
    fun `allow all reads as everybody`() {
        assertEquals(
            LastSeenVisibility.Everybody,
            lastSeenVisibilityOf(listOf(UserPrivacySettingRuleAllowAll()))
        )
    }

    @Test
    fun `allow contacts reads as contacts`() {
        assertEquals(
            LastSeenVisibility.Contacts,
            lastSeenVisibilityOf(listOf(UserPrivacySettingRuleAllowContacts()))
        )
    }

    @Test
    fun `restrict all reads as nobody`() {
        assertEquals(
            LastSeenVisibility.Nobody,
            lastSeenVisibilityOf(listOf(UserPrivacySettingRuleRestrictAll()))
        )
    }

    @Test
    fun `restricting contacts does not narrow an allow-contacts base`() {
        assertEquals(
            LastSeenVisibility.Contacts,
            lastSeenVisibilityOf(
                listOf(
                    UserPrivacySettingRuleAllowContacts(),
                    UserPrivacySettingRuleRestrictContacts()
                )
            )
        )
    }

    @Test
    fun `a list of user exceptions alone reads as nobody`() {
        assertEquals(
            LastSeenVisibility.Nobody,
            lastSeenVisibilityOf(listOf(UserPrivacySettingRuleAllowUsers(longArrayOf(1L))))
        )
    }

    @Test
    fun `an empty list denies everyone`() {
        assertEquals(LastSeenVisibility.Nobody, lastSeenVisibilityOf(emptyList()))
    }

    @Test
    fun `writing a visibility uses a single base rule`() {
        assertEquals(1, lastSeenRulesFor(LastSeenVisibility.Everybody).rules.size)
        assertEquals(1, lastSeenRulesFor(LastSeenVisibility.Contacts).rules.size)
        assertEquals(1, lastSeenRulesFor(LastSeenVisibility.Nobody).rules.size)

        assertTrue(
            lastSeenRulesFor(LastSeenVisibility.Nobody).rules.single()
                is UserPrivacySettingRuleRestrictAll
        )
    }

    @Test
    fun `writing a visibility keeps user exceptions in front of the base rule`() {
        val exception = UserPrivacySettingRuleAllowUsers(longArrayOf(7L, 9L))

        val rules = lastSeenRulesFor(
            visibility = LastSeenVisibility.Nobody,
            existing = listOf(exception, UserPrivacySettingRuleAllowAll())
        ).rules.toList()

        assertEquals(2, rules.size)
        assertEquals(exception, rules.first())
        assertTrue(rules.last() is UserPrivacySettingRuleRestrictAll)
    }

    @Test
    fun `every visibility survives a write and read round trip`() {
        LastSeenVisibility.entries.forEach { value ->
            assertEquals(
                value,
                lastSeenVisibilityOf(lastSeenRulesFor(value).rules.toList())
            )
        }
    }
}
