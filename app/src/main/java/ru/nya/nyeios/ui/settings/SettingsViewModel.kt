package ru.nya.nyeios.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.data.update.GithubRateLimitState
import ru.nya.nyeios.data.update.UpdateRepository

enum class SettingsSubtab(val title: String) {
    GENERAL("ОБЩЕЕ"),
    THEME("ТЕМА")
}

data class SettingsUiState(
    val currentSubtab: SettingsSubtab = SettingsSubtab.GENERAL,
    val pingResult: PingResult = PingResult.Idle,
    val lastGetInfo: EiosLastGetInfo = EiosLastGetInfo(null, null, null),
    val githubRateLimit: GithubRateLimitState = GithubRateLimitState(null, null, false),
    val externalIp: String? = null,
    val localIp: String? = null,
    val networkType: String = "НЕИЗВЕСТНО",
    val isVpnActive: Boolean = false,
    val isMeasuringPing: Boolean = false,
    val isFetchingIp: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val metricsTracker = NetworkMetricsTracker.getInstance(application)
    private val updateRepository = UpdateRepository.getInstance(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            lastGetInfo = metricsTracker.lastGetInfo.value,
            githubRateLimit = updateRepository.getSavedRateLimit(),
            externalIp = metricsTracker.externalIp.value,
            localIp = metricsTracker.getLocalIpAddress(),
            networkType = metricsTracker.getNetworkTypeName(),
            isVpnActive = metricsTracker.isVpnActive()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Observe last GET updates reactively
        viewModelScope.launch {
            metricsTracker.lastGetInfo.collect { getInfo ->
                _uiState.update { it.copy(lastGetInfo = getInfo) }
            }
        }

        // Observe ping result updates reactively
        viewModelScope.launch {
            metricsTracker.pingResult.collect { ping ->
                _uiState.update {
                    it.copy(
                        pingResult = ping,
                        isMeasuringPing = ping is PingResult.Measuring
                    )
                }
            }
        }

        // Observe external IP updates reactively
        viewModelScope.launch {
            metricsTracker.externalIp.collect { ip ->
                _uiState.update { it.copy(externalIp = ip) }
            }
        }

        // Initial fetch of diagnostics
        refreshDiagnostics()
    }

    fun selectSubtab(subtab: SettingsSubtab) {
        _uiState.update { it.copy(currentSubtab = subtab) }
        if (subtab == SettingsSubtab.GENERAL) {
            refreshLocalNetworkInfo()
        }
    }

    fun refreshDiagnostics() {
        refreshLocalNetworkInfo()
        measurePing()
        fetchExternalIp()
    }

    fun measurePing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isMeasuringPing = true) }
            metricsTracker.measureEiosPing()
            _uiState.update { it.copy(isMeasuringPing = false) }
        }
    }

    fun fetchExternalIp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingIp = true) }
            metricsTracker.fetchExternalIp()
            _uiState.update { it.copy(isFetchingIp = false) }
        }
    }

    private fun refreshLocalNetworkInfo() {
        val rateLimit = updateRepository.getSavedRateLimit()
        val localIp = metricsTracker.getLocalIpAddress()
        val netType = metricsTracker.getNetworkTypeName()
        val vpn = metricsTracker.isVpnActive()

        _uiState.update {
            it.copy(
                githubRateLimit = rateLimit,
                localIp = localIp,
                networkType = netType,
                isVpnActive = vpn
            )
        }
    }
}
