package com.mrboomdev.scrollix.data.api

import com.mrboomdev.scrollix.data.model.ActiveSessionInfo
import com.mrboomdev.scrollix.data.model.SessionException
import com.mrboomdev.scrollix.data.model.sessionExceptionFrom
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Session

/** TDLib reports session times in seconds, the UI works in milliseconds. */
internal fun TdlResult.Failure.toSessionException(): SessionException =
    sessionExceptionFrom(message)

internal fun Session.toActiveSessionInfo(): ActiveSessionInfo {
    return ActiveSessionInfo(
        id = id,
        isCurrent = isCurrent,
        isPasswordPending = isPasswordPending,
        applicationName = applicationName,
        deviceModel = deviceModel,
        platform = platform,
        systemVersion = systemVersion,
        location = location,
        ipAddress = ipAddress,
        lastActiveAtMillis = lastActiveDate.toLong() * 1000L,
        logInAtMillis = logInDate.toLong() * 1000L,
        isOfficialApplication = isOfficialApplication
    )
}
