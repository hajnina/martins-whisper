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
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only): with RECORD_AUDIO denied the app must report the missing permission
 * and fail closed instead of crashing or silently recording.
 *
 * Revoking a runtime permission kills the app process, so this test never revokes it from inside
 * instrumentation. Prepare the device first (the process is not running at that point):
 *
 * ```
 * adb shell pm revoke com.martins.whisper android.permission.RECORD_AUDIO
 * adb shell am instrument -w -e class com.martins.whisper.harness.PermissionDenialTest \
 *   com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner
 * adb shell pm grant com.martins.whisper android.permission.RECORD_AUDIO
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PermissionDenialTest {

    @Test
    fun microphone_denial_fails_closed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        assumeTrue(
            "RECORD_AUDIO is granted; run `scripts/test-device.sh --deny-mic` for this case",
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
    }
}
