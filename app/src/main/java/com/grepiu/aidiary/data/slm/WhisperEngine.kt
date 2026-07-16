package com.grepiu.aidiary.data.slm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * whisper.cpp 기반 온디바이스 음성→텍스트 변환 엔진입니다.
 *
 * 의존성: com.github.adefresne:whisper-cpp-android (JitPack)
 *         또는 프로젝트 내 JNI/CMake로 빌드된 libwhisper.so
 */
class WhisperEngine private constructor(
    private val nativePtr: Long,
    private val modelPath: String
) {
    companion object {
        private const val TAG = "WhisperEngine"

        init {
            System.loadLibrary("whisper_jni")
        }

        /**
         * whisper.cpp 모델 파일을 로드하여 엔진을 생성합니다.
         * @param modelPath ggml-small-q8_0.bin 등 whisper.cpp 모델 경로
         */
        fun create(context: Context, modelPath: String): WhisperEngine {
            val modelFile = File(modelPath)
            require(modelFile.exists()) { "Whisper model not found: $modelPath" }
            require(modelFile.length() > 10 * 1024 * 1024) { "Whisper model too small: ${modelFile.length()} bytes" }

            val ptr = nativeInit(modelPath)
            Log.d(TAG, "Whisper engine initialized, nativePtr=$ptr, model=${modelFile.length() / 1024 / 1024}MB")
            return WhisperEngine(ptr, modelPath)
        }

        // JNI 네이티브 메서드 선언
        @JvmStatic private external fun nativeInit(modelPath: String): Long
        @JvmStatic private external fun nativeTranscribe(ptr: Long, wavPath: String, language: String): String
        @JvmStatic private external fun nativeFree(ptr: Long)
    }

    /**
     * WAV 오디오 파일을 텍스트로 변환합니다.
     * @param wavPath 16kHz 모노 PCM WAV 파일 경로
     * @param language 언어 코드 (예: "ko", "auto")
     * @return 변환된 텍스트
     */
    fun transcribe(wavPath: String, language: String = "auto"): String {
        Log.d(TAG, "Transcribing: $wavPath, language=$language")
        val result = nativeTranscribe(nativePtr, wavPath, language)
        Log.d(TAG, "Transcription result (${result.length} chars): ${result.take(80)}...")
        return result.trim()
    }

    fun dispose() {
        try {
            nativeFree(nativePtr)
            Log.d(TAG, "Whisper engine disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing whisper engine", e)
        }
    }
}
