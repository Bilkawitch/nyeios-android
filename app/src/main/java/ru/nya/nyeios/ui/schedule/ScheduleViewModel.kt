package ru.nya.nyeios.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.model.AuthSession
import ru.nya.nyeios.data.model.ScheduleUiState
import ru.nya.nyeios.data.model.WeekSchedule
import ru.nya.nyeios.data.repository.EiosRepository
import java.time.LocalDate

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EiosRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    val lastSyncTime: StateFlow<Long> = repository.lastSyncTime

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(getInitialDayIndex())
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    val authSession: StateFlow<AuthSession> = repository.authSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthSession())

    private val _isLoginSheetVisible = MutableStateFlow(false)
    val isLoginSheetVisible: StateFlow<Boolean> = _isLoginSheetVisible.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private var scheduleJob: kotlinx.coroutines.Job? = null

    init {
        loadSchedule(0, forceNetwork = false)
    }

    private fun getInitialDayIndex(): Int {
        val dayOfWeek = LocalDate.now().dayOfWeek.value // 1 (Mon) - 7 (Sun)
        return if (dayOfWeek in 1..6) dayOfWeek - 1 else 0
    }

    fun loadSchedule(offset: Int, forceNetwork: Boolean = false) {
        scheduleJob?.cancel()
        _weekOffset.value = offset
        scheduleJob = viewModelScope.launch {
            val currentSchedule = (_uiState.value as? ScheduleUiState.Success)?.schedule
            if (forceNetwork && currentSchedule != null) {
                _uiState.value = ScheduleUiState.Success(currentSchedule, isRefreshing = true)
            } else if (!forceNetwork) {
                _uiState.value = ScheduleUiState.Loading
            }

            val result = repository.getSchedule(offset, forceNetwork)
            result.onSuccess { schedule ->
                if (offset == 0) {
                    val todayIndex = schedule.days.indexOfFirst { it.isToday }
                    if (todayIndex >= 0) {
                        _selectedDayIndex.value = todayIndex
                    }
                }
                _uiState.value = ScheduleUiState.Success(schedule, isRefreshing = false)
            }.onFailure { error ->
                _uiState.value = ScheduleUiState.Error(
                    message = error.localizedMessage ?: "Не удалось загрузить расписание",
                    cachedSchedule = currentSchedule
                )
            }
        }
    }

    fun nextWeek() {
        val next = _weekOffset.value + 1
        loadSchedule(next)
    }

    fun prevWeek() {
        val prev = _weekOffset.value - 1
        loadSchedule(prev)
    }

    fun currentWeek() {
        _selectedDayIndex.value = getInitialDayIndex()
        loadSchedule(0)
    }

    fun refresh() {
        loadSchedule(_weekOffset.value, forceNetwork = true)
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index
    }

    fun showLoginSheet() {
        _loginError.value = null
        _isLoginSheetVisible.value = true
    }

    fun hideLoginSheet() {
        _isLoginSheetVisible.value = false
        _loginError.value = null
    }

    fun performLogin(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _loginError.value = "Заполните логин и пароль"
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            val result = repository.login(username.trim(), pass)
            _isLoggingIn.value = false

            result.onSuccess {
                hideLoginSheet()
                // Auto reload schedule with new session
                refresh()
            }.onFailure { error ->
                _loginError.value = error.localizedMessage ?: "Ошибка авторизации"
            }
        }
    }

    fun logout() {
        repository.logout()
        hideLoginSheet()
    }
}
