package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.EndpointHealthItem
import ru.nya.nyeios.data.net.EndpointStatus

class EndpointHealthTest {

    @Test
    fun testEndpointHealthItemDefaultValues() {
        val item = EndpointHealthItem(
            id = "timetable",
            name = "РАСПИСАНИЕ",
            path = "/eios/contacts/timetable/"
        )

        assertEquals("timetable", item.id)
        assertEquals("РАСПИСАНИЕ", item.name)
        assertEquals("/eios/contacts/timetable/", item.path)
        assertEquals(EndpointStatus.IDLE, item.status)
        assertEquals(null, item.httpCode)
        assertEquals(null, item.latencyMs)
        assertEquals(null, item.message)
        assertEquals(null, item.rawLog)
    }

    @Test
    fun testEndpointStatusTransitions() {
        val pending = EndpointHealthItem(
            id = "feed",
            name = "ЖИВАЯ ЛЕНТА",
            path = "/eios/",
            status = EndpointStatus.PENDING,
            message = "В очереди на проверку..."
        )
        assertEquals(EndpointStatus.PENDING, pending.status)
        assertEquals("В очереди на проверку...", pending.message)

        val checking = pending.copy(
            status = EndpointStatus.CHECKING,
            message = "Кидаю GET на /eios/ с таймаутом в 15с (3\\15с)"
        )
        assertEquals(EndpointStatus.CHECKING, checking.status)
        assertTrue(checking.message!!.contains("таймаутом в 15с"))

        val completed = checking.copy(
            status = EndpointStatus.OK,
            httpCode = 200,
            latencyMs = 52,
            message = "200 ОК · Живая лента доступна (52 мс)",
            rawLog = ">>> HTTP-ЗАПРОС\nGET /eios/ HTTP/1.1\n\n<<< HTTP-ОТВЕТ\nHTTP/1.1 200 OK"
        )
        assertEquals(EndpointStatus.OK, completed.status)
        assertEquals(200, completed.httpCode)
        assertEquals(52L, completed.latencyMs)
        assertNotNull(completed.rawLog)
        assertTrue(completed.rawLog!!.contains("200 OK"))
    }
}
