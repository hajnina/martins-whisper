package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Coordinates storage, download and verification. It never downloads implicitly: callers must
 * explicitly [install] a catalog model or [importFile] a custom one.
 */
class ModelRepository(
    val storage: ModelStorage,
    private val downloader: ModelDownloader = ResumableModelDownloader(storage),
    private val sha256: (File) -> String = Sha256::hex,
) {
    val modelsDir: File
        get() = storage.modelsDir

    fun modelFile(spec: ModelSpec): File = storage.modelFile(spec)

    fun exists(spec: ModelSpec): Boolean = storage.exists(spec)

    /** Verifies the hash when [verifyHash] is true (streams the whole file, so avoid on the UI thread). */
    fun isInstalled(spec: ModelSpec, verifyHash: Boolean = false): Boolean {
        if (!storage.exists(spec)) return false
        if (!verifyHash) return true
        return Sha256.matches(storage.modelFile(spec), spec.sha256)
    }

    fun installed(specs: List<ModelSpec> = ModelCatalog.specs, verifyHash: Boolean = false): List<ModelSpec> =
        specs.filter { isInstalled(it, verifyHash) }

    fun install(
        spec: ModelSpec,
        force: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        require(spec.url != null) { "Model '${spec.id}' has no URL; use importFile()" }
        return downloader.download(spec, force, onProgress)
    }

    /**
     * Imports an existing model file (for example via the Storage Access Framework) and verifies it.
     * The target is only replaced after verification succeeds.
     */
    fun importFile(
        spec: ModelSpec,
        source: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        require(source.isFile) { "Source model does not exist: $source" }
        val target = storage.modelFile(spec)
        val part = storage.partFile(spec)
        if (source.canonicalFile == target.canonicalFile) {
            val hash = spec.sha256 ?: sha256(source)
            if (spec.sha256 != null && !Sha256.matches(source, spec.sha256)) {
                throw ModelVerificationException("SHA256 mismatch for '${spec.id}'")
            }
            return DownloadResult(source, source.length(), hash, resumed = false, attempts = 0)
        }

        storage.ensureModelsDir()
        val total = source.length()
        var copied = 0L
        onProgress(DownloadProgress(0, total, 0))
        source.inputStream().use { input ->
            FileOutputStream(part, false).use { output ->
                copied = copyStream(input, output, total, onProgress)
                output.flush()
            }
        }
        if (total > 0 && copied != total) {
            part.delete()
            throw IOException("Import incomplete: expected $total bytes, got $copied")
        }
        val actualHash = sha256(part)
        if (spec.sha256 != null && actualHash != spec.sha256.lowercase()) {
            part.delete()
            throw ModelVerificationException(
                "SHA256 mismatch for '${spec.id}': expected ${spec.sha256}, got $actualHash",
            )
        }
        storage.promote(part, target)
        onProgress(DownloadProgress(target.length(), target.length(), 0))
        return DownloadResult(target, target.length(), spec.sha256 ?: actualHash, resumed = false, attempts = 0)
    }

    fun delete(spec: ModelSpec): Boolean = storage.delete(spec)

    private fun copyStream(
        input: InputStream,
        output: FileOutputStream,
        total: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(DownloadProgress(copied, total.takeIf { it > 0 }, 0))
        }
        return copied
    }

    companion object {
        /** Convenience for `context.filesDir`: stores models under `<filesDir>/models`. */
        fun forFilesDir(filesDir: File): ModelRepository =
            ModelRepository(ModelStorage(filesDir))
    }
}
