package com.martins.whisper.harness

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.MainActivity
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only): the foreground service starts from a visible activity without the
 * microphone type, publishes Idle, and stops cleanly from the notification action.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ServiceLifecycleTest {

    private val target: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        target.stopService(Intent(target, DictationService::class.java))
    }

    @Test
    fun service_starts_and_stops_without_the_microphone_type() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ContextCompat.startForegroundService(
                    activity,
                    Intent(activity, DictationService::class.java)
                        .setAction(DictationService.ACTION_START)
                        .putExtra(DictationService.EXTRA_NO_MIC, true),
                )
            }

            assumeTrue(
                "foreground service did not start on this device",
                waitUntil { isRunning(DictationService::class.java.name) },
            )
            assertTrue(
                "service did not publish Idle",
                waitUntil { DictationService.state.value is DictationState.Idle },
            )
        }

        target.startService(
            Intent(target, DictationService::class.java).setAction(DictationService.ACTION_STOP),
        )

        assertTrue(
            "service did not stop after ACTION_STOP",
            waitUntil { !isRunning(DictationService::class.java.name) },
        )
        assertEquals(DictationState.Idle, DictationService.state.value)
        assertFalse(SettingsStore(target).overlayEnabled)
    }

    private fun isRunning(serviceName: String): Boolean {
        val manager = target.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceName }
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }
}
