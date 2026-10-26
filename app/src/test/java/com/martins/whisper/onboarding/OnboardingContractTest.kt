package com.martins.whisper.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingContractTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `onboarding flag is off by default and resets to off`() {
        assertFalse(Flags.onboardingV2)
        Flags.onboardingV2 = true
        Flags.reset()
        assertFalse(Flags.onboardingV2)
    }

    @Test
    fun `store starts incomplete, persists completion and can reset`() {
        val store = OnboardingStore(context)
        store.reset()
        assertFalse(store.isCompleted())
        store.markCompleted()
        assertTrue(OnboardingStore(context).isCompleted())
        store.reset()
        assertFalse(OnboardingStore(context).isCompleted())
    }

    @Test
    fun `step rules stay inside the flow`() {
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.entries.first())
        assertNull(OnboardingRules.stepBefore(OnboardingStep.WELCOME))
        assertNull(OnboardingRules.stepAfter(OnboardingStep.entries.last()))
        assertEquals("1/7", OnboardingRules.progressLabel(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DONE, OnboardingStep.entries.last())
    }
}
