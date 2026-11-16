package ru.nya.nyeios

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorSummary
import ru.nya.nyeios.data.net.EiosStatusFeed
import ru.nya.nyeios.data.net.EiosStatusFeedStore

/**
 * Contract for the public GitHub status feed used as the third monitor fallback
 * (`https://raw.githubusercontent.com/Bilkawitch/sttpage/status/summary.json`).
 *
 * Rules from the feed documentation:
 * - poll at most once per minute, keep the last valid JSON locally;
 * - 404 / network failure is not "site down" — fall back to the cached snapshot;
 * - freshness is computed from `ts` / `publishedAt`, not from the server-side `ageSec`;
 * - after ~20 minutes without a fresh cycle the data is stale;
 * - an explicit check retries twice (backoff 1 s, 2 s) with a 10 s timeout.
 */
class EiosStatusFeedTest {

    private class FakeStore(
        var json: String? = null,
        var lastSuccessAtMs: Long = 0L,
        var lastAttemptAtMs: Long = 0L,
    ) : EiosStatusFeedStore {
        override fun readCachedJson(): String? = json
        override fun readLastSuccessAtMs(): Long = lastSuccessAtMs
        override fun readLastAttemptAtMs(): Long = lastAttemptAtMs
        override fun writeSuccess(json: String, atMs: Long) {
            this.json = json
            lastSuccessAtMs = atMs
            lastAttemptAtMs = atMs
        }

        override fun writeAttempt(atMs: Long) {
            lastAttemptAtMs = atMs
        }
    }

    private class FakeFetch {
        var calls = 0
        val timeouts = mutableListOf<Long>()
        var failuresBeforeSuccess = 0
        var result: String? = null
        var exception: Exception? = null

        suspend fun fetch(url: String, timeoutMs: Long): String? {
            calls++
            timeouts += timeoutMs
            assertTrue("feed must use the documented HTTPS endpoint: $url", url.startsWith("https://"))
            exception?.let { throw it }
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                return null
            }
            return result
        }
    }

    private var now = 1_790_000_000_000L
    private val sleeps = mutableListOf<Long>()

    private fun buildFeed(store: FakeStore, fetch: FakeFetch): EiosStatusFeed = EiosStatusFeed(
        store = store,
        fetchBody = fetch::fetch,
        clock = { now },
        sleep = { millis ->
            sleeps += millis
            now += millis
        },
    )

    private fun feedJson(ts: Long, status: String = "up", stale: Boolean = false): String = """
        {"schema":1,"publishedAt":${ts + 40},"source":"nyan-ops","ts":$ts,"ageSec":20,"stale":$stale,
         "status":"$status","alive":${status == "up" || status == "degraded"},"state":"OK",
         "label":"ШТАТНО","reason":"все слои в норме","confirmed":true,
         "loading":{"ttfbMs":99,"freeWorkers":8,"freeLabel":"≥ 8 свободно","serviceMs":141,"queueRatio":1.51,"rps":26.9},
         "uptime":{"h24":100,"d7":100,"d30":100},"session":{"configured":true,"ok":true}}
    """.trimIndent()

    @Test
    fun `first load fetches and caches the public status`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = feedJson(ts = now - 20_000L) }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = false)

        assertNotNull(result)
        assertEquals("up", result!!.summary.status)
        assertEquals(EiosStatusFeed.STATUS_URL, result.endpoint)
        assertEquals(1, fetch.calls)
        assertEquals(1, result.summary.schemaVersion)
        assertEquals("nyan-ops", result.summary.source)
        assertNotNull(store.json)
        assertEquals(now, store.lastSuccessAtMs)
    }

    @Test
    fun `second load inside the cache window does not hit the network`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = feedJson(ts = now - 20_000L) }
        val feed = buildFeed(store, fetch)

        feed.load(force = false)
        now += 60_000L
        val second = feed.load(force = false)

        assertNotNull(second)
        assertEquals(1, fetch.calls)
    }

    @Test
    fun `refreshes the snapshot when the cached copy is older than the trust window`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = feedJson(ts = now - 20_000L) }
        val feed = buildFeed(store, fetch)

        feed.load(force = false)
        val fetchedAt = now
        now = fetchedAt + EiosStatusFeed.CACHE_TRUST_MS + 1L
        feed.load(force = false)

        assertEquals(2, fetch.calls)
    }

    @Test
    fun `returns cached data when refresh fails inside the freshness window`() = runBlocking {
        val json = feedJson(ts = now - 20_000L)
        val store = FakeStore(json = json, lastSuccessAtMs = now - 6 * 60_000L)
        val fetch = FakeFetch().apply { result = null }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = false)

        assertNotNull(result)
        assertEquals("up", result!!.summary.status)
        assertTrue("six-minute-old data is not stale yet", !result.summary.stale)
        assertEquals(1, fetch.calls)
        assertEquals("failed refresh must not wipe the cache", json, store.json)
    }

    @Test
    fun `one failed fetch per minute at most`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = null }
        val feed = buildFeed(store, fetch)

        assertNull(feed.load(force = false))
        assertEquals(1, fetch.calls)

        now += 30_000L
        assertNull(feed.load(force = false))
        assertEquals("one attempt per minute", 1, fetch.calls)

        now += 31_000L
        assertNull(feed.load(force = false))
        assertEquals(2, fetch.calls)
    }

    @Test
    fun `force retries twice with one and two seconds backoff`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply {
            failuresBeforeSuccess = 2
            result = feedJson(ts = now)
        }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = true)

        assertNotNull(result)
        assertEquals(3, fetch.calls)
        assertEquals(listOf(10_000L, 10_000L, 10_000L), fetch.timeouts)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun `force returns the cached snapshot if the network is down`() = runBlocking {
        val json = feedJson(ts = now - 30 * 60_000L)
        val store = FakeStore(json = json, lastSuccessAtMs = now - 30 * 60_000L)
        val fetch = FakeFetch().apply { result = null }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = true)

        assertNotNull(result)
        assertTrue("old snapshot must be flagged stale", result!!.summary.stale)
        assertEquals(3, fetch.calls)
    }

    @Test
    fun `no cache and no network gives null`() = runBlocking {
        val feed = buildFeed(FakeStore(), FakeFetch().apply { result = null })

        assertNull(feed.load(force = false))
    }

    @Test
    fun `computes age from ts instead of the stale server-side ageSec`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = feedJson(ts = now - 10 * 60_000L) }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = false)

        assertEquals(600L, result!!.summary.ageSec)
        assertTrue("ten minutes old is not stale yet", !result.summary.stale)
    }

    @Test
    fun `fresh publication older than twenty minutes is marked stale`() = runBlocking {
        val store = FakeStore()
        val fetch = FakeFetch().apply { result = feedJson(ts = now - 21 * 60_000L) }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = false)

        assertNotNull(result)
        assertTrue(result!!.summary.stale)
    }

    @Test
    fun `transport exceptions do not escape`() = runBlocking {
        val fetch = FakeFetch().apply { exception = java.io.IOException("network down") }
        val feed = buildFeed(FakeStore(), fetch)

        assertNull(feed.load(force = false))
    }

    @Test
    fun `prefers a fresh network snapshot over the cached one`() = runBlocking {
        val oldJson = feedJson(ts = now - 2 * 60 * 60_000L)
        val store = FakeStore(json = oldJson, lastSuccessAtMs = now - 2 * 60 * 60_000L)
        val freshJson = feedJson(ts = now - 5_000L)
        val fetch = FakeFetch().apply { result = freshJson }
        val feed = buildFeed(store, fetch)

        val result = feed.load(force = false)

        assertNotNull(result)
        assertEquals(5L, result!!.summary.ageSec)
        assertTrue(!result.summary.stale)
    }
}
