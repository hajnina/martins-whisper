package com.martins.whisper.harness

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.audio.AudioRecordSource
import com.martins.whisper.ui.PermissionGate
import com.martins.whisper.ui.PermissionItem
import com.martins.whisper.ui.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Layer 2 (device-only): with RECORD_AUDIO denied the app must report the missing permission
 * and fail closed instead of crashing or silently recording.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PermissionDenialTest {

    @Test
    fun microphone_denial_fails_closed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        runCatching {
            instrumentation.uiAutomation.revokeRuntimePermission(
                target.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
        }
        try {
            assumeTrue(
                "RECORD_AUDIO is still granted; cannot test denial",
                target.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED,
            )

            val missing = PermissionGate.missing(
                PermissionSnapshot(
                    microphone = false,
                    overlay = true,
                    accessibility = true,
                    notifications = true,
                ),
            )
            assertEquals(listOf(PermissionItem.MICROPHONE), missing)
            assertFalse(
                PermissionGate.readyForDictation(
                    PermissionSnapshot(
                        microphone = false,
                        overlay = true,
                        accessibility = true,
                        notifications = true,
                    ),
                ),
            )

            val frames = AtomicReference<ShortArray>()
            val error = AtomicReference<Throwable>()
            val source = AudioRecordSource(target)
            source.start(
                onFrame = { samples, _ -> frames.set(samples) },
                onError = { error.set(it) },
            )
            source.release()

            assertNull("denied microphone must not produce audio frames", frames.get())
            assertTrue(
                "expected SecurityException, got ${error.get()}",
                error.get() is SecurityException,
            )
        } finally {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    target.packageName,
                    Manifest.permission.RECORD_AUDIO,
                )
            }
        }
    }
}
