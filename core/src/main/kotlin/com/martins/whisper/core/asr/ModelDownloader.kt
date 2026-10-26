package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_RANGE_NOT_SATISFIABLE = 416

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val resumedFrom: Long,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toDouble() / it).toFloat().coerceIn(0f, 1f) }
}

data class DownloadResult(
    val file: File,
    val bytes: Long,
    val sha256: String?,
    val resumed: Boolean,
    val attempts: Int,
)

/** One open HTTP response. [input] must be closed by the caller. */
data class DownloadResponse(
    val input: InputStream,
    val contentLength: Long?,
    val totalLength: Long?,
    val resumed: Boolean,
)

fun interface ModelSource {
    /** Opens [url] starting at [offset]. Implementations should send a Range request when offset > 0. */
    fun open(url: String, offset: Long): DownloadResponse
}

open class DownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ModelVerificationException(message: String) : IOException(message)

interface ModelDownloader {
    fun download(
        spec: ModelSpec,
        force: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult
}

/**
 * Resumable, retrying downloader. Writes to `<id>.bin.part`, verifies SHA256, then atomically
 * promotes the file. Blocking I/O: call from a background thread/coroutine.
 */
class ResumableModelDownloader(
    private val storage: ModelStorage,
    private val source: ModelSource = HttpModelSource(),
    private val maxRetries: Int = 3,
    private val retryDelayMs: Long = 750L,
    private val bufferSize: Int = 64 * 1024,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val sha256: (File) -> String = Sha256::hex,
) : ModelDownloader {

    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(retryDelayMs >= 0) { "retryDelayMs must be >= 0" }
        require(bufferSize > 0) { "bufferSize must be > 0" }
    }

    override fun download(
        spec: ModelSpec,
        force: Boolean,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult {
        val url = spec.url ?: throw DownloadException("Model '${spec.id}' has no download URL (custom import required)")
        val target = storage.modelFile(spec)
        val part = storage.partFile(spec)
        storage.ensureModelsDir()

        if (!force && target.isFile && target.length() > 0) {
            val digest = if (Sha256.matches(target, spec.sha256)) {
                spec.sha256 ?: sha256(target)
            } else {
                null
            }
            if (digest != null) {
                onProgress(DownloadProgress(target.length(), target.length(), target.length()))
                return DownloadResult(target, target.length(), digest, resumed = false, attempts = 0)
            }
            target.delete()
        }

        var attempts = 0
        var lastError: IOException? = null
        while (attempts <= maxRetries) {
            attempts++
            try {
                return downloadOnce(spec, url, target, part, onProgress, attempts)
            } catch (e: ModelVerificationException) {
                part.delete()
                throw e
            } catch (e: IOException) {
                lastError = e
                part.parentFile?.mkdirs()
                if (attempts > maxRetries) break
                sleep(retryDelayMs * attempts)
            }
        }
        throw DownloadException(
            "Download of '${spec.id}' failed after $attempts attempt(s): ${lastError?.message}",
            lastError,
        )
    }

    private fun downloadOnce(
        spec: ModelSpec,
        url: String,
        target: File,
        part: File,
        onProgress: (DownloadProgress) -> Unit,
        attempt: Int,
    ): DownloadResult {
        val offset = if (part.isFile) part.length() else 0L
        val response = source.open(url, offset)
        var resumed = offset > 0 && response.resumed
        try {
            if (offset > 0 && !resumed) part.delete()
            if (resumed && response.totalLength != null && offset > response.totalLength) {
                part.delete()
                throw IOException("Server resource is smaller than the partial file")
            }
            val append = resumed && part.isFile
            var downloaded = if (append) offset else 0L
            val total = when {
                response.totalLength != null -> response.totalLength
                resumed -> response.contentLength?.let { offset + it }
                else -> response.contentLength
            }
            onProgress(DownloadProgress(downloaded, total, if (append) offset else 0L))

            part.parentFile?.mkdirs()
            response.input.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(bufferSize)
                    var copiedThisResponse = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copiedThisResponse += read
                        downloaded += read
                        onProgress(DownloadProgress(downloaded, total, if (append) offset else 0L))
                    }
                    output.flush()
                    if (response.contentLength != null && response.contentLength >= 0 &&
                        copiedThisResponse != response.contentLength
                    ) {
                        throw IOException(
                            "Short read: expected ${response.contentLength} bytes, got $copiedThisResponse",
                        )
                    }
                }
            }
            if (total != null && downloaded != total) {
                throw IOException("Incomplete download: expected $total bytes, got $downloaded")
            }

            val actualHash = sha256(part)
            val expected = spec.sha256?.lowercase()
            if (expected != null && actualHash != expected) {
                throw ModelVerificationException(
                    "SHA256 mismatch for '${spec.id}': expected $expected, got $actualHash",
                )
            }
            storage.promote(part, target)
            onProgress(DownloadProgress(downloaded, total ?: downloaded, if (append) offset else 0L))
            return DownloadResult(
                file = target,
                bytes = target.length(),
                sha256 = expected ?: actualHash,
                resumed = resumed,
                attempts = attempt,
            )
        } finally {
            runCatching { response.input.close() }
        }
    }
}

/**
 * Plain JDK HTTP source (works on Android too). Uses Range requests and `Accept-Encoding: identity`
 * so that resumption is byte-accurate. No third-party HTTP client is required.
 */
class HttpModelSource(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
    private val userAgent: String = "MartinsWhisper/0.1 (local model downloader)",
) : ModelSource {

    override fun open(url: String, offset: Long): DownloadResponse {
        require(offset >= 0) { "offset must be >= 0" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", userAgent)
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }

        val code = try {
            connection.responseCode
        } catch (e: IOException) {
            connection.disconnect()
            throw DownloadException("Cannot connect to $url", e)
        }

        if (code == HTTP_RANGE_NOT_SATISFIABLE && offset > 0) {
            connection.disconnect()
            return DownloadResponse(ByteArray(0).inputStream(), 0L, offset, resumed = true)
        }
        if (code !in 200..299) {
            val message = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
            connection.disconnect()
            throw DownloadException("HTTP $code for $url${message?.let { ": ${it.take(200)}" } ?: ""}")
        }

        val resumed = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL
        val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
        val rangeTotal = connection.getHeaderField("Content-Range")
            ?.substringAfterLast('/', "")
            ?.takeIf { it != "*" }
            ?.toLongOrNull()
        val totalLength = if (resumed) rangeTotal else contentLength

        val stream = object : FilterInputStream(connection.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    connection.disconnect()
                }
            }
        }
        return DownloadResponse(stream, contentLength, totalLength, resumed)
    }
}
