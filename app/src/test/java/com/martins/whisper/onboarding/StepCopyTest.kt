package com.martins.whisper.onboarding

import com.martins.whisper.ui.PermissionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StepCopyTest {

    private fun check(item: PermissionItem, title: String, why: String, action: String) {
        assertEquals(title, PermissionCopy.title(item))
        assertEquals(why, PermissionCopy.why(item))
        assertEquals(action, PermissionCopy.action(item))
    }

    @Test
    fun `copy matches the approved step literals`() {
        check(
            PermissionItem.MICROPHONE,
            "Let me hear you",
            "So Martins Whisper can turn your voice into text. Audio stays on your phone.",
            "Allow microphone",
        )
        check(
            PermissionItem.OVERLAY,
            "Show the mic bubble",
            "A small floating button over any app, so you can dictate anywhere.",
            "Allow overlay",
        )
        check(
            PermissionItem.ACCESSIBILITY,
            "Type for you",
            "Lets the app put the words into the text box you are using. If Android says restricted: open App info, tap the three dots, Allow restricted settings.",
            "Open accessibility",
        )
        check(
            PermissionItem.NOTIFICATIONS,
            "Stay informed",
            "A quiet notice while listening, so you always know the mic is on.",
            "Allow notifications",
        )
    }
}
