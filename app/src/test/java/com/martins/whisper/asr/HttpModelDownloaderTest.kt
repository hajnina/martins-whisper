package com.martins.whisper.asr

import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.asr.DownloadException
import com.martins.whisper.core.asr.DownloadProgress
import com.martins.whisper.core.asr.HttpModelSource
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.core.asr.ModelStorage
import com.martins.whisper.core.asr.ModelVerificationException
import com.martins.whisper.core.asr.ResumableModelDownloader
import com.martins.whisper.core.asr.Sha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class HttpModelDownloaderTest {

    private val payload = ByteArray(2_000) { (it * 37 % 251).toByte() }
    private val payloadHash = Sha256.hex(payload)
    private val cutBytes = 500

    @Test
    fun `happy path downloads verifies and promotes over real http`() = withTempDir { tmp ->
        FakeHttpServer(payload, cutBytes).use { server ->
            val repo = repository(tmp)
            val progress = CopyOnWriteArrayList<DownloadProgress>()

            val result = repo.install(spec(server, "model"), onProgress = { progress += it })

            assertEquals(1, result.attempts)
            assertFalse(result.resumed)
            assertEquals(payload.size.toLong(), result.bytes)
            assertEquals(payloadHash, result.sha256)
            assertArrayEquals(payload, repo.modelFile(spec(server, "model")).readBytes())
            assertFalse(ModelStorage(tmp).partFile(spec(server, "model")).exists())
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesDownloaded >= a.bytesDownloaded })
            assertEquals(payload.size.toLong(), progress.last().bytesDownloaded)
            assertEquals(payload.size.toLong(), progress.last().totalBytes)
            assertEquals(1, server.requests.size)
            assertNull(server.requests.single().range)
            assertEquals("/model", server.requests.single().path)
        }
    }

    @Test
    fun `resumes after server cuts the connection mid-body`() = withTempDir { tmp ->
        FakeHttpServer(payload, cutBytes).use { server ->
            val repo = repository(tmp)

            val result = repo.install(spec(server, "cut"))

            assertArrayEquals(payload, repo.modelFile(spec(server, "cut")).readBytes())
            assertFalse(ModelStorage(tmp).partFile(spec(server, "cut")).exists())
            assertEquals(payloadHash, result.sha256)
            assertEquals(payload.size.toLong(), result.bytes)
            assertTrue(result.resumed)
            assertEquals(2, result.attempts)
            assertEquals(2, server.requests.size)
            assertNull(server.requests[0].range)
            assertEquals("bytes=$cutBytes-", server.requests[1].range)
            assertEquals("/cut", server.requests[1].path)
        }
    }

    @Test
    fun `bad hash fails verification and leaves no files`() = withTempDir { tmp ->
        FakeHttpServer(payload, cutBytes).use { server ->
            val repo = repository(tmp)
            val bad = spec(server, "model").copy(sha256 = "0".repeat(64))

            try {
                repo.install(bad)
                throw AssertionError("expected ModelVerificationException")
            } catch (_: ModelVerificationException) {
            }

            assertFalse(ModelStorage(tmp).modelFile(bad).exists())
            assertFalse(ModelStorage(tmp).partFile(bad).exists())
        }
    }

    @Test
    fun `404 is reported and leaves no files`() = withTempDir { tmp ->
        FakeHttpServer(payload, cutBytes).use { server ->
            val repo = repository(tmp)
            val missing = spec(server, "missing")

            try {
                repo.install(missing)
                throw AssertionError("expected DownloadException")
            } catch (e: DownloadException) {
                assertTrue("message was: ${e.message}", e.message!!.contains("404"))
            }

            assertFalse(ModelStorage(tmp).modelFile(missing).exists())
            assertFalse(ModelStorage(tmp).partFile(missing).exists())
        }
    }

    private fun repository(tmp: File): ModelRepository {
        val storage = ModelStorage(tmp)
        val downloader = ResumableModelDownloader(
            storage = storage,
            source = HttpModelSource(connectTimeoutMs = 2_000, readTimeoutMs = 5_000),
            maxRetries = 3,
            retryDelayMs = 10,
        )
        return ModelRepository(storage, downloader)
    }

    private fun spec(server: FakeHttpServer, path: String): ModelSpec = ModelSpec(
        id = "http-$path",
        displayName = "HTTP $path",
        fileName = "$path.bin",
        url = "http://127.0.0.1:${server.port}/$path",
        sha256 = payloadHash,
        sizeBytes = payload.size.toLong(),
        multilingual = true,
    )

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("g3a-http").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private data class Request(val method: String, val path: String, val range: String?)

    private class FakeHttpServer(private val payload: ByteArray, private val cutBytes: Int) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<Request>()
        val port: Int get() = serverSocket.localPort
        private val worker = Thread { acceptLoop() }.apply {
            isDaemon = true
            name = "fake-http"
            start()
        }

        private fun acceptLoop() {
            while (!serverSocket.isClosed) {
                try {
                    serverSocket.accept().use { handle(it) }
                } catch (_: IOException) {
                    if (serverSocket.isClosed) return
                }
            }
        }

        private fun handle(socket: Socket) {
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            var range: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).equals("Range", ignoreCase = true)) {
                    range = line.substring(colon + 1).trim()
                }
            }
            requests += Request(method, path, range)

            val out = BufferedOutputStream(socket.getOutputStream())
            when {
                path == "/missing" -> writeResponse(
                    out, "404 Not Found", "missing",
                )
                path == "/cut" && range == null -> writeCut(out)
                else -> writeBody(out, range)
            }
        }

        private fun writeCut(out: BufferedOutputStream) {
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray()
            out.write(headers)
            out.write(payload, 0, cutBytes)
            out.flush()
        }

        private fun writeBody(out: BufferedOutputStream, range: String?) {
            val start = range
                ?.takeIf { it.startsWith("bytes=") }
                ?.substringAfter("bytes=")
                ?.substringBefore('-')
                ?.toLongOrNull()
                ?: 0L
            val remaining = payload.size - start.toInt()
            if (start > 0) {
                val headers = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Length: $remaining\r\n")
                    append("Content-Range: bytes ${start}-${payload.size - 1}/${payload.size}\r\n")
                    append("Content-Type: application/octet-stream\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }.toByteArray()
                out.write(headers)
            } else {
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${payload.size}\r\n")
                    append("Content-Type: application/octet-stream\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }.toByteArray()
                out.write(headers)
            }
            out.write(payload, start.toInt(), remaining)
            out.flush()
        }

        private fun writeResponse(out: BufferedOutputStream, status: String, body: String) {
            val bytes = body.toByteArray()
            val headers = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Content-Type: text/plain\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray()
            out.write(headers)
            out.write(bytes)
            out.flush()
        }

        private fun readLine(input: InputStream): String? {
            val builder = StringBuilder()
            var previous = -1
            while (true) {
                val b = input.read()
                if (b < 0) return if (builder.isEmpty()) null else builder.toString()
                if (b == '\n'.code) {
                    if (previous == '\r'.code) builder.setLength(builder.length - 1)
                    return builder.toString()
                }
                builder.append(b.toChar())
                previous = b
            }
        }

        override fun close() {
            serverSocket.close()
            worker.join(2_000)
        }
    }
}
