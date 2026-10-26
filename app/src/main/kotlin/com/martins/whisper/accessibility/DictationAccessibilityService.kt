package com.martins.whisper.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.martins.whisper.R
import com.martins.whisper.core.TextInsertionPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DictationAccessibilityService : AccessibilityService(), TextInserter {

    @Volatile
    private var lastFocused: NodeSnapshot? = null

    @Volatile
    private var startTarget: NodeSnapshot? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        AccessibilityInserter.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) {
            lastFocused = null
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> {
                val source = event.source ?: return
                try {
                    if (source.isEditable && source.isEnabled && source.isVisibleToUser) {
                        lastFocused = source.toSnapshot()
                    } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                        lastFocused = null
                    }
                } finally {
                    source.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> lastFocused = null
        }
    }

    override fun onInterrupt() {
        lastFocused = null
        startTarget = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityInserter.detach(this)
        lastFocused = null
        startTarget = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityInserter.detach(this)
        lastFocused = null
        startTarget = null
        super.onDestroy()
    }

    /** Remembers the field that was focused when the user started dictating. Main thread only. */
    fun captureTargetAtStart(): Boolean {
        val captured = try {
            obtainTarget()?.let { node ->
                try {
                    node.toSnapshot()
                } finally {
                    node.recycle()
                }
            } ?: lastFocused
        } catch (_: Throwable) {
            null
        }
        startTarget = captured
        AccessibilityInserter.noteTarget(captured?.packageName)
        return captured != null
    }

    override fun insert(text: String): InsertResult {
        if (text.isEmpty()) return InsertResult.Failed("empty text")
        val captured = startTarget.also { startTarget = null }
            ?: return copyFallback(text, "no target field at start")
        val node = obtainTarget() ?: return InsertResult.Rejected("target field disappeared")
        try {
            val live = node.toSnapshot()
            if (!TargetGuard.stillSameTarget(captured, live)) {
                AccessibilityInserter.noteTargetChanged()
                return InsertResult.Rejected("target changed")
            }
            return when (val route = InsertionPolicy.route(live, packageName, text)) {
                InsertionRoute.SetText -> setText(node, live, text)
                InsertionRoute.Paste -> paste(node, live, text)
                is InsertionRoute.ClipboardOnly -> copyFallback(text, route.reason)
                is InsertionRoute.Reject -> InsertResult.Rejected(route.reason)
            }
        } finally {
            node.recycle()
        }
    }

    fun lastFocusedSnapshot(): NodeSnapshot? = lastFocused

    private fun obtainTarget(): AccessibilityNodeInfo? {
        var node = findFocusedEditable()
        if (node != null && !node.refresh()) {
            node.recycle()
            node = findFocusedEditable()
        }
        return node
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isEditable) return focused
            focused.recycle()
        }
        rootInActiveWindow?.let { root ->
            focusedIn(root)?.let { return it }
        }
        val windows = windows.orEmpty()
        try {
            val ordered = windows.sortedByDescending {
                (if (it.isFocused) 2 else 0) + (if (it.isActive) 1 else 0)
            }
            for (window in ordered) {
                val root = window.root ?: continue
                focusedIn(root)?.let { return it }
            }
        } finally {
            windows.forEach { it.recycle() }
        }
        return null
    }

    private fun focusedIn(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = try {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (t: Throwable) {
            runCatching { root.recycle() }
            throw t
        }
        if (focused == null) {
            root.recycle()
            return null
        }
        if (focused === root) {
            if (focused.isEditable) return focused
            focused.recycle()
            return null
        }
        if (focused.isEditable) {
            root.recycle()
            return focused
        }
        focused.recycle()
        root.recycle()
        return null
    }

    private fun setText(
        node: AccessibilityNodeInfo,
        snapshot: NodeSnapshot,
        insert: String,
    ): InsertResult {
        val current = node.text?.toString().takeUnless { snapshot.isShowingHintText }
        val formatted = TextInsertionPlanner.formatForCaret(insert, current, snapshot.selectionStart)
        val plan = TextInsertionPlanner.insert(
            current = current,
            selectionStart = snapshot.selectionStart,
            selectionEnd = snapshot.selectionEnd,
            insert = formatted,
        )
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                plan.text,
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                plan.selectionStart,
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                plan.selectionEnd,
            )
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            InsertResult.Inserted
        } else {
            paste(node, snapshot, insert)
        }
    }

    private fun paste(node: AccessibilityNodeInfo, snapshot: NodeSnapshot, text: String): InsertResult {
        val current = node.text?.toString().takeUnless { snapshot.isShowingHintText }
        val formatted = TextInsertionPlanner.formatForCaret(text, current, snapshot.selectionStart)
        if (!copyToClipboard(formatted)) return InsertResult.Failed("clipboard unavailable")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            InsertResult.Inserted
        } else {
            copyFallback(formatted, "paste unsupported")
        }
    }

    private fun copyFallback(text: String, reason: String): InsertResult {
        if (!copyToClipboard(text)) return InsertResult.Failed("clipboard unavailable")
        Toast.makeText(this, R.string.insertion_copied_toast, Toast.LENGTH_SHORT).show()
        notifyCopied()
        return InsertResult.Copied(reason)
    }

    private fun copyToClipboard(text: String): Boolean {
        val manager = getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
            true
        }.getOrDefault(false)
    }

    private fun notifyCopied() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.insertion_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(getString(R.string.insertion_copied_title))
            .setContentText(getString(R.string.insertion_copied_text))
            .setStyle(
                Notification.BigTextStyle().bigText(getString(R.string.insertion_copied_text)),
            )
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun AccessibilityNodeInfo.toSnapshot(): NodeSnapshot {
        val password = isPassword
        val hint = isShowingHintText
        return NodeSnapshot(
            packageName = packageName?.toString(),
            isEditable = isEditable,
            isEnabled = isEnabled,
            isVisible = isVisibleToUser,
            isPassword = password,
            hasReadableText = !password && !hint && text != null,
            selectionStart = textSelectionStart,
            selectionEnd = textSelectionEnd,
            windowId = windowId,
            viewId = viewIdResourceName,
            isShowingHintText = hint,
        )
    }

    companion object {
        const val CHANNEL_ID = "martins_whisper_insertion"
        const val NOTIFICATION_ID = 2001
    }
}

object AccessibilityInserter {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Telemetry: package of the field captured at the last START tap; null when none. */
    @Volatile
    var lastTargetPackage: String? = null
        private set

    fun noteTarget(packageName: String?) {
        lastTargetPackage = packageName
        lastTargetChanged = false
    }

    fun noteTargetChanged() {
        lastTargetChanged = true
    }

    /** Telemetry: whether the last insert was refused because the target changed. */
    @Volatile
    var lastTargetChanged: Boolean? = null
        private set

    @Volatile
    private var service: DictationAccessibilityService? = null

    internal fun attach(service: DictationAccessibilityService) {
        this.service = service
        _connected.value = true
    }

    internal fun detach(service: DictationAccessibilityService) {
        if (this.service === service) {
            this.service = null
            _connected.value = false
        }
    }

    suspend fun insert(text: String): InsertResult {
        if (text.isEmpty()) return InsertResult.Failed("empty text")
        return withContext(Dispatchers.Main.immediate) { insertNow(text) }
    }

    /** Non-suspending entry for callers already on the main thread (for example the dictation service). */
    fun insertNow(text: String): InsertResult {
        if (text.isEmpty()) return InsertResult.Failed("empty text")
        val active = service ?: return InsertResult.Failed("accessibility service not connected")
        return active.insert(text)
    }

    /** Main thread only: snapshot the focused field when the dictation session starts. */
    fun captureTargetAtStart(): Boolean = service?.captureTargetAtStart() ?: false
}
