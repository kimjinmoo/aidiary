package com.grepiu.aidiary.data.slm

import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class SherpaEngine private constructor(val recognizer: OfflineRecognizer) {
    companion object {
        private const val TAG = "SherpaEngine"

        /**
         * Sherpa-ONNX SenseVoice 가 인식 가능한 언어 코드.
         * 모델은 sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 (zh/en/ja/ko/yue + auto).
         * @param language "auto" | "ko" | "en" | "ja" | "zh" | "yue"
         */
        fun create(modelDir: String, language: String = "auto"): SherpaEngine? {
            return try {
                var dir = File(modelDir)
                dir.listFiles()?.firstOrNull { it.isDirectory && !it.name.startsWith(".") && File(it, "tokens.txt").exists() }?.let { dir = it }

                val modelFile = dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
                val tok = File(dir, "tokens.txt")
                if (modelFile == null || !tok.exists() || modelFile.length() < 10L * 1024 * 1024) {
                    Log.w(TAG, "Sherpa model files missing or corrupted in $modelDir")
                    return null
                }

                val normalizedLanguage = when (language) {
                    "ko", "en", "ja", "zh", "yue", "auto" -> language
                    else -> "auto"
                }

                val cfg = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = modelFile.absolutePath,
                            language = normalizedLanguage,
                            useInverseTextNormalization = true
                        ),
                        tokens = tok.absolutePath,
                        numThreads = 4,
                        provider = "cpu"
                    ),
                    decodingMethod = "greedy_search"
                )
                SherpaEngine(OfflineRecognizer(config = cfg))
            } catch (e: Exception) {
                Log.e(TAG, "SherpaEngine initialization failed due to native exception", e)
                null
            }
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
