cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(GGML_DIR ${WHISPER_DIR}/ggml)

# ggml CPU backend sources (static)
file(GLOB GGML_SOURCES
    ${GGML_DIR}/src/ggml-cpu/*.c
    ${GGML_DIR}/src/ggml-cpu/*.cpp
    ${GGML_DIR}/src/ggml-cpu/arch/*/*.c
    ${GGML_DIR}/src/ggml-cpu/arch/*/*.cpp
)
list(FILTER GGML_SOURCES EXCLUDE REGEX ".*/repack\\.cpp$")

# ggml base sources
set(GGML_BASE_SOURCES
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
)
file(GLOB GGML_BASE_SRC ${GGML_DIR}/src/ggml*.c ${GGML_DIR}/src/ggml*.cpp)
if(GGML_BASE_SRC)
    set(GGML_BASE_SOURCES ${GGML_BASE_SRC})
endif()

# whisper sources
set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

# JNI bridge
add_library(whisper_jni SHARED
    whisper_jni.cpp
    ${GGML_BASE_SOURCES}
    ${GGML_SOURCES}
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
