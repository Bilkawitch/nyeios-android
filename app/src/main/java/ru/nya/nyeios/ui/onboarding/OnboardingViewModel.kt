package ru.nya.nyeios.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure state machine for the 14-step first-run onboarding tour.
 */
class OnboardingSessionState(
    private val totalSteps: Int,
    private val isCompletedOnDisk: () -> Boolean,
    private val saveCompletedOnDisk: () -> Unit
) {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private var autoStartAttempted = false

    fun startIfFirstLaunch() {
        if (autoStartAttempted) return
        autoStartAttempted = true
        if (!isCompletedOnDisk()) {
            _currentStepIndex.value = 0
            _isActive.value = true
        }
    }

    fun replay() {
        autoStartAttempted = true
        _currentStepIndex.value = 0
        _isActive.value = true
    }

    fun nextStep() {
        if (!_isActive.value) return
        val next = _currentStepIndex.value + 1
        if (next < totalSteps) {
            _currentStepIndex.value = next
        } else {
            complete()
        }
    }

    fun prevStep() {
        if (!_isActive.value) return
        val prev = _currentStepIndex.value - 1
        if (prev >= 0) {
            _currentStepIndex.value = prev
        }
    }

    fun skip() {
        saveCompletedOnDisk()
        _isActive.value = false
    }

    fun complete() {
        saveCompletedOnDisk()
        _isActive.value = false
    }
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val sessionState = OnboardingSessionState(
        totalSteps = OnboardingSteps.size,
        isCompletedOnDisk = { prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false) },
        saveCompletedOnDisk = {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }
    )

    val isActive: StateFlow<Boolean> = sessionState.isActive
    val currentStepIndex: StateFlow<Int> = sessionState.currentStepIndex

    fun startIfFirstLaunch() {
        sessionState.startIfFirstLaunch()
    }

    fun replay() {
        sessionState.replay()
    }

    fun nextStep() {
        sessionState.nextStep()
    }

    fun prevStep() {
        sessionState.prevStep()
    }

    fun skip() {
        sessionState.skip()
    }

    companion object {
        const val PREFS_NAME = "nyeios_onboarding_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_v1"
    }
}
