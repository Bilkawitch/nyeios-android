package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.data.net.NetworkLogger

class NetworkLoggerTest {

    @Before
    fun setUp() {
        NetworkLogger.clear()
    }

    @Test
    fun testLogRequest_addsEntry() {
        NetworkLogger.logRequest(
            method = "POST",
            url = "https://eios.gukolomna.ru/index.php?login=yes",
            userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile)",
            summary = "Отправка данных входа"
        )

        val logs = NetworkLogger.logs.value
        assertEquals(1, logs.size)
        val entry = logs[0]
        assertEquals(NetworkLogLevel.REQUEST, entry.level)
        assertEquals("HTTP POST", entry.tag)
        assertTrue(entry.message.contains("POST"))
        assertNotNull(entry.details)
        assertTrue(entry.details!!.contains("User-Agent: Mozilla/5.0"))
        assertTrue(entry.details!!.contains("Отправка данных входа"))
    }

    @Test
    fun testLogResponse_successAndErrorLevels() {
        NetworkLogger.logResponse(200, "OK", "https://eios.gukolomna.ru/", 350L, "Куки: PHPSESSID")
        NetworkLogger.logResponse(504, "Gateway Timeout", "https://eios.gukolomna.ru/eios/", 15000L, null)

        val logs = NetworkLogger.logs.value
        assertEquals(2, logs.size)

        // Newest is at index 0
        assertEquals(NetworkLogLevel.ERROR, logs[0].level)
        assertEquals("HTTP 504", logs[0].tag)

        assertEquals(NetworkLogLevel.RESPONSE, logs[1].level)
        assertEquals("HTTP 200", logs[1].tag)
    }

    @Test
    fun testLogError_formatsException() {
        val ex = java.net.SocketTimeoutException("timeout")
        NetworkLogger.logError("TIMEOUT", "Таймаут запроса", ex, 15000L)

        val logs = NetworkLogger.logs.value
        assertEquals(1, logs.size)
        val entry = logs[0]
        assertEquals(NetworkLogLevel.ERROR, entry.level)
        assertTrue(entry.details!!.contains("SocketTimeoutException"))
        assertTrue(entry.details!!.contains("15000 мс"))
    }

    @Test
    fun testGetAllFormatted_producesReadableExport() {
        NetworkLogger.logInfo("INIT", "Приложение запущено")
        NetworkLogger.logRequest("GET", "https://eios.gukolomna.ru/", "TestAgent")

        val formatted = NetworkLogger.getAllFormatted()
        assertTrue(formatted.contains("[INIT] Приложение запущено"))
        assertTrue(formatted.contains("HTTP GET"))
    }

    @Test
    fun testClear_emptiesLogs() {
        NetworkLogger.logInfo("TEST", "Test message")
        assertEquals(1, NetworkLogger.logs.value.size)

        NetworkLogger.clear()
        assertEquals(0, NetworkLogger.logs.value.size)
    }
}
