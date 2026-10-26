#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cctype>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
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

// 10 ms at 16 kHz: below this whisper.cpp has nothing to decode. The service's own minimum is
// 150 ms; this only guards the JNI contract against 0/1-sample calls.
constexpr jsize MIN_AUDIO_SAMPLES = 160;
// 10 minutes: the service caps a hold at 30 s; refuse absurd allocations with a typed status.
constexpr jsize MAX_AUDIO_SAMPLES = 16'000 * 600;

constexpr int MAX_THREADS = 64;

// A context handle is an opaque id, never a raw pointer. The registry keeps the entry alive while
// a call is in flight, so a concurrent free can wait for the inference and a double free is a
// harmless no-op instead of use-after-free.
struct ContextEntry {
    std::mutex mutex;  // serializes whisper_full/nativeFree and the segment getters
    struct whisper_context * context = nullptr;
};

std::mutex g_registry_mutex;
std::unordered_map<jlong, std::shared_ptr<ContextEntry>> g_contexts;
jlong g_next_handle = 1;

// whisper.cpp context creation/destruction is serialized globally (ggml backend init/free).
std::mutex g_lifecycle_mutex;

std::shared_ptr<ContextEntry> acquire(jlong handle) {
    if (handle == 0) return nullptr;
    std::lock_guard<std::mutex> lock(g_registry_mutex);
    const auto it = g_contexts.find(handle);
    return it == g_contexts.end() ? nullptr : it->second;
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
    if (model_path == nullptr) {
        LOGW("nativeInit: null model path");
        return 0;
    }
    const std::string path = jstring_to_string(env, model_path);
    if (path.empty()) {
        LOGW("nativeInit: empty model path");
        return 0;
    }

    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = use_gpu == JNI_TRUE;
    params.gpu_device = gpu_device;

    struct whisper_context * context = nullptr;
    {
        // Load is serialized with free: ggml backend registration is process-global.
        std::lock_guard<std::mutex> lifecycle(g_lifecycle_mutex);
        LOGI("Loading model %s (use_gpu=%d, gpu_device=%d)", path.c_str(), params.use_gpu, params.gpu_device);
        context = whisper_init_from_file_with_params(path.c_str(), params);
    }
    if (context == nullptr) {
        LOGW("whisper_init_from_file_with_params returned null for %s", path.c_str());
        return 0;
    }

    auto entry = std::make_shared<ContextEntry>();
    entry->context = context;

    jlong handle = 0;
    {
        std::lock_guard<std::mutex> lock(g_registry_mutex);
        handle = g_next_handle++;
        if (handle <= 0) handle = g_next_handle++;
        g_contexts.emplace(handle, entry);
    }
    LOGI("Model loaded; handle=%lld devices: %s",
         static_cast<long long>(handle), backend_devices_summary().c_str());
    return handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeFull(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong handle,
        jfloatArray samples,
        jstring language,
        jint threads) {
    const auto entry = acquire(handle);
    if (entry == nullptr) {
        LOGW("nativeFull: invalid or freed handle %lld", static_cast<long long>(handle));
        return STATUS_INVALID_CONTEXT;
    }
    if (samples == nullptr) return STATUS_INVALID_AUDIO;

    const jsize length = env->GetArrayLength(samples);
    if (length < MIN_AUDIO_SAMPLES || length > MAX_AUDIO_SAMPLES) {
        LOGW("nativeFull: refused audio of %d samples", static_cast<int>(length));
        return STATUS_INVALID_AUDIO;
    }

    jfloat * data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return STATUS_INVALID_AUDIO;

    const std::string language_value = jstring_to_string(env, language);
    const bool auto_detect = language_value.empty() || language_value == "auto";

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? std::min<int>(threads, MAX_THREADS) : 4;
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
    // whisper_full auto-detects when language is null/empty/"auto" and then continues decoding.
    // Do NOT set detect_language: it returns after detection with zero segments.
    params.language = auto_detect ? "auto" : language_value.c_str();
    params.detect_language = false;

    int result = 0;
    {
        // Serialize inference with nativeFree for this context. A free that arrives now removes
        // the handle from the registry (new calls get -1) and waits here for us to finish.
        std::lock_guard<std::mutex> lock(entry->mutex);
        if (entry->context == nullptr) {
            env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
            return STATUS_INVALID_CONTEXT;
        }
        whisper_reset_timings(entry->context);
        result = whisper_full(entry->context, params, data, length);
    }

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (result != 0) {
        LOGW("whisper_full failed with code %d", result);
        return STATUS_TRANSCRIBE_FAILED;
    }
    return STATUS_OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentCount(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return 0;
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr) return 0;
    return whisper_full_n_segments(entry->context);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentText(
        JNIEnv * env, jobject /*thiz*/, jlong handle, jint index) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return env->NewByteArray(0);
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr || index < 0 || index >= whisper_full_n_segments(entry->context)) {
        return env->NewByteArray(0);
    }
    const char * text = whisper_full_get_segment_text(entry->context, index);
    if (text == nullptr) return env->NewByteArray(0);
    // Return the raw UTF-8 bytes. NewStringUTF expects modified UTF-8 and can abort the VM on
    // standard 4-byte sequences (emoji) or invalid/split multibyte input; Kotlin decodes instead.
    const jsize length = static_cast<jsize>(std::strlen(text));
    jbyteArray bytes = env->NewByteArray(length);
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(text));
    return bytes;
}

// Test/diagnostic fixture: standard UTF-8 bytes for the JNI decode contract, one script per
// line. Kotlin must decode these bytes with Charsets.UTF_8 (never NewStringUTF, which expects
// modified UTF-8 and can abort the VM on 4-byte sequences such as emoji).
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeUtf8Fixture(
        JNIEnv * env, jobject /*thiz*/) {
    static const char * fixture =
        u8"Příliš žluťoučký kůň úpěl ďábelské ódy.\n"
        u8"Съешь ещё этих мягких французских булок.\n"
        u8"مرحبًا بالعالم\n"
        u8"नमस्ते दुनिया\n"
        u8"สวัสดีชาวโลก\n"
        u8"こんにちは世界\n"
        u8"你好，世界\n"
        u8"안녕하세요 세계\n"
        u8"😀🚀🧑🏽‍🚀";
    const jsize length = static_cast<jsize>(std::strlen(fixture));
    jbyteArray bytes = env->NewByteArray(length);
    if (bytes == nullptr) return nullptr;
    env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(fixture));
    return bytes;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentStartMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return -1;
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr || index < 0 || index >= whisper_full_n_segments(entry->context)) return -1;
    return static_cast<jlong>(whisper_full_get_segment_t0(entry->context, index) * 10);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentEndMs(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return -1;
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr || index < 0 || index >= whisper_full_n_segments(entry->context)) return -1;
    return static_cast<jlong>(whisper_full_get_segment_t1(entry->context, index) * 10);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeSegmentNoSpeechProb(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint index) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return 0.0f;
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr || index < 0 || index >= whisper_full_n_segments(entry->context)) return 0.0f;
    return whisper_full_get_segment_no_speech_prob(entry->context, index);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeDetectedLanguage(
        JNIEnv * env, jobject /*thiz*/, jlong handle) {
    const auto entry = acquire(handle);
    if (entry == nullptr) return env->NewStringUTF("");
    std::lock_guard<std::mutex> lock(entry->mutex);
    if (entry->context == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(entry->context);
    const char * language = whisper_lang_str(id);
    return env->NewStringUTF(language != nullptr ? language : "");
}

extern "C" JNIEXPORT void JNICALL
Java_com_martins_whisper_asr_WhisperCppNative_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;

    std::shared_ptr<ContextEntry> entry;
    {
        std::lock_guard<std::mutex> lock(g_registry_mutex);
        const auto it = g_contexts.find(handle);
        if (it == g_contexts.end()) {
            LOGW("nativeFree: handle %lld already freed or invalid (no-op)",
                 static_cast<long long>(handle));
            return;
        }
        entry = it->second;
        // Remove first: new calls fail closed with STATUS_INVALID_CONTEXT while we wait.
        g_contexts.erase(it);
    }

    struct whisper_context * context = nullptr;
    {
        std::lock_guard<std::mutex> lock(entry->mutex);
        context = entry->context;
        entry->context = nullptr;
    }
    if (context != nullptr) {
        std::lock_guard<std::mutex> lifecycle(g_lifecycle_mutex);
        whisper_free(context);
        LOGI("whisper context freed (handle=%lld)", static_cast<long long>(handle));
    }
}
