package com.martins.whisper.service

import com.martins.whisper.core.ModelState
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.core.asr.ModelManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationEngineHostTest {

    private val clock = AtomicLong(0L)

    private fun host(
        create: () -> FakeSpeechEngine,
        idleMs: Long = 300_000L,
        maxFrames: Int = AudioCaptureBuffer.DEFAULT_MAX_FRAMES,
    ) = DictationEngineHost(
        createEngine = create,
        clock = { clock.get() },
        idleMs = idleMs,
        maxFrames = maxFrames,
    )

    @Test
    fun `capture during a blocked model load keeps every frame in order`() {
        val loaderEntered = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val host = host({
            loaderEntered.countDown()
            releaseLoader.await(5, TimeUnit.SECONDS)
            FakeSpeechEngine()
        })
        host.prepare("base")
        host.beginSession()

        val load = thread { host.load() }
        assertTrue(loaderEntered.await(2, TimeUnit.SECONDS))

        repeat(200) { index -> assertTrue(host.addFrame(shortArrayOf(index.toShort()))) }
        assertEquals(200, host.bufferedFrames())

        releaseLoader.countDown()
        load.join(5_000)
        assertFalse(load.isAlive)

        val captured = host.finishSession()
        assertNotNull(captured)
        assertEquals(200, captured!!.frames.size)
        assertEquals(200L, captured.sampleCount)
        captured.frames.forEachIndexed { index, frame ->
            assertEquals(index.toShort(), frame[0])
        }
        assertEquals(0, host.bufferedFrames())
        assertEquals(ModelState.READY, host.state)
    }

    @Test
    fun `release while loading still transcribes after the engine finishes loading`() {
        val loaderEntered = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val host = host({
            loaderEntered.countDown()
            releaseLoader.await(5, TimeUnit.SECONDS)
            FakeSpeechEngine(backend = "fake")
        })
        host.prepare("base")
        host.beginSession()
        host.addFrame(shortArrayOf(1, 2, 3, 4))

        val load = thread { host.load() }
        assertTrue(loaderEntered.await(2, TimeUnit.SECONDS))

        val captured = host.finishSession()!!
        val samples = captured.toFloatArray()
        val transcribe = thread {
            host.transcribe(samples, captured.durationMs(), "en")
        }
        releaseLoader.countDown()
        load.join(5_000)
        transcribe.join(5_000)
        assertFalse(transcribe.isAlive)
        assertEquals(ModelState.READY, host.state)
    }

    @Test
    fun `idle unload uses the injected clock and the default 300 s idle time`() {
        var engines = 0
        val instances = mutableListOf<FakeSpeechEngine>()
        val host = host({
            engines++
            FakeSpeechEngine().also { instances += it }
        })
        host.prepare("base")
        host.beginSession()
        host.load()
        host.finishSession()
        assertEquals(ModelManager.DEFAULT_IDLE_MS, host.idleMs)
        assertEquals(300_000L, host.idleMs)

        clock.set(299_999L)
        assertFalse(host.unloadIfIdle())
        assertEquals(ModelState.READY, host.state)
        assertFalse(instances.single().closed)

        clock.set(300_000L)
        assertTrue(host.unloadIfIdle())
        assertEquals(ModelState.UNLOADED, host.state)
        assertTrue(instances.single().closed)

        host.load()
        assertEquals(2, engines)
    }

    @Test
    fun `a full 600 second hold is buffered without dropping frames`() {
        val buffer = AudioCaptureBuffer(AudioCaptureBuffer.DEFAULT_MAX_FRAMES)
        val frame = ShortArray(AudioCaptureBuffer.FRAME_SAMPLES)
        repeat(30_000) { index ->
            frame[0] = index.toShort()
            assertTrue("frame $index was dropped", buffer.add(frame.copyOf()))
        }
        assertEquals(30_000, buffer.frameCount())
        assertEquals(30_000L * 320L, buffer.sampleCount())
        assertFalse(buffer.isFull())

        val captured = buffer.snapshot()
        assertEquals(30_000, captured.size)
        assertEquals(0.toShort(), captured.first()[0])
    }

    @Test
    fun `switching model closes the previous engine before the next load`() {
        val first = FakeSpeechEngine()
        val second = FakeSpeechEngine()
        val created = AtomicInteger(0)
        val host = host({
            if (created.getAndIncrement() == 0) first else second
        })
        host.prepare("base")
        host.load()
        assertFalse(first.closed)

        host.prepare("small")
        assertTrue(first.closed)
        assertEquals(ModelState.UNLOADED, host.state)

        host.load()
        assertFalse(second.closed)
        assertEquals(2, created.get())
    }

    @Test
    fun `cancel clears the capture without touching the loaded engine`() {
        val engine = FakeSpeechEngine()
        val host = host({ engine })
        host.prepare("base")
        host.load()
        host.beginSession()
        host.addFrame(shortArrayOf(1))
        host.cancelSession()
        assertNull(host.finishSession())
        assertEquals(ModelState.READY, host.state)
        assertFalse(engine.closed)
    }
}
