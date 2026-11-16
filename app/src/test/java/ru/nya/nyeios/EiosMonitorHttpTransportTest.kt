package ru.nya.nyeios

import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorFailover
import ru.nya.nyeios.data.net.EiosMonitorHttpTransport

/**
 * Transport-level tests for the real OkHttp branch used by the monitor probe.
 *
 * These run against a loopback HTTP server, so they cover what the failover tests cannot:
 * HTTP status mapping, hard per-attempt deadlines (`callTimeout`), oversized body cap and
 * connection failures. They also prove that no exception ever escapes the transport.
 *
 * (android.jar has no `com.sun.net.httpserver`, hence [LoopbackHttpServer].)
 */
class EiosMonitorHttpTransportTest {

    private lateinit var server: LoopbackHttpServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        val routes = mapOf<String, (OutputStream) -> Unit>(
            "/ok" to { out -> LoopbackHttpServer.writeResponse(out, 200, summaryJson) },
            "/error500" to { out -> LoopbackHttpServer.writeResponse(out, 500, "{}") },
            "/slow" to { out ->
                Thread.sleep(1_500)
                LoopbackHttpServer.writeResponse(out, 200, summaryJson)
            },
            "/huge" to { out ->
                LoopbackHttpServer.writeResponse(out, 200, ByteArray(200_000) { 'x'.code.toByte() })
            },
        )
        server = LoopbackHttpServer(routes)
        baseUrl = server.baseUrl
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `returns body on http 200`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        val body = transport.fetchBody("$baseUrl/ok", 1_000L)

        assertTrue(body!!.contains("\"status\":\"up\""))
    }

    @Test
    fun `lan endpoint is polled with post and public feed with get`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        transport.fetchBody("$baseUrl/ok", 1_000L)
        assertEquals("POST", server.lastMethod("/ok"))

        val getBody = transport.fetchGetBody("$baseUrl/ok", 1_000L)
        assertEquals("GET", server.lastMethod("/ok"))
        assertTrue(getBody!!.contains("\"status\":\"up\""))
    }

    @Test
    fun `maps non-200 to null`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        assertNull(transport.fetchBody("$baseUrl/error500", 1_000L))
    }

    @Test
    fun `enforces per-attempt deadline`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        val startedAt = System.nanoTime()
        val body = transport.fetchBody("$baseUrl/slow", 100L)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

        assertNull(body)
        assertTrue("deadline not enforced, took ${elapsedMs}ms", elapsedMs < 1_000L)
    }

    @Test
    fun `rejects oversized body without unbounded buffering`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        assertNull(transport.fetchBody("$baseUrl/huge", 2_000L))
    }

    @Test
    fun `returns null on connection failure`() = runBlocking {
        val transport = EiosMonitorHttpTransport(userAgent = "test")

        assertNull(transport.fetchBody("http://127.0.0.1:1/api/eios/summary", 200L))
    }

    @Test
    fun `documented attempt budget is 1000 ms then 1500 ms`() {
        assertEquals(1_000L, EiosMonitorFailover.DEFAULT_ATTEMPTS[0].timeoutMs)
        assertEquals(1_500L, EiosMonitorFailover.DEFAULT_ATTEMPTS[1].timeoutMs)
    }

    companion object {
        private const val summaryJson =
            """{"status":"up","alive":true,"state":"OK","label":"ШТАТНО","reason":"ok"}"""
    }
}
