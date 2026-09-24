package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.PingResult

class NetworkMetricsTrackerTest {

    @Test
    fun `eios last get info stores duration and path correctly`() {
        val info = EiosLastGetInfo(
            durationMs = 1240L,
            timestamp = 1727210000000L,
            path = "/eios/contacts/timetable/"
        )
        assertEquals(1240L, info.durationMs)
        assertEquals("/eios/contacts/timetable/", info.path)
    }

    @Test
    fun `ping result types represent idle measuring success and error`() {
        val idle: PingResult = PingResult.Idle
        assertTrue(idle is PingResult.Idle)

        val measuring: PingResult = PingResult.Measuring
        assertTrue(measuring is PingResult.Measuring)

        val success: PingResult = PingResult.Success(42L)
        assertTrue(success is PingResult.Success)
        assertEquals(42L, (success as PingResult.Success).latencyMs)

        val error: PingResult = PingResult.Error("Timeout")
        assertTrue(error is PingResult.Error)
        assertEquals("Timeout", (error as PingResult.Error).message)
    }
}
