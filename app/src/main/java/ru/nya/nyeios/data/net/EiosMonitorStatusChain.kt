package ru.nya.nyeios.data.net

/**
 * Monitor source chain: the two LAN endpoints first (fast, freshest data), then the public
 * GitHub snapshot as the third fallback.
 *
 * The fallback is only allowed to drive the "site is not in shape" popup when its data is fresh
 * and means something: a stale snapshot or `status = unknown` fails open (the refresh proceeds
 * silently), exactly as the public feed documentation requires — "404/сеть — не «сайт упал»,
 * unknown — «нет данных мониторинга»". An explicit settings check (`force = true`) may still
 * receive the stale snapshot so it can be displayed together with its age.
 */
internal class EiosMonitorStatusChain(
    private val primary: EiosMonitorProbe,
    private val fallback: EiosMonitorProbe,
) : EiosMonitorProbe {

    override suspend fun probe(force: Boolean): EiosMonitorFetch? {
        primary.probe(force)?.let { return it }
        val fallbackFetch = fallback.probe(force) ?: return null
        if (fallbackFetch.summary.stale || fallbackFetch.summary.status == EiosMonitorSummary.STATUS_UNKNOWN) {
            return if (force) fallbackFetch else null
        }
        return fallbackFetch
    }
}
