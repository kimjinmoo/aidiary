package com.grepiu.aidiary.data.slm

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-Onnx 기반 온디바이스 음성→텍스트 변환 엔진입니다.
 *
 * 의존성: libs/sherpa-onnx-android.aar
 * 모델: zipformer-small-korean (encoder/decoder/joiner/tokens)
 */
class WhisperEngine private constructor(
    private val recognizer: OfflineRecognizer,
    private val modelDir: File
) {
    companion object {
        private const val TAG = "WhisperEngine"

        fun create(context: Context, modelDir: String): WhisperEngine {
            val dir = File(modelDir)
            val encoderFile = File(dir, "encoder.onnx")
            val decoderFile = File(dir, "decoder.onnx")
            val joinerFile = File(dir, "joiner.onnx")
            val tokensFile = File(dir, "tokens.txt")

            // 파일 존재 확인
            listOf(encoderFile, decoderFile, joinerFile, tokensFile).forEach {
                require(it.exists()) { "Model file not found: ${it.absolutePath}" }
            }

            // Sherpa-Onnx Zipformer 모델 설정
            val zipformerConfig = OfflineZipformerModelConfig.builder()
                .setEncoder(encoderFile.absolutePath)
                .setDecoder(decoderFile.absolutePath)
                .setJoiner(joinerFile.absolutePath)
                .build()

            val modelConfig = OfflineModelConfig.builder()
                .setZipformer(zipformerConfig)
                .setTokens(tokensFile.absolutePath)
                .setNumThreads(4)
                .setDebug(false)
                .setProvider("cpu")  // CPU 전용 (GPU/OpenCL 이슈 회피)
                .build()

            val config = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build()

            val recognizer = OfflineRecognizer(config)
            Log.d(TAG, "Sherpa-Onnx recognizer created, model=${dir.name}")
            return WhisperEngine(recognizer, dir)
        }
    }

    /**
     * WAV 오디오 파일을 텍스트로 변환합니다.
     */
    fun transcribe(wavPath: String): String {
        Log.d(TAG, "Transcribing: $wavPath")

        // WAV 파일 읽기
        val wavFile = File(wavPath)
        if (!wavFile.exists()) {
            Log.e(TAG, "WAV file not found: $wavPath")
            return ""
        }

        val (samples, sampleRate) = readWavFile(wavPath)
        if (samples.isEmpty()) {
            Log.e(TAG, "Failed to read WAV: $wavPath")
            return ""
        }

        Log.d(TAG, "Read ${samples.size} samples @ $sampleRate Hz (${samples.size / sampleRate}s)")

        // 인식 실행
        val stream = recognizer.createStream()
        stream.acceptWaveform(sampleRate, samples)
        recognizer.decodeStream(stream)

        val result = stream.result.text
        stream.release()
        Log.d(TAG, "Result: \"${result.take(80)}...\"")
        return result.trim()
    }

    fun dispose() {
        try {
            recognizer.release()
            Log.d(TAG, "Sherpa-Onnx recognizer disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing", e)
        }
    }

    /**
     * WAV 파일에서 float 샘플을 읽어옵니다.
     */
    private fun readWavFile(path: String): Pair<FloatArray, Int> {
        return try {
            val file = File(path)
            val bytes = file.readBytes()
            if (bytes.size < 44) return Pair(FloatArray(0), 0)

            // WAV 헤더 파싱
            val sampleRate = ((bytes[24].toInt() and 0xFF)
                or ((bytes[25].toInt() and 0xFF) shl 8)
                or ((bytes[26].toInt() and 0xFF) shl 16)
                or ((bytes[27].toInt() and 0xFF) shl 24))

            val bitsPerSample = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)

            val dataOffset = 44
            val numSamples = (bytes.size - dataOffset) / (bitsPerSample / 8)
            val samples = FloatArray(numSamples)

            if (bitsPerSample == 16) {
                for (i in 0 until numSamples) {
                    val idx = dataOffset + i * 2
                    val sample = ((bytes[idx].toInt() and 0xFF)
                        or ((bytes[idx + 1].toInt() and 0xFF) shl 8))
                        .toShort().toInt()
                    samples[i] = sample / 32768.0f
                }
            }

            Pair(samples, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV", e)
            Pair(FloatArray(0), 0)
        }
    }
}
