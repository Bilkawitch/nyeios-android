package ru.nya.nyeios.ui.curriculum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nya.nyeios.data.model.CurriculumTerm
import ru.nya.nyeios.data.model.CurriculumUiState
import ru.nya.nyeios.data.repository.EiosRepository

class CurriculumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EiosRepository.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow<CurriculumUiState>(CurriculumUiState.Loading)
    val uiState: StateFlow<CurriculumUiState> = _uiState.asStateFlow()

    private val _selectedTermNum = MutableStateFlow<Int?>(null)
    val selectedTermNum: StateFlow<Int?> = _selectedTermNum.asStateFlow()

    private var curriculumJob: kotlinx.coroutines.Job? = null

    init {
        loadCachedOnly()
    }

    private fun loadCachedOnly() {
        val cached = repository.getCachedCurriculum()
        if (cached != null) {
            val chosenTermNum = cached.firstOrNull { it.isCurrent }?.termNum
                ?: cached.firstOrNull()?.termNum
                ?: 0
            _selectedTermNum.value = chosenTermNum
            _uiState.value = CurriculumUiState.Success(
                terms = cached,
                selectedTermNum = chosenTermNum,
                isRefreshing = false
            )
        }
    }

    fun loadCurriculum(forceNetwork: Boolean = false) {
        curriculumJob?.cancel()
        curriculumJob = viewModelScope.launch {
            val currentTerms = (_uiState.value as? CurriculumUiState.Success)?.terms
            if (forceNetwork && currentTerms != null) {
                _uiState.value = CurriculumUiState.Success(
                    terms = currentTerms,
                    selectedTermNum = _selectedTermNum.value ?: currentTerms.firstOrNull()?.termNum ?: 0,
                    isRefreshing = true
                )
            } else if (!forceNetwork) {
                _uiState.value = CurriculumUiState.Loading
            }

            val result = repository.getCurriculum(forceNetwork)
            result.onSuccess { terms ->
                val chosenTermNum = _selectedTermNum.value
                    ?: terms.firstOrNull { it.isCurrent }?.termNum
                    ?: terms.firstOrNull()?.termNum
                    ?: 0

                _selectedTermNum.value = chosenTermNum
                _uiState.value = CurriculumUiState.Success(
                    terms = terms,
                    selectedTermNum = chosenTermNum,
                    isRefreshing = false
                )
            }.onFailure { error ->
                _uiState.value = CurriculumUiState.Error(
                    message = error.localizedMessage ?: "Не удалось загрузить успеваемость",
                    cachedTerms = currentTerms
                )
            }
        }
    }

    fun selectTerm(termNum: Int) {
        _selectedTermNum.value = termNum
        val currentState = _uiState.value
        if (currentState is CurriculumUiState.Success) {
            _uiState.value = currentState.copy(selectedTermNum = termNum)
        }
    }

    fun refresh() {
        loadCurriculum(forceNetwork = true)
    }
}
