package com.martins.whisper.onboarding
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class OnboardingFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test
    fun `flow starts incomplete and completion persists`() {
        val store = OnboardingStore(context)
        store.reset()
        assertFalse(OnboardingStore(context).isCompleted())
        store.markCompleted()
        assertTrue(OnboardingStore(context).isCompleted())
        store.reset()
    }
    @Test
    fun `steps follow the documented order`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.MIC, OnboardingStep.OVERLAY, OnboardingStep.ACCESSIBILITY,
                OnboardingStep.NOTIFICATIONS, OnboardingStep.LANGUAGE, OnboardingStep.DONE),
            OnboardingStep.entries,
        )
        assertEquals("1/7", OnboardingRules.progressLabel(OnboardingStep.WELCOME))
    }
}
