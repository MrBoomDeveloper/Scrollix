package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.LastSeenVisibility
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.UserPrivacySettingRule
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRules
import dev.g000sha256.tdl.dto.UserPrivacySettingShowStatus
import kotlinx.coroutines.CancellationException

/**
 * Reads the base of Telegram's last seen rule list. Rules are matched in order
 * and deny when nothing matches, so the widest allow rule decides what most
 * people see; narrower user or chat exceptions in the same list do not widen
 * that answer.
 */
internal fun lastSeenVisibilityOf(rules: List<UserPrivacySettingRule>): LastSeenVisibility {
    return when {
        rules.any { rule -> rule is UserPrivacySettingRuleAllowAll } ->
            LastSeenVisibility.Everybody

        rules.any { rule -> rule is UserPrivacySettingRuleAllowContacts } ->
            LastSeenVisibility.Contacts

        else -> LastSeenVisibility.Nobody
    }
}

/**
 * The rule list for one visibility. Writing it replaces every rule, including
 * exceptions another client added, because the app only offers the three base
 * values and inventing an order for unknown rules would change who can see
 * what.
 */
internal fun lastSeenRulesFor(visibility: LastSeenVisibility): UserPrivacySettingRules {
    val rule: UserPrivacySettingRule = when (visibility) {
        LastSeenVisibility.Everybody -> UserPrivacySettingRuleAllowAll()
        LastSeenVisibility.Contacts -> UserPrivacySettingRuleAllowContacts()
        LastSeenVisibility.Nobody -> UserPrivacySettingRuleRestrictAll()
    }

    return UserPrivacySettingRules(rules = arrayOf(rule))
}

internal suspend fun TelegramSession.loadLastSeenVisibilityImpl(): Result<LastSeenVisibility> {
    return try {
        when (val result = client.getUserPrivacySettingRules(UserPrivacySettingShowStatus())) {
            is TdlResult.Success -> Result.success(
                lastSeenVisibilityOf(result.result.rules.toList())
            )

            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

internal suspend fun TelegramSession.setLastSeenVisibilityImpl(
    visibility: LastSeenVisibility
): Result<Unit> {
    return try {
        when (
            val result = client.setUserPrivacySettingRules(
                setting = UserPrivacySettingShowStatus(),
                rules = lastSeenRulesFor(visibility)
            )
        ) {
            is TdlResult.Success -> Result.success(Unit)
            is TdlResult.Failure -> Result.failure(result.toSessionException())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
