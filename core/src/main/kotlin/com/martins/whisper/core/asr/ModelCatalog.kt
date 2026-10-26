package com.martins.whisper.core.asr

import com.martins.whisper.core.ModelSpec

/**
 * Catalog of downloadable whisper.cpp GGML models.
 *
 * All URLs point at the canonical `ggerganov/whisper.cpp` Hugging Face repository.
 * Sizes and SHA256 values were taken from the Hugging Face LFS metadata; the tiny and
 * base hashes were additionally verified by downloading the files and running sha256sum.
 */
object ModelCatalog {
    const val DEFAULT_MODEL_ID: String = "base"

    private const val HF_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    val specs: List<ModelSpec> = listOf(
        model(
            id = "tiny",
            displayName = "Tiny (multilingual)",
            file = "ggml-tiny.bin",
            size = 77_691_713L,
            sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            multilingual = true,
            notes = "Fastest, lowest accuracy. Good for low-end devices.",
        ),
        model(
            id = "tiny.en",
            displayName = "Tiny (English)",
            file = "ggml-tiny.en.bin",
            size = 77_704_715L,
            sha256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",
            multilingual = false,
            notes = "English-only tiny model.",
        ),
        model(
            id = "base",
            displayName = "Base (multilingual)",
            file = "ggml-base.bin",
            size = 147_951_465L,
            sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            multilingual = true,
            notes = "Default model. Good speed/quality trade-off.",
        ),
        model(
            id = "base.en",
            displayName = "Base (English)",
            file = "ggml-base.en.bin",
            size = 147_964_211L,
            sha256 = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
            multilingual = false,
            notes = "English-only base model.",
        ),
        model(
            id = "small",
            displayName = "Small (multilingual)",
            file = "ggml-small.bin",
            size = 487_601_967L,
            sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            multilingual = true,
            notes = "Higher accuracy, noticeably slower.",
        ),
        model(
            id = "small.en",
            displayName = "Small (English)",
            file = "ggml-small.en.bin",
            size = 487_614_201L,
            sha256 = "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d",
            multilingual = false,
            notes = "English-only small model.",
        ),
        model(
            id = "medium",
            displayName = "Medium (multilingual)",
            file = "ggml-medium.bin",
            size = 1_533_763_059L,
            sha256 = "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208",
            multilingual = true,
            notes = "Desktop-class accuracy; may be too slow for phones.",
        ),
        model(
            id = "medium.en",
            displayName = "Medium (English)",
            file = "ggml-medium.en.bin",
            size = 1_533_774_781L,
            sha256 = "cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356",
            multilingual = false,
            notes = "English-only medium model.",
        ),
        model(
            id = "large-v3-turbo",
            displayName = "Large v3 Turbo (multilingual)",
            file = "ggml-large-v3-turbo.bin",
            size = 1_624_555_275L,
            sha256 = "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69",
            multilingual = true,
            notes = "Best quality; large download and high memory use.",
        ),
    )

    private val specsById: Map<String, ModelSpec> = specs.associateBy { it.id }

    val default: ModelSpec = requireNotNull(specsById[DEFAULT_MODEL_ID])

    fun byId(id: String): ModelSpec? = specsById[id]

    fun requireById(id: String): ModelSpec =
        specsById[id] ?: throw NoSuchElementException("Unknown model id: $id")

    fun isKnown(id: String): Boolean = specsById.containsKey(id)

    /**
     * A user-supplied model. No URL is set: the caller imports an existing file.
     * [id] must be stable because it is used for the on-disk file name and settings persistence.
     */
    fun custom(
        id: String,
        fileName: String,
        displayName: String = fileName,
        sha256: String? = null,
        sizeBytes: Long? = null,
        multilingual: Boolean = true,
    ): ModelSpec {
        require(id.isNotBlank()) { "custom model id must not be blank" }
        require(id.none { it == '/' || it == '\\' }) { "custom model id must not contain path separators" }
        require(id != "." && id != "..") { "custom model id must not be a relative path" }
        require(fileName.isNotBlank()) { "custom model file name must not be blank" }
        val normalizedSha = sha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        require(normalizedSha == null || normalizedSha.matches(HEX_64)) {
            "sha256 must be 64 hexadecimal characters"
        }
        require(sizeBytes == null || sizeBytes > 0) { "sizeBytes must be positive" }
        return ModelSpec(
            id = id,
            displayName = displayName,
            fileName = fileName,
            url = null,
            sha256 = normalizedSha,
            sizeBytes = sizeBytes,
            multilingual = multilingual,
            notes = "Custom imported model.",
        )
    }

    private fun model(
        id: String,
        displayName: String,
        file: String,
        size: Long,
        sha256: String,
        multilingual: Boolean,
        notes: String,
    ): ModelSpec = ModelSpec(
        id = id,
        displayName = displayName,
        fileName = file,
        url = "$HF_BASE/$file",
        sha256 = sha256,
        sizeBytes = size,
        multilingual = multilingual,
        notes = notes,
    )

    private val HEX_64 = Regex("[0-9a-f]{64}")
}
