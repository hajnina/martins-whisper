package com.martins.whisper.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.martins.whisper.ui.PermissionItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Every onboarding step must compose without crashing (owner brief: fresh-state launch per
 * entry point + each step renders). Fails if a screen is removed or blows up at composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OnboardingScreensRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { MartinsOnboardingTheme { content() } }
        compose.waitForIdle()
    }

    @Test
    fun `welcome renders the hero copy`() {
        render { WelcomeScreen(onStart = {}) }
        compose.onNodeWithText("Talk. It types.").assertIsDisplayed()
    }

    @Test
    fun `language renders`() {
        render {
            LanguageStepScreen(selected = "en", onSelect = {}, onNext = {}, onBack = {})
        }
        compose.onNodeWithText("Your language").assertIsDisplayed()
    }

    @Test
    fun `mic permission renders`() = permissionRenders(PermissionItem.MICROPHONE)

    @Test
    fun `overlay permission renders`() = permissionRenders(PermissionItem.OVERLAY)

    @Test
    fun `accessibility permission renders`() = permissionRenders(PermissionItem.ACCESSIBILITY)

    @Test
    fun `notifications permission renders`() = permissionRenders(PermissionItem.NOTIFICATIONS)

    private fun permissionRenders(item: PermissionItem) {
        render {
            PermissionStepScreen(
                item = item,
                granted = false,
                onOpen = {},
                onNext = {},
                onBack = {},
            )
        }
        compose.onNodeWithText(PermissionCopy.title(item)).assertIsDisplayed()
    }

    @Test
    fun `model step renders and can be continued`() {
        render { ModelStepScreen(onNext = {}, onBack = {}) }
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun `first dictation step renders and can be continued`() {
        render { FirstDictationScreen(onNext = {}, onBack = {}) }
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun `done step renders`() {
        render { DoneStepScreen(onFinish = {}) }
        compose.onNodeWithText("You are set").assertIsDisplayed()
    }
}
