package com.martins.whisper

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import com.martins.whisper.onboarding.OnboardingStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OnboardingLaunchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store: OnboardingStore = OnboardingStore(context)

    @Before
    fun onboardingFlagOn() {
        Flags.onboardingV2 = true
        store.reset()
    }

    @After
    fun resetFlags() {
        Flags.reset()
        store.reset()
    }

    private fun contentRoot(activity: Activity) =
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

    @Test
    fun `fresh install lands on the compose onboarding and survives resume`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.mainClock.autoAdvance = false
            compose.waitForIdle()
            compose.onNodeWithText("Talk. It types.").assertIsDisplayed()
            compose.onNode(hasText("Ready", substring = true)).assertDoesNotExist()
            scenario.onActivity { activity ->
                assertTrue(contentRoot(activity) is ComposeView)
                assertFalse(activity.isFinishing)
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun `completed onboarding opens home, not onboarding or legacy settings`() {
        store.markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(contentRoot(activity) is ComposeView)
                assertFalse("legacy settings must not be the main view", contentRoot(activity) is ScrollView)
            }
            compose.waitForIdle()
            compose.onNode(hasText("Ready", substring = true)).assertIsDisplayed()
            compose.onNodeWithText("Talk. It types.").assertDoesNotExist()
        }
    }

    @Test
    fun `gear opens legacy settings and system back returns home`() {
        store.markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.waitForIdle()
            scenario.onActivity { activity ->
                assertTrue("gear must open the legacy settings view", contentRoot(activity) is ScrollView)
            }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                assertFalse(contentRoot(activity) is ScrollView)
            }
            compose.onNode(hasText("Ready", substring = true)).assertIsDisplayed()
        }
    }

    @Test
    fun `restart onboarding clears completion and returns to onboarding`() {
        store.markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Restart onboarding").performClick()
            compose.waitForIdle()

            assertFalse(store.isCompleted())
            scenario.onActivity { assertFalse(it.isFinishing) }
            compose.onNodeWithText("Talk. It types.").assertIsDisplayed()
        }
    }
}
