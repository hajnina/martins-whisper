package com.martins.whisper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGateTest {

    @Test
    fun `all granted means ready`() {
        val snapshot = PermissionSnapshot(
            microphone = true,
            overlay = true,
            accessibility = true,
            notifications = true,
        )
        assertTrue(PermissionGate.readyForDictation(snapshot))
        assertEquals("All required permissions granted", PermissionGate.summary(snapshot))
        assertTrue(PermissionGate.missing(snapshot).isEmpty())
    }

    @Test
    fun `missing mic and overlay block dictation but accessibility does not`() {
        val snapshot = PermissionSnapshot(
            microphone = false,
            overlay = true,
            accessibility = false,
            notifications = false,
        )
        assertFalse(PermissionGate.canRecord(snapshot))
        assertTrue(PermissionGate.canShowOverlay(snapshot))
        assertFalse(PermissionGate.readyForDictation(snapshot))
        assertEquals(
            listOf(PermissionItem.MICROPHONE, PermissionItem.ACCESSIBILITY, PermissionItem.NOTIFICATIONS),
            PermissionGate.missing(snapshot),
        )
    }

    @Test
    fun `summary lowercases and spaces items`() {
        val snapshot = PermissionSnapshot(
            microphone = false,
            overlay = false,
            accessibility = true,
            notifications = true,
        )
        assertEquals("Missing: microphone, overlay", PermissionGate.summary(snapshot))
    }

    @Test
    fun `accessibility is read from the enabled services setting`() {
        val pkg = "com.martins.whisper"
        val cls = "com.martins.whisper.accessibility.DictationAccessibilityService"
        assertTrue(
            PermissionGate.accessibilityServiceEnabled(
                "com.other.app/com.other.Service:$pkg/$cls",
                pkg,
                cls,
            ),
        )
        assertTrue(PermissionGate.accessibilityServiceEnabled("$pkg/.accessibility.DictationAccessibilityService", pkg, cls))
        assertFalse(PermissionGate.accessibilityServiceEnabled("com.other.app/com.other.Service", pkg, cls))
        assertFalse(PermissionGate.accessibilityServiceEnabled("", pkg, cls))
        assertFalse(PermissionGate.accessibilityServiceEnabled(null, pkg, cls))
    }
}
