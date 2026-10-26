package com.martins.whisper.service

import android.content.Context
import java.io.File

/** One JSONL record per finished dictation session (protocol: owner/HUMAN-TEST-PROTOCOL.md). */
data class DictationRecord(
    val tTapStart: Long,
    val tMicOpen: Long,
    val tTapStop: Long,
    val audioMs: Long,
    val sampleRate: Int,
    val peakDbfs: Float,
    val rmsDbfs: Float,
    val noiseFloorDbfs: Float,
    val gateThresholdDb: Float,
    val gateVerdict: String,
    val model: String,
    val language: String,
    val threads: Int,
    val engineLoadMs: Long,
    val engineInferMs: Long,
    val textLen: Int,
    val targetPackage: String?,
    val targetChanged: Boolean?,
    val tInserted: Long,
    val insertMethod: String,
    val result: String,
    val error: String?,
) {
    fun toJson(): String = org.json.JSONObject()
        .put("t_tap_start", tTapStart)
        .put("t_mic_open", tMicOpen)
        .put("t_tap_stop", tTapStop)
        .put("audio_ms", audioMs)
        .put("sample_rate", sampleRate)
        .put("peak_dBFS", peakDbfs.round(1))
        .put("rms_dBFS", rmsDbfs.round(1))
        .put("noise_floor_dBFS", noiseFloorDbfs.round(1))
        .put("gate_threshold", gateThresholdDb)
        .put("gate_verdict", gateVerdict)
        .put("model", model)
        .put("language", language)
        .put("threads", threads)
        .put("engine_load_ms", engineLoadMs)
        .put("engine_infer_ms", engineInferMs)
        .put("text_len", textLen)
        .put("target_package", targetPackage ?: org.json.JSONObject.NULL)
        .put("target_changed", targetChanged ?: org.json.JSONObject.NULL)
        .put("t_inserted", tInserted)
        .put("insert_method", insertMethod)
        .put("result", result)
        .put("error", error ?: org.json.JSONObject.NULL)
        .put("ts_utc", System.currentTimeMillis())
        .toString()

    private fun Float.round(digits: Int): String =
        String.format(java.util.Locale.US, "%.${digits}f", this)
}

/**
 * Local-only dictation telemetry: appends one JSONL line per session to
 * files/telemetry/records.jsonl and mirrors the latest record to files/telemetry/last.json,
 * both pullable with adb. Never networked.
 */
class TelemetryRecorder(private val dir: File) {

    fun record(target: DictationRecord) {
        dir.mkdirs()
        File(dir, "records.jsonl").appendText(target.toJson() + "\n")
        File(dir, "last.json").writeText(target.toJson())
    }

    companion object {
        fun forContext(context: Context): TelemetryRecorder =
            TelemetryRecorder(File(context.filesDir, "telemetry"))
    }
}

/** Accumulates one dictation session and publishes it at the end (or on every terminal state). */
class DictationTelemetry(private val recorder: TelemetryRecorder?) {

    var tTapStart: Long = 0
    var tMicOpen: Long = 0
    var tTapStop: Long = 0
    var audioMs: Long = 0
    var sampleRate: Int = 16_000
    var peakDbfs: Float = 0f
    var rmsDbfs: Float = 0f
    var noiseFloorDbfs: Float = 0f
    var gateThresholdDb: Float = 0f
    var gateVerdict: String = ""
    var model: String = ""
    var language: String = ""
    var threads: Int = 0
    var engineLoadMs: Long = 0
    var engineInferMs: Long = 0
    var textLen: Int = 0
    var targetPackage: String? = null
    var targetChanged: Boolean? = null
    var tInserted: Long = 0
    var insertMethod: String = ""
    var error: String? = null

    fun publishTelemetry(result: String, error: String? = null) {
        val record = DictationRecord(
            tTapStart = tTapStart,
            tMicOpen = tMicOpen,
            tTapStop = tTapStop,
            audioMs = audioMs,
            sampleRate = sampleRate,
            peakDbfs = peakDbfs,
            rmsDbfs = rmsDbfs,
            noiseFloorDbfs = noiseFloorDbfs,
            gateThresholdDb = gateThresholdDb,
            gateVerdict = gateVerdict,
            model = model,
            language = language,
            threads = threads,
            engineLoadMs = engineLoadMs,
            engineInferMs = engineInferMs,
            textLen = textLen,
            targetPackage = targetPackage,
            targetChanged = targetChanged,
            tInserted = tInserted,
            insertMethod = insertMethod,
            result = result,
            error = error,
        )
        try {
            recorder?.record(record)
        } catch (t: Throwable) {
            android.util.Log.d("MartinsWhisper", "telemetry write failed: ${t.message}")
        }
    }
}
