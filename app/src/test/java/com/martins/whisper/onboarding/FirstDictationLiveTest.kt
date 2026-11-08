package com.martins.whisper.onboarding

import android.Manifest
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.MainActivity
import com.martins.whisper.accessibility.InsertionPolicy
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.Flags
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.overlay.MicBubbleView
import com.martins.whisper.overlay.OverlayController
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationTelemetry
import com.martins.whisper.service.DictationState
import com.martins.whisper.service.EngineFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowSettings

/**
 * End-to-end over the onboarding step: the FIRST_DICTATION screen starts the real service, the
 * real overlay bubble in the window manager is tapped down/up, the fake engine answers
 * "hello whisper" and the transcript must land in the practice field through the product
 * broadcast while the service reports insert success and the own-package policy never leaks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h851dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class FirstDictationLiveTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var engine: FakeSpeechEngine
    private lateinit var originalEngine: (Context, SettingsStore) -> SpeechEngine
    private lateinit var originalAudio: (Context) -> AudioSource
    private lateinit var originalTelemetry: (Context) -> DictationTelemetry?

    @Before
    fun setUp() {
        Flags.onboardingV2 = true
        OnboardingStore(app).reset()
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ModelRepositoryProvider.resetForTests()
        seedModel(SettingsStore(app).modelId)

        engine = FakeSpeechEngine(
            backend = "fake-jvm",
            respond = { _, language ->
                Transcript(text = "hello whisper", language = language ?: "en")
            },
        )
        originalEngine = EngineFactory.factory
        originalAudio = DictationService.audioSourceFactory
        originalTelemetry = DictationService.telemetryFactory
        EngineFactory.factory = { _, _ -> engine }
        DictationService.audioSourceFactory = { FakeAudioSource() }
        DictationService.telemetryFactory = { null }
    }

    @After
    fun tearDown() {
        EngineFactory.factory = originalEngine
        DictationService.audioSourceFactory = originalAudio
        DictationService.telemetryFactory = originalTelemetry
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
        InsertionPolicy.allowOwnPackageForE2E = false
        ModelRepositoryProvider.resetForTests()
        OnboardingStore(app).reset()
        Flags.reset()
    }

    private fun grantOverlayPermission() {
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(Settings.canDrawOverlays(app))
    }

    private fun refreshSnapshot(scenario: ActivityScenario<MainActivity>) {
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun seedModel(modelId: String) {
        val repo = ModelRepositoryProvider.forContext(app)
        val spec = ModelCatalog.requireById(modelId)
        val file = repo.modelFile(spec)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(64))
        assertTrue(repo.isInstalled(spec))
    }

    @Test
    fun `bubble tap records the transcript into the practice field and the flag never leaks`() {
        grantOverlayPermission()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            launchAndThawClock()
            rule.onNodeWithText("Talk. It types.").assertIsDisplayed()

            walkToFirstDictation(scenario, overlayGranted = true)
            rule.onNodeWithText("Your words land here").assertIsDisplayed()
            rule.onNodeWithText("Try it now").assertIsDisplayed()
            assertFalse(InsertionPolicy.allowOwnPackageForOnboardingTest)

            rule.onNodeWithText("Try it now").performClick()
            rule.waitForIdle()
            assertTrue(InsertionPolicy.allowOwnPackageForOnboardingTest)

            val startIntent = shadowOf(app).getNextStartedService()
            assertNotNull("step must start the dictation service", startIntent)
            assertEquals(DictationService.ACTION_START, startIntent!!.action)
            val controller = Robolectric
                .buildService(DictationService::class.java, startIntent)
                .create()
                .startCommand(0, 0)
            val bubble = bubbleOf(controller.get())
            assertNotNull("overlay bubble must be added to the window manager", bubble)

            bubble!!.dispatchTouchEvent(
                MotionEvent.obtain(1_000, 1_000, MotionEvent.ACTION_DOWN, 36f, 36f, 0),
            )
            bubble.dispatchTouchEvent(
                MotionEvent.obtain(1_000, 1_100, MotionEvent.ACTION_UP, 36f, 36f, 0),
            )
            await("recording after the first tap") {
                DictationService.state.value is DictationState.Recording
            }
            assertEquals(0, engine.received.size)

            bubble.dispatchTouchEvent(
                MotionEvent.obtain(1_000, 1_600, MotionEvent.ACTION_DOWN, 36f, 36f, 0),
            )
            bubble.dispatchTouchEvent(
                MotionEvent.obtain(1_000, 1_700, MotionEvent.ACTION_UP, 36f, 36f, 0),
            )
            await("transcript broadcast") {
                shadowOf(app).broadcastIntents.any { it.action == DictationService.ACTION_TRANSCRIPT }
            }
            val broadcast = shadowOf(app).broadcastIntents
                .last { it.action == DictationService.ACTION_TRANSCRIPT }
            assertEquals(
                "hello whisper",
                broadcast.getStringExtra(DictationService.EXTRA_TRANSCRIPT),
            )
            await("service success") { DictationService.state.value is DictationState.Success }

            rule.waitForIdle()
            rule.onNodeWithText("hello whisper").assertIsDisplayed()
            await("status message surfaced") {
                rule.onAllNodesWithText("Transcript copied to clipboard").fetchSemanticsNodes().isNotEmpty()
            }
            await("own-package flag cleared after success") {
                !InsertionPolicy.allowOwnPackageForOnboardingTest
            }

            controller.destroy()
            assertEquals(DictationState.Idle, DictationService.state.value)
            assertFalse(InsertionPolicy.allowOwnPackageForOnboardingTest)
        }
    }

    @Test
    fun `stop bubble clears the field and the flag and records the stop intent`() {
        grantOverlayPermission()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            launchAndThawClock()
            walkToFirstDictation(scenario, overlayGranted = true)

            rule.onNodeWithText("Try it now").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Stop bubble").performClick()
            rule.waitForIdle()

            await("flag cleared by stop") { !InsertionPolicy.allowOwnPackageForOnboardingTest }
            rule.waitForIdle()
            rule.onNodeWithText("Your words land here").assertIsDisplayed()
            rule.onNodeWithText("Stop bubble").assertDoesNotExist()
            assertEquals(
                DictationService.ACTION_START,
                shadowOf(app).getNextStartedService()?.action,
            )
            assertEquals(
                DictationService.ACTION_STOP,
                shadowOf(app).getNextStartedService()?.action,
            )
        }
    }

    @Test
    fun `without the overlay permission the step shows guidance instead of starting the service`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            launchAndThawClock()

            walkToFirstDictation(scenario, overlayGranted = false)
            rule.onNode(
                hasText("Allow overlay", substring = true),
            ).assertIsDisplayed()
            rule.onNodeWithText("Your words land here").assertIsDisplayed()

            rule.onNodeWithText("Try it now").performClick()
            rule.waitForIdle()
            assertFalse(InsertionPolicy.allowOwnPackageForOnboardingTest)
            assertNull(shadowOf(app).getNextStartedService())

            rule.onNodeWithText("Continue").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Start using it").assertIsDisplayed()
        }
    }

    @Test
    fun `continue from the step reaches done even when no session ran`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            launchAndThawClock()
            walkToFirstDictation(scenario, overlayGranted = false)
            rule.onNodeWithText("Continue").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Start using it").assertIsDisplayed()
            assertFalse(InsertionPolicy.allowOwnPackageForOnboardingTest)
        }
    }

    private fun launchAndThawClock() {
        rule.mainClock.autoAdvance = false
        rule.waitForIdle()
        rule.mainClock.autoAdvance = true
    }

    private fun walkToFirstDictation(
        scenario: ActivityScenario<MainActivity>,
        overlayGranted: Boolean,
    ) {
        refreshSnapshot(scenario)
        tap("Get started")
        tap("Continue")
        tap("Next")
        tap(if (overlayGranted) "Next" else "Skip for now")
        tap("Skip for now")
        tap("Next")
        awaitCompose("model ready") {
            rule.onAllNodesWithText("Model ready.").fetchSemanticsNodes().isNotEmpty()
        }
        tap("Continue")
        awaitCompose("first dictation step") {
            rule.onAllNodesWithText("Try it now").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Try your first dictation").assertIsDisplayed()
    }

    private fun tap(label: String) {
        awaitCompose("step showing '$label'") {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(label).performClick()
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
    }

    private fun awaitCompose(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                rule.waitForIdle()
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for $what")
    }


    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError(
            "timed out waiting for $what; state=${DictationService.state.value}",
        )
    }

    private fun bubbleOf(service: DictationService): MicBubbleView? {
        val field = DictationService::class.java.getDeclaredField("overlay")
        field.isAccessible = true
        return (field.get(service) as OverlayController).bubbleForTest()
    }

    private class FakeAudioSource : AudioSource {
        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            var time = 1_000L
            repeat(FRAMES) {
                onFrame(TONE, time)
                time += 20
            }
        }

        override fun stop() = Unit
        override fun release() = Unit

        companion object {
            const val FRAMES = 60
            val TONE = ShortArray(320) { 6_000.toShort() }
        }
    }
}
