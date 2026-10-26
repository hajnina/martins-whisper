package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class ModelDownloaderTest {

    private val payload = ByteArray(1_000) { (it * 31 % 251).toByte() }
    private val payloadHash = Sha256.hex(payload)

    @Test
    fun `downloads verifies and promotes atomically`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val source = ScriptedSource(payload)
        val downloader = downloader(storage, source)
        val progress = CopyOnWriteArrayList<DownloadProgress>()

        val result = downloader.download(spec(), onProgress = { progress += it })

        assertEquals(1, source.opens)
        assertEquals(listOf(0L), source.offsets)
        assertEquals(1, result.attempts)
        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals(payloadHash, result.sha256)
        assertFalse(result.resumed)
        assertArrayEquals(payload, storage.modelFile(spec()).readBytes())
        assertFalse(storage.partFile(spec()).exists())
        assertEquals(payload.size.toLong(), progress.last().bytesDownloaded)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesDownloaded >= a.bytesDownloaded })
    }

    @Test
    fun `resumes after mid-stream failure`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val source = ScriptedSource(payload, failAtByte = 100)
        val sleeps = mutableListOf<Long>()
        val downloader = downloader(storage, source, sleep = { sleeps += it })

        val result = downloader.download(spec())

        assertEquals(2, source.opens)
        assertEquals(listOf(0L, 100L), source.offsets)
        assertEquals(2, result.attempts)
        assertTrue(result.resumed)
        assertEquals(listOf(10L), sleeps)
        assertArrayEquals(payload, storage.modelFile(spec()).readBytes())
    }

    @Test
    fun `server ignoring range restarts cleanly`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val source = ScriptedSource(payload, failAtByte = 100, ignoreRangeOnAttempt = 2)
        val downloader = downloader(storage, source)

        val result = downloader.download(spec())

        assertEquals(2, source.opens)
        assertEquals(listOf(0L, 100L), source.offsets)
        assertFalse(result.resumed)
        assertArrayEquals(payload, storage.modelFile(spec()).readBytes())
    }

    @Test
    fun `retries are bounded and report the failure`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val source = ScriptedSource(payload, failAtByte = 50, failEveryAttempt = true)
        val downloader = downloader(storage, source, maxRetries = 2)

        try {
            downloader.download(spec())
            throw AssertionError("expected DownloadException")
        } catch (e: DownloadException) {
            assertTrue(e.message!!.contains("after 3 attempt"))
        }
        assertEquals(3, source.opens)
    }

    @Test
    fun `hash mismatch deletes the partial file`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val bad = spec(sha256 = "0".repeat(64))
        val downloader = downloader(storage, ScriptedSource(payload))

        try {
            downloader.download(bad)
            throw AssertionError("expected ModelVerificationException")
        } catch (_: ModelVerificationException) {
        }
        assertFalse(storage.modelFile(bad).exists())
        assertFalse(storage.partFile(bad).exists())
    }

    @Test
    fun `existing verified model is reused unless forced`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        storage.ensureModelsDir()
        storage.modelFile(spec()).writeBytes(payload)
        val source = ScriptedSource(payload)
        val downloader = downloader(storage, source)

        val reused = downloader.download(spec())
        assertEquals(0, reused.attempts)
        assertEquals(0, source.opens)

        val forced = downloader.download(spec(), force = true)
        assertEquals(1, forced.attempts)
        assertEquals(1, source.opens)
    }

    @Test
    fun `model without url is rejected`() = withTempDir { tmp ->
        val storage = ModelStorage(tmp)
        val downloader = downloader(storage, ScriptedSource(payload))
        val custom = ModelCatalog.custom(id = "custom", fileName = "custom.bin")
        try {
            downloader.download(custom)
            throw AssertionError("expected DownloadException")
        } catch (_: DownloadException) {
        }
    }

    private fun spec(sha256: String = payloadHash): ModelSpec = ModelSpec(
        id = "test-model",
        displayName = "Test",
        fileName = "ggml-test.bin",
        url = "https://example.invalid/ggml-test.bin",
        sha256 = sha256,
        sizeBytes = payload.size.toLong(),
        multilingual = true,
    )

    private fun downloader(
        storage: ModelStorage,
        source: ModelSource,
        maxRetries: Int = 3,
        sleep: (Long) -> Unit = {},
    ) = ResumableModelDownloader(
        storage = storage,
        source = source,
        maxRetries = maxRetries,
        retryDelayMs = 10,
        bufferSize = 16,
        sleep = sleep,
    )

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("e1-downloader").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private class ScriptedSource(
        private val data: ByteArray,
        private val failAtByte: Long? = null,
        private val failEveryAttempt: Boolean = false,
        private val ignoreRangeOnAttempt: Int? = null,
    ) : ModelSource {
        val offsets = mutableListOf<Long>()
        var opens: Int = 0
            private set

        override fun open(url: String, offset: Long): DownloadResponse {
            opens++
            offsets += offset
            val honorRange = ignoreRangeOnAttempt != opens
            val start = if (honorRange) offset.toInt() else 0
            val resumed = honorRange && offset > 0
            val shouldFail = failAtByte != null && (failEveryAttempt || opens == 1)
            val failAt = if (shouldFail) failAtByte!! else Long.MAX_VALUE

            val stream = object : InputStream() {
                private var pos = start

                override fun read(): Int {
                    val one = ByteArray(1)
                    val n = read(one, 0, 1)
                    return if (n < 0) -1 else one[0].toInt() and 0xff
                }

                override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                    if (pos >= data.size) return -1
                    if (pos >= failAt) throw IOException("simulated network failure at $pos")
                    val allowedByFailure = if (shouldFail) {
                        (failAt - pos).toInt().coerceAtLeast(0)
                    } else {
                        Int.MAX_VALUE
                    }
                    val n = minOf(len, data.size - pos, allowedByFailure)
                    if (n <= 0) throw IOException("simulated network failure at $pos")
                    System.arraycopy(data, pos, buffer, off, n)
                    pos += n
                    return n
                }
            }
            return DownloadResponse(
                input = stream,
                contentLength = (data.size - start).toLong(),
                totalLength = data.size.toLong(),
                resumed = resumed,
            )
        }
    }
}
