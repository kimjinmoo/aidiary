package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-Onnx 기반 온디바이스 음성→텍스트 변환 엔진입니다.
 */
class SherpaEngine private constructor(
    private val recognizer: OfflineRecognizer
) {
    companion object {
        private const val TAG = "SherpaEngine"

        fun create(modelDir: String): WhisperEngine {
            var dir = File(modelDir)
            // tar.bz2 추출 시 내부 서브디렉토리 대응
            val subDirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            if (subDirs != null && subDirs.size == 1 && File(subDirs[0], "tokens.txt").exists()) {
                dir = subDirs[0]
            }
            // 파일명 패턴 대응 (encoder*.onnx, decoder*.onnx, joiner*.onnx)
            val encoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("encoder") && it.name.endsWith(".onnx") }
            val decoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("decoder") && it.name.endsWith(".onnx") }
            val joinerFile = dir.listFiles()?.firstOrNull { it.name.startsWith("joiner") && it.name.endsWith(".onnx") }
            val tokensFile = File(dir, "tokens.txt")

            val files = listOfNotNull(encoderFile, decoderFile, joinerFile, tokensFile)
            files.forEach { require(it.exists()) { "Model file not found: ${it.absolutePath}" } }

            val transducerConfig = OfflineTransducerModelConfig(
                encoder = encoderFile!!.absolutePath,
                decoder = decoderFile!!.absolutePath,
                joiner = joinerFile!!.absolutePath
            )

            val bpeFile = File(dir, "bpe.model")
            val modelConfig = OfflineModelConfig(
                transducer = transducerConfig,
                tokens = tokensFile.absolutePath,
                modelingUnit = if (bpeFile.exists()) "bpe" else "",
                bpeVocab = if (bpeFile.exists()) bpeFile.absolutePath else "",
                numThreads = 4,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            val recognizer = OfflineRecognizer(config = config)
            Log.d(TAG, "Sherpa-Onnx recognizer created, model=${dir.name}, bpe=${bpeFile.exists()}")
            return SherpaEngine(recognizer)
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
