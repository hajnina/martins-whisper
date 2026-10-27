package com.martins.whisper

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import com.martins.whisper.onboarding.OnboardingStore
import com.martins.whisper.ui.PermissionItem
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

/**
 * Tracer bullet through the real onboarding UI: one composition, every step in order,
 * driven by the same buttons a person taps. Fails if a step crashes, is skipped, is
 * unreachable or if finishing does not persist completion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OnboardingWalkthroughTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store: OnboardingStore = OnboardingStore(context)

    @Before
    fun freshInstall() {
        Flags.onboardingV2 = true
        store.reset()
    }

    @After
    fun cleanUp() {
        Flags.reset()
        store.reset()
    }

    private fun click(label: String) {
        compose.waitForIdle()
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun expect(label: String) {
        compose.waitForIdle()
        compose.onNode(hasText(label)).assertExists()
    }

    @Test
    fun `walk every onboarding step in order with the real buttons`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }

            expect("Talk. It types.")
            click("Get started")

            expect("Your language")
            click("Continue")

            for (item in listOf(
                PermissionItem.MICROPHONE,
                PermissionItem.OVERLAY,
                PermissionItem.ACCESSIBILITY,
                PermissionItem.NOTIFICATIONS,
            )) {
                expect(com.martins.whisper.onboarding.PermissionCopy.title(item))
                click("Skip for now")
            }

            expect("Choose your model")
            click("Continue")

            expect("Try your first dictation")
            click("Continue")

            expect("You are set")
            click("Start using it")

            assertTrue("finishing onboarding must persist completion", store.isCompleted())
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
