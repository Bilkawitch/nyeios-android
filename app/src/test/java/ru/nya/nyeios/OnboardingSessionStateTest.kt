package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.ui.onboarding.OnboardingSessionState
import ru.nya.nyeios.ui.onboarding.OnboardingSteps

class OnboardingSessionStateTest {

    @Test
    fun `onboarding spec has 14 sequential steps covering all 4 tabs`() {
        assertEquals(14, OnboardingSteps.size)
        assertEquals((1..14).toList(), OnboardingSteps.map { it.stepNumber })
        assertEquals(0, OnboardingSteps[0].requiredTab)
        assertEquals(0, OnboardingSteps[7].requiredTab)
        assertEquals(1, OnboardingSteps[8].requiredTab)
        assertEquals(1, OnboardingSteps[9].requiredTab)
        assertEquals(2, OnboardingSteps[10].requiredTab)
        assertEquals(2, OnboardingSteps[11].requiredTab)
        assertEquals(3, OnboardingSteps[12].requiredTab)
        assertEquals(3, OnboardingSteps[13].requiredTab)
        assertTrue(OnboardingSteps.last().isFinal)
        assertTrue(OnboardingSteps.dropLast(1).none { it.isFinal })
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
    fun `nextStep and prevStep navigate across all 14 steps and complete on last`() {
        var completed = false
        val state = OnboardingSessionState(
            totalSteps = OnboardingSteps.size,
            isCompletedOnDisk = { completed },
            saveCompletedOnDisk = { completed = true }
        )

        state.startIfFirstLaunch()
        state.prevStep()
        assertEquals(0, state.currentStepIndex.value)

        repeat(13) { idx ->
            assertEquals(idx, state.currentStepIndex.value)
            state.nextStep()
        }
        assertEquals(13, state.currentStepIndex.value)
        assertTrue(state.isActive.value)

        state.prevStep()
        assertEquals(12, state.currentStepIndex.value)
        state.nextStep()
        assertEquals(13, state.currentStepIndex.value)

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
