package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.net.EiosMonitorFreshness
import ru.nya.nyeios.data.net.EiosMonitorFreshness.Level
import ru.nya.nyeios.data.net.EiosMonitorSummary

/**
 * Freshness policy shared by the refresh gate and the settings check.
 *
 * The monitor publishes every ~10 minutes, but the GitHub fallback keeps serving the last file
 * after the monitor dies. The thresholds are strict: a snapshot at exactly 15 minutes is still
 * trusted, anything older is stale; at exactly 30 minutes it is still shown with a warning,
 * anything older is reported as an overdue report.
 */
class EiosMonitorFreshnessTest {

    private fun summary(ageSec: Long, stale: Boolean = false) = EiosMonitorSummary(
        ageSec = ageSec,
        stale = stale,
        status = "up",
        alive = true,
    )

    @Test
    fun `fresh snapshot is trusted`() {
        assertEquals(Level.TRUSTED, EiosMonitorFreshness.level(summary(ageSec = 0L)))
        assertEquals(Level.TRUSTED, EiosMonitorFreshness.level(summary(ageSec = 14 * 60L + 59L)))
        assertTrue(EiosMonitorFreshness.isTrusted(summary(ageSec = 5 * 60L)))
    }

    @Test
    fun `fifteen minutes is the last trusted second`() {
        assertEquals(Level.TRUSTED, EiosMonitorFreshness.level(summary(ageSec = 15 * 60L)))
        assertEquals(Level.STALE, EiosMonitorFreshness.level(summary(ageSec = 15 * 60L + 1L)))
    }

    @Test
    fun `snapshot older than fifteen minutes is stale`() {
        assertEquals(Level.STALE, EiosMonitorFreshness.level(summary(ageSec = 16 * 60L)))
        assertEquals(Level.STALE, EiosMonitorFreshness.level(summary(ageSec = 29 * 60L)))
        assertFalse(EiosMonitorFreshness.isTrusted(summary(ageSec = 20 * 60L)))
    }

    @Test
    fun `thirty minutes is the last second shown with a warning`() {
        assertEquals(Level.STALE, EiosMonitorFreshness.level(summary(ageSec = 30 * 60L)))
        assertEquals(Level.EXPIRED, EiosMonitorFreshness.level(summary(ageSec = 30 * 60L + 1L)))
    }

    @Test
    fun `monitor-side stale flag is never trusted even when the age is fresh`() {
        val flagged = summary(ageSec = 60L, stale = true)

        assertEquals(Level.TRUSTED, EiosMonitorFreshness.level(flagged))
        assertFalse(EiosMonitorFreshness.isTrusted(flagged))
    }

    @Test
    fun `negative age is clamped to zero`() {
        assertEquals(Level.TRUSTED, EiosMonitorFreshness.level(summary(ageSec = -5L)))
    }
}
