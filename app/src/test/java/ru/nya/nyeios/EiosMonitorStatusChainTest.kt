package ru.nya.nyeios

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorFetch
import ru.nya.nyeios.data.net.EiosMonitorProbe
import ru.nya.nyeios.data.net.EiosMonitorStatusChain
import ru.nya.nyeios.data.net.EiosMonitorSummary

/**
 * Third-source policy: LAN endpoints first (fast, freshest), then the public GitHub feed.
 * Stale, expired (older than 15 minutes) or `unknown` fallback data must never open the
 * "site is dead" popup — it fails open instead, exactly as the public feed documentation
 * requires. An explicit settings check (`force = true`) is allowed to return stale data so it
 * can be shown with its age or rejected as overdue.
 */
class EiosMonitorStatusChainTest {

    private class FakeProbe(private val result: EiosMonitorFetch?) : EiosMonitorProbe {
        var calls = 0
        var lastForce: Boolean? = null

        override suspend fun probe(force: Boolean): EiosMonitorFetch? {
            calls++
            lastForce = force
            return result
        }
    }

    private fun summary(
        status: String = "up",
        stale: Boolean = false,
        ageSec: Long = 0L,
        alive: Boolean = status == "up" || status == "degraded",
    ) = EiosMonitorSummary(
        status = status,
        stale = stale,
        ageSec = ageSec,
        alive = alive,
        label = "label",
        reason = "reason",
    )

    private fun fetch(value: EiosMonitorSummary) =
        EiosMonitorFetch(summary = value, endpoint = "https://raw.githubusercontent.com")

    @Test
    fun `primary answer is returned without touching the fallback`() = runBlocking {
        val primaryFetch = fetch(summary(status = "degraded"))
        val primary = FakeProbe(primaryFetch)
        val fallback = FakeProbe(fetch(summary()))
        val chain = EiosMonitorStatusChain(primary, fallback)

        val result = chain.probe(force = false)

        assertSame(primaryFetch, result)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `fallback is used when both LAN endpoints fail`() = runBlocking {
        val fallbackFetch = fetch(summary(status = "down", alive = false))
        val chain = EiosMonitorStatusChain(FakeProbe(null), FakeProbe(fallbackFetch))

        val result = chain.probe(force = false)

        assertSame(fallbackFetch, result)
    }

    @Test
    fun `both sources failing gives null`() = runBlocking {
        val chain = EiosMonitorStatusChain(FakeProbe(null), FakeProbe(null))

        assertNull(chain.probe(force = false))
    }

    @Test
    fun `stale fallback data never drives the gate popup`() = runBlocking {
        val chain = EiosMonitorStatusChain(
            FakeProbe(null),
            FakeProbe(fetch(summary(status = "up", stale = true)))
        )

        assertNull(chain.probe(force = false))
    }

    @Test
    fun `stale primary data never drives the gate popup`() = runBlocking {
        val fallback = FakeProbe(fetch(summary()))
        val chain = EiosMonitorStatusChain(
            FakeProbe(fetch(summary(status = "down", stale = true, alive = false))),
            fallback
        )

        assertNull(chain.probe(force = false))
        assertEquals("stale primary must not trigger the fallback", 0, fallback.calls)
    }

    @Test
    fun `primary data older than fifteen minutes fails open`() = runBlocking {
        val chain = EiosMonitorStatusChain(
            FakeProbe(fetch(summary(status = "down", ageSec = 16 * 60L, alive = false))),
            FakeProbe(null)
        )

        assertNull(chain.probe(force = false))
    }

    @Test
    fun `fallback data older than fifteen minutes fails open`() = runBlocking {
        val fallback = FakeProbe(fetch(summary(status = "down", ageSec = 16 * 60L, alive = false)))
        val chain = EiosMonitorStatusChain(FakeProbe(null), fallback)

        assertNull(chain.probe(force = false))
        assertEquals(false, fallback.lastForce)
    }

    @Test
    fun `forced check still returns an expired snapshot for the settings digest`() = runBlocking {
        val primaryFetch = fetch(summary(status = "up", ageSec = 40 * 60L))
        val chain = EiosMonitorStatusChain(FakeProbe(primaryFetch), FakeProbe(null))

        assertSame(primaryFetch, chain.probe(force = true))
    }

    @Test
    fun `unknown fallback status means no monitoring data, not an outage`() = runBlocking {
        val chain = EiosMonitorStatusChain(
            FakeProbe(null),
            FakeProbe(fetch(summary(status = "unknown", alive = false)))
        )

        assertNull(chain.probe(force = false))
    }

    @Test
    fun `fresh fallback degradation is returned for the popup`() = runBlocking {
        val fallbackFetch = fetch(summary(status = "degraded"))
        val chain = EiosMonitorStatusChain(FakeProbe(null), FakeProbe(fallbackFetch))

        assertSame(fallbackFetch, chain.probe(force = false))
    }

    @Test
    fun `explicit forced check may return stale fallback for diagnostics`() = runBlocking {
        val staleFetch = fetch(summary(status = "down", stale = true, alive = false))
        val fallback = FakeProbe(staleFetch)
        val chain = EiosMonitorStatusChain(FakeProbe(null), fallback)

        val result = chain.probe(force = true)

        assertSame(staleFetch, result)
        assertEquals(true, fallback.lastForce)
        assertEquals(true, chain.probe(force = true) != null)
    }

    @Test
    fun `force flag is forwarded to both probes`() = runBlocking {
        val primary = FakeProbe(null)
        val fallback = FakeProbe(fetch(summary()))
        val chain = EiosMonitorStatusChain(primary, fallback)

        chain.probe(force = true)

        assertEquals(true, primary.lastForce)
        assertEquals(true, fallback.lastForce)
    }
}
