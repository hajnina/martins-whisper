package com.martins.whisper.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioRecordSource(
    private val context: Context,
    private val frameSamples: Int = 320,
    private val sampleRate: Int = 16_000,
) : AudioSource {

    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
        if (running.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError(SecurityException("RECORD_AUDIO not granted"))
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onError(IllegalStateException("AudioRecord unavailable (minBuffer=$minBuffer)"))
            return
        }
        val bufferBytes = maxOf(minBuffer, frameSamples * 2 * 4)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
        } catch (t: Throwable) {
            onError(t)
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError(IllegalStateException("AudioRecord init failed"))
            return
        }
        record = recorder
        running.set(true)
        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            running.set(false)
            record = null
            recorder.release()
            onError(t)
            return
        }
        thread = Thread({
            val frame = ShortArray(frameSamples)
            try {
                while (running.get()) {
                    val read = recorder.read(frame, 0, frame.size)
                    if (read > 0) {
                        val copy = if (read == frame.size) frame.copyOf() else frame.copyOf(read)
                        onFrame(copy, SystemClock.elapsedRealtime())
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error $read")
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) onError(t)
            } finally {
                try {
                    recorder.stop()
                } catch (_: Throwable) {
                }
                recorder.release()
            }
        }, "audio-capture").apply { isDaemon = true }
        thread?.start()
    }

    override fun stop() {
        running.set(false)
        thread?.join(500)
        thread = null
        record = null
    }

    override fun release() = stop()

    companion object {
        fun rms(samples: ShortArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (sample in samples) {
                val normalized = sample / 32768.0
                sum += normalized * normalized
            }
            return sqrt(sum / samples.size).toFloat().coerceIn(0f, 1f)
        }
    }
}
