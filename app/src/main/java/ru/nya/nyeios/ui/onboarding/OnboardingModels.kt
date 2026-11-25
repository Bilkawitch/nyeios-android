package ru.nya.nyeios.ui.onboarding

import androidx.annotation.StringRes
import ru.nya.nyeios.R

enum class OnboardingTargetId {
    LESSON_CARD,
    LESSON_ROOM_BUTTON,
    LESSON_NOTE_BUTTON,
    WEEK_AND_DAY,
    REFRESH_BUTTON,
    REMINDERS_CLOCK,
    HOMEWORK_PULL_HANDLE,
    TAB_FEED,
    FEED_FETCH_BUTTON,
    TAB_CURRICULUM,
    CURRICULUM_GRADES,
    TAB_SETTINGS,
    SCHEDULE_AREA,
    CURRICULUM_AREA
}

data class OnboardingStepSpec(
    val stepNumber: Int,
    val requiredTab: Int,
    val targetId: OnboardingTargetId?,
    val fallbackTargetId: OnboardingTargetId? = null,
    @StringRes val textRes: Int,
    val isFinal: Boolean = false
)

val OnboardingSteps: List<OnboardingStepSpec> = listOf(
    OnboardingStepSpec(
        stepNumber = 1,
        requiredTab = 0,
        targetId = OnboardingTargetId.LESSON_CARD,
        fallbackTargetId = OnboardingTargetId.SCHEDULE_AREA,
        textRes = R.string.onboarding_step_1
    ),
    OnboardingStepSpec(
        stepNumber = 2,
        requiredTab = 0,
        targetId = OnboardingTargetId.LESSON_ROOM_BUTTON,
        fallbackTargetId = OnboardingTargetId.SCHEDULE_AREA,
        textRes = R.string.onboarding_step_2
    ),
    OnboardingStepSpec(
        stepNumber = 3,
        requiredTab = 0,
        targetId = OnboardingTargetId.LESSON_NOTE_BUTTON,
        fallbackTargetId = OnboardingTargetId.SCHEDULE_AREA,
        textRes = R.string.onboarding_step_3
    ),
    OnboardingStepSpec(
        stepNumber = 4,
        requiredTab = 0,
        targetId = OnboardingTargetId.WEEK_AND_DAY,
        textRes = R.string.onboarding_step_4
    ),
    OnboardingStepSpec(
        stepNumber = 5,
        requiredTab = 0,
        targetId = OnboardingTargetId.REFRESH_BUTTON,
        textRes = R.string.onboarding_step_5
    ),
    OnboardingStepSpec(
        stepNumber = 6,
        requiredTab = 0,
        targetId = OnboardingTargetId.REMINDERS_CLOCK,
        textRes = R.string.onboarding_step_6
    ),
    OnboardingStepSpec(
        stepNumber = 7,
        requiredTab = 0,
        targetId = OnboardingTargetId.HOMEWORK_PULL_HANDLE,
        textRes = R.string.onboarding_step_7
    ),
    OnboardingStepSpec(
        stepNumber = 8,
        requiredTab = 0,
        targetId = OnboardingTargetId.TAB_FEED,
        textRes = R.string.onboarding_step_8
    ),
    OnboardingStepSpec(
        stepNumber = 9,
        requiredTab = 1,
        targetId = OnboardingTargetId.FEED_FETCH_BUTTON,
        textRes = R.string.onboarding_step_9
    ),
    OnboardingStepSpec(
        stepNumber = 10,
        requiredTab = 1,
        targetId = OnboardingTargetId.TAB_CURRICULUM,
        textRes = R.string.onboarding_step_10
    ),
    OnboardingStepSpec(
        stepNumber = 11,
        requiredTab = 2,
        targetId = OnboardingTargetId.CURRICULUM_GRADES,
        fallbackTargetId = OnboardingTargetId.CURRICULUM_AREA,
        textRes = R.string.onboarding_step_11
    ),
    OnboardingStepSpec(
        stepNumber = 12,
        requiredTab = 2,
        targetId = OnboardingTargetId.TAB_SETTINGS,
        textRes = R.string.onboarding_step_12
    ),
    OnboardingStepSpec(
        stepNumber = 13,
        requiredTab = 3,
        targetId = null,
        textRes = R.string.onboarding_step_13
    ),
    OnboardingStepSpec(
        stepNumber = 14,
        requiredTab = 3,
        targetId = null,
        textRes = R.string.onboarding_step_14,
        isFinal = true
    )
)
