package ru.nya.nyeios.data.net

import kotlinx.coroutines.CancellationException

/** Result of a successful monitor probe: the summary plus the endpoint that answered. */
data class EiosMonitorFetch(
    val summary: EiosMonitorSummary,
    /** The endpoint URL that produced [summary] (useful for settings diagnostics). */
    val endpoint: String,
)

/** Suspend transport of the monitor probe; [EiosMonitorFailover] is the production implementation. */
fun interface EiosMonitorProbe {
    suspend fun probe(): EiosMonitorFetch?
}

/**
 * Sequential failover between the monitor endpoints.
 *
 * Product requirement: most users can only reach the server through the VPN address
 * `192.168.89.99`, while LAN users reach `192.168.0.209`. So the probe first pokes the LAN
 * address, then falls back to the VPN address, then gives up. No errors, no retries, no
 * user-visible failure — the caller treats `null` as "monitor has no opinion" and fails open.
 *
 * Budgets are deliberately generous: a cold TCP+HTTP round trip measured on a real phone in
 * the same Wi-Fi LAN as the monitor takes ~225 ms (warm pooled connection ~9 ms), so the
 * original 50 ms attempt could never succeed on a cold connection and the feature always
 * reported "monitor unreachable". The per-attempt deadlines are enforced by OkHttp
 * `callTimeout`, which covers the whole call (connect, write, read).
 *
 * This is a deliberately simple cousin of Happy Eyeballs (RFC 8305): attempts are sequential
 * rather than raced, because the two addresses belong to the same monitor and a sequential
 * walk keeps the 10 req/min monitor budget at one request per check when the first address
 * answers. Worst case without a monitor answer is [DEFAULT_ATTEMPTS] sum (~2.5 s), and it only
 * happens on the fail-open path.
 */
class EiosMonitorFailover(
    private val attempts: List<Attempt> = DEFAULT_ATTEMPTS,
    private val fetchBody: suspend (url: String, timeoutMs: Long) -> String?,
) : EiosMonitorProbe {

    data class Attempt(val url: String, val timeoutMs: Long)

    override suspend fun probe(): EiosMonitorFetch? {
        for (attempt in attempts) {
            val body = try {
                fetchBody(attempt.url, attempt.timeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Timeout, no route, TLS/DNS failure, interrupted I/O — just try the next endpoint.
                null
            }
            if (body.isNullOrBlank()) continue
            val summary = EiosMonitorSummary.parse(body) ?: continue
            return EiosMonitorFetch(summary = summary, endpoint = attempt.url)
        }
        return null
    }

    companion object {
        const val LAN_ENDPOINT = "http://192.168.0.209:7790/api/eios/summary"
        const val VPN_ENDPOINT = "http://192.168.89.99:7790/api/eios/summary"

        /**
         * 1000 ms on the LAN address (measured cold round trip ~225 ms, ×4 headroom),
         * then 1500 ms on the VPN address (VPN adds routing and handshake latency).
         */
        val DEFAULT_ATTEMPTS = listOf(
            Attempt(url = LAN_ENDPOINT, timeoutMs = 1_000L),
            Attempt(url = VPN_ENDPOINT, timeoutMs = 1_500L),
        )
    }
}
