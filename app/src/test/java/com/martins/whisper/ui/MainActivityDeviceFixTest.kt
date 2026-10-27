package com.martins.whisper.ui

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.martins.whisper.MainActivity
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.onboarding.OnboardingStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MainActivityDeviceFixTest {

    @Before
    fun useLegacyScreen() {
        OnboardingStore(ApplicationProvider.getApplicationContext()).markCompleted()
    }

    private fun launchSettings(): ActivityController<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
        return Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    @Test
    fun `system bar insets are applied as scroll padding`() {
        val activity = launchSettings().get()
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
        val controller = launchSettings()
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
        val activity = launchSettings().get()
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

    @Test
    fun `language spinner offers Auto English Czech and defaults to English`() {
        val activity = launchSettings().get()
        val spinner = languageSpinner(activity.window.decorView)
            ?: error("language spinner not found")

        assertEquals(3, spinner.adapter.count)
        val labels = (0 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }
        assertTrue(labels.first().startsWith("Auto detect"))
        assertEquals("English", labels[1])
        assertEquals("\u010ce\u0161tina", labels[2])
        assertEquals(1, spinner.selectedItemPosition)

        spinner.setSelection(2)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("cs", SettingsStore(activity).language)
        assertEquals("cs", SettingsStore(activity).effectiveLanguage())
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

    private fun languageSpinner(view: View): Spinner? {
        if (view is Spinner) {
            val labels = (0 until view.adapter.count).map { view.adapter.getItem(it).toString() }
            if (labels.contains("English") && labels.contains("\u010ce\u0161tina")) return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                languageSpinner(view.getChildAt(index))?.let { return it }
            }
        }
        return null
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
