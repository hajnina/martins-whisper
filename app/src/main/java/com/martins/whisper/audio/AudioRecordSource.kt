package com.martins.whisper.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Captures 16 kHz mono PCM_16BIT for whisper.cpp.
 *
 * Source choice matters on real phones: [MediaRecorder.AudioSource.VOICE_RECOGNITION] is
 * "tuned for voice recognition **if available**" and several OEMs (notably Xiaomi/HyperOS)
 * either route it to a DSP that never feeds the app or deliver near-silent samples, while the
 * plain [MediaRecorder.AudioSource.MIC] always works. We therefore prefer the raw sources:
 *
 *   1. [MediaRecorder.AudioSource.MIC] (universally available, no special OEM DSP)
 *   2. [MediaRecorder.AudioSource.UNPROCESSED] when the device advertises support
 *      (bypasses OEM AGC/NS if MIC is processed into silence)
 *   3. [MediaRecorder.AudioSource.VOICE_RECOGNITION] (last resort; optional per device)
 *
 * If the chosen source initialises but only produces exact digital silence, the capture thread
 * transparently reopens the recorder with the next candidate: 400 ms of warm-up are ignored and
 * the switch needs 500 ms of peak == [DEAD_SOURCE_PEAK] (0). An ordinary quiet room, or a user
 * who waits before speaking, is never switched. If the next source cannot be opened or started,
 * the previous one is reopened and the capture continues; a failed fallback never aborts a
 * running session (the gate/telemetry report a genuinely silent mic).
 */
class AudioRecordSource(
    private val context: Context,
    private val frameSamples: Int = FRAME_SAMPLES,
    private val sampleRate: Int = SAMPLE_RATE,
    /** Test/diagnostic override. When set, no probing or fallback is attempted. */
    private val preferredSource: Int? = null,
) : AudioSource {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @Volatile
    private var activeSource: Int = -1

    /** Name of the source currently feeding [AudioSource.start] (for telemetry/logcat). */
    val sourceName: String
        get() = sourceNameFor(activeSource)

    override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            running.set(false)
            onError(SecurityException("RECORD_AUDIO not granted"))
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            running.set(false)
            onError(IllegalStateException("AudioRecord unavailable (minBuffer=$minBuffer)"))
            return
        }
        // A single minBuffer underruns on scheduling hiccups (logging, GC, engine load);
        // give the HAL 4x minBuffer and at least 8 frames of headroom.
        val bufferBytes = maxOf(minBuffer * BUFFER_MULTIPLIER, frameSamples * 2 * BUFFER_FRAMES)
        val candidates = sourceCandidates(preferredSource, supportsUnprocessed(context))

        thread = Thread({
            var recorder: AudioRecord? = null
            try {
                var index = 0
                while (index < candidates.size) {
                    recorder = open(candidates[index], bufferBytes, sampleRate)
                    if (recorder != null) break
                    Log.w(TAG, "capture: ${sourceNameFor(candidates[index])} unavailable; trying next")
                    index++
                }
                var current: AudioRecord = recorder
                    ?: throw IllegalStateException(
                        "No usable microphone source (${candidates.map(::sourceNameFor)})",
                    )
                recorder = current
                activeSource = candidates[index]
                Log.i(
                    TAG,
                    "capture start: source=${sourceNameFor(activeSource)} rate=$sampleRate " +
                        "channels=1 frame=$frameSamples samples buffer=$bufferBytes bytes",
                )
                current.startRecording()
                if (current.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("AudioRecord did not start (source=${sourceNameFor(activeSource)})")
                }

                val frame = ShortArray(frameSamples)
                var warmupFrames = 0
                var probeFrames = 0
                var probePeak = 0
                var zeroReads = 0
                while (running.get()) {
                    val read = current.read(frame, 0, frame.size)
                    if (read > 0) {
                        zeroReads = 0
                        if (warmupFrames < PROBE_WARMUP_FRAMES) {
                            // The HAL can deliver zeros while the route opens; that is not a
                            // dead source and must never trigger a switch.
                            warmupFrames++
                        } else if (probeFrames < PROBE_FRAMES) {
                            probePeak = maxOf(probePeak, peakOf(frame, read))
                            probeFrames++
                        }
                        val copy = if (read == frame.size) frame.copyOf() else frame.copyOf(read)
                        onFrame(copy, SystemClock.elapsedRealtime())

                        if (preferredSource == null && shouldFallBack(probeFrames, probePeak) &&
                            index < candidates.lastIndex
                        ) {
                            val previous = activeSource
                            val nextIndex = index + 1
                            val next = candidates[nextIndex]
                            Log.w(
                                TAG,
                                "capture: ${sourceNameFor(previous)} returned only digital silence " +
                                    "(peak=$probePeak); trying ${sourceNameFor(next)}",
                            )
                            runCatching { current.stop() }
                            current.release()
                            // Skip a candidate that failed once for the rest of this session.
                            index = nextIndex
                            val switched = startSource(next, bufferBytes, sampleRate)
                            if (switched != null) {
                                current = switched
                                activeSource = next
                                Log.i(TAG, "capture: switched to ${sourceNameFor(next)}")
                            } else {
                                // A failed fallback must never abort a capture that was running.
                                Log.w(
                                    TAG,
                                    "capture: ${sourceNameFor(next)} unavailable; keeping " +
                                        sourceNameFor(previous),
                                )
                                val restored = startSource(previous, bufferBytes, sampleRate)
                                    ?: throw IllegalStateException(
                                        "No usable microphone source after fallback " +
                                            "(tried ${sourceNameFor(next)}, ${sourceNameFor(previous)})",
                                    )
                                current = restored
                                activeSource = previous
                            }
                            recorder = current
                            warmupFrames = 0
                            probeFrames = 0
                            probePeak = 0
                        }
                    } else if (read == 0) {
                        // A source that only returns 0 is dead (another app holds the mic,
                        // privacy mute, or a bad HAL source). Never spin or lie about audio.
                        zeroReads++
                        if (zeroReads >= MAX_ZERO_READS) {
                            throw IllegalStateException(
                                "Microphone returned no audio (source=${sourceNameFor(activeSource)})",
                            )
                        }
                        Thread.sleep(ZERO_READ_BACKOFF_MS)
                    } else {
                        throw IllegalStateException("AudioRecord read error $read (source=${sourceNameFor(activeSource)})")
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) onError(t)
            } finally {
                running.set(false)
                try {
                    recorder?.stop()
                } catch (_: Throwable) {
                }
                try {
                    recorder?.release()
                } catch (_: Throwable) {
                }
            }
        }, "audio-capture").apply { isDaemon = true }
        thread?.start()
    }

    override fun stop() {
        val wasRunning = running.getAndSet(false)
        if (wasRunning) {
            Log.i(TAG, "capture stop requested (source=${sourceNameFor(activeSource)})")
        }
        thread?.join(STOP_JOIN_MS)
        thread = null
    }

    override fun release() = stop()

    companion object {
        const val SAMPLE_RATE: Int = 16_000

        /** 20 ms at 16 kHz. */
        const val FRAME_SAMPLES: Int = 320

        private const val TAG = "MartinsWhisper"
        private const val BUFFER_MULTIPLIER = 4
        private const val BUFFER_FRAMES = 8
        // 400 ms of warm-up is not probed: a HAL may deliver zeros while the route opens.
        internal const val PROBE_WARMUP_FRAMES = 20
        // Then 500 ms of *exact* digital silence (peak == 0), not merely a quiet room.
        internal const val PROBE_FRAMES = 25
        internal const val DEAD_SOURCE_PEAK = 0
        private const val MAX_ZERO_READS = 400
        private const val ZERO_READ_BACKOFF_MS = 5L
        private const val STOP_JOIN_MS = 500L

        /**
         * Ordered capture sources. MIC is deliberately preferred over VOICE_RECOGNITION:
         * the latter is optional per device and silent on some HyperOS builds.
         */
        fun sourceCandidates(preferred: Int?, supportsUnprocessed: Boolean): List<Int> {
            if (preferred != null) return listOf(preferred)
            return buildList {
                add(MediaRecorder.AudioSource.MIC)
                if (supportsUnprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }.distinct()
        }

        fun supportsUnprocessed(context: Context): Boolean = runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
                ?.equals("true", ignoreCase = true) == true
        }.getOrDefault(false)

        fun sourceNameFor(source: Int): String = when (source) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
            else -> "source#$source"
        }

        fun rms(samples: ShortArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (sample in samples) {
                val normalized = sample / 32768.0
                sum += normalized * normalized
            }
            return sqrt(sum / samples.size).toFloat().coerceIn(0f, 1f)
        }

        /**
         * A source is dead only after the warm-up and [PROBE_FRAMES] consecutive frames of exact
         * digital silence. A quiet room or a user who waits before speaking stays above zero.
         */
        internal fun shouldFallBack(probedFrames: Int, peak: Int): Boolean =
            probedFrames >= PROBE_FRAMES && peak <= DEAD_SOURCE_PEAK

        /** Opens and starts [source]; returns null instead of throwing when it cannot be used. */
        private fun startSource(source: Int, bufferBytes: Int, sampleRate: Int): AudioRecord? {
            val recorder = open(source, bufferBytes, sampleRate) ?: return null
            return try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.release()
                    null
                } else {
                    recorder
                }
            } catch (t: Throwable) {
                Log.w(TAG, "capture: ${sourceNameFor(source)} failed to start: ${t.message}")
                runCatching { recorder.release() }
                null
            }
        }

        private fun peakOf(samples: ShortArray, count: Int = samples.size): Int {
            var peak = 0
            for (i in 0 until minOf(count, samples.size)) {
                val magnitude = abs(samples[i].toInt())
                if (magnitude > peak) peak = magnitude
            }
            return peak
        }

        private fun open(source: Int, bufferBytes: Int, sampleRate: Int = SAMPLE_RATE): AudioRecord? {
            val recorder = try {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "capture: AudioRecord(${sourceNameFor(source)}) failed: ${t.message}")
                return null
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return null
            }
            return recorder
        }
    }
}
