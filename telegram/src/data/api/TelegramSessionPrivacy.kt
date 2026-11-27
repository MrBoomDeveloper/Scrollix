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
 * The rule list for one visibility, keeping the per-user and per-group
 * exceptions another client added. Only the base rule is replaced; exceptions
 * stay in front of it, where Telegram evaluates rules in order, so the chosen
 * visibility applies to everyone except the people the user listed.
 */
internal fun lastSeenRulesFor(
    visibility: LastSeenVisibility,
    existing: List<UserPrivacySettingRule> = emptyList()
): UserPrivacySettingRules {
    val base: UserPrivacySettingRule = when (visibility) {
        LastSeenVisibility.Everybody -> UserPrivacySettingRuleAllowAll()
        LastSeenVisibility.Contacts -> UserPrivacySettingRuleAllowContacts()
        LastSeenVisibility.Nobody -> UserPrivacySettingRuleRestrictAll()
    }

    val exceptions = existing.filterNot { rule ->
        rule is UserPrivacySettingRuleAllowAll ||
            rule is UserPrivacySettingRuleAllowContacts ||
            rule is UserPrivacySettingRuleRestrictAll
    }

    return UserPrivacySettingRules(rules = (exceptions + base).toTypedArray())
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
        val current = client.getUserPrivacySettingRules(UserPrivacySettingShowStatus())

        val existingRules = when (current) {
            is TdlResult.Success -> current.result.rules.toList()
            is TdlResult.Failure -> return Result.failure(current.toSessionException())
        }

        when (
            val result = client.setUserPrivacySettingRules(
                setting = UserPrivacySettingShowStatus(),
                rules = lastSeenRulesFor(visibility, existingRules)
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
