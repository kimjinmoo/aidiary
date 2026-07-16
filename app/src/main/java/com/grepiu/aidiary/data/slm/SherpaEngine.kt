package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class SherpaEngine private constructor(val recognizer: OfflineRecognizer) {
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

            val cfg = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(enc!!.absolutePath, dec!!.absolutePath, joi!!.absolutePath),
                    tokens = tok.absolutePath,
                    modelingUnit = if (bpe.exists()) "bpe" else "",
                    bpeVocab = if (bpe.exists()) bpe.absolutePath else "",
                    numThreads = 4,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            return SherpaEngine(OfflineRecognizer(config = cfg))
        }
    }

    fun transcribe(wavPath: String): String {
        val bytes = File(wavPath).readBytes()
        val rate = ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16) or ((bytes[27].toInt() and 0xFF) shl 24))
        val bits = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
        val samples = FloatArray((bytes.size - 44) / (bits / 8)) { i ->
            val idx = 44 + i * 2
            (((bytes[idx].toInt() and 0xFF) or ((bytes[idx + 1].toInt() and 0xFF) shl 8)).toShort() / 32768f)
        }
        val s = recognizer.createStream()
        s.acceptWaveform(samples, rate)
        recognizer.decode(s)
        val t = recognizer.getResult(s).text
        s.release()
        return t.trim()
    }

    fun dispose() { try { recognizer.release() } catch (_: Exception) {} }
}
