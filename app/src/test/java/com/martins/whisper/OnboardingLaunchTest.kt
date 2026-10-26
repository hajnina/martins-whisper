package com.martins.whisper

import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.core.Flags
import com.martins.whisper.onboarding.OnboardingStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OnboardingLaunchTest {

    @Before
    fun onboardingIncomplete() {
        Flags.onboardingV2 = true
        OnboardingStore(ApplicationProvider.getApplicationContext()).reset()
    }

    @After
    fun resetFlags() {
        Flags.reset()
        OnboardingStore(ApplicationProvider.getApplicationContext()).reset()
    }

    @Test
    fun `resuming while onboarding is shown does not touch legacy views`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().resume()
        val activity = controller.get()

        controller.pause().resume()

        assertFalse(activity.isFinishing)
    }
}
