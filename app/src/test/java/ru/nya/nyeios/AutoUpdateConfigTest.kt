package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.autoupdate.AutoUpdateConfig
import ru.nya.nyeios.data.autoupdate.AutoUpdateNetwork
import ru.nya.nyeios.data.autoupdate.normalized

/**
 * Инварианты конфига автообновления: допустимые интервалы и запрет уведомлений
 * при выключенной функции.
 */
class AutoUpdateConfigTest {

    @Test
    fun defaultConfigIsSafe() {
        val default = AutoUpdateConfig.DEFAULT
        assertFalse(default.enabled)
        assertEquals(3, default.intervalHours)
        assertEquals(AutoUpdateNetwork.ANY, default.network)
        assertFalse(default.notificationsEnabled)
        assertEquals(AutoUpdateConfig(), default)
    }

    @Test
    fun allowedIntervalsAreExactlyTheSupportedSet() {
        assertEquals(listOf(1, 2, 3, 6, 12, 24), AutoUpdateConfig.ALLOWED_INTERVALS)
    }

    @Test
    fun normalizedKeepsEveryAllowedInterval() {
        AutoUpdateConfig.ALLOWED_INTERVALS.forEach { hours ->
            val normalized = AutoUpdateConfig(enabled = true, intervalHours = hours).normalized()
            assertEquals("interval $hours must be preserved", hours, normalized.intervalHours)
        }
    }

    @Test
    fun normalizedFallsBackToDefaultIntervalForUnknownValues() {
        listOf(-24, -1, 0, 4, 5, 7, 8, 11, 13, 48, 100, Int.MAX_VALUE, Int.MIN_VALUE).forEach { hours ->
            val normalized = AutoUpdateConfig(enabled = true, intervalHours = hours).normalized()
            assertEquals(
                "interval $hours must fall back to default",
                AutoUpdateConfig.DEFAULT.intervalHours,
                normalized.intervalHours
            )
        }
    }

    @Test
    fun normalizedDropsNotificationsWhenDisabled() {
        val config = AutoUpdateConfig(
            enabled = false,
            intervalHours = 6,
            network = AutoUpdateNetwork.WIFI_ONLY,
            notificationsEnabled = true
        ).normalized()

        assertFalse(config.enabled)
        assertFalse(config.notificationsEnabled)
        assertEquals(6, config.intervalHours)
        assertEquals(AutoUpdateNetwork.WIFI_ONLY, config.network)
    }

    @Test
    fun normalizedKeepsNotificationsWhenEnabled() {
        val config = AutoUpdateConfig(
            enabled = true,
            intervalHours = 12,
            network = AutoUpdateNetwork.MOBILE_ONLY,
            notificationsEnabled = true
        ).normalized()

        assertTrue(config.enabled)
        assertTrue(config.notificationsEnabled)
        assertEquals(12, config.intervalHours)
        assertEquals(AutoUpdateNetwork.MOBILE_ONLY, config.network)
    }

    @Test
    fun normalizedIsIdempotent() {
        val cases = listOf(
            AutoUpdateConfig(),
            AutoUpdateConfig(enabled = true, intervalHours = 5, notificationsEnabled = true),
            AutoUpdateConfig(enabled = false, intervalHours = 24, notificationsEnabled = true),
            AutoUpdateConfig(enabled = true, network = AutoUpdateNetwork.WIFI_ONLY)
        )
        cases.forEach { config ->
            val once = config.normalized()
            assertEquals(once, once.normalized())
        }
    }

    @Test
    fun defaultConfigIsAlreadyNormalized() {
        assertEquals(AutoUpdateConfig.DEFAULT, AutoUpdateConfig.DEFAULT.normalized())
    }

    @Test
    fun normalizationDoesNotMutateOriginal() {
        val original = AutoUpdateConfig(enabled = false, intervalHours = 7, notificationsEnabled = true)
        original.normalized()
        assertEquals(7, original.intervalHours)
        assertTrue(original.notificationsEnabled)
    }
}
