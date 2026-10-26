package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ModelManagerTest {

    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
        fun advance(ms: Long) {
            now += ms
        }
    }

    private class Model(val id: String)

    private class Harness(
        idleMs: Long = ModelManager.DEFAULT_IDLE_MS,
        loader: () -> Model = { Model("base") },
        closer: (Model) -> Unit = {},
    ) {
        val clock = FakeClock()
        val loaded = AtomicInteger()
        val closed = AtomicInteger()
        val states = mutableListOf<ModelState>()
        var loader: () -> Model = loader
        val manager = ModelManager(
            loader = { loaded.incrementAndGet(); this.loader() },
            closer = { closed.incrementAndGet(); closer(it) },
            clock = clock,
            idleMs = idleMs,
            stateListener = { states += it },
        )
    }

    @Test
    fun `loads lazily and only once`() {
        val h = Harness()
        assertEquals(ModelState.UNLOADED, h.manager.state)
        val first = h.manager.getOrLoad()
        val second = h.manager.getOrLoad()
        assertSame(first, second)
        assertEquals(1, h.loaded.get())
        assertEquals(ModelState.READY, h.manager.state)
        assertEquals(listOf(ModelState.LOADING, ModelState.READY), h.states)
    }

    @Test
    fun `unloads at exactly 300 seconds idle`() {
        val h = Harness()
        h.manager.getOrLoad()

        h.clock.advance(299_999)
        assertFalse(h.manager.maybeUnload())
        assertEquals(ModelState.READY, h.manager.state)

        h.clock.advance(1)
        assertTrue(h.manager.maybeUnload())
        assertEquals(1, h.closed.get())
        assertEquals(ModelState.UNLOADED, h.manager.state)
        assertFalse(h.manager.maybeUnload())
    }

    @Test
    fun `markUsed postpones idle unload`() {
        val h = Harness()
        h.manager.getOrLoad()
        h.clock.advance(200_000)
        h.manager.markUsed()
        h.clock.advance(200_000)
        assertFalse(h.manager.maybeUnload())
        h.clock.advance(100_000)
        assertTrue(h.manager.maybeUnload())
    }

    @Test
    fun `withModel marks usage and returns value`() {
        val h = Harness()
        val result = h.manager.withModel { it.id }
        assertEquals("base", result)
        h.clock.advance(299_000)
        assertFalse(h.manager.maybeUnload())
    }

    @Test
    fun `withModel marks usage even when block throws`() {
        val h = Harness()
        try {
            h.manager.withModel<Unit> { throw IllegalStateException("boom") }
            throw AssertionError("expected exception")
        } catch (_: IllegalStateException) {
        }
        assertEquals(ModelState.READY, h.manager.state)
        h.clock.advance(299_000)
        assertFalse(h.manager.maybeUnload())
        h.clock.advance(1_000)
        assertTrue(h.manager.maybeUnload())
    }

    @Test
    fun `unloadNow frees immediately and reloads on demand`() {
        val h = Harness()
        h.manager.getOrLoad()
        h.manager.unloadNow()
        assertEquals(1, h.closed.get())
        assertEquals(ModelState.UNLOADED, h.manager.state)
        h.manager.getOrLoad()
        assertEquals(2, h.loaded.get())
        assertEquals(ModelState.READY, h.manager.state)
    }

    @Test
    fun `loader failure sets error and a later call retries`() {
        val h = Harness()
        h.loader = { throw IllegalStateException("cannot load") }
        try {
            h.manager.getOrLoad()
            throw AssertionError("expected loader failure")
        } catch (_: IllegalStateException) {
        }
        assertEquals(ModelState.ERROR, h.manager.state)
        assertEquals(1, h.loaded.get())
        assertEquals(0, h.closed.get())

        h.loader = { Model("recovered") }
        assertEquals("recovered", h.manager.getOrLoad().id)
        assertEquals(ModelState.READY, h.manager.state)
        assertEquals(2, h.loaded.get())
        assertEquals(
            listOf(ModelState.LOADING, ModelState.ERROR, ModelState.LOADING, ModelState.READY),
            h.states,
        )
    }

    @Test
    fun `switchModel unloads old and loads new lazily`() {
        val h = Harness()
        val old = h.manager.getOrLoad()
        h.manager.switchModel { Model("new") }
        assertEquals(1, h.closed.get())
        assertEquals(ModelState.UNLOADED, h.manager.state)

        val new = h.manager.getOrLoad()
        assertNotSame(old, new)
        assertEquals("new", new.id)
        assertEquals(ModelState.READY, h.manager.state)
    }

    @Test
    fun `closer failure still transitions to unloaded`() {
        val h = Harness(closer = { throw IllegalStateException("close failed") })
        h.manager.getOrLoad()
        h.manager.unloadNow()
        assertEquals(ModelState.UNLOADED, h.manager.state)
        assertEquals(1, h.closed.get())
    }

    @Test
    fun `listeners are added and removed`() {
        val h = Harness()
        val seen = mutableListOf<ModelState>()
        val listener: (ModelState) -> Unit = { seen += it }
        h.manager.addStateListener(listener)
        h.manager.getOrLoad()
        assertEquals(listOf(ModelState.LOADING, ModelState.READY), seen)
        h.manager.removeStateListener(listener)
        h.manager.unloadNow()
        assertEquals(listOf(ModelState.LOADING, ModelState.READY), seen)
    }

    @Test
    fun `withModel serializes access`() {
        val h = Harness()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val overlap = AtomicBoolean(false)
        val active = AtomicInteger(0)

        val first = Thread {
            h.manager.withModel {
                active.incrementAndGet()
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                active.decrementAndGet()
            }
        }
        first.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val second = Thread {
            h.manager.withModel {
                if (active.get() != 0) overlap.set(true)
                secondEntered.countDown()
            }
        }
        second.start()
        assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS))

        release.countDown()
        assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
        first.join(5_000)
        second.join(5_000)
        assertFalse(overlap.get())
        assertEquals(1, h.loaded.get())
    }
}
