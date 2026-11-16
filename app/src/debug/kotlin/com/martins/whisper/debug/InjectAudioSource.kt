package com.martins.whisper.debug

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.martins.whisper.audio.AudioRecordSource
import com.martins.whisper.audio.AudioSource
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Replays the fixture selected in [DebugInjectState] (device E2E) as real-time 20 ms frames so
 * the emulator gate can exercise the full tap -> capture -> gate -> whisper.cpp -> insert chain
 * without a silent guest mic tripping the speech gate. No fixture selected: falls back to the
 * real microphone so ordinary debug use is unchanged. Debug source set only.
 */
class InjectAudioSource(private val context: Context) : AudioSource {

    private val replaying = AtomicBoolean(false)
    private var replayThread: Thread? = null
    private var realMic: AudioRecordSource? = null

    override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
        val wav = DebugInjectState.selected(context)
        if (wav == null) {
            val mic = AudioRecordSource(context)
            realMic = mic
            mic.start(onFrame, onError)
            return
        }
        replaying.set(true)
        DebugInjectState.writeStatus(
            context,
            status(wav, state = "started", emitted = 0L),
        )
        replayThread = Thread({ stream(wav, onFrame) }, "inject-audio").apply {
            isDaemon = true
            start()
        }
    }

    private fun status(
        wav: File,
        state: String,
        emitted: Long,
        total: Long = totalSamples(wav),
        error: String? = null,
    ): DebugInjectState.InjectionStatus = DebugInjectState.InjectionStatus(
        wav = wav.name,
        state = state,
        samplesEmitted = emitted,
        totalSamples = total,
        error = error,
    )

    private fun totalSamples(wav: File): Long =
        runCatching { WavPcm.read(wav).samples.size.toLong() }.getOrDefault(0L)

    private fun stream(wav: File, onFrame: (ShortArray, Long) -> Unit) {
        try {
            val samples = WavPcm.read(wav).samples
            val total = samples.size.toLong()
            var emitted = 0L
            var sinceStatus = 0
            for (frame in WavPcm.frames(samples)) {
                if (!replaying.get()) return
                onFrame(frame, SystemClock.elapsedRealtime())
                emitted += frame.size
                if (++sinceStatus >= STATUS_EVERY_FRAMES) {
                    sinceStatus = 0
                    DebugInjectState.writeStatus(context, status(wav, "streaming", emitted, total))
                }
                Thread.sleep(FRAME_MS)
            }
            DebugInjectState.writeStatus(context, status(wav, "completed", emitted, total))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            Log.w(TAG, "inject replay failed: ${t.message}")
            DebugInjectState.writeStatus(
                context,
                status(wav, "error", emitted = 0L, error = t.message),
            )
        }
    }

    override fun stop() {
        replaying.set(false)
        realMic?.stop()
        realMic = null
        val thread = replayThread
        replayThread = null
        thread?.interrupt()
        thread?.join(STOP_JOIN_MS)
    }

    override fun release() = stop()

    private companion object {
        const val TAG = "InjectAudioSource"
        // 20 ms per 320-sample frame at 16 kHz, matching AudioRecordSource pacing.
        const val FRAME_MS = 20L
        const val STOP_JOIN_MS = 500L
        const val STATUS_EVERY_FRAMES = 50
    }
}
