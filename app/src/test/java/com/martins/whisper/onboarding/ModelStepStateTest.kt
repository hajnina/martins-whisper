package com.martins.whisper.onboarding

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStepStateTest {

    @Test
    fun `happy path walks idle, downloading with progress, installed`() = runTest {
        val state = ModelStepState { report ->
            report(0.25f, 262_144L, 1_048_576L)
            yield()
            report(0.5f, 524_288L, 1_048_576L)
            yield()
            Result.success(Unit)
        }
        val seen = mutableListOf<ModelStepPhase>()
        val observer = launch { state.phase.collect { seen.add(it) } }
        yield()
        state.start()
        yield()
        observer.cancel()
        assertEquals(
            listOf(
                ModelStepPhase.Idle,
                ModelStepPhase.Downloading(0.25f, 262_144L, 1_048_576L),
                ModelStepPhase.Downloading(0.5f, 524_288L, 1_048_576L),
                ModelStepPhase.Installed,
            ),
            seen,
        )
    }

    @Test
    fun `skip path jumps straight to installed without downloading`() = runTest {
        val state = ModelStepState { Result.success(Unit) }
        val seen = mutableListOf<ModelStepPhase>()
        val observer = launch { state.phase.collect { seen.add(it) } }
        yield()
        state.start()
        yield()
        observer.cancel()
        assertEquals(listOf<ModelStepPhase>(ModelStepPhase.Idle, ModelStepPhase.Installed), seen)
    }

    @Test
    fun `failure surfaces message and retry installs`() = runTest {
        var attempts = 0
        val state = ModelStepState { report ->
            attempts++
            if (attempts == 1) {
                Result.failure(IOException("network down"))
            } else {
                report(1f, 1_048_576L, 1_048_576L)
                Result.success(Unit)
            }
        }
        state.start()
        assertEquals(ModelStepPhase.Failed("network down"), state.phase.value)
        state.retry()
        assertEquals(ModelStepPhase.Installed, state.phase.value)
        assertEquals(2, attempts)
    }

    @Test
    fun `start while downloading does not run a parallel install`() = runTest {
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val state = ModelStepState { report ->
            calls++
            report(0.5f, 512L, 1024L)
            entered.send(Unit)
            release.await()
            Result.success(Unit)
        }
        val first = async { state.start() }
        entered.receive()
        assertEquals(ModelStepPhase.Downloading(0.5f, 512L, 1024L), state.phase.value)
        state.start()
        release.complete(Unit)
        first.await()
        assertEquals(1, calls)
        assertEquals(ModelStepPhase.Installed, state.phase.value)
    }
}
