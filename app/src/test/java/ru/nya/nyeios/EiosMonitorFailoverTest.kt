package ru.nya.nyeios

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorFailover

/**
 * Failover contract: try 192.168.0.209 with 1000 ms, then 192.168.89.99 with 1500 ms,
 * then forget it. Errors, timeouts and malformed payloads never escape.
 */
class EiosMonitorFailoverTest {

    private val validJson = """{"status":"up","alive":true,"state":"OK","label":"ШТАТНО","reason":"ok"}"""

    @Test
    fun `uses first endpoint when it answers`() = runBlocking {
        val calls = mutableListOf<Pair<String, Long>>()
        val failover = EiosMonitorFailover(fetchBody = { url, timeout ->
            calls += url to timeout
            validJson
        })

        val result = failover.probe(force = false)

        assertEquals(listOf(EiosMonitorFailover.DEFAULT_ATTEMPTS[0].url to 1_000L), calls)
        assertEquals(EiosMonitorFailover.DEFAULT_ATTEMPTS[0].url, result?.endpoint)
        assertEquals("up", result?.summary?.status)
    }

    @Test
    fun `falls over to second endpoint after no answer`() = runBlocking {
        val calls = mutableListOf<Pair<String, Long>>()
        val failover = EiosMonitorFailover(fetchBody = { url, timeout ->
            calls += url to timeout
            if (url.contains("192.168.0.209")) null else validJson
        })

        val result = failover.probe(force = false)

        assertEquals(
            listOf(
                "http://192.168.0.209:7790/api/eios/summary" to 1_000L,
                "http://192.168.89.99:7790/api/eios/summary" to 1_500L
            ),
            calls
        )
        assertEquals("http://192.168.89.99:7790/api/eios/summary", result?.endpoint)
    }

    @Test
    fun `returns null after both endpoints fail`() = runBlocking {
        val failover = EiosMonitorFailover(fetchBody = { _, _ -> null })

        assertNull(failover.probe(force = false))
    }

    @Test
    fun `carries on when the first transport throws`() = runBlocking {
        val failover = EiosMonitorFailover(fetchBody = { url, _ ->
            if (url.contains("192.168.0.209")) throw IOException("connect timed out") else validJson
        })

        val result = failover.probe(force = false)

        assertEquals("http://192.168.89.99:7790/api/eios/summary", result?.endpoint)
    }

    @Test
    fun `carries on when the first endpoint returns garbage`() = runBlocking {
        val failover = EiosMonitorFailover(fetchBody = { url, _ ->
            if (url.contains("192.168.0.209")) "<html>502 Bad Gateway</html>" else validJson
        })

        val result = failover.probe(force = false)

        assertEquals("http://192.168.89.99:7790/api/eios/summary", result?.endpoint)
    }

    @Test
    fun `never throws when both transports throw`() = runBlocking {
        val failover = EiosMonitorFailover(fetchBody = { _, _ -> throw IOException("boom") })

        try {
            assertNull(failover.probe(force = false))
        } catch (t: Throwable) {
            fail("probe() must not throw, but threw $t")
        }
    }

    @Test
    fun `never throws when the second payload is malformed`() = runBlocking {
        val failover = EiosMonitorFailover(fetchBody = { _, _ -> "{" })

        assertNull(failover.probe(force = false))
    }

    @Test
    fun `default attempts pin the documented order and timeouts`() {
        val attempts = EiosMonitorFailover.DEFAULT_ATTEMPTS

        assertEquals(2, attempts.size)
        assertEquals("http://192.168.0.209:7790/api/eios/summary", attempts[0].url)
        assertEquals(1_000L, attempts[0].timeoutMs)
        assertEquals("http://192.168.89.99:7790/api/eios/summary", attempts[1].url)
        assertEquals(1_500L, attempts[1].timeoutMs)
    }

    @Test
    fun `does not call any transport on empty attempt list`() = runBlocking {
        val failover = EiosMonitorFailover(
            attempts = emptyList(),
            fetchBody = { _, _ -> throw AssertionError("must not be called") }
        )

        assertNull(failover.probe(force = false))
        assertTrue(failover.probe(force = false) == null)
    }
}
