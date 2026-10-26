package com.martins.whisper.debug

import android.app.Application
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DebugInjectStateTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        DebugInjectState.dir(app).deleteRecursively()
        DebugInjectState.dir(app)
    }

    @Test
    fun `select stores a safe fixture and writes selected status`() {
        val wav = DebugInjectState.wavFile(app, "jfk.wav")
        WavPcm.write(wav, ShortArray(320) { 1_000 })

        val selected = DebugInjectState.select(app, "jfk.wav")

        assertEquals(wav.absolutePath, selected.absolutePath)
        assertEquals(wav.absolutePath, DebugInjectState.selected(app)?.absolutePath)
        val status = JSONObject(File(DebugInjectState.dir(app), "status.json").readText())
        assertEquals("jfk.wav", status.getString("wav"))
        assertEquals("selected", status.getString("state"))
    }

    @Test
    fun `path traversal and non-wav names are refused`() {
        WavPcm.write(DebugInjectState.wavFile(app, "ok.wav"), ShortArray(10))
        assertThrows(IllegalArgumentException::class.java) {
            DebugInjectState.select(app, "../ok.wav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebugInjectState.select(app, "/data/local/tmp/ok.wav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebugInjectState.select(app, "ok.mp3")
        }
        assertNull(DebugInjectState.selected(app))
    }

    @Test
    fun `missing fixture is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebugInjectState.select(app, "absent.wav")
        }
    }

    @Test
    fun `clear removes the selection`() {
        WavPcm.write(DebugInjectState.wavFile(app, "ok.wav"), ShortArray(10))
        DebugInjectState.select(app, "ok.wav")
        DebugInjectState.clear(app)

        assertNull(DebugInjectState.selected(app))
        val status = JSONObject(File(DebugInjectState.dir(app), "status.json").readText())
        assertEquals("cleared", status.getString("state"))
        assertTrue(status.isNull("wav"))
    }
}
