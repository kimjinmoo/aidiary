cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp 옵션 설정 (안드로이드 최적화)
set(WHISPER_BUILD_EXAMPLES OFF)
set(WHISPER_BUILD_TESTS OFF)
set(WHISPER_BUILD_SERVER OFF)
set(GGML_BUILD_EXAMPLES OFF)
set(GGML_BUILD_TESTS OFF)
set(GGML_CUDA OFF)
set(GGML_METAL OFF)
set(GGML_VULKAN OFF)
set(GGML_BLAS OFF)
set(GGML_OPENCL OFF)

# whisper.cpp 서브디렉토리 추가 (자체 CMakeLists.txt 사용)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp ${CMAKE_CURRENT_BINARY_DIR}/whisper_build)

# JNI 브릿지 라이브러리
add_library(whisper_jni SHARED whisper_jni.cpp)
target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml/include
)
target_link_libraries(whisper_jni whisper ggml-base ggml-cpu)

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
