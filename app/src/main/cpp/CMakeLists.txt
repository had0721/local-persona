cmake_minimum_required(VERSION 3.22)

project(persona LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp 在 CI 里 clone 到仓库根目录（见 .github/workflows/android.yml）
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_DIR} — clone it first")
endif()

# 静态链接：否则 libpersona.so 会依赖 libllama.so，而它不会被打进 APK
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# 只要推理库：关掉 tests / examples / tools / server，省一大半编译时间
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV   OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON  CACHE BOOL "" FORCE)
    # OpenMP 在 NDK 上要额外链接 libomp，先关掉，用 pthread 就行
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${LLAMA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama-build")

add_library(persona SHARED persona_jni.cpp)

target_include_directories(persona PRIVATE
        "${LLAMA_DIR}/include"
        "${LLAMA_DIR}/ggml/include")

target_link_libraries(persona
        llama
        android
        log)
