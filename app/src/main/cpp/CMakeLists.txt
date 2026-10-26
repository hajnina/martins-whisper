cmake_minimum_required(VERSION 3.22.1)

project(martins_whisper_jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp" CACHE PATH "Pinned whisper.cpp checkout")
if (NOT EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "whisper.cpp is missing at ${WHISPER_CPP_DIR}. "
        "Run scripts/fetch-whisper-cpp.sh before building.")
endif()

# Keep the build small and deterministic for an Android library.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# CPU-first: Vulkan is opt-in via -DGGML_VULKAN=ON (Gradle: -Pmartins.vulkan=true).
if (NOT DEFINED GGML_VULKAN)
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_BINARY_DIR}/whisper.cpp" EXCLUDE_FROM_ALL)

add_library(martins_whisper SHARED whisper_jni.cpp)

target_include_directories(martins_whisper PRIVATE
    "${WHISPER_CPP_DIR}/include"
    "${WHISPER_CPP_DIR}/ggml/include"
)

target_compile_options(martins_whisper PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(martins_whisper PRIVATE -Wl,--gc-sections)

find_library(ANDROID_LOG_LIB log)
target_link_libraries(martins_whisper PRIVATE whisper ${ANDROID_LOG_LIB} android)
