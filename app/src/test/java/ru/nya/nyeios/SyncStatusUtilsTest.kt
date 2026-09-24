package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nya.nyeios.ui.common.SyncState
import ru.nya.nyeios.ui.common.SyncStatusUtils

class SyncStatusUtilsTest {

    @Test
    fun testFreshStateUnderFiveMinutes() {
        val now = 1_000_000_000L
        // 0 ms ago
        assertEquals(SyncState.FRESH, SyncStatusUtils.getSyncState(now, now))
        // 1 min ago
        assertEquals(SyncState.FRESH, SyncStatusUtils.getSyncState(now - 60_000L, now))
        // 4 min 59 sec ago
        assertEquals(SyncState.FRESH, SyncStatusUtils.getSyncState(now - (5 * 60 * 1000L - 1000L), now))
    }

    @Test
    fun testAgedStateBetweenFiveAndThirtyMinutes() {
        val now = 1_000_000_000L
        // Exactly 5 min ago
        assertEquals(SyncState.AGED, SyncStatusUtils.getSyncState(now - 5 * 60 * 1000L, now))
        // 15 min ago
        assertEquals(SyncState.AGED, SyncStatusUtils.getSyncState(now - 15 * 60 * 1000L, now))
        // Exactly 30 min ago
        assertEquals(SyncState.AGED, SyncStatusUtils.getSyncState(now - 30 * 60 * 1000L, now))
    }

    @Test
    fun testStaleStateOverThirtyMinutes() {
        val now = 1_000_000_000L
        // 30 min 1 sec ago
        assertEquals(SyncState.STALE, SyncStatusUtils.getSyncState(now - (30 * 60 * 1000L + 1000L), now))
        // 2 hours ago
        assertEquals(SyncState.STALE, SyncStatusUtils.getSyncState(now - 2 * 3600 * 1000L, now))
        // 0 / never synced
        assertEquals(SyncState.STALE, SyncStatusUtils.getSyncState(0L, now))
        assertEquals(SyncState.STALE, SyncStatusUtils.getSyncState(-1L, now))
    }
}
