package com.martins.whisper

import android.app.Activity
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import com.martins.whisper.onboarding.OnboardingStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Fresh-state launch per entry point (owner brief): the real MainActivity is started, not a
 * composable stub, so a launch crash fails here. Not onboarded must show the Compose onboarding;
 * completed onboarding must show the app home (still the legacy settings view until S3 lands).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OnboardingLaunchTest {

    private val store: OnboardingStore = OnboardingStore(ApplicationProvider.getApplicationContext())

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
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().resume()
        val activity = controller.get()

        assertTrue(
            "not onboarded must show the Compose onboarding, got ${contentRoot(activity)}",
            contentRoot(activity) is ComposeView,
        )
        controller.pause().resume()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `completed onboarding skips onboarding and opens the home screen`() {
        store.markCompleted()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().resume().get()

        assertFalse(
            "completed onboarding must never show the Compose onboarding again",
            contentRoot(activity) is ComposeView,
        )
        assertTrue(
            "completed onboarding lands on the app home, got ${contentRoot(activity)}",
            contentRoot(activity) is ScrollView,
        )
        assertFalse(activity.isFinishing)
    }
}
