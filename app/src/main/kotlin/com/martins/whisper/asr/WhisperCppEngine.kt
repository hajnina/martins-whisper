package com.martins.whisper.asr

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.EngineState
import com.martins.whisper.core.asr.WhisperCppEngine as WhisperCppEngineContract
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Decodes a segment returned by the native layer. whisper.cpp emits standard UTF-8; the JVM's
 * replacement behaviour is intentional so a split/invalid sequence cannot abort transcription.
 */
internal fun decodeNativeUtf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

class SpeechEngineException(message: String, val code: Int? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

class SpeechEngineUnavailableException(
    message: String,
    val reason: String = EngineState.ENGINE_ERROR,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal object WhisperCppNative {
    private val loadFailure: Throwable? = runCatching { System.loadLibrary("martins_whisper") }.exceptionOrNull()

    val isAvailable: Boolean
        get() = loadFailure == null

    val loadError: String?
        get() = loadFailure?.let { "${it.javaClass.simpleName}: ${it.message}" }

    fun requireAvailable() {
        loadFailure?.let {
            throw SpeechEngineUnavailableException(
                "Native whisper.cpp is not available in this APK. Install a build with native " +
                    "support (-Pmartins.native=true).",
                EngineState.NATIVE_UNAVAILABLE,
                it,
            )
        }
    }

    external fun nativeVersion(): String
    external fun nativeSystemInfo(): String
    external fun nativeBackendInfo(): String
    external fun nativeHasVulkanDevice(): Boolean
    external fun nativeSetLogging(enabled: Boolean)
    external fun nativeInit(modelPath: String, useGpu: Boolean, gpuDevice: Int): Long
    external fun nativeFull(handle: Long, samples: FloatArray, language: String?, threads: Int): Int
    external fun nativeSegmentCount(handle: Long): Int
    external fun nativeSegmentText(handle: Long, index: Int): ByteArray
    external fun nativeSegmentStartMs(handle: Long, index: Int): Long
    external fun nativeSegmentEndMs(handle: Long, index: Int): Long
    external fun nativeSegmentNoSpeechProb(handle: Long, index: Int): Float
    external fun nativeDetectedLanguage(handle: Long): String
    external fun nativeFree(handle: Long)
}

/**
 * Android/JNI implementation of the whisper.cpp speech engine.
 *
 * Construction loads the model eagerly (so the caller can put it behind `ModelManager`); each
 * instance owns exactly one whisper context and a single-thread dispatcher, because whisper.cpp
 * contexts are not safe for concurrent use. `close()` is idempotent and blocks until any in-flight
 * transcription has finished.
 */
class WhisperCppEngine private constructor(
    override val modelPath: String,
    private val handle: Long,
    override val backend: String,
    override val runtimeInfo: String,
    private val threads: Int,
) : WhisperCppEngineContract {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "whisper-cpp").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val lock = ReentrantLock(true)

    @Volatile
    private var context: Long = handle

    @Volatile
    private var closed: Boolean = false

    val isClosed: Boolean
        get() = closed

    override suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript =
        withContext(dispatcher) {
            lock.withLock {
                check(!closed) { "WhisperCppEngine is closed" }
                require(chunk.sampleRate == SAMPLE_RATE) {
                    "whisper.cpp requires ${SAMPLE_RATE} Hz mono audio, got ${chunk.sampleRate}"
                }
                require(chunk.samples.isNotEmpty()) { "audio chunk is empty" }

                val handle = context
                check(handle != 0L) { "WhisperCppEngine has no native context" }
                val code = WhisperCppNative.nativeFull(
                    handle = handle,
                    samples = chunk.samples,
                    language = language?.trim()?.takeIf { it.isNotEmpty() },
                    threads = threads,
                )
                if (code != 0) {
                    throw SpeechEngineException(
                        message = when (code) {
                            -1 -> "whisper.cpp context is invalid"
                            -2 -> "whisper.cpp rejected the audio samples"
                            -3 -> "whisper.cpp transcription failed"
                            else -> "whisper.cpp transcription failed (code $code)"
                        },
                        code = code,
                    )
                }

                val count = WhisperCppNative.nativeSegmentCount(handle)
                val builder = StringBuilder()
                var noSpeech = 0f
                for (index in 0 until count) {
                    val text = decodeNativeUtf8(WhisperCppNative.nativeSegmentText(handle, index)).trim()
                    if (text.isNotEmpty()) {
                        if (builder.isNotEmpty()) builder.append(' ')
                        builder.append(text)
                    }
                    noSpeech = maxOf(noSpeech, WhisperCppNative.nativeSegmentNoSpeechProb(handle, index))
                }
                val detected = WhisperCppNative.nativeDetectedLanguage(handle).takeIf { it.isNotBlank() }
                Transcript(
                    text = builder.toString().trim(),
                    language = detected ?: language,
                    avgLogProb = null,
                    noSpeechProb = if (count > 0) noSpeech else null,
                    compressionRatio = null,
                )
            }
        }

    fun segmentTimestamps(): List<Pair<Long, Long>> = lock.withLock {
        if (closed || context == 0L) return emptyList()
        val count = WhisperCppNative.nativeSegmentCount(context)
        (0 until count).map { index ->
            WhisperCppNative.nativeSegmentStartMs(context, index) to
                WhisperCppNative.nativeSegmentEndMs(context, index)
        }
    }

    override fun close() {
        val toFree = lock.withLock {
            if (closed) return
            closed = true
            val current = context
            context = 0L
            current
        }
        if (toFree != 0L) {
            runCatching { WhisperCppNative.nativeFree(toFree) }
        }
        runCatching { executor.shutdown() }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_THREADS = 4

        val isNativeAvailable: Boolean
            get() = WhisperCppNative.isAvailable

        val nativeLoadError: String?
            get() = WhisperCppNative.loadError

        fun systemInfo(): String =
            if (isNativeAvailable) WhisperCppNative.nativeSystemInfo() else nativeLoadError.orEmpty()

        fun backendDevices(): String =
            if (isNativeAvailable) WhisperCppNative.nativeBackendInfo() else nativeLoadError.orEmpty()

        /**
         * Loads [modelPath]. For [ComputeBackend.AUTO]/[ComputeBackend.GPU] it first asks whisper.cpp
         * for GPU, then retries once on CPU if context creation fails.
         */
        fun create(
            modelPath: String,
            backend: ComputeBackend = ComputeBackend.AUTO,
            threads: Int = DEFAULT_THREADS,
        ): WhisperCppEngine {
            WhisperCppNative.requireAvailable()
            val file = File(modelPath)
            require(file.isFile && file.length() > 0) { "Model file is missing or empty: $modelPath" }
            val threadCount = threads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            WhisperCppNative.nativeSetLogging(true)

            val preferGpu = backend != ComputeBackend.CPU
            var gpuUsed = false
            var handle = WhisperCppNative.nativeInit(modelPath, useGpu = preferGpu, gpuDevice = 0)
            if (handle != 0L) {
                gpuUsed = preferGpu
            } else if (preferGpu) {
                handle = WhisperCppNative.nativeInit(modelPath, useGpu = false, gpuDevice = 0)
            }
            if (handle == 0L) {
                throw SpeechEngineException(
                    "Failed to load whisper.cpp model (missing/corrupt file or incompatible GGML format): $modelPath",
                )
            }

            val hasVulkan = gpuUsed && runCatching { WhisperCppNative.nativeHasVulkanDevice() }.getOrDefault(false)
            val backendName = when {
                !gpuUsed -> "whisper.cpp/cpu"
                hasVulkan -> "whisper.cpp/vulkan"
                else -> "whisper.cpp/gpu"
            }
            val info = buildString {
                append("version=").append(runCatching { WhisperCppNative.nativeVersion() }.getOrDefault("unknown"))
                append("; backend=").append(backendName)
                append("; threads=").append(threadCount)
                append("; system=").append(runCatching { WhisperCppNative.nativeSystemInfo() }.getOrDefault(""))
                append("; devices=").append(runCatching { WhisperCppNative.nativeBackendInfo() }.getOrDefault(""))
            }
            return WhisperCppEngine(
                modelPath = modelPath,
                handle = handle,
                backend = backendName,
                runtimeInfo = info,
                threads = threadCount,
            )
        }
    }
}
