package ru.nya.nyeios.data.net

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nya.nyeios.data.update.AppVersionProvider

/**
 * Client for the read-only public EIOS monitor API (`:7790/api/eios/summary`).
 *
 * Design constraints:
 * - fail-open: every failure (no route, timeout, 429, garbage body) becomes `null`, never an exception;
 * - cheap: a successful answer is cached for [DEFAULT_CACHE_TTL_MS] = 6 s, matching the monitor's
 *   token-bucket refill rate (10 req/min, one token per 6 s), so repeated refresh taps cannot
 *   exhaust the budget; failures are negative-cached for the same short window for the same reason;
 * - short total deadline: two sequential attempts, 50 ms and 200 ms, enforced with OkHttp
 *   `callTimeout` (covers DNS, connect, write and read of the whole call).
 */
class EiosMonitorClient internal constructor(
    private val probe: EiosMonitorProbe,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: EiosMonitorFetch? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var failedAtMs: Long = 0L

    /**
     * Returns the latest monitor verdict, or null when the monitor gave no usable answer.
     * Never throws. Set [force] to bypass the short cache (settings "check via server").
     */
    suspend fun fetch(force: Boolean = false): EiosMonitorFetch? = mutex.withLock {
        val now = clock()
        val snapshot = cached
        if (!force && snapshot != null && now - cachedAtMs < cacheTtlMs) {
            return@withLock snapshot
        }
        if (!force && snapshot == null && failedAtMs != 0L && now - failedAtMs < cacheTtlMs) {
            return@withLock null
        }

        val fresh = try {
            probe.probe(force)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fresh != null) {
            cached = fresh
            cachedAtMs = clock()
        } else {
            failedAtMs = clock()
        }
        fresh
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 6_000L

        @Volatile
        private var INSTANCE: EiosMonitorClient? = null

        fun getInstance(context: Context): EiosMonitorClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDefault(context.applicationContext).also { INSTANCE = it }
            }

        private fun createDefault(context: Context): EiosMonitorClient {
            val transport = EiosMonitorHttpTransport(
                userAgent = "NyEIOS/" + AppVersionProvider.getVersionName(context)
            )
            val lanFailover = EiosMonitorFailover(fetchBody = transport::fetchBody)
            val statusFeed = EiosStatusFeed(
                store = PrefsEiosStatusFeedStore(context),
                fetchBody = transport::fetchGetBody,
            )
            return EiosMonitorClient(
                probe = EiosMonitorStatusChain(
                    primary = lanFailover,
                    fallback = EiosMonitorProbe { force -> statusFeed.load(force) },
                )
            )
        }
    }
}
