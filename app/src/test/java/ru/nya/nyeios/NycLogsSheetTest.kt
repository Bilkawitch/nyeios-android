package ru.nya.nyeios

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.NetworkLogEntry
import ru.nya.nyeios.data.net.NetworkLogLevel
import ru.nya.nyeios.ui.debug.codeColor
import ru.nya.nyeios.ui.debug.deriveRow
import ru.nya.nyeios.ui.debug.passesLogFilter

class NycLogsSheetTest {

    private fun entry(
        tag: String,
        message: String,
        details: String? = null,
        level: NetworkLogLevel = NetworkLogLevel.RESPONSE
    ) = NetworkLogEntry(
        timestamp = "13:19:08.123",
        level = level,
        tag = tag,
        message = message,
        details = details
    )

    @Test
    fun `request row derives GET method and stripped path without code`() {
        val row = deriveRow(
            entry(
                tag = "HTTP GET",
                message = "Отправлен запрос GET https://eios.gukolomna.ru/eios/",
                details = "Метод: GET\nURL: https://eios.gukolomna.ru/eios/",
                level = NetworkLogLevel.REQUEST
            )
        )
        assertEquals("GET", row.method)
        assertEquals("/eios/", row.path)
        assertNull(row.code)
        assertNull(row.ms)
        assertEquals("13:19:08", row.time)
        assertFalse(row.isBad)
    }

    @Test
    fun `response row derives code ms and path`() {
        val row = deriveRow(
            entry(
                tag = "HTTP",
                message = "GET /eios/ 200",
                details = "Код: 200 OK\nВремя ответа: 412 мс\nURL: https://eios.gukolomna.ru/eios/"
            )
        )
        assertEquals("/eios/", row.path)
        assertEquals(200, row.code)
        assertEquals(412L, row.ms)
        assertFalse(row.isBad)
    }

    @Test
    fun `gateway timeout row is bad and red`() {
        val row = deriveRow(
            entry(
                tag = "HTTP",
                message = "GET /eios/map/floor/4 504",
                details = "Код: 504 Gateway Timeout\nВремя ответа: 5000 мс\nURL: https://eios.gukolomna.ru/eios/map/floor/4",
                level = NetworkLogLevel.ERROR
            )
        )
        assertEquals(504, row.code)
        assertEquals(5000L, row.ms)
        assertTrue(row.isBad)
        assertEquals(Color(0xFFF26D6D), codeColor(row.code))
    }

    @Test
    fun `code colors follow mockup bands`() {
        assertEquals(Color(0xFF46E08C), codeColor(200))
        assertEquals(Color(0xFF6FA8FF), codeColor(302))
        assertEquals(Color(0xFF8794A7), codeColor(404))
        assertEquals(Color(0xFFF26D6D), codeColor(500))
    }

    @Test
    fun `filter mapping matches chip order`() {
        // 0=ВСЕ 1=2XX 2=3XX 3=4XX 4=5XX; anything without code shows in ВСЕ only.
        assertTrue(passesLogFilter(200, 1))
        assertFalse(passesLogFilter(200, 2))
        assertTrue(passesLogFilter(304, 2))
        assertTrue(passesLogFilter(404, 3))
        assertTrue(passesLogFilter(504, 4))
        assertFalse(passesLogFilter(null, 1))
        assertFalse(passesLogFilter(null, 4))
        assertTrue(passesLogFilter(null, 0))
        assertTrue(passesLogFilter(200, 0))
    }
}
