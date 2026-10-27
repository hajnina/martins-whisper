package com.martins.whisper.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
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
        assertEquals("1/9", OnboardingRules.progressLabel(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DONE, OnboardingStep.entries.last())
        assertEquals(9, OnboardingStep.total)
    }

    @Test
    fun `steps follow the exact documented order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.LANGUAGE,
                OnboardingStep.MIC,
                OnboardingStep.OVERLAY,
                OnboardingStep.ACCESSIBILITY,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.MODEL,
                OnboardingStep.FIRST_DICTATION,
                OnboardingStep.DONE,
            ),
            OnboardingStep.entries,
        )
    }

    @Test
    fun `flow keeps all colors in the theme`() {
        val candidates = listOf(
            File("src/main/java/com/martins/whisper/onboarding/OnboardingFlow.kt"),
            File("app/src/main/java/com/martins/whisper/onboarding/OnboardingFlow.kt"),
        )
        val source = candidates.firstOrNull { it.isFile }
            ?: error("OnboardingFlow.kt not found from ${File(".").absolutePath}")
        assertFalse(
            "OnboardingFlow.kt must take colors from MaterialTheme.colorScheme",
            source.readText().contains("Color(0x"),
        )
    }
}
