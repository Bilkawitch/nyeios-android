package ru.nya.nyeios.ui.common

enum class SyncState {
    FRESH,   // < 5 min: Green
    AGED,    // 5..30 min: Amber / Yellow
    STALE    // > 30 min: Red, triggers blinking refresh button
}

object SyncStatusUtils {
    const val FRESH_THRESHOLD_MS = 5 * 60 * 1000L
    const val STALE_THRESHOLD_MS = 30 * 60 * 1000L

    fun getSyncState(lastSyncTime: Long, currentTimeMs: Long = System.currentTimeMillis()): SyncState {
        if (lastSyncTime <= 0L) return SyncState.STALE
        val elapsed = maxOf(0L, currentTimeMs - lastSyncTime)
        return when {
            elapsed < FRESH_THRESHOLD_MS -> SyncState.FRESH
            elapsed <= STALE_THRESHOLD_MS -> SyncState.AGED
            else -> SyncState.STALE
        }
    }
}
