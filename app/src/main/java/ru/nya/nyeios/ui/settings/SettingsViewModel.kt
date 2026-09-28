package ru.nya.nyeios.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.model.UpdateInfo
import ru.nya.nyeios.data.net.EiosLastGetInfo
import ru.nya.nyeios.data.net.EndpointHealthItem
import ru.nya.nyeios.data.net.EndpointStatus
import ru.nya.nyeios.data.net.NetworkMetricsTracker
import ru.nya.nyeios.data.net.PingResult
import ru.nya.nyeios.data.repository.EiosRepository
import ru.nya.nyeios.data.update.AppVersionProvider
import ru.nya.nyeios.data.update.GithubRateLimitState
import ru.nya.nyeios.data.update.UpdateRepository

import ru.nya.nyeios.R

enum class SettingsSubtab(val titleResId: Int, val title: String) {
    GENERAL(R.string.settings_tab_general, "ОБЩЕЕ"),
    THEME(R.string.settings_tab_theme, "ТЕМА"),
    VERSION(R.string.settings_tab_version, "ВЕРСИЯ"),
    LANGUAGE(R.string.settings_tab_language, "ЯЗЫК")
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
    val isFetchingIp: Boolean = false,
    val isCheckingEndpoints: Boolean = false,
    val endpointsHealth: List<EndpointHealthItem> = listOf(
        EndpointHealthItem("timetable", "РАСПИСАНИЕ", "/eios/contacts/timetable/"),
        EndpointHealthItem("feed", "ЖИВАЯ ЛЕНТА", "/eios/"),
        EndpointHealthItem("curriculum", "УСПЕВАЕМОСТЬ (БРС)", "/eios/contacts/curriculum/")
    ),
    val currentVersion: String = "",
    val isCheckingUpdate: Boolean = false,
    val updateCheckStatus: String? = null,
    val availableUpdate: UpdateInfo? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val metricsTracker = NetworkMetricsTracker.getInstance(application)
    private val updateRepository = UpdateRepository.getInstance(application)
    private val eiosRepository = EiosRepository.getInstance(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            lastGetInfo = metricsTracker.lastGetInfo.value,
            githubRateLimit = updateRepository.getSavedRateLimit(),
            externalIp = metricsTracker.externalIp.value,
            localIp = metricsTracker.getLocalIpAddress(),
            networkType = metricsTracker.getNetworkTypeName(),
            isVpnActive = metricsTracker.isVpnActive(),
            currentVersion = AppVersionProvider.getVersionName(application)
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
        when (subtab) {
            SettingsSubtab.GENERAL -> refreshLocalNetworkInfo()
            SettingsSubtab.VERSION -> {
                val rateLimit = updateRepository.getSavedRateLimit()
                val ver = AppVersionProvider.getVersionName(getApplication())
                _uiState.update {
                    it.copy(
                        githubRateLimit = rateLimit,
                        currentVersion = ver
                    )
                }
            }
            SettingsSubtab.THEME -> Unit
            SettingsSubtab.LANGUAGE -> Unit
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateCheckStatus = "ПРОВЕРКА ОБНОВЛЕНИЙ НА GITHUB..."
                )
            }
            try {
                val update = updateRepository.checkForUpdate(forceCheck = true)
                val rateLimit = updateRepository.getSavedRateLimit()
                if (update != null) {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = update,
                            updateCheckStatus = "ДОСТУПНО ОБНОВЛЕНИЕ: v${update.version}",
                            githubRateLimit = rateLimit
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = null,
                            updateCheckStatus = "УСТАНОВЛЕНА ПОСЛЕДНЯЯ ВЕРСИЯ (ОБНОВЛЕНИЙ НЕТ)",
                            githubRateLimit = rateLimit
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateCheckStatus = "ОШИБКА ПРОВЕРКИ: ${e.message}"
                    )
                }
            }
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

    /**
     * Ручная проверка доступности основных эндпоинтов (Расписание, Лента, БРС).
     * Запускается только по явному нажатию кнопки пользователем.
     * Проверка выполняется строго ПООЧЕРЕДНО (не параллельно), чтобы сервер не дропал соединение.
     */
    fun checkEndpoints() {
        if (_uiState.value.isCheckingEndpoints) return
        viewModelScope.launch {
            val endpointIds = listOf("timetable", "feed", "curriculum")

            _uiState.update { state ->
                state.copy(
                    isCheckingEndpoints = true,
                    endpointsHealth = state.endpointsHealth.mapIndexed { index, item ->
                        if (index == 0) {
                            item.copy(
                                status = EndpointStatus.CHECKING,
                                latencyMs = null,
                                message = "Кидаю GET на ${item.path} с таймаутом в 15с (1\\15с)"
                            )
                        } else {
                            item.copy(
                                status = EndpointStatus.PENDING,
                                latencyMs = null,
                                message = "В очереди на проверку..."
                            )
                        }
                    }
                )
            }

            for (id in endpointIds) {
                val targetItem = _uiState.value.endpointsHealth.find { it.id == id } ?: continue
                val path = targetItem.path

                // Устанавливаем текущий как CHECKING
                _uiState.update { state ->
                    state.copy(
                        endpointsHealth = state.endpointsHealth.map { item ->
                            if (item.id == id) {
                                item.copy(
                                    status = EndpointStatus.CHECKING,
                                    latencyMs = null,
                                    message = "Кидаю GET на $path с таймаутом в 15с (1\\15с)"
                                )
                            } else item
                        }
                    )
                }

                // Счетчик в реальном времени с шагом в 1 секунду
                var second = 1
                val tickerJob = launch {
                    while (isActive && second <= 15) {
                        _uiState.update { state ->
                            state.copy(
                                endpointsHealth = state.endpointsHealth.map { item ->
                                    if (item.id == id && item.status == EndpointStatus.CHECKING) {
                                        item.copy(
                                            message = "Кидаю GET на $path с таймаутом в 15с (${second}\\15с)"
                                        )
                                    } else item
                                }
                            )
                        }
                        delay(1000)
                        second++
                    }
                }

                val result = try {
                    eiosRepository.checkEndpointHealth(id)
                } finally {
                    tickerJob.cancel()
                }

                _uiState.update { state ->
                    state.copy(
                        endpointsHealth = state.endpointsHealth.map { item ->
                            if (item.id == id) result else item
                        }
                    )
                }
            }

            _uiState.update { it.copy(isCheckingEndpoints = false) }
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
