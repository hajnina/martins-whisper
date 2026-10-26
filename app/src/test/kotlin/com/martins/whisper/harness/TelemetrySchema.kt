package com.martins.whisper.harness

import java.io.File
import org.json.JSONObject

/**
 * Contract for MARA's per-dictation telemetry (`files/telemetry/records.jsonl` + `last.json`).
 *
 * The device is the source of truth for these records, so this validator is deliberately strict
 * about keys, enums, ranges and the timestamps that must be monotonic. It accepts the numeric
 * strings the producer uses for dBFS (`String.format("%.1f", ...)`) and the `""` gate verdict a
 * pre-audio failure (engine load / mic start) legitimately publishes with `audio_ms == 0`.
 */
object TelemetrySchema {

    val REQUIRED_FIELDS: List<String> = listOf(
        "t_tap_start", "t_mic_open", "t_tap_stop", "audio_ms", "sample_rate",
        "peak_dBFS", "rms_dBFS", "noise_floor_dBFS", "gate_threshold", "gate_verdict",
        "model", "language", "threads", "engine_load_ms", "engine_infer_ms", "text_len",
        "target_package", "target_changed", "t_inserted", "insert_method", "result", "error",
        "ts_utc",
    )

    val GATE_VERDICTS: Set<String> = setOf("speak", "no-speech", "silent-mic")
    val RESULTS: Set<String> = setOf("inserted", "insert-failed", "rejected", "error")
    val INSERT_METHODS: Set<String> = setOf("accessibility", "clipboard")
    val TELEMETRY_DIR_NAME: String = "telemetry"

    fun parse(line: String): JSONObject = JSONObject(line)

    fun readRecord(file: File): JSONObject = parse(file.readText())

    /** Validates one record; returns human-readable problems, empty list means valid. */
    fun validate(json: JSONObject): List<String> {
        val errors = mutableListOf<String>()
        REQUIRED_FIELDS.filterNot { json.has(it) }.forEach { errors += "missing:$it" }

        val tTapStart = json.longOrNull("t_tap_start", errors)
        val tMicOpen = json.longOrNull("t_mic_open", errors)
        val tTapStop = json.longOrNull("t_tap_stop", errors)
        val audioMs = json.longOrNull("audio_ms", errors)
        val engineLoadMs = json.longOrNull("engine_load_ms", errors)
        val engineInferMs = json.longOrNull("engine_infer_ms", errors)
        val tInserted = json.longOrNull("t_inserted", errors)
        val tsUtc = json.longOrNull("ts_utc", errors)
        val sampleRate = json.intOrNull("sample_rate", errors)
        val threads = json.intOrNull("threads", errors)
        val textLen = json.intOrNull("text_len", errors)
        val peak = json.doubleOrNull("peak_dBFS", errors)
        val rms = json.doubleOrNull("rms_dBFS", errors)
        val floor = json.doubleOrNull("noise_floor_dBFS", errors)
        val gateThreshold = json.doubleOrNull("gate_threshold", errors)

        val gate = json.stringOrNull("gate_verdict", errors)
        val result = json.stringOrNull("result", errors)
        val method = json.stringOrNull("insert_method", errors)
        val model = json.stringOrNull("model", errors)
        val language = json.stringOrNull("language", errors)
        val error = json.stringOrNull("error", errors, allowNull = true)
        val targetPackage = json.stringOrNull("target_package", errors, allowNull = true)
        val targetChanged = json.booleanOrNull("target_changed", errors)
        if (json.has("target_changed") && !json.isNull("target_changed") && targetChanged == null) {
            errors += "not-boolean:target_changed"
        }
        if (json.has("target_package") && !json.isNull("target_package") &&
            targetPackage != null && targetPackage.isEmpty()
        ) {
            errors += "empty:target_package"
        }

        if (gate != null && gate.isNotEmpty() && gate !in GATE_VERDICTS) {
            errors += "enum:gate_verdict=$gate (expected $GATE_VERDICTS or \"\" for a pre-audio failure)"
        }
        if (result != null && result !in RESULTS) {
            errors += "enum:result=$result (expected $RESULTS)"
        }
        if (method != null && method.isNotEmpty() && method !in INSERT_METHODS) {
            errors += "enum:insert_method=$method (expected $INSERT_METHODS or \"\")"
        }
        if (model != null && model.isBlank()) errors += "empty:model"
        if (language != null && language.isNotEmpty() && !LANGUAGE.matches(language)) {
            errors += "format:language=$language"
        }
        if (targetPackage != null && !TARGET_PACKAGE.matches(targetPackage)) {
            errors += "format:target_package=$targetPackage"
        }

        if (sampleRate != null && sampleRate != 16_000) errors += "range:sample_rate=$sampleRate (expected 16000)"
        if (threads != null && threads !in 1..64) errors += "range:threads=$threads"
        if (textLen != null && textLen < 0) errors += "negative:text_len=$textLen"
        if (audioMs != null && audioMs < 0) errors += "negative:audio_ms=$audioMs"
        if (engineLoadMs != null && engineLoadMs < 0) errors += "negative:engine_load_ms=$engineLoadMs"
        if (engineInferMs != null && engineInferMs < 0) errors += "negative:engine_infer_ms=$engineInferMs"
        if (tsUtc != null && tsUtc <= 0) errors += "range:ts_utc=$tsUtc"

        val finalized = (audioMs ?: 0) > 0
        if (finalized) {
            if (gate != null && gate !in GATE_VERDICTS) {
                errors += "enum:gate_verdict=$gate for a finalized dictation"
            }
            if (tTapStop != null && tTapStop <= 0) errors += "range:t_tap_stop=$tTapStop"
            if (gateThreshold != null && (gateThreshold <= 0f || gateThreshold > 60f)) {
                errors += "range:gate_threshold=$gateThreshold"
            }
            listOf("peak_dBFS" to peak, "rms_dBFS" to rms, "noise_floor_dBFS" to floor).forEach { (key, value) ->
                if (value != null && value !in -120.0..0.0) errors += "range:$key=$value (dBFS)"
            }
            if (peak != null && rms != null && peak < rms - 0.2) {
                errors += "order:peak_dBFS($peak) < rms_dBFS($rms)"
            }
            if (floor != null && rms != null && floor > rms + 0.6) {
                errors += "order:noise_floor_dBFS($floor) > rms_dBFS($rms)"
            }
        } else if (gate != null && gate.isNotEmpty()) {
            errors += "gate_verdict=$gate without buffered audio (audio_ms=$audioMs)"
        }

        // Unset timestamps are 0 on early failures (permission/mic/engine), so only compare
        // events that actually happened.
        if (tTapStart != null && tMicOpen != null && tMicOpen > 0 && tMicOpen < tTapStart) {
            errors += "order:t_mic_open($tMicOpen) < t_tap_start($tTapStart)"
        }
        if (tTapStart != null && tTapStop != null && tTapStop > 0 && tTapStop < tTapStart) {
            errors += "order:t_tap_stop($tTapStop) < t_tap_start($tTapStart)"
        }
        if (tMicOpen != null && tTapStop != null && tTapStop > 0 && tMicOpen > 0 && tTapStop < tMicOpen) {
            errors += "order:t_tap_stop($tTapStop) < t_mic_open($tMicOpen)"
        }
        if (tInserted != null && tInserted != 0L && tTapStop != null && tTapStop > 0 && tInserted < tTapStop) {
            errors += "order:t_inserted($tInserted) < t_tap_stop($tTapStop)"
        }

        if (result != null && gate != null) {
            when (result) {
                "inserted", "insert-failed" -> {
                    if (gate != "speak") errors += "consistency:$result with gate_verdict=$gate"
                    if (tInserted == null || tInserted <= 0) errors += "consistency:$result without t_inserted"
                    if (textLen == null || textLen <= 0) errors += "consistency:$result without text_len"
                    if (method.isNullOrEmpty()) errors += "consistency:$result without insert_method"
                }
                "rejected" -> {
                    if (gate != "speak") errors += "consistency:rejected with gate_verdict=$gate"
                    if (tInserted != 0L) errors += "consistency:rejected has t_inserted=$tInserted"
                    if (textLen != 0) errors += "consistency:rejected has text_len=$textLen"
                    if (!method.isNullOrEmpty()) errors += "consistency:rejected has insert_method=$method"
                }
                "error" -> {
                    if (tInserted != 0L) errors += "consistency:error has t_inserted=$tInserted"
                    if (!method.isNullOrEmpty()) errors += "consistency:error has insert_method=$method"
                }
            }
        }
        if (result == "inserted" && error != null) errors += "consistency:inserted carries error=$error"
        if (result != null && result != "inserted" && error.isNullOrBlank()) {
            errors += "consistency:$result without error text"
        }

        return errors
    }

    /** Validates records.jsonl and that last.json mirrors its final record (ts_utc may differ by a tick). */
    fun validateDir(dir: File): List<String> {
        val errors = mutableListOf<String>()
        val jsonl = File(dir, "records.jsonl")
        val last = File(dir, "last.json")
        if (!jsonl.isFile) return listOf("missing:${jsonl.name} in $dir")
        val lines = jsonl.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) errors += "empty:${jsonl.name}"
        val parsed = lines.mapIndexed { index, line ->
            try {
                parse(line).also { errors += validate(it).map { problem -> "line${index + 1}:$problem" } }
            } catch (t: Throwable) {
                errors += "line${index + 1}:invalid-json:${t.message}"
                null
            }
        }
        if (!last.isFile) {
            errors += "missing:${last.name} in $dir"
        } else {
            val mirrored = try {
                parse(last.readText()).also { errors += validate(it).map { problem -> "last.json:$problem" } }
            } catch (t: Throwable) {
                errors += "last.json:invalid-json:${t.message}"
                null
            }
            val final = parsed.lastOrNull()
            if (mirrored != null && final != null) {
                REQUIRED_FIELDS.filterNot { it == "ts_utc" }.forEach { key ->
                    if (!sameValue(final.opt(key), mirrored.opt(key))) {
                        errors += "mirror:$key jsonl=${final.opt(key)} last=${mirrored.opt(key)}"
                    }
                }
            }
        }
        return errors
    }

    private fun sameValue(a: Any?, b: Any?): Boolean = when {
        a == null && b == null -> true
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a?.toString() == b?.toString()
    }

    private fun JSONObject.longOrNull(key: String, errors: MutableList<String>): Long? {
        val value = numberOrNull(key, errors) ?: return null
        if (value % 1.0 != 0.0) {
            errors += "not-integer:$key=$value"
            return null
        }
        return value.toLong()
    }

    private fun JSONObject.intOrNull(key: String, errors: MutableList<String>): Int? {
        val value = longOrNull(key, errors) ?: return null
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            errors += "range:$key=$value"
            return null
        }
        return value.toInt()
    }

    private fun JSONObject.doubleOrNull(key: String, errors: MutableList<String>): Double? {
        val value = numberOrNull(key, errors) ?: return null
        if (value.isNaN() || value.isInfinite()) {
            errors += "not-finite:$key=$value"
            return null
        }
        return value
    }

    private fun JSONObject.numberOrNull(key: String, errors: MutableList<String>): Double? {
        if (!has(key)) return null
        val raw = opt(key)
        val parsed = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        if (parsed == null) errors += "not-numeric:$key=$raw"
        return parsed
    }

    private fun JSONObject.stringOrNull(
        key: String,
        errors: MutableList<String>,
        allowNull: Boolean = false,
    ): String? {
        if (!has(key)) return null
        val raw = opt(key)
        if (raw == null || raw == JSONObject.NULL) {
            if (!allowNull) errors += "null:$key"
            return null
        }
        if (raw !is String) {
            errors += "not-string:$key=$raw"
            return null
        }
        return raw
    }

    private fun JSONObject.booleanOrNull(key: String, errors: MutableList<String>): Boolean? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)
        if (raw is Boolean) return raw
        errors += "not-boolean:$key=$raw"
        return null
    }

    private val LANGUAGE = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})?|auto")
    private val TARGET_PACKAGE = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
}
