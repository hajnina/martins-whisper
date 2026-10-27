package com.martins.whisper

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import com.martins.whisper.onboarding.OnboardingStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Fresh-state launch per entry point (owner brief, STEERING-PLAYBOOK 6c):
 * not onboarded -> onboarding. This is the test whose absence let a launch crash reach CI,
 * so it launches the real MainActivity, not a composable stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class EntryPointLaunchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun onboardingFlagOn() {
        Flags.onboardingV2 = true
        OnboardingStore(context).reset()
    }

    @After
    fun cleanUp() {
        Flags.reset()
        OnboardingStore(context).reset()
    }

    @Test
    fun `fresh install with the flag on opens the onboarding welcome`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
            compose.waitForIdle()
            compose.onNodeWithText("Talk. It types.").assertIsDisplayed()
        }
    }
}
