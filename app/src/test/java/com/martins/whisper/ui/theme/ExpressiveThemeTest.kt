package com.martins.whisper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ExpressiveThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `light scheme carries the indigo brand`() {
        assertEquals(Color(0xFF4A46FF), WhisperLightColorScheme.primary)
        assertEquals(Color(0xFF8C89FF), WhisperDarkColorScheme.primary)
    }

    @Test
    fun `theme supplies the indigo expressive scheme in light theme`() {
        var primary = Color.Unspecified
        compose.setContent {
            MartinsWhisperTheme {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        compose.runOnIdle { assertEquals(Color(0xFF4A46FF), primary) }
    }
}
