#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cctype>
#include <cstring>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "whisper.h"

#define TAG "MartinsWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

constexpr jint STATUS_OK = 0;
constexpr jint STATUS_INVALID_CONTEXT = -1;
constexpr jint STATUS_INVALID_AUDIO = -2;
constexpr jint STATUS_TRANSCRIBE_FAILED = -3;

struct whisper_context * as_context(jlong handle) {
    return reinterpret_cast<struct whisper_context *>(handle);
}

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

const char * device_type_name(enum ggml_backend_dev_type type) {
    switch (type) {
        case GGML_BACKEND_DEVICE_TYPE_CPU:   return "CPU";
        case GGML_BACKEND_DEVICE_TYPE_GPU:   return "GPU";
        case GGML_BACKEND_DEVICE_TYPE_IGPU:  return "IGPU";
        case GGML_BACKEND_DEVICE_TYPE_ACCEL: return "ACCEL";
        case GGML_BACKEND_DEVICE_TYPE_META:  return "META";
        default:                             return "UNKNOWN";
    }
}

std::string backend_devices_summary() {
    std::string out;
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (device == nullptr) continue;
        const char * name = ggml_backend_dev_name(device);
        const char * description = ggml_backend_dev_description(device);
        if (!out.empty()) out += "; ";
        out += "device=";
        out += name != nullptr ? name : "?";
        out += " type=";
        out += device_type_name(ggml_backend_dev_type(device));
        if (description != nullptr && std::strlen(description) > 0) {
            out += " (";
            out += description;
            out += ")";
        }
    }
    if (out.empty()) out = "no ggml backend devices";
    return out;
}

bool has_vulkan_device() {
    const size_t count = ggml_backend_dev_count();
    for (size_t i = 0; i < count; ++i) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        if (device == nullptr) continue;
        const char * name = ggml_backend_dev_name(device);
        if (name == nullptr) continue;
        std::string lower(name);
        std::transform(lower.begin(), lower.end(), lower.begin(),
                       [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        if (lower.find("vulkan") != std::string::npos) return true;
    }
    return false;
}

void android_log_callback(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    if (text == nullptr) return;
    int priority = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) priority = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) priority = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_INFO) priority = ANDROID_LOG_INFO;
    __android_log_print(priority, TAG, "%s", text);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeVersion(JNIEnv * env, jobject /*thiz*/) {
    const char * version = whisper_version();
    return env->NewStringUTF(version != nullptr ? version : "unknown");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSystemInfo(JNIEnv * env, jobject /*thiz*/) {
    const char * info = whisper_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeBackendInfo(JNIEnv * env, jobject /*thiz*/) {
    return env->NewStringUTF(backend_devices_summary().c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeHasVulkanDevice(JNIEnv * /*env*/, jobject /*thiz*/) {
    return has_vulkan_device() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSetLogging(JNIEnv * /*env*/, jobject /*thiz*/, jboolean enabled) {
    whisper_log_set(enabled ? android_log_callback : nullptr, nullptr);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeInit(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring model_path,
        jboolean use_gpu,
        jint gpu_device) {
    if (model_path == nullptr) return 0;
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;

    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = use_gpu == JNI_TRUE;
    params.gpu_device = gpu_device;

    LOGI("Loading model %s (use_gpu=%d, gpu_device=%d)", path, params.use_gpu, params.gpu_device);
    struct whisper_context * context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);

    if (context == nullptr) {
        LOGW("whisper_init_from_file_with_params returned null");
        return 0;
    }
    LOGI("Model loaded; devices: %s", backend_devices_summary().c_str());
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeFull(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong handle,
        jfloatArray samples,
        jstring language,
        jint threads) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr) return STATUS_INVALID_CONTEXT;
    if (samples == nullptr) return STATUS_INVALID_AUDIO;

    const jsize length = env->GetArrayLength(samples);
    if (length <= 0) return STATUS_INVALID_AUDIO;

    jfloat * data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return STATUS_INVALID_AUDIO;

    const char * language_chars = nullptr;
    if (language != nullptr) {
        language_chars = env->GetStringUTFChars(language, nullptr);
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.no_timestamps = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.suppress_nst = false;
    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;
    params.no_speech_thold = 0.6f;
    params.language = language_chars;
    params.detect_language = language_chars == nullptr ||
                             std::strcmp(language_chars, "auto") == 0;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, data, length);

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (language_chars != nullptr) {
        env->ReleaseStringUTFChars(language, language_chars);
    }
    if (result != 0) {
        LOGW("whisper_full failed with code %d", result);
        return STATUS_TRANSCRIBE_FAILED;
    }
    return STATUS_OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentCount(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr) return 0;
    return whisper_full_n_segments(context);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentText(
        JNIEnv * env, jobject /*thiz*/, jlong handle, jint index) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr || index < 0 || index >= whisper_full_n_segments(context)) {
        return env->NewByteArray(0);
    }
    const char * text = whisper_full_get_segment_text(context, index);
    if (text == nullptr) return env->NewByteArray(0);
    // Return the raw UTF-8 bytes. NewStringUTF expects modified UTF-8 and can abort the VM on
    // standard 4-byte sequences (emoji) or invalid/split multibyte input; Kotlin decodes instead.
    const jsize length = static_cast<jsize>(std::strlen(text));
    jbyteArray bytes = env->NewByteArray(length);
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(text));
    return bytes;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentStartMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr || index < 0 || index >= whisper_full_n_segments(context)) return -1;
    return static_cast<jlong>(whisper_full_get_segment_t0(context, index) * 10);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentEndMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr || index < 0 || index >= whisper_full_n_segments(context)) return -1;
    return static_cast<jlong>(whisper_full_get_segment_t1(context, index) * 10);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentNoSpeechProb(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr || index < 0 || index >= whisper_full_n_segments(context)) return 0.0f;
    return whisper_full_get_segment_no_speech_prob(context, index);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeDetectedLanguage(
        JNIEnv * env, jobject /*thiz*/, jlong handle) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(context);
    const char * language = whisper_lang_str(id);
    return env->NewStringUTF(language != nullptr ? language : "");
}

extern "C" JNIEXPORT void JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    struct whisper_context * context = as_context(handle);
    if (context == nullptr) return;
    whisper_free(context);
    LOGI("whisper context freed");
}
