package ru.nya.nyeios.data.net

/**
 * Monitor source chain: the two LAN endpoints first (fast, freshest data), then the public
 * GitHub snapshot as the third fallback.
 *
 * A source may drive the "site is not in shape" popup only when its data is fresh and means
 * something. The GitHub file keeps its last state after the monitor dies, so a snapshot older
 * than [EiosMonitorFreshness.TRUST_AGE_MS] (15 minutes) is treated as no verdict at all: the
 * refresh proceeds silently. The same applies to a stale flag from the monitor and to
 * `status = unknown` from the fallback, exactly as the public feed documentation requires —
 * "404/сеть — не «сайт упал», unknown — «нет данных мониторинга»". An explicit settings check
 * (`force = true`) may still receive the stale snapshot so it can be displayed together with
 * its age or rejected as overdue.
 */
internal class EiosMonitorStatusChain(
    private val primary: EiosMonitorProbe,
    private val fallback: EiosMonitorProbe,
) : EiosMonitorProbe {

    override suspend fun probe(force: Boolean): EiosMonitorFetch? {
        primary.probe(force)?.let { fetch ->
            return if (force || fetch.isTrustedForGate()) fetch else null
        }
        val fallbackFetch = fallback.probe(force) ?: return null
        if (!force && !fallbackFetch.isUsableFallback()) return null
        return fallbackFetch
    }

    /** Fresh, not flagged stale, and recent enough to decide whether to warn about the site. */
    private fun EiosMonitorFetch.isTrustedForGate(): Boolean =
        EiosMonitorFreshness.isTrusted(summary)

    /** The public feed additionally must carry a real verdict, not "no monitoring data". */
    private fun EiosMonitorFetch.isUsableFallback(): Boolean =
        isTrustedForGate() && summary.status != EiosMonitorSummary.STATUS_UNKNOWN
}
