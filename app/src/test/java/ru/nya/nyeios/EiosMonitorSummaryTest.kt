package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorSummary

/**
 * Contract tests for the EIOS monitor summary payload.
 *
 * The JSON shape is defined by the nyan-ops server:
 * `server/admin-daemon/eios_summary.go` (docs/API.md §13.8–13.9).
 */
class EiosMonitorSummaryTest {

    private val fullPayload = """
        {
          "ts": 1794780755067, "ageSec": 375, "stale": false,
          "status": "degraded", "alive": true, "state": "SATURATED",
          "label": "ОЧЕРЕДЬ / ПЕРЕГРУЗКА",
          "reason": "динамика не ответила вовремя, статика быстрая — очередь запросов",
          "confirmed": false,
          "loading": {
            "ttfbMs": 0, "publicTtfbMs": 226, "authTtfbMs": 2630,
            "freeWorkers": 4, "freeLabel": "≈ 4 свободно",
            "serviceMs": 89, "queueRatio": 1.18, "rps": 26.8, "measuredAgeSec": 1026
          },
          "uptime": {"h24": 100, "d7": 99.5, "d30": 99.1},
          "session": {"configured": true, "ok": false}
        }
    """.trimIndent()

    @Test
    fun `parses full server payload`() {
        val summary = EiosMonitorSummary.parse(fullPayload)

        assertNotNull(summary)
        summary!!
        assertEquals(1794780755067L, summary.ts)
        assertEquals(375L, summary.ageSec)
        assertFalse(summary.stale)
        assertEquals("degraded", summary.status)
        assertTrue(summary.alive)
        assertEquals("SATURATED", summary.state)
        assertEquals("ОЧЕРЕДЬ / ПЕРЕГРУЗКА", summary.label)
        assertEquals("динамика не ответила вовремя, статика быстрая — очередь запросов", summary.reason)
        assertFalse(summary.confirmed)
        assertEquals(0L, summary.loading.ttfbMs)
        assertEquals(226L, summary.loading.publicTtfbMs)
        assertEquals(2630L, summary.loading.authTtfbMs)
        assertEquals(4, summary.loading.freeWorkers)
        assertEquals("≈ 4 свободно", summary.loading.freeLabel)
        assertEquals(89L, summary.loading.serviceMs)
        assertEquals(1.18, summary.loading.queueRatio!!, 0.0001)
        assertEquals(26.8, summary.loading.rps!!, 0.0001)
        assertEquals(1026L, summary.loading.measuredAgeSec)
        assertEquals(100.0, summary.uptime!!.h24, 0.0001)
        assertEquals(99.5, summary.uptime!!.d7, 0.0001)
        assertEquals(99.1, summary.uptime!!.d30, 0.0001)
        assertTrue(summary.session!!.configured)
        assertFalse(summary.session!!.ok)
    }

    @Test
    fun `tolerates omitted optional fields and null freeWorkers`() {
        val minimal = """
            {
              "ts": 1794780755067, "ageSec": 2, "stale": false,
              "status": "up", "alive": true, "state": "OK", "label": "ШТАТНО",
              "reason": "все слои в норме", "confirmed": true,
              "loading": {"ttfbMs": 46, "freeWorkers": null}
            }
        """.trimIndent()

        val summary = EiosMonitorSummary.parse(minimal)

        assertNotNull(summary)
        summary!!
        assertEquals(46L, summary.loading.ttfbMs)
        assertNull(summary.loading.freeWorkers)
        assertNull(summary.loading.publicTtfbMs)
        assertNull(summary.loading.authTtfbMs)
        assertNull(summary.loading.freeLabel)
        assertNull(summary.loading.serviceMs)
        assertNull(summary.loading.queueRatio)
        assertNull(summary.loading.rps)
        assertNull(summary.loading.measuredAgeSec)
        assertNull(summary.uptime)
        assertNull(summary.session)
        assertTrue(summary.isHealthy)
    }

    @Test
    fun `tolerates unknown extra fields`() {
        val payload = """{"status":"up","alive":true,"loading":{"ttfbMs":1,"brandNewField":42},"futureTopLevel":{"a":1}}"""
        val summary = EiosMonitorSummary.parse(payload)
        assertNotNull(summary)
        assertEquals("up", summary!!.status)
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(EiosMonitorSummary.parse(""))
        assertNull(EiosMonitorSummary.parse("not json at all"))
        assertNull(EiosMonitorSummary.parse("{\"status\":"))
        assertNull(EiosMonitorSummary.parse("[]"))
        assertNull(EiosMonitorSummary.parse("null"))
        assertNull(EiosMonitorSummary.parse("42"))
        assertNull(EiosMonitorSummary.parse("true"))
        assertNull(EiosMonitorSummary.parse("\"up\""))
    }

    @Test
    fun `ignores wrongly typed optional objects`() {
        val summary = EiosMonitorSummary.parse(
            """{"status":"down","alive":false,"loading":5,"uptime":"n/a","session":[]}"""
        )

        assertNotNull(summary)
        summary!!
        assertEquals("down", summary.status)
        assertEquals(0L, summary.loading.ttfbMs)
        assertNull(summary.loading.freeWorkers)
        assertNull(summary.uptime)
        assertNull(summary.session)
    }

    @Test
    fun `missing status falls back to unknown and never counts as healthy`() {
        val summary = EiosMonitorSummary.parse("""{"ts":1,"alive":true}""")

        assertNotNull(summary)
        summary!!
        assertEquals(EiosMonitorSummary.STATUS_UNKNOWN, summary.status)
        assertFalse(summary.isHealthy)
    }

    @Test
    fun `parses public github feed envelope fields`() {
        val publicFeed = """
            {"schema":1,"publishedAt":1794844764680,"source":"nyan-ops","ts":1794844744664,"ageSec":20,
             "stale":false,"status":"up","alive":true,"state":"OK","label":"ШТАТНО",
             "reason":"все слои в норме","confirmed":true,
             "loading":{"ttfbMs":99,"freeWorkers":8},"uptime":{"h24":100,"d7":100,"d30":100},
             "session":{"configured":true,"ok":true}}
        """.trimIndent()

        val summary = EiosMonitorSummary.parse(publicFeed)

        assertNotNull(summary)
        summary!!
        assertEquals(1, summary.schemaVersion)
        assertEquals(1794844764680L, summary.publishedAt)
        assertEquals("nyan-ops", summary.source)
        assertEquals(1794844744664L, summary.ts)
    }

    @Test
    fun `lan payload has no public feed envelope fields`() {
        val summary = EiosMonitorSummary.parse(fullPayload)

        assertNotNull(summary)
        assertNull(summary!!.schemaVersion)
        assertNull(summary.publishedAt)
        assertNull(summary.source)
    }

    @Test
    fun `healthy means up alive and fresh`() {
        assertTrue(summary(status = "up", alive = true, stale = false).isHealthy)
        assertFalse(summary(status = "up", alive = true, stale = true).isHealthy)
        assertFalse(summary(status = "up", alive = false, stale = false).isHealthy)
        assertFalse(summary(status = "degraded", alive = true, stale = false).isHealthy)
        assertFalse(summary(status = "down", alive = false, stale = false).isHealthy)
        assertFalse(summary(status = "unknown", alive = false, stale = false).isHealthy)
    }

    private fun summary(status: String, alive: Boolean, stale: Boolean) = EiosMonitorSummary(
        status = status,
        alive = alive,
        stale = stale
    )
}
