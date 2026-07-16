cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 17)

add_library(whisper_jni SHARED whisper_jni.cpp)

# whisper.cpp 및 ggml 소스 경로 (git submodule 또는 복사)
# git clone https://github.com/ggerganov/whisper.cpp.git app/src/main/cpp/whisper.cpp
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(GGML_DIR ${WHISPER_DIR}/ggml)

add_library(ggml OBJECT
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.c
    ${GGML_DIR}/src/ggml-quants.c
)
target_include_directories(ggml PUBLIC ${GGML_DIR}/include)
target_compile_definitions(ggml PRIVATE GGML_USE_CPU)

add_library(whisper OBJECT
    ${WHISPER_DIR}/src/whisper.cpp
)
target_include_directories(whisper PUBLIC ${WHISPER_DIR}/include ${GGML_DIR}/include)

target_link_libraries(whisper_jni whisper ggml)

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
