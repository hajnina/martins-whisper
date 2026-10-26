package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Streaming SHA256 helpers. Never loads the whole model into memory. */
object Sha256 {
    private const val BUFFER_SIZE = 64 * 1024

    fun hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun matches(file: File, expectedHex: String?): Boolean {
        if (!file.isFile) return false
        val expected = expectedHex?.trim()?.lowercase().orEmpty()
        if (expected.isEmpty()) return true
        return hex(file) == expected
    }
}

/**
 * Owns the on-disk layout: `<root>/models/<id>.bin` and `<root>/models/<id>.bin.part`.
 * The id is sanitized so catalog ids are safe even if they come from persisted user input.
 */
class ModelStorage(val rootDir: File) {
    val modelsDir: File
        get() = File(rootDir, MODELS_DIR_NAME)

    fun modelFile(spec: ModelSpec): File = File(modelsDir, "${safeId(spec.id)}.bin")

    fun partFile(spec: ModelSpec): File = File(modelFile(spec).path + PART_SUFFIX)

    fun ensureModelsDir(): File {
        if (!modelsDir.isDirectory && !modelsDir.mkdirs() && !modelsDir.isDirectory) {
            throw java.io.IOException("Cannot create models directory: $modelsDir")
        }
        return modelsDir
    }

    fun exists(spec: ModelSpec): Boolean = modelFile(spec).let { it.isFile && it.length() > 0 }

    fun delete(spec: ModelSpec): Boolean {
        val model = modelFile(spec).delete()
        val part = partFile(spec).delete()
        return model || part
    }

    fun installed(specs: List<ModelSpec> = ModelCatalog.specs): List<ModelSpec> =
        specs.filter { exists(it) }

    fun safeId(id: String): String {
        val cleaned = id.map { ch ->
            if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_'
        }.joinToString("")
        require(cleaned.isNotEmpty()) { "model id must contain at least one safe character" }
        require(cleaned != "." && cleaned != "..") { "invalid model id: $id" }
        return cleaned
    }

    /** Best-effort atomic replace used after verification. */
    internal fun promote(part: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                part.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val MODELS_DIR_NAME = "models"
        const val PART_SUFFIX = ".part"
    }
}
