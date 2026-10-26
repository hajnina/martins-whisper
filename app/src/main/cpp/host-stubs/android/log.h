// Host-only stub of <android/log.h> for the JVM tracer bullet. It is never used by the
// Android build (the NDK sysroot provides the real header); scripts/host-jni-test.sh adds
// app/src/main/cpp/host-stubs to the include path before the NDK/Android headers.
#ifndef MARTINS_WHISPER_HOST_ANDROID_LOG_H
#define MARTINS_WHISPER_HOST_ANDROID_LOG_H

#include <cstdarg>
#include <cstdio>

#ifdef __cplusplus
extern "C" {
#endif

enum android_LogPriority {
    ANDROID_LOG_UNKNOWN = 0,
    ANDROID_LOG_DEFAULT,
    ANDROID_LOG_VERBOSE,
    ANDROID_LOG_DEBUG,
    ANDROID_LOG_INFO,
    ANDROID_LOG_WARN,
    ANDROID_LOG_ERROR,
    ANDROID_LOG_FATAL,
    ANDROID_LOG_SILENT,
};

static inline int __android_log_print(int priority, const char * tag, const char * format, ...) {
    static const char * names[] = {"?", "?", "V", "D", "I", "W", "E", "F", "S"};
    const char * level = (priority >= 0 && priority <= 8) ? names[priority] : "?";
    std::fprintf(stderr, "[%s/%s] ", level, tag != nullptr ? tag : "?");
    va_list args;
    va_start(args, format);
    std::vfprintf(stderr, format, args);
    va_end(args);
    std::fputc('\n', stderr);
    return 0;
}

#ifdef __cplusplus
}
#endif

#endif
