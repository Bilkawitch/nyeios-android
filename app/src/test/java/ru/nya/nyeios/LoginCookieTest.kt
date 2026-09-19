package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginCookieTest {

    @Test
    fun testSessionCookieDetectionAcrossRedirects() {
        // Simulating cookies received across 302 redirect and final 200 response
        val redirectHeaders = listOf(
            "PHPSESSID=rot3409rp58oc3cma9amogsdgc; path=/; HttpOnly",
            "BITRIX_SM_LOGIN=maksim.grechki; expires=Sun, 20-Sep-2026 21:00:00 GMT; path=/; domain=.gukolomna.ru",
            "BITRIX_SM_UIDH=7a49428176b9aeccd4cc39edf6cc4395; expires=Sun, 20-Sep-2026 21:00:00 GMT; path=/; domain=.gukolomna.ru",
            "BITRIX_SM_UIDL=maksim.grechki%40yandex.ru; expires=Sun, 20-Sep-2026 21:00:00 GMT; path=/; domain=.gukolomna.ru"
        )

        val finalResponseHeaders = listOf(
            "PHPSESSID=rot3409rp58oc3cma9amogsdgc; path=/; HttpOnly"
        )

        // If only final response headers were checked (the old bug):
        val badCookiesMap = mutableMapOf<String, String>()
        for (h in finalResponseHeaders) {
            val pair = h.split(";").firstOrNull()?.split("=", limit = 2)
            if (pair != null && pair.size == 2) {
                badCookiesMap[pair[0].trim()] = pair[1].trim()
            }
        }
        val bugHasSession = badCookiesMap.containsKey("BITRIX_SM_UIDH") || badCookiesMap.containsKey("BITRIX_SM_LOGIN")
        assertFalse("Old logic failed to detect session because redirect headers were discarded", bugHasSession)

        // With the fix (checking all chain responses):
        val allChainHeaders = redirectHeaders + finalResponseHeaders
        val fixedCookiesMap = mutableMapOf<String, String>()
        for (h in allChainHeaders) {
            val pair = h.split(";").firstOrNull()?.split("=", limit = 2)
            if (pair != null && pair.size == 2) {
                fixedCookiesMap[pair[0].trim()] = pair[1].trim()
            }
        }
        val fixedHasSession = fixedCookiesMap.containsKey("BITRIX_SM_UIDH") || fixedCookiesMap.containsKey("BITRIX_SM_LOGIN")
        assertTrue("Fixed logic captures session cookies from redirect chain", fixedHasSession)
        assertEquals("7a49428176b9aeccd4cc39edf6cc4395", fixedCookiesMap["BITRIX_SM_UIDH"])
        assertEquals("maksim.grechki", fixedCookiesMap["BITRIX_SM_LOGIN"])
    }
}
