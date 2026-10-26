package com.martins.whisper.ui

import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.martins.whisper.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MainActivityDeviceFixTest {

    @Test
    fun `system bar insets are applied as scroll padding`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val scroll = contentScroll(activity)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 120, 0, 180))
            .build()
        ViewCompat.dispatchApplyWindowInsets(scroll, insets)

        assertEquals(120, scroll.paddingTop)
        assertEquals(180, scroll.paddingBottom)
    }

    @Test
    fun `accessibility status refreshes on resume after the setting changes`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val status = accessibilityStatusView(activity)
        assertEquals("Not granted", status.text.toString())

        Settings.Secure.putString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.martins.whisper/com.martins.whisper.accessibility.DictationAccessibilityService",
        )
        controller.pause().resume()

        assertEquals("Granted", status.text.toString())
    }

    @Test
    fun `accessibility status refreshes when the setting changes while visible`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val status = accessibilityStatusView(activity)
        assertEquals("Not granted", status.text.toString())

        Settings.Secure.putString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.martins.whisper/com.martins.whisper.accessibility.DictationAccessibilityService",
        )
        activity.contentResolver.notifyChange(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            null,
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Granted", status.text.toString())
    }

    private fun contentScroll(activity: MainActivity): ScrollView {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        return content.getChildAt(0) as ScrollView
    }

    private fun accessibilityStatusView(activity: MainActivity): TextView {
        val title = findText(activity.window.decorView, "Accessibility insertion")
            ?: error("accessibility row title not found")
        val row = title.parent as ViewGroup
        return row.getChildAt(1) as TextView
    }

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findText(view.getChildAt(index), text)?.let { return it }
            }
        }
        return null
    }
}
