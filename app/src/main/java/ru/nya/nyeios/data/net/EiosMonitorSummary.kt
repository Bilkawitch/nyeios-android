package ru.nya.nyeios.data.net

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Compact read-only snapshot of the EIOS availability monitor.
 *
 * Contract source of truth: `nyan-ops` server, `server/admin-daemon/eios_summary.go`
 * (`EiosSummary`), documented in `docs/API.md` §13.8–13.9. The monitor is a separate
 * LAN service (`:7790`) that probes `eios.gukolomna.ru`; this app only reads its verdict.
 *
 * The payload is intentionally parsed defensively: the monitor is a third-party service
 * on a LAN address, so a malformed / truncated / non-JSON answer must degrade to `null`
 * instead of crashing a refresh. Server-side `label` / `reason` / `state` are Russian
 * diagnostic strings and are rendered as data, not translated.
 */
data class EiosMonitorSummary(
    /** Schema version of the public GitHub status feed (`schema` field); null for LAN API. */
    val schemaVersion: Int? = null,
    /** When the monitor published the public status file, unix ms (GitHub feed only). */
    val publishedAt: Long? = null,
    /** Producer id, e.g. `nyan-ops` (GitHub feed only). */
    val source: String? = null,
    /** Time of the last monitor cycle, unix ms. */
    val ts: Long = 0L,
    /** How many seconds ago the last cycle finished. */
    val ageSec: Long = 0L,
    /** true — the scheduler did not run; data is stale (> 3 intervals). */
    val stale: Boolean = false,
    /** Coarse verdict for clients: [STATUS_UP] | [STATUS_DEGRADED] | [STATUS_DOWN] | [STATUS_UNKNOWN]. */
    val status: String = STATUS_UNKNOWN,
    /** Is the portal reachable for an ordinary user. */
    val alive: Boolean = false,
    /** Detailed server-side state, e.g. `OK`, `SATURATED`, `BACKEND_DOWN`. */
    val state: String = "",
    /** Human-readable Russian label, server-side. */
    val label: String = "",
    /** Short Russian reason, server-side. */
    val reason: String = "",
    /** The failure was confirmed by two consecutive monitor cycles. */
    val confirmed: Boolean = false,
    val loading: EiosMonitorLoading = EiosMonitorLoading(),
    val uptime: EiosMonitorUptime? = null,
    val session: EiosMonitorSession? = null,
) {
    /**
     * The only verdict that may skip the warning popup: the monitor says the site is up,
     * reachable for a user, and the data is not stale.
     */
    val isHealthy: Boolean
        get() = status == STATUS_UP && alive && !stale

    companion object {
        const val STATUS_UP = "up"
        const val STATUS_DEGRADED = "degraded"
        const val STATUS_DOWN = "down"
        const val STATUS_UNKNOWN = "unknown"

        /** Parses the monitor JSON; returns null for anything that is not a valid summary object. */
        fun parse(json: String): EiosMonitorSummary? = try {
            if (json.isBlank()) {
                null
            } else {
                val root = JsonParser.parseString(json)
                if (!root.isJsonObject) {
                    null
                } else {
                    val obj = root.asJsonObject
                    EiosMonitorSummary(
                        schemaVersion = obj.intOrNull("schema"),
                        publishedAt = obj.longOrNull("publishedAt"),
                        source = obj.stringOrNull("source"),
                        ts = obj.longOr(0L, "ts"),
                        ageSec = obj.longOr(0L, "ageSec"),
                        stale = obj.boolOr("stale"),
                        status = obj.stringOr(STATUS_UNKNOWN, "status"),
                        alive = obj.boolOr("alive"),
                        state = obj.stringOr("", "state"),
                        label = obj.stringOr("", "label"),
                        reason = obj.stringOr("", "reason"),
                        confirmed = obj.boolOr("confirmed"),
                        loading = parseLoading(obj.objectOrNull("loading")),
                        uptime = obj.objectOrNull("uptime")?.let { u ->
                            EiosMonitorUptime(
                                h24 = u.doubleOr(0.0, "h24"),
                                d7 = u.doubleOr(0.0, "d7"),
                                d30 = u.doubleOr(0.0, "d30"),
                            )
                        },
                        session = obj.objectOrNull("session")?.let { s ->
                            EiosMonitorSession(
                                configured = s.boolOr("configured"),
                                ok = s.boolOr("ok"),
                            )
                        },
                    )
                }
            }
        } catch (_: Exception) {
            null
        }

        private fun parseLoading(obj: JsonObject?): EiosMonitorLoading {
            if (obj == null) return EiosMonitorLoading()
            return EiosMonitorLoading(
                ttfbMs = obj.longOr(0L, "ttfbMs"),
                publicTtfbMs = obj.longOrNull("publicTtfbMs"),
                authTtfbMs = obj.longOrNull("authTtfbMs"),
                freeWorkers = obj.intOrNull("freeWorkers"),
                freeLabel = obj.stringOrNull("freeLabel"),
                serviceMs = obj.longOrNull("serviceMs"),
                queueRatio = obj.doubleOrNull("queueRatio"),
                rps = obj.doubleOrNull("rps"),
                measuredAgeSec = obj.longOrNull("measuredAgeSec"),
            )
        }
    }
}

data class EiosMonitorLoading(
    /** TTFB of the login page (dynamics), ms. */
    val ttfbMs: Long = 0,
    /** TTFB of the public schedule page, ms. */
    val publicTtfbMs: Long? = null,
    /** TTFB of the authenticated page, ms (absent when session cookies are not configured). */
    val authTtfbMs: Long? = null,
    /** Lower-bound estimate of free pool workers (0…8), null when not measured yet. */
    val freeWorkers: Int? = null,
    val freeLabel: String? = null,
    /** Request service time, ms. */
    val serviceMs: Long? = null,
    /** T95/T50 on the deep step — a queue signal. */
    val queueRatio: Double? = null,
    /** Throughput, requests per second. */
    val rps: Double? = null,
    /** Age of the capacity measurement, seconds. */
    val measuredAgeSec: Long? = null,
)

data class EiosMonitorUptime(
    val h24: Double = 0.0,
    val d7: Double = 0.0,
    val d30: Double = 0.0,
)

data class EiosMonitorSession(
    val configured: Boolean = false,
    val ok: Boolean = false,
)

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    val value = get(name) ?: return null
    return if (value.isJsonObject) value.asJsonObject else null
}

private fun JsonObject.stringOr(fallback: String, name: String): String = stringOrNull(name) ?: fallback

private fun JsonObject.stringOrNull(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull || !value.isJsonPrimitive) return null
    return try {
        value.asString
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.boolOr(name: String): Boolean {
    val value = get(name) ?: return false
    if (value.isJsonNull || !value.isJsonPrimitive) return false
    return try {
        value.asBoolean
    } catch (_: Exception) {
        false
    }
}

private fun JsonObject.longOr(fallback: Long, name: String): Long {
    val value = get(name) ?: return fallback
    if (value.isJsonNull || !value.isJsonPrimitive) return fallback
    return try {
        value.asLong
    } catch (_: Exception) {
        fallback
    }
}

private fun JsonObject.longOrNull(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isJsonNull || !value.isJsonPrimitive) return null
    return try {
        value.asLong
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.intOrNull(name: String): Int? = longOrNull(name)?.toInt()

private fun JsonObject.doubleOr(fallback: Double, name: String): Double {
    val value = get(name) ?: return fallback
    if (value.isJsonNull || !value.isJsonPrimitive) return fallback
    return try {
        value.asDouble
    } catch (_: Exception) {
        fallback
    }
}

private fun JsonObject.doubleOrNull(name: String): Double? {
    val value = get(name) ?: return null
    if (value.isJsonNull || !value.isJsonPrimitive) return null
    return try {
        value.asDouble
    } catch (_: Exception) {
        null
    }
}
