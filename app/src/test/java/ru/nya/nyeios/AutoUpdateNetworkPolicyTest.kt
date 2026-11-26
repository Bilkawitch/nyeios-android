package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.autoupdate.AutoUpdateNetwork
import ru.nya.nyeios.data.autoupdate.AutoUpdateNetworkPolicy

/**
 * Чистая политика сети автообновления: все комбинации интернета/Wi-Fi/сотовой сети.
 * WIFI_ONLY -> только Wi-Fi; MOBILE_ONLY -> сотовая при отсутствии активного Wi-Fi;
 * ANY -> любая сеть с интернетом.
 */
class AutoUpdateNetworkPolicyTest {

    private fun connectivity(
        hasInternet: Boolean,
        onWifi: Boolean,
        onCellular: Boolean
    ): AutoUpdateNetworkPolicy.Connectivity =
        AutoUpdateNetworkPolicy.Connectivity(hasInternet, onWifi, onCellular)

    private fun allowed(
        policy: AutoUpdateNetwork,
        hasInternet: Boolean,
        onWifi: Boolean,
        onCellular: Boolean
    ): Boolean = AutoUpdateNetworkPolicy.isAllowed(
        policy,
        connectivity(hasInternet, onWifi, onCellular)
    )

    @Test
    fun wifiOnlyAllowsOnlyWifiWithInternet() {
        for (hasInternet in listOf(true, false)) {
            for (onWifi in listOf(true, false)) {
                for (onCellular in listOf(true, false)) {
                    assertEquals(
                        "WIFI_ONLY internet=$hasInternet wifi=$onWifi cell=$onCellular",
                        hasInternet && onWifi,
                        allowed(AutoUpdateNetwork.WIFI_ONLY, hasInternet, onWifi, onCellular)
                    )
                }
            }
        }
    }

    @Test
    fun mobileOnlyAllowsCellularOnlyWithoutWifi() {
        for (hasInternet in listOf(true, false)) {
            for (onWifi in listOf(true, false)) {
                for (onCellular in listOf(true, false)) {
                    assertEquals(
                        "MOBILE_ONLY internet=$hasInternet wifi=$onWifi cell=$onCellular",
                        hasInternet && onCellular && !onWifi,
                        allowed(AutoUpdateNetwork.MOBILE_ONLY, hasInternet, onWifi, onCellular)
                    )
                }
            }
        }
    }

    @Test
    fun anyAllowsAnyNetworkWithInternet() {
        for (hasInternet in listOf(true, false)) {
            for (onWifi in listOf(true, false)) {
                for (onCellular in listOf(true, false)) {
                    assertEquals(
                        "ANY internet=$hasInternet wifi=$onWifi cell=$onCellular",
                        hasInternet,
                        allowed(AutoUpdateNetwork.ANY, hasInternet, onWifi, onCellular)
                    )
                }
            }
        }
    }

    @Test
    fun wifiOnlyRejectsCellularWithInternet() {
        assertFalse(allowed(AutoUpdateNetwork.WIFI_ONLY, true, false, true))
    }

    @Test
    fun mobileOnlyRejectsWifiPreferenceWhenBothTransportsActive() {
        // При активном Wi-Fi мобильный трафик не тратим даже если ядро сообщает о сотовом транспорте.
        assertFalse(allowed(AutoUpdateNetwork.MOBILE_ONLY, true, true, true))
        assertFalse(allowed(AutoUpdateNetwork.MOBILE_ONLY, true, true, false))
    }

    @Test
    fun mobileOnlyAllowsCellularWithInternet() {
        assertTrue(allowed(AutoUpdateNetwork.MOBILE_ONLY, true, false, true))
    }

    @Test
    fun noInternetBlocksEveryPolicy() {
        for (policy in AutoUpdateNetwork.values()) {
            assertFalse(
                "policy=$policy must not run without internet",
                allowed(policy, false, true, true)
            )
        }
    }

    @Test
    fun anyWithInternetButUnknownTransportStillAllowed() {
        // Например, Ethernet/другой транспорт: политика ANY смотрит только на интернет.
        assertTrue(allowed(AutoUpdateNetwork.ANY, true, false, false))
        assertFalse(allowed(AutoUpdateNetwork.WIFI_ONLY, true, false, false))
        assertFalse(allowed(AutoUpdateNetwork.MOBILE_ONLY, true, false, false))
    }
}
