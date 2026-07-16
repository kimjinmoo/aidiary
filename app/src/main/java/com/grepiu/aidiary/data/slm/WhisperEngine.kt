package com.grepiu.aidiary.data.slm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Android 내장 음성인식 기반 온디바이스 음성→텍스트 변환 엔진입니다.
 * Android 12+에서는 다운로드된 언어팩으로 오프라인에서도 동작합니다.
 */
class WhisperEngine private constructor(
    private val speechRecognizer: SpeechRecognizer
) {
    companion object {
        private const val TAG = "WhisperEngine"

        fun create(context: Context): WhisperEngine {
            val recognizer = if (SpeechRecognizer.isOnDeviceSpeechRecognizerAvailable(context)) {
                Log.d(TAG, "On-device speech recognizer available")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.d(TAG, "Using network-based speech recognizer")
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            return WhisperEngine(recognizer)
        }
    }

    /**
     * 음성 인식을 시작하고 결과를 콜백으로 전달합니다.
     */
    fun startListening(
        language: String = "ko-KR",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "오디오 오류"
                    SpeechRecognizer.ERROR_CLIENT -> "클라이언트 오류"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 부족"
                    SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                    SpeechRecognizer.ERROR_NO_MATCH -> "음성 인식 결과 없음"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기가 사용 중"
                    SpeechRecognizer.ERROR_SERVER -> "서버 오류"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간 초과"
                    else -> "알 수 없는 오류 ($error)"
                }
                Log.e(TAG, "Recognition error: $msg")
                onError(msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                Log.d(TAG, "Final result: $text")
                onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    Log.d(TAG, "Partial: $text")
                    onResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun dispose() {
        try {
            speechRecognizer.destroy()
            Log.d(TAG, "Speech recognizer disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing", e)
        }
    }
}
