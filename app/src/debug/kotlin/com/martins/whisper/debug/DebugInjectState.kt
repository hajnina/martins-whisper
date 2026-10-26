package com.martins.whisper.debug

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Debug-only selection and live status for WAV injection. The device script writes a fixture
 * into files/inject/ and selects it with a broadcast; InjectAudioSource reads this state when
 * a dictation session starts. Never present in release builds (debug source set only).
 */
object DebugInjectState {

    private val NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.wav")

    fun dir(context: Context): File = File(context.filesDir, "inject").apply { mkdirs() }

    fun wavFile(context: Context, name: String): File = File(dir(context), name)

    fun selected(context: Context): File? {
        val marker = File(dir(context), "selected.txt")
        if (!marker.isFile) return null
        return resolve(context, marker.readText().trim())
    }

    fun select(context: Context, name: String): File {
        val file = resolve(context, name) ?: throw IllegalArgumentException("unsafe or missing WAV: $name")
        File(dir(context), "selected.txt").writeText(file.name)
        writeStatus(
            context,
            InjectionStatus(wav = file.name, state = "selected", samplesEmitted = 0, totalSamples = 0),
        )
        return file
    }

    fun clear(context: Context) {
        File(dir(context), "selected.txt").delete()
        writeStatus(context, InjectionStatus(wav = null, state = "cleared", samplesEmitted = 0, totalSamples = 0))
    }

    fun resolve(context: Context, name: String): File? {
        val bare = name.substringAfterLast('/')
        if (!NAME.matches(bare)) return null
        if (bare != name) return null
        return wavFile(context, bare).takeIf { it.isFile }
    }

    fun writeStatus(context: Context, status: InjectionStatus) {
        val json = JSONObject()
            .put("wav", status.wav ?: JSONObject.NULL)
            .put("state", status.state)
            .put("samples_emitted", status.samplesEmitted)
            .put("total_samples", status.totalSamples)
            .put("duration_ms", status.durationMs)
            .put("started_at", status.startedAt ?: JSONObject.NULL)
            .put("completed_at", status.completedAt ?: JSONObject.NULL)
            .put("error", status.error ?: JSONObject.NULL)
        File(dir(context), "status.json").writeText(json.toString())
    }

    data class InjectionStatus(
        val wav: String?,
        val state: String,
        val samplesEmitted: Long,
        val totalSamples: Long,
        val durationMs: Long = 0,
        val startedAt: Long? = null,
        val completedAt: Long? = null,
        val error: String? = null,
    )
}
