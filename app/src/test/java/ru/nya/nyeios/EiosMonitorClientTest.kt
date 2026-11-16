package ru.nya.nyeios

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorClient
import ru.nya.nyeios.data.net.EiosMonitorFetch
import ru.nya.nyeios.data.net.EiosMonitorProbe
import ru.nya.nyeios.data.net.EiosMonitorSummary

/**
 * Cache / rate-limit-friendliness contract for the monitor client.
 * The public API allows 10 requests per minute per IP (token refill every 6 s),
 * so a successful answer is reused for [EiosMonitorClient.DEFAULT_CACHE_TTL_MS].
 */
class EiosMonitorClientTest {

    private val healthySummary = EiosMonitorSummary.parse(
        """{"status":"up","alive":true,"state":"OK","label":"ШТАТНО","reason":"ok"}"""
    )!!
    private val fetch = EiosMonitorFetch(
        summary = healthySummary,
        endpoint = "http://192.168.0.209:7790/api/eios/summary"
    )

    @Test
    fun `caches successful answer within ttl`() = runBlocking {
        var now = 1_000L
        var calls = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> calls++; fetch },
            clock = { now },
            cacheTtlMs = 6_000L
        )

        val first = client.fetch()
        now += 5_999L
        val second = client.fetch()

        assertEquals(1, calls)
        assertSame(fetch, first)
        assertSame(first, second)
    }

    @Test
    fun `refetches after ttl expires`() = runBlocking {
        var now = 1_000L
        var calls = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> calls++; fetch },
            clock = { now },
            cacheTtlMs = 6_000L
        )

        client.fetch()
        now += 6_001L
        client.fetch()

        assertEquals(2, calls)
    }

    @Test
    fun `force bypasses the cache`() = runBlocking {
        var calls = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> calls++; fetch },
            clock = { 1_000L },
            cacheTtlMs = 6_000L
        )

        client.fetch()
        client.fetch(force = true)

        assertEquals(2, calls)
    }

    @Test
    fun `negative-caches failures within ttl and retries after it`() = runBlocking {
        var now = 1_000L
        var calls = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> calls++; null },
            clock = { now },
            cacheTtlMs = 6_000L
        )

        assertNull(client.fetch())
        now += 5_999L
        assertNull(client.fetch())
        assertEquals(1, calls)

        now += 2L
        assertNull(client.fetch())
        assertEquals(2, calls)
    }

    @Test
    fun `force bypasses the negative cache`() = runBlocking {
        var calls = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> calls++; null },
            clock = { 1_000L },
            cacheTtlMs = 6_000L
        )

        client.fetch()
        client.fetch(force = true)

        assertEquals(2, calls)
    }

    @Test
    fun `returns null and does not throw when the probe throws`() = runBlocking {
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> throw IllegalStateException("unexpected") },
            clock = { 1_000L },
            cacheTtlMs = 6_000L
        )

        assertNull(client.fetch())
    }

    @Test
    fun `failed probe does not poison a following success`() = runBlocking {
        var now = 1_000L
        var attempt = 0
        val client = EiosMonitorClient(
            probe = EiosMonitorProbe { _ -> attempt++; if (attempt == 1) null else fetch },
            clock = { now },
            cacheTtlMs = 6_000L
        )

        assertNull(client.fetch())
        now += 6_001L
        assertSame(fetch, client.fetch())
    }

    @Test
    fun `documented default cache ttl matches token refill`() {
        assertEquals(6_000L, EiosMonitorClient.DEFAULT_CACHE_TTL_MS)
    }
}
