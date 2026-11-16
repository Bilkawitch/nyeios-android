package ru.nya.nyeios.data.net

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.nya.nyeios.data.AppLocale

/**
 * Plain-HTTP transport for the monitor probe. Kept separate from [EiosMonitorFailover] so the
 * real OkHttp branch (deadline, status mapping, body cap) can be exercised by JVM tests against
 * a loopback server — the failover unit tests only cover the sequencing policy.
 *
 * Per-attempt hard deadline is OkHttp `callTimeout`, which spans DNS, connect, write and read
 * of the whole call. [MAX_BODY_BYTES] caps the response read so a wrong/rogue service on the
 * port cannot make the app buffer an unbounded body.
 */
internal class EiosMonitorHttpTransport(
    private val userAgent: String,
    private val clientFactory: (timeoutMs: Long) -> OkHttpClient = ::defaultClient,
) {

    private val clients = ConcurrentHashMap<Long, OkHttpClient>()

    suspend fun fetchBody(url: String, timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        val client = clients.computeIfAbsent(timeoutMs) { timeout -> clientFactory(timeout) }
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .build()
        val startedAtNs = System.nanoTime()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    logProbeFailure(
                        url = url,
                        timeoutMs = timeoutMs,
                        summary = "HTTP ${response.code}",
                        details = "elapsedMs=${(System.nanoTime() - startedAtNs) / 1_000_000L}"
                    )
                    null
                } else {
                    readCappedBody(response)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logProbeFailure(
                url = url,
                timeoutMs = timeoutMs,
                summary = e.javaClass.simpleName,
                details = "elapsedMs=${(System.nanoTime() - startedAtNs) / 1_000_000L}; ${e.message}"
            )
            // Timeout, no route, cleartext policy, connection reset — the caller tries the next endpoint.
            null
        }
    }

    /**
     * Failures are silent for the user (the gate fails open), so surface the reason in the
     * in-app network journal: without it "Монитор недоступен" is impossible to diagnose in
     * the field. Successes are not logged to keep the journal readable.
     */
    private fun logProbeFailure(url: String, timeoutMs: Long, summary: String, details: String?) {
        val host = url.removePrefix("http://").removePrefix("https://").substringBefore('/')
        NetworkLogger.log(
            level = NetworkLogLevel.WARNING,
            tag = "EIOS MONITOR",
            message = AppLocale.pick(
                "Монитор: $host не ответил — $summary (лимит $timeoutMs мс)",
                "Monitor: $host did not answer — $summary (limit $timeoutMs ms)"
            ),
            details = details,
            isDegraded = true
        )
    }

    private fun readCappedBody(response: Response): String? {
        val body = response.body ?: return null
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_BODY_BYTES) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        body.byteStream().use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (out.size() + read > MAX_BODY_BYTES) return null
                out.write(buffer, 0, read)
            }
        }
        return try {
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Monitor summary is ~0.5 KB; anything bigger means the wrong service answered. */
        const val MAX_BODY_BYTES = 64 * 1024

        private fun defaultClient(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }
}
