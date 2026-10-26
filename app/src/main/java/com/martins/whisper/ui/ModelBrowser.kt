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

    data class Download(override val modelId: String, val fraction: Float? = null) : ModelTransfer
    data class Import(override val modelId: String, val fraction: Float? = null) : ModelTransfer
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
    private val specs: List<ModelSpec>,
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

    fun syncInstalled(ids: Set<String>) {
        installed.clear()
        installed.addAll(ids)
        state = snapshot(transfer = state.transfer, message = state.message, error = state.error)
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

    fun progress(fraction: Float?) {
        val clamped = fraction?.coerceIn(0f, 1f)
        state = when (val transfer = state.transfer) {
            is ModelTransfer.Download -> state.copy(transfer = transfer.copy(fraction = clamped))
            is ModelTransfer.Import -> state.copy(transfer = transfer.copy(fraction = clamped))
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
    fun sizeLabel(bytes: Long?): String = when {
        bytes == null || bytes <= 0 -> "unknown size"
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        else -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
    }

    fun statusLabel(installed: Boolean, selected: Boolean): String = when {
        installed && selected -> "Installed \u00b7 selected"
        installed -> "Installed"
        selected -> "Not installed \u00b7 selected"
        else -> "Not installed"
    }

    fun transferLabel(transfer: ModelTransfer, displayName: String): String = when (transfer) {
        is ModelTransfer.Download -> progressText("Downloading", displayName, transfer.fraction)
        is ModelTransfer.Import -> progressText("Importing", displayName, transfer.fraction)
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

    private fun progressText(verb: String, displayName: String, fraction: Float?): String =
        if (fraction == null) {
            "$verb $displayName\u2026"
        } else {
            "$verb $displayName\u2026 ${(fraction * 100).toInt()}%"
        }
}
