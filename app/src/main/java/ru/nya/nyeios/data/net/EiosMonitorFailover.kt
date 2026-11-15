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
 * address with a tight 50 ms budget, then falls back to the VPN address with 200 ms, then
 * gives up. No errors, no retries, no user-visible failure — the caller treats `null` as
 * "monitor has no opinion" and fails open.
 *
 * This is a deliberately simpler cousin of Happy Eyeballs (RFC 8305): attempts are
 * sequential rather than raced, because the two addresses belong to the same monitor and
 * the budget (250 ms total) is small enough that a race would burn the 10 req/min budget
 * twice per refresh. Per-attempt deadlines are enforced by OkHttp `callTimeout`, which
 * covers the whole call (connect, write, read), see OkHttp docs.
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

        /** 50 ms on the LAN address, then 200 ms on the VPN address. */
        val DEFAULT_ATTEMPTS = listOf(
            Attempt(url = LAN_ENDPOINT, timeoutMs = 50L),
            Attempt(url = VPN_ENDPOINT, timeoutMs = 200L),
        )
    }
}
