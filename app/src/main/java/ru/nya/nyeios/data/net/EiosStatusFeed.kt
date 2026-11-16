package ru.nya.nyeios.data.net

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent store for the public GitHub status snapshot. An interface so the feed logic can be
 * tested on the JVM without Android preferences.
 */
internal interface EiosStatusFeedStore {
    fun readCachedJson(): String?
    fun readLastSuccessAtMs(): Long
    fun readLastAttemptAtMs(): Long
    fun writeSuccess(json: String, atMs: Long)
    fun writeAttempt(atMs: Long)
}

/** SharedPreferences-backed store; the snapshot is small (~0.5 KB) and survives restarts. */
internal class PrefsEiosStatusFeedStore(context: Context) : EiosStatusFeedStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readCachedJson(): String? = prefs.getString(KEY_JSON, null)

    override fun readLastSuccessAtMs(): Long = prefs.getLong(KEY_LAST_SUCCESS, 0L)

    override fun readLastAttemptAtMs(): Long = prefs.getLong(KEY_LAST_ATTEMPT, 0L)

    override fun writeSuccess(json: String, atMs: Long) {
        prefs.edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_LAST_SUCCESS, atMs)
            .putLong(KEY_LAST_ATTEMPT, atMs)
            .apply()
    }

    override fun writeAttempt(atMs: Long) {
        prefs.edit().putLong(KEY_LAST_ATTEMPT, atMs).apply()
    }

    private companion object {
        const val PREFS_NAME = "eios_status_feed"
        const val KEY_JSON = "summary_json"
        const val KEY_LAST_SUCCESS = "last_success_at"
        const val KEY_LAST_ATTEMPT = "last_attempt_at"
    }
}

/**
 * Third monitor source: a public read-only JSON published by the monitor to GitHub
 * (`Bilkawitch/sttpage`, branch `status`), documented as the "EIOS status JSON" contract.
 *
 * Design (follows the feed documentation):
 * - poll at most once per minute; keep the last valid snapshot locally and serve it while it is
 *   younger than [CACHE_TRUST_MS] (the file itself updates every 10 minutes and GitHub's CDN may
 *   serve a copy up to 5 minutes old, so refetching faster than that is pointless);
 * - a 404 / network failure is not "site down": fall back to the cached snapshot, never throw;
 * - freshness is recomputed from `ts` (or `publishedAt`) on every read: the server-side `ageSec`
 *   only describes the moment of publication, and a cached file may be hours old;
 * - after [STALE_AGE_MS] without a fresh cycle the snapshot is flagged `stale`, and the refresh
 *   gate ignores it (it must not claim the site is dead from a day-old file); an explicit check
 *   (`force = true`) still returns it so the settings digest can show the data with its age.
 *
 * Interactive refresh attempts are bounded to [GATE_TIMEOUT_MS] and a single try; the explicit
 * settings check follows the documented retry policy: [FORCE_TIMEOUT_MS] per attempt, two retries
 * with 1 s / 2 s backoff, overall deadline [FORCE_DEADLINE_MS].
 */
internal class EiosStatusFeed(
    private val store: EiosStatusFeedStore,
    private val fetchBody: suspend (url: String, timeoutMs: Long) -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: suspend (millis: Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()

    suspend fun load(force: Boolean): EiosMonitorFetch? = mutex.withLock {
        val now = clock()
        val cached = readCached(now)
        if (!force) {
            if (cached != null && now - store.readLastSuccessAtMs() < CACHE_TRUST_MS) {
                return@withLock cached
            }
            if (now - store.readLastAttemptAtMs() < MIN_ATTEMPT_INTERVAL_MS) {
                return@withLock cached
            }
        }

        val snapshot = fetchFromNetwork(force)
        if (snapshot == null) {
            store.writeAttempt(clock())
            return@withLock cached
        }
        store.writeSuccess(snapshot.raw, clock())
        return@withLock normalize(snapshot.summary, clock())
    }

    private fun readCached(now: Long): EiosMonitorFetch? =
        store.readCachedJson()
            ?.let { EiosMonitorSummary.parse(it) }
            ?.let { normalize(it, now) }

    /**
     * Returns the network snapshot only when it parses as a valid summary; both 404 and broken
     * JSON behave like "no answer" so the cached snapshot (if any) wins.
     */
    private suspend fun fetchFromNetwork(force: Boolean): NetworkSnapshot? {
        val attempts = if (force) FORCE_ATTEMPTS else 1
        val startedAt = clock()
        repeat(attempts) { index ->
            if (index > 0) {
                val backoff = FORCE_BACKOFF_MS * index
                if (clock() - startedAt + backoff > FORCE_DEADLINE_MS) return null
                sleep(backoff)
            }
            val remaining = FORCE_DEADLINE_MS - (clock() - startedAt)
            if (force && remaining <= 0L) return null
            val timeoutMs = if (force) minOf(FORCE_TIMEOUT_MS, remaining) else GATE_TIMEOUT_MS
            val body = try {
                fetchBody(STATUS_URL, timeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val summary = body?.let { EiosMonitorSummary.parse(it) }
            if (body != null && summary != null) return NetworkSnapshot(body, summary)
        }
        return null
    }

    private fun normalize(summary: EiosMonitorSummary, nowMs: Long): EiosMonitorFetch? {
        val referenceTs = summary.ts.takeIf { it > 0L } ?: summary.publishedAt ?: return null
        val ageMs = (nowMs - referenceTs).coerceAtLeast(0L)
        val freshEnough = ageMs <= STALE_AGE_MS
        if (!freshEnough && summary.status == EiosMonitorSummary.STATUS_UNKNOWN) {
            // Unknown verdict from an old snapshot is worthless: report "no data" instead.
            return null
        }
        return EiosMonitorFetch(
            summary = summary.copy(
                ageSec = ageMs / 1_000L,
                stale = summary.stale || !freshEnough,
            ),
            endpoint = STATUS_URL,
        )
    }

    private data class NetworkSnapshot(val raw: String, val summary: EiosMonitorSummary)

    companion object {
        const val STATUS_URL = "https://raw.githubusercontent.com/Bilkawitch/sttpage/status/summary.json"

        /** The feed documentation asks clients to poll at most once per minute. */
        const val MIN_ATTEMPT_INTERVAL_MS = 60_000L

        /** A snapshot younger than this is served without a network round trip. */
        const val CACHE_TRUST_MS = 5 * 60_000L

        /** "Noticeably more than ~20 minutes" from the feed documentation. */
        const val STALE_AGE_MS = 20 * 60_000L

        /** Interactive refresh: one quick attempt, never freeze the UI for the documented 10 s. */
        const val GATE_TIMEOUT_MS = 4_000L

        /** Explicit settings check: documented per-request timeout. */
        const val FORCE_TIMEOUT_MS = 10_000L

        /** Total budget of the explicit check including retries and backoff. */
        const val FORCE_DEADLINE_MS = 15_000L

        const val FORCE_ATTEMPTS = 3
        const val FORCE_BACKOFF_MS = 1_000L
    }
}
