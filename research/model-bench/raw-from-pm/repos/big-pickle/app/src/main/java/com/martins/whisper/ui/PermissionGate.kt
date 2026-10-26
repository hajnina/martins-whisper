package com.martins.whisper.ui

data class PermissionSnapshot(
    val microphone: Boolean,
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
)

enum class PermissionItem { MICROPHONE, OVERLAY, ACCESSIBILITY, NOTIFICATIONS }

object PermissionGate {

    fun missing(snapshot: PermissionSnapshot): List<PermissionItem> = buildList {
        if (!snapshot.microphone) add(PermissionItem.MICROPHONE)
        if (!snapshot.overlay) add(PermissionItem.OVERLAY)
        if (!snapshot.accessibility) add(PermissionItem.ACCESSIBILITY)
        if (!snapshot.notifications) add(PermissionItem.NOTIFICATIONS)
    }

    fun canRecord(snapshot: PermissionSnapshot): Boolean = snapshot.microphone

    fun canShowOverlay(snapshot: PermissionSnapshot): Boolean = snapshot.overlay

    fun readyForDictation(snapshot: PermissionSnapshot): Boolean =
        snapshot.microphone && snapshot.overlay

    fun summary(snapshot: PermissionSnapshot): String =
        if (missing(snapshot).isEmpty()) "All required permissions granted" else {
            "Missing: " + missing(snapshot).joinToString { it.name.lowercase().replace('_', ' ') }
        }
}
