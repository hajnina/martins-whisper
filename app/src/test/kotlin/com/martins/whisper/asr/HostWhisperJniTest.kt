package com.martins.whisper.asr

import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.ComputeBackend
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Host tracer bullet: loads the *real* JNI library (built by scripts/host-jni-test.sh), loads
 * ggml-tiny.bin through whisper.cpp, transcribes whisper.cpp's samples/jfk.wav and asserts the
 * literal words. Skipped unless the script provides the fixtures and java.library.path entry.
 */
class HostWhisperJniTest {

    @Test
    fun transcribesJfkWithLiteralWords() {
        val model = fixture("WHISPER_HOST_MODEL")
        val wav = fixture("WHISPER_HOST_JFK")
        assumeTrue(
            "set WHISPER_HOST_MODEL/WHISPER_HOST_JFK and -Pwhisper.host.libDir (scripts/host-jni-test.sh)",
            model != null && wav != null,
        )
        assumeTrue(
            "host libmartins_whisper.so not loadable: ${WhisperCppEngine.nativeLoadError}",
            WhisperCppEngine.isNativeAvailable,
        )
        assertTrue("whisper.cpp version/system info is empty", WhisperCppEngine.systemInfo().isNotBlank())

        val samples = HostWav.read16kMono(wav!!)
        assertTrue("jfk.wav looks empty (${samples.size} samples)", samples.size > 16_000)

        val engine = WhisperCppEngine.create(model!!.absolutePath, ComputeBackend.CPU, threads = 4)
        try {
            val chunk = AudioChunk(
                index = 0,
                samples = samples,
                sampleRate = 16_000,
                startMs = 0,
                endMs = samples.size * 1_000L / 16_000,
                reason = ChunkReason.FLUSH,
            )
            val transcript = runBlocking { engine.transcribe(chunk, language = "en") }
            val normalized = transcript.text.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ").trim()

            assertFalse("native UTF-8 decode produced U+FFFD: ${transcript.text}", transcript.text.contains('\uFFFD'))
            assertTrue(
                "transcript missing the JFK line, got: '${transcript.text}'",
                normalized.contains("ask not what your country can do for you"),
            )
            assertTrue(
                "transcript missing 'fellow americans', got: '${transcript.text}'",
                normalized.contains("fellow americans"),
            )
        } finally {
            engine.close()
        }
    }

    private fun fixture(name: String): File? =
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

    private fun readWav16kMono(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "not a WAV file: $file" }
        fun ascii(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        require(ascii(0) == "RIFF" && ascii(8) == "WAVE") { "not a RIFF/WAVE file: $file" }

        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var pcm: ByteArray? = null
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = ascii(pos)
            val size = le32(bytes, pos + 4)
            val start = pos + 8
            require(size >= 0 && start + size <= bytes.size) { "truncated WAV chunk $id" }
            when (id) {
                "fmt " -> {
                    format = le16(bytes, start)
                    channels = le16(bytes, start + 2)
                    rate = le32(bytes, start + 4)
                    bits = le16(bytes, start + 14)
                }
                "data" -> pcm = bytes.copyOfRange(start, start + size)
            }
            pos = start + size + (size and 1)
        }

        require(format == 1 && channels == 1 && rate == 16_000 && bits == 16) {
            "expected 16 kHz mono PCM16, got format=$format channels=$channels rate=$rate bits=$bits"
        }
        val data = requireNotNull(pcm) { "WAV has no data chunk" }
        return FloatArray(data.size / 2) { index ->
            le16(data, index * 2).toShort() / 32768f
        }
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
