package com.martins.whisper.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tracer bullet for the onboarding step order (owner/TASK-S0-contract-theme.md).
 * Data only: no Compose, no Android. Fails if a step is dropped, added or moved.
 */
class OnboardingTracerTest {

    private val expected = listOf(
        OnboardingStep.WELCOME,
        OnboardingStep.LANGUAGE,
        OnboardingStep.MIC,
        OnboardingStep.OVERLAY,
        OnboardingStep.ACCESSIBILITY,
        OnboardingStep.NOTIFICATIONS,
        OnboardingStep.MODEL,
        OnboardingStep.FIRST_DICTATION,
        OnboardingStep.DONE,
    )

    @Test
    fun `tracer walks every onboarding step from welcome to done`() {
        assertEquals(expected, OnboardingStep.entries)

        val walked = mutableListOf<OnboardingStep>()
        var cursor: OnboardingStep? = OnboardingStep.WELCOME
        while (cursor != null) {
            walked += cursor
            cursor = OnboardingRules.stepAfter(cursor)
        }
        assertEquals("stepAfter must walk the exact order", expected, walked)

        val walkedBack = mutableListOf<OnboardingStep>()
        cursor = OnboardingStep.DONE
        while (cursor != null) {
            walkedBack += cursor
            cursor = OnboardingRules.stepBefore(cursor)
        }
        assertEquals("stepBefore must walk the exact reverse order", expected.reversed(), walkedBack)

        assertNull(OnboardingRules.stepBefore(OnboardingStep.WELCOME))
        assertNull(OnboardingRules.stepAfter(OnboardingStep.DONE))
    }

    @Test
    fun `progress label counts nine steps`() {
        assertEquals(9, OnboardingStep.total)
        OnboardingStep.entries.forEachIndexed { index, step ->
            assertEquals("${index + 1}/9", OnboardingRules.progressLabel(step))
        }
    }
}
