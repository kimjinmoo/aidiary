package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-Onnx OnlineRecognizer 기반 실시간 음성→텍스트 변환 엔진입니다.
 */
class SherpaEngine private constructor(
    val recognizer: OnlineRecognizer
) {
    companion object {
        private const val TAG = "SherpaEngine"

        fun create(modelDir: String): SherpaEngine {
            var dir = File(modelDir)
            val subDirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            if (subDirs != null && subDirs.size == 1 && File(subDirs[0], "tokens.txt").exists()) {
                dir = subDirs[0]
            }

            val encoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("encoder") && it.name.endsWith(".onnx") }
            val decoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("decoder") && it.name.endsWith(".onnx") }
            val joinerFile = dir.listFiles()?.firstOrNull { it.name.startsWith("joiner") && it.name.endsWith(".onnx") }
            val tokensFile = File(dir, "tokens.txt")
            val bpeFile = File(dir, "bpe.model")

            val files = listOfNotNull(encoderFile, decoderFile, joinerFile, tokensFile)
            files.forEach { require(it.exists()) { "Model file not found: ${it.absolutePath}" } }

            val transducerConfig = OnlineTransducerModelConfig(
                encoder = encoderFile!!.absolutePath,
                decoder = decoderFile!!.absolutePath,
                joiner = joinerFile!!.absolutePath
            )

            val modelConfig = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = tokensFile.absolutePath,
                modelingUnit = if (bpeFile.exists()) "bpe" else "",
                bpeVocab = if (bpeFile.exists()) bpeFile.absolutePath else "",
                numThreads = 4,
                debug = false,
                provider = "cpu"
            )

            val config = OnlineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
                enableEndpoint = false // 수동으로 녹음 종료 관리
            )

            val recognizer = OnlineRecognizer(config = config)
            Log.d(TAG, "OnlineRecognizer created, bpe=${bpeFile.exists()}")
            return SherpaEngine(recognizer)
        }
    }

    fun createStream(): OnlineStream = recognizer.createStream()

    fun acceptWaveform(stream: OnlineStream, samples: FloatArray, sampleRate: Int) {
        stream.acceptWaveform(samples, sampleRate)
    }

    fun decode(stream: OnlineStream) {
        recognizer.decode(stream)
    }

    fun isReady(stream: OnlineStream): Boolean = recognizer.isReady(stream)

    fun getResult(stream: OnlineStream): String = recognizer.getResult(stream).text

    fun dispose() {
        try {
            recognizer.release()
            Log.d(TAG, "OnlineRecognizer disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing", e)
        }
    }
}
