package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class SherpaEngine private constructor(val recognizer: OnlineRecognizer) {
    companion object {
        private const val TAG = "SherpaEngine"

        fun create(modelDir: String): SherpaEngine {
            var dir = File(modelDir)
            dir.listFiles()?.firstOrNull { it.isDirectory && !it.name.startsWith(".") && File(it, "tokens.txt").exists() }?.let { dir = it }

            val enc = dir.listFiles()?.firstOrNull { it.name.startsWith("encoder") && it.name.endsWith(".onnx") }
            val dec = dir.listFiles()?.firstOrNull { it.name.startsWith("decoder") && it.name.endsWith(".onnx") }
            val joi = dir.listFiles()?.firstOrNull { it.name.startsWith("joiner") && it.name.endsWith(".onnx") }
            val tok = File(dir, "tokens.txt")
            val bpe = File(dir, "bpe.model")
            listOfNotNull(enc, dec, joi, tok).forEach { require(it.exists()) { "Missing: ${it.name}" } }

            val cfg = OnlineRecognizerConfig(
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(enc!!.absolutePath, dec!!.absolutePath, joi!!.absolutePath),
                    tokens = tok.absolutePath,
                    modelingUnit = if (bpe.exists()) "bpe" else "cjkchar",
                    bpeVocab = if (bpe.exists()) bpe.absolutePath else "",
                    numThreads = 4,
                    debug = true,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search",
                enableEndpoint = false
            )
            Log.d(TAG, "OnlineRecognizer created, bpe=${bpe.exists()}")
            return SherpaEngine(OnlineRecognizer(config = cfg))
        }
    }

    fun createStream() = recognizer.createStream()
    fun acceptWaveform(s: OnlineStream, samples: FloatArray, rate: Int) { s.acceptWaveform(samples, rate) }
    fun decode(s: OnlineStream) { recognizer.decode(s) }
    fun isReady(s: OnlineStream) = recognizer.isReady(s)
    fun result(s: OnlineStream) = recognizer.getResult(s).text
    fun dispose() { try { recognizer.release() } catch (_: Exception) {} }
}
