package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class SherpaEngine private constructor(
    val recognizer: OnlineRecognizer
) {
    companion object {
        private const val TAG = "SherpaEngine"

        fun create(modelDir: String): SherpaEngine {
            var dir = File(modelDir)
            val subDirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            if (subDirs != null && subDirs.size == 1 && File(subDirs[0], "tokens.txt").exists()) dir = subDirs[0]

            val encoder = dir.listFiles()?.firstOrNull { it.name.startsWith("encoder") && it.name.endsWith(".onnx") }
            val decoder = dir.listFiles()?.firstOrNull { it.name.startsWith("decoder") && it.name.endsWith(".onnx") }
            val joiner = dir.listFiles()?.firstOrNull { it.name.startsWith("joiner") && it.name.endsWith(".onnx") }
            val tokens = File(dir, "tokens.txt")
            val bpe = File(dir, "bpe.model")

            listOfNotNull(encoder, decoder, joiner, tokens).forEach { require(it.exists()) { "Missing: ${it.absolutePath}" } }

            val cfg = OnlineRecognizerConfig(
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(encoder!!.absolutePath, decoder!!.absolutePath, joiner!!.absolutePath),
                    tokens = tokens.absolutePath,
                    modelingUnit = if (bpe.exists()) "bpe" else "cjkchar",
                    bpeVocab = if (bpe.exists()) bpe.absolutePath else "",
                    modelType = "",
                    numThreads = 4,
                    debug = true,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search",
                enableEndpoint = false
            )

            val recognizer = OnlineRecognizer(config = cfg)
            Log.d(TAG, "OnlineRecognizer ready, bpe=${bpe.exists()}, type=auto")
            return SherpaEngine(recognizer)
        }
    }

    fun createStream() = recognizer.createStream()

    fun acceptWaveform(s: OnlineStream, samples: FloatArray, rate: Int) { s.acceptWaveform(samples, rate) }
    fun decode(s: OnlineStream) { recognizer.decode(s) }
    fun isReady(s: OnlineStream) = recognizer.isReady(s)
    fun result(s: OnlineStream) = recognizer.getResult(s).text
    fun dispose() { try { recognizer.release() } catch (_: Exception) {} }
}
