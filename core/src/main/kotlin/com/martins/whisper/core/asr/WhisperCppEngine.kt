package com.martins.whisper.core.asr

import com.martins.whisper.core.SpeechEngine

/**
 * Marker/contract for the native whisper.cpp engine. The Android implementation lives in the app
 * module (`com.martins.whisper.asr.WhisperCppEngine`) and adds JNI lifecycle details.
 */
interface WhisperCppEngine : SpeechEngine {
    val modelPath: String

    /** Build/backend diagnostics, suitable for the doctor screen. */
    val runtimeInfo: String
}
