package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.data.net.NetworkLogger
import ru.nya.nyeios.data.repository.EiosRepository

class NetworkLoggerAndDiagnosticsTest {

    @Before
    fun setUp() {
        NetworkLogger.clear()
    }

    @Test
    fun testNormalResponseLogging() {
        NetworkLogger.logResponse(
            code = 200,
            message = "OK",
            url = "https://eios.gukolomna.ru/eios/",
            durationMs = 250,
            isDegraded = false
        )

        val logs = NetworkLogger.logs.value
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertEquals(NetworkLogLevel.RESPONSE, entry.level)
        assertEquals("HTTP 200", entry.tag)
        assertFalse(entry.isDegraded)
    }

    @Test
    fun testDegraded200ResponseLogging() {
        NetworkLogger.logResponse(
            code = 200,
            message = "OK",
            url = "https://eios.gukolomna.ru/eios/contacts/timetable/",
            durationMs = 180,
            isDegraded = true,
            degradedReason = "Отсутствует соединение со службой 1С"
        )

        val logs = NetworkLogger.logs.value
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertEquals(NetworkLogLevel.WARNING, entry.level)
        assertEquals("200 ПУСТО", entry.tag)
        assertTrue(entry.isDegraded)
        assertTrue(entry.message.contains("СБОЙ: Отсутствует соединение со службой 1С"))
        assertTrue(entry.details?.contains("ПУСТАЯ СТРАНИЦА / СБОЙ СЕРВЕРА") == true)
    }

    @Test
    fun testSystemAlertWordings() {
        assertEquals(
            "Ошибка со стороны сервера, сервер вернул пустую страницу. Попробуйте позже, может починят",
            EiosRepository.ERROR_SERVER_EMPTY_OR_DOWN
        )
        assertEquals(
            "Ошибка со стороны сервера... или вашего интернета. Сайт никак не отреагировал",
            EiosRepository.ERROR_NO_CONNECTION
        )
    }
}
