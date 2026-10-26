package com.martins.whisper.ui

import com.martins.whisper.core.ModelSpec

/**
 * Local catalog stub used until E1 exposes [com.martins.whisper.core.ModelCatalog].
 * Ids and filenames follow the whisper.cpp GGML naming convention so persistence survives
 * the later swap to the core catalog.
 */
object ModelOptions {
    val specs: List<ModelSpec> = listOf(
        ModelSpec(
            id = "tiny",
            displayName = "Tiny (multilingual)",
            fileName = "ggml-tiny.bin",
            url = null,
            sha256 = null,
            sizeBytes = 75L * 1024 * 1024,
            multilingual = true,
        ),
        ModelSpec(
            id = "base",
            displayName = "Base (multilingual, default)",
            fileName = "ggml-base.bin",
            url = null,
            sha256 = null,
            sizeBytes = 142L * 1024 * 1024,
            multilingual = true,
        ),
        ModelSpec(
            id = "small",
            displayName = "Small (multilingual)",
            fileName = "ggml-small.bin",
            url = null,
            sha256 = null,
            sizeBytes = 466L * 1024 * 1024,
            multilingual = true,
        ),
        ModelSpec(
            id = "medium",
            displayName = "Medium (multilingual)",
            fileName = "ggml-medium.bin",
            url = null,
            sha256 = null,
            sizeBytes = 1500L * 1024 * 1024,
            multilingual = true,
        ),
        ModelSpec(
            id = "large-v3-turbo",
            displayName = "Large v3 Turbo",
            fileName = "ggml-large-v3-turbo.bin",
            url = null,
            sha256 = null,
            sizeBytes = 1600L * 1024 * 1024,
            multilingual = true,
        ),
    )

    fun byId(id: String): ModelSpec? = specs.firstOrNull { it.id == id }

    fun displayName(id: String): String = byId(id)?.displayName ?: id
}
