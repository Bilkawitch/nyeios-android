package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.onboarding.OnboardingSessionState
import ru.nya.nyeios.ui.onboarding.OnboardingSteps
import ru.nya.nyeios.ui.onboarding.OnboardingTargetId

class OnboardingSessionStateTest {

    @Test
    fun `onboarding spec has 15 sequential steps covering all 4 tabs`() {
        assertEquals(15, OnboardingSteps.size)
        assertEquals((1..15).toList(), OnboardingSteps.map { it.stepNumber })
        assertEquals(0, OnboardingSteps[0].requiredTab)
        assertEquals(0, OnboardingSteps[7].requiredTab)
        assertEquals(0, OnboardingSteps[8].requiredTab)
        assertEquals(1, OnboardingSteps[9].requiredTab)
        assertEquals(1, OnboardingSteps[10].requiredTab)
        assertEquals(2, OnboardingSteps[11].requiredTab)
        assertEquals(2, OnboardingSteps[12].requiredTab)
        assertEquals(3, OnboardingSteps[13].requiredTab)
        assertEquals(3, OnboardingSteps[14].requiredTab)
        assertTrue(OnboardingSteps.last().isFinal)
        assertTrue(OnboardingSteps.dropLast(1).none { it.isFinal })
    }

    @Test
    fun `auto-update step follows schedule steps and precedes feed transition`() {
        val autoUpdate = OnboardingSteps.first { it.textRes == R.string.onboarding_step_8 }
        assertEquals(8, autoUpdate.stepNumber)
        assertEquals(0, autoUpdate.requiredTab)
        assertEquals(OnboardingTargetId.REFRESH_BUTTON, autoUpdate.targetId)

        // Шаги расписания заканчиваются записанными ДЗ, дальше — автообновление,
        // и только потом начинается переход к вкладке «Лента».
        assertEquals(OnboardingTargetId.HOMEWORK_PULL_HANDLE, OnboardingSteps[6].targetId)
        assertEquals(OnboardingTargetId.TAB_FEED, OnboardingSteps[8].targetId)
        assertTrue(OnboardingSteps.take(8).all { it.requiredTab == 0 })
        assertTrue(OnboardingSteps.drop(9).none { it.requiredTab == 0 })
    }

    @Test
    fun `startIfFirstLaunch activates only when not completed`() {
        var completed = false
        val firstRun = OnboardingSessionState(
            totalSteps = OnboardingSteps.size,
            isCompletedOnDisk = { completed },
            saveCompletedOnDisk = { completed = true }
        )

        firstRun.startIfFirstLaunch()
        assertTrue(firstRun.isActive.value)
        assertEquals(0, firstRun.currentStepIndex.value)

        // Calling again in same session after skip does not reopen
        firstRun.skip()
        assertFalse(firstRun.isActive.value)
        assertTrue(completed)
        firstRun.startIfFirstLaunch()
        assertFalse(firstRun.isActive.value)

        // Next launch sees persisted completion
        val secondRun = OnboardingSessionState(
            totalSteps = OnboardingSteps.size,
            isCompletedOnDisk = { completed },
            saveCompletedOnDisk = { completed = true }
        )
        secondRun.startIfFirstLaunch()
        assertFalse(secondRun.isActive.value)
    }

    @Test
    fun `nextStep and prevStep navigate across all 15 steps and complete on last`() {
        var completed = false
        val state = OnboardingSessionState(
            totalSteps = OnboardingSteps.size,
            isCompletedOnDisk = { completed },
            saveCompletedOnDisk = { completed = true }
        )

        state.startIfFirstLaunch()
        state.prevStep()
        assertEquals(0, state.currentStepIndex.value)

        repeat(14) { idx ->
            assertEquals(idx, state.currentStepIndex.value)
            state.nextStep()
        }
        assertEquals(14, state.currentStepIndex.value)
        assertTrue(state.isActive.value)

        state.prevStep()
        assertEquals(13, state.currentStepIndex.value)
        state.nextStep()
        assertEquals(14, state.currentStepIndex.value)

        state.nextStep()
        assertFalse(state.isActive.value)
        assertTrue(completed)
    }

    @Test
    fun `skip works at any intermediate step and replay restarts from step 0`() {
        var completed = false
        val state = OnboardingSessionState(
            totalSteps = OnboardingSteps.size,
            isCompletedOnDisk = { completed },
            saveCompletedOnDisk = { completed = true }
        )

        state.startIfFirstLaunch()
        state.nextStep()
        state.nextStep()
        assertEquals(2, state.currentStepIndex.value)

        state.skip()
        assertFalse(state.isActive.value)
        assertTrue(completed)

        state.replay()
        assertTrue(state.isActive.value)
        assertEquals(0, state.currentStepIndex.value)
    }
}
