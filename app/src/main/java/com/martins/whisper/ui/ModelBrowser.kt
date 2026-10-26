package com.martins.whisper.ui

import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.asr.EngineState
import java.util.Locale

enum class ModelAction { DOWNLOAD, IMPORT, DELETE }

data class ModelRow(
    val spec: ModelSpec,
    val installed: Boolean,
    val selected: Boolean,
)

sealed interface ModelTransfer {
    val modelId: String

    data class Download(
        override val modelId: String,
        val fraction: Float? = null,
        val bytesDownloaded: Long? = null,
        val totalBytes: Long? = null,
    ) : ModelTransfer

    data class Import(
        override val modelId: String,
        val fraction: Float? = null,
        val bytesDownloaded: Long? = null,
        val totalBytes: Long? = null,
    ) : ModelTransfer

    data class Delete(override val modelId: String) : ModelTransfer
}

data class ModelBrowserState(
    val rows: List<ModelRow> = emptyList(),
    val transfer: ModelTransfer? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val isBusy: Boolean
        get() = transfer != null

    fun row(id: String): ModelRow? = rows.firstOrNull { it.spec.id == id }
}

/**
 * Pure model-management state used by the settings screen. It never touches files or the network;
 * the activity performs the blocking work and reports progress back here.
 */
class ModelBrowser(
    private var specs: List<ModelSpec>,
    installedIds: Set<String>,
    selectedId: String,
) {
    private val installed = installedIds.toMutableSet()
    private var selected = selectedId

    var state: ModelBrowserState = snapshot()
        private set

    fun installedIds(): Set<String> = installed.toSet()

    fun select(id: String) {
        if (specs.none { it.id == id }) return
        selected = id
        state = snapshot(transfer = state.transfer, message = state.message, error = state.error)
    }

    fun setSpecs(newSpecs: List<ModelSpec>) {
        specs = newSpecs
        if (specs.none { it.id == selected }) selected = specs.firstOrNull()?.id ?: selected
        installed.retainAll(specs.map { it.id }.toSet())
        state = snapshot(state.transfer, state.message, state.error)
    }

    fun syncInstalled(ids: Set<String>) {
        installed.clear()
        installed.addAll(ids)
        state = snapshot(transfer = state.transfer, message = state.message, error = state.error)
    }

    fun begin(spec: ModelSpec, action: ModelAction): Boolean {
        if (state.transfer != null) return false
        if (specs.none { it.id == spec.id }) {
            specs = specs + spec
            state = snapshot(state.transfer, state.message, state.error)
        }
        return begin(spec.id, action)
    }

    fun begin(id: String, action: ModelAction): Boolean {
        if (state.transfer != null) return false
        if (specs.none { it.id == id }) return false
        if (action == ModelAction.DELETE && id !in installed) return false
        val transfer = when (action) {
            ModelAction.DOWNLOAD -> ModelTransfer.Download(id)
            ModelAction.IMPORT -> ModelTransfer.Import(id)
            ModelAction.DELETE -> ModelTransfer.Delete(id)
        }
        state = snapshot(transfer = transfer)
        return true
    }

    fun progress(fraction: Float?, bytesDownloaded: Long? = null, totalBytes: Long? = null) {
        val clamped = fraction?.coerceIn(0f, 1f)
        state = when (val transfer = state.transfer) {
            is ModelTransfer.Download -> state.copy(
                transfer = transfer.copy(
                    fraction = clamped,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                ),
            )
            is ModelTransfer.Import -> state.copy(
                transfer = transfer.copy(
                    fraction = clamped,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                ),
            )
            else -> state
        }
    }

    fun completeInstalled(id: String) {
        installed += id
        state = snapshot(transfer = null, message = "Model ready")
    }

    fun completeDeleted(id: String) {
        installed -= id
        state = snapshot(transfer = null, message = "Model deleted")
    }

    fun fail(message: String) {
        state = snapshot(transfer = null, error = message)
    }

    fun dismissMessage() {
        state = snapshot(transfer = state.transfer)
    }

    private fun snapshot(
        transfer: ModelTransfer? = null,
        message: String? = null,
        error: String? = null,
    ): ModelBrowserState = ModelBrowserState(
        rows = specs.map { spec ->
            ModelRow(spec = spec, installed = spec.id in installed, selected = spec.id == selected)
        },
        transfer = transfer,
        message = message,
        error = error,
    )
}

object ModelPresentation {
    fun sizeLabel(bytes: Long?): String =
        if (bytes == null || bytes <= 0) "unknown size" else byteCount(bytes)

    fun byteCount(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun statusLabel(installed: Boolean, selected: Boolean): String = when {
        installed && selected -> "Installed \u00b7 selected"
        installed -> "Installed"
        selected -> "Not installed \u00b7 selected"
        else -> "Not installed"
    }

    fun transferLabel(transfer: ModelTransfer, displayName: String): String = when (transfer) {
        is ModelTransfer.Download -> progressText(
            "Downloading",
            displayName,
            transfer.fraction,
            transfer.bytesDownloaded,
            transfer.totalBytes,
        )
        is ModelTransfer.Import -> progressText(
            "Importing",
            displayName,
            transfer.fraction,
            transfer.bytesDownloaded,
            transfer.totalBytes,
        )
        is ModelTransfer.Delete -> "Deleting $displayName\u2026"
    }

    fun fallbackLabel(reason: String?): String = when (reason) {
        null -> "none"
        EngineState.NATIVE_UNAVAILABLE -> "native engine unavailable"
        EngineState.MODEL_MISSING -> "model not installed"
        EngineState.GPU_UNAVAILABLE -> "GPU unavailable; using CPU"
        EngineState.AUTO_CPU -> "auto selected CPU"
        EngineState.ENGINE_ERROR -> "engine error"
        else -> reason
    }

    private fun progressText(
        verb: String,
        displayName: String,
        fraction: Float?,
        bytesDownloaded: Long?,
        totalBytes: Long?,
    ): String {
        val details = buildList {
            if (fraction != null) add("${(fraction * 100).toInt()}%")
            if (bytesDownloaded != null && bytesDownloaded >= 0) {
                add(
                    if (totalBytes != null && totalBytes > 0) {
                        "${byteCount(bytesDownloaded)} / ${byteCount(totalBytes)}"
                    } else {
                        byteCount(bytesDownloaded)
                    },
                )
            }
        }
        return if (details.isEmpty()) {
            "$verb $displayName\u2026"
        } else {
            "$verb $displayName\u2026 ${details.joinToString(" \u00b7 ")}"
        }
    }
}
