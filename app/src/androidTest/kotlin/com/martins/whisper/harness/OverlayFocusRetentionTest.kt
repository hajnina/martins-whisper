package com.martins.whisper.harness

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.MainActivity
import com.martins.whisper.service.DictationService
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only, not executed in the sandbox): the overlay bubble must never steal
 * focus from the field the user is dictating into.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class OverlayFocusRetentionTest {

    @Test
    fun bubble_does_not_steal_focus_from_the_target_field() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        grant(instrumentation, target, Manifest.permission.RECORD_AUDIO, target.packageName)
        shell(instrumentation, "appops set ${target.packageName} SYSTEM_ALERT_WINDOW allow")
        assumeTrue("overlay permission not grantable on this device", Settings.canDrawOverlays(target))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var edit: EditText
            scenario.onActivity { activity ->
                edit = EditText(activity).apply {
                    hint = "dictation target"
                    id = TARGET_ID
                }
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        edit,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                activity.setContentView(root)
                edit.requestFocus()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue("target field must own focus before the overlay", edit.hasFocus())
            }

            target.startForegroundService(
                Intent(target, DictationService::class.java)
                    .setAction(DictationService.ACTION_START),
            )
            instrumentation.waitForIdleSync()
            Thread.sleep(OVERLAY_SETTLE_MS)

            scenario.onActivity {
                assertTrue("overlay stole focus from the target field", edit.hasFocus())
                assertTrue("target window lost focus while the bubble was shown", edit.hasWindowFocus())
            }

            target.startService(
                Intent(target, DictationService::class.java)
                    .setAction(DictationService.ACTION_STOP),
            )
        }
    }

    private fun grant(
        instrumentation: android.app.Instrumentation,
        context: Context,
        permission: String,
        packageName: String,
    ) {
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        }
        assumeTrue(
            "runtime permission $permission is not granted",
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    private fun shell(instrumentation: android.app.Instrumentation, command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private companion object {
        const val TARGET_ID = 0x7f0f0001
        const val OVERLAY_SETTLE_MS = 1_000L
    }
}
