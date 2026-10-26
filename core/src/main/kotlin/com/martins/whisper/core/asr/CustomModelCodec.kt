package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec
import java.util.Base64

/**
 * Persists imported (non-catalog) model descriptors in SharedPreferences.
 *
 * The format is deliberately boring: one newline-separated record per model, text fields
 * Base64-URL encoded so display names with separators are safe. Decoding never throws; malformed
 * or partially written records are skipped so a bad preference cannot brick model selection.
 */
object CustomModelCodec {

    private const val VERSION = "v1"
    private const val FIELD_SEPARATOR = "|"
    private val HEX_64 = Regex("[0-9a-f]{64}")

    fun encode(models: List<ModelSpec>): String =
        models.mapNotNull(::encodeOne).joinToString("\n")

    fun decode(raw: String?): List<ModelSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return raw.lineSequence()
            .mapNotNull { decodeOne(it) }
            .filter { seen.add(it.id) }
            .toList()
    }

    private fun encodeOne(spec: ModelSpec): String? = runCatching {
        listOf(
            VERSION,
            b64(spec.id),
            b64(spec.displayName),
            b64(spec.fileName),
            spec.sha256.orEmpty(),
            spec.sizeBytes?.toString().orEmpty(),
            if (spec.multilingual) "1" else "0",
        ).joinToString(FIELD_SEPARATOR)
    }.getOrNull()

    private fun decodeOne(line: String): ModelSpec? {
        if (line.isBlank()) return null
        val parts = line.split(FIELD_SEPARATOR)
        if (parts.size != 7 || parts[0] != VERSION) return null
        return runCatching {
            val id = unb64(parts[1])
            val displayName = unb64(parts[2])
            val fileName = unb64(parts[3])
            val sha = parts[4].trim().lowercase().takeIf { it.isNotEmpty() }
            val size = parts[5].takeIf { it.isNotEmpty() }?.toLong()
            require(id.isNotBlank() && displayName.isNotBlank() && fileName.isNotBlank())
            require(id.none { it == '/' || it == '\\' } && id != "." && id != "..")
            require(sha == null || sha.matches(HEX_64))
            require(size == null || size > 0)
            ModelSpec(
                id = id,
                displayName = displayName,
                fileName = fileName,
                url = null,
                sha256 = sha,
                sizeBytes = size,
                multilingual = parts[6] == "1",
                notes = "Custom imported model.",
            )
        }.getOrNull()
    }

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unb64(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
