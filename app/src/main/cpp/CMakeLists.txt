cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(GGML_DIR ${WHISPER_DIR}/ggml)

# ggml 기본 소스 (백엔드 제외, CPU만)
set(GGML_SOURCES
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/ggml-backend.cpp
    ${GGML_DIR}/src/ggml-rpc.cpp
)

# ggml-cpu 백엔드 소스만 포함
file(GLOB GGML_CPU_SOURCES
    ${GGML_DIR}/src/ggml-cpu/*.c
    ${GGML_DIR}/src/ggml-cpu/*.cpp
)
# repack.cpp 제외 (빌드 이슈)
list(FILTER GGML_CPU_SOURCES EXCLUDE REGEX ".*repack\\.cpp$")

# arch 특정 소스
file(GLOB GGML_ARCH_SOURCES
    ${GGML_DIR}/src/ggml-cpu/arch/*/ggml-arch-impl.c
    ${GGML_DIR}/src/ggml-cpu/arch/*/ggml-arch-impl.cpp
)

# whisper 소스
set(WHISPER_SOURCES ${WHISPER_DIR}/src/whisper.cpp)

add_library(whisper_jni SHARED
    whisper_jni.cpp
    ${GGML_SOURCES}
    ${GGML_CPU_SOURCES}
    ${GGML_ARCH_SOURCES}
    ${WHISPER_SOURCES}
)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src/ggml-cpu
    ${WHISPER_DIR}/src
)

target_compile_definitions(whisper_jni PRIVATE GGML_USE_CPU)

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
