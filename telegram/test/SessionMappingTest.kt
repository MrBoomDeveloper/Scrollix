package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.dto.Session
import dev.g000sha256.tdl.dto.SessionDeviceTypeAndroid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionMappingTest {
    @Test
    fun `tdlib seconds become epoch milliseconds`() {
        val session = Session(
            id = 42L,
            isCurrent = true,
            isPasswordPending = false,
            isUnconfirmed = false,
            canAcceptSecretChats = true,
            canAcceptCalls = false,
            deviceType = SessionDeviceTypeAndroid(),
            apiId = 12345,
            applicationName = "Scrollix",
            applicationVersion = "13.1.1",
            isOfficialApplication = false,
            deviceModel = "Pixel 9",
            platform = "Android",
            systemVersion = "37",
            logInDate = 1_700_000_000,
            lastActiveDate = 1_700_000_100,
            ipAddress = "203.0.113.5",
            location = "Berlin, Germany"
        )

        val info = session.toActiveSessionInfo()

        assertEquals(42L, info.id)
        assertTrue(info.isCurrent)
        assertEquals("Scrollix", info.applicationName)
        assertEquals("Pixel 9 · Android · 37", info.deviceLabel)
        assertEquals("Berlin, Germany", info.locationLabel)
        assertEquals(1_700_000_100_000L, info.lastActiveAtMillis)
        assertEquals(1_700_000_000_000L, info.logInAtMillis)
    }

    @Test
    fun `unknown device fields produce readable fallbacks`() {
        val session = Session(
            id = 7L,
            isCurrent = false,
            isPasswordPending = true,
            isUnconfirmed = false,
            canAcceptSecretChats = false,
            canAcceptCalls = false,
            deviceType = SessionDeviceTypeAndroid(),
            apiId = 1,
            applicationName = "",
            applicationVersion = "",
            isOfficialApplication = true,
            deviceModel = "",
            platform = "",
            systemVersion = "",
            logInDate = 0,
            lastActiveDate = 0,
            ipAddress = "198.51.100.7",
            location = ""
        )

        val info = session.toActiveSessionInfo()

        assertEquals("Unknown device", info.deviceLabel)
        assertEquals("198.51.100.7", info.locationLabel)
        assertTrue(info.isPasswordPending)
    }
}
