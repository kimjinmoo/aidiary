package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-Onnx 기반 온디바이스 음성→텍스트 변환 엔진입니다.
 */
class WhisperEngine private constructor(
    private val recognizer: OfflineRecognizer
) {
    companion object {
        private const val TAG = "WhisperEngine"

        fun create(modelDir: String): WhisperEngine {
            val dir = File(modelDir)
            val encoderFile = File(dir, "encoder.onnx")
            val decoderFile = File(dir, "decoder.onnx")
            val joinerFile = File(dir, "joiner.onnx")
            val tokensFile = File(dir, "tokens.txt")

            listOf(encoderFile, decoderFile, joinerFile, tokensFile).forEach {
                require(it.exists()) { "Model file not found: ${it.absolutePath}" }
            }

            val transducerConfig = OfflineTransducerModelConfig(
                encoder = encoderFile.absolutePath,
                decoder = decoderFile.absolutePath,
                joiner = joinerFile.absolutePath
            )

            val modelConfig = OfflineModelConfig(
                transducer = transducerConfig,
                tokens = tokensFile.absolutePath,
                numThreads = 4,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            val recognizer = OfflineRecognizer(config = config)
            Log.d(TAG, "Sherpa-Onnx recognizer created, model=${dir.name}")
            return WhisperEngine(recognizer)
        }
    }

    fun transcribe(wavPath: String): String {
        Log.d(TAG, "Transcribing: $wavPath")

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

        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val resultText = recognizer.getResult(stream).text
        stream.release()

        Log.d(TAG, "Result: \"${resultText.take(80)}...\"")
        return resultText.trim()
    }

    fun dispose() {
        try {
            recognizer.release()
            Log.d(TAG, "Sherpa-Onnx recognizer disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing", e)
        }
    }

    private fun readWavFile(path: String): Pair<FloatArray, Int> {
        return try {
            val file = File(path)
            val bytes = file.readBytes()
            if (bytes.size < 44) return Pair(FloatArray(0), 0)

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
