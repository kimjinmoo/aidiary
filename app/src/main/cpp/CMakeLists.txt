cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_BUILD_EXAMPLES OFF)
set(WHISPER_BUILD_TESTS OFF)
set(WHISPER_BUILD_SERVER OFF)
set(GGML_BUILD_EXAMPLES OFF)
set(GGML_BUILD_TESTS OFF)
set(GGML_CUDA OFF)
set(GGML_METAL OFF)
set(GGML_VULKAN OFF)
set(GGML_BLAS OFF)
# ARM 최적화 비활성화 (Snapdragon 8 Gen 2 ARMv9 충돌 디버깅)
set(GGML_CPU_ARM_ARCH "")
set(GGML_NO_ACCELERATE ON)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp ${CMAKE_CURRENT_BINARY_DIR}/whisper_build)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml/include
)
target_link_libraries(whisper_jni whisper ggml-base ggml-cpu)

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
