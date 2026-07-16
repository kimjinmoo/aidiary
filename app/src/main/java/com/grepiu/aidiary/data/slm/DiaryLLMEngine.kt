@file:Suppress("DEPRECATION")
package com.grepiu.aidiary.data.slm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM SDK API를 활용하여 온디바이스 일기 분석 피드백 결과를 생성하는 언어 모델 엔진입니다.
 */
class DiaryLLMEngine private constructor(private val engine: Engine) {

    // 스트리밍 방식으로 텍스트 토큰이 도착할 때마다 호출될 실시간 콜백
    var onTokenReceived: ((String, Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "DiaryLLMEngine"

        /**
         * 지정된 로컬 모델 파일 경로(.litertlm)를 읽어와 LiteRT-LM Engine 인스턴스를 빌드하고 엔진을 생성합니다.
         */
        fun create(context: Context, modelPath: String): DiaryLLMEngine {
            val engine: Engine = try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    maxNumTokens = 1024
                )
                Engine(config).also { it.initialize() }
                Log.d(TAG, "LiteRT-LM Engine initialized with GPU backend")
            } catch (e: Exception) {
                Log.w(TAG, "GPU(OpenCL) 초기화 실패, CPU 백엔드로 폴백합니다: ${e.message}")
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    maxNumTokens = 1024
                )
                Engine(config).also { it.initialize() }
                Log.d(TAG, "LiteRT-LM Engine initialized with CPU backend")
            }
            return DiaryLLMEngine(engine)
        }
    }

    /**
     * 비동기 방식으로 일기 제목, 본문, 날짜를 입력하여 실시간 토큰 콜백 또는 한 번에 해석 결과를 출력합니다.
     */
    suspend fun generateAnalysis(
        title: String,
        content: String,
        dateString: String
    ): String = withContext(Dispatchers.Default) {
        val prompt = buildPrompt(title, content, dateString)
        Log.d(TAG, "Prompt (${prompt.length} chars): ${prompt.take(150)}...")

        val builder = StringBuilder()
        try {
            // temperature 낮춰 포맷 준수율 향상, topK 좁혀 반복·환각 억제
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 25,
                        topP = 0.7,
                        temperature = 0.3
                    )
                )
            )

            // Flow 형태로 반환되는 스트리밍 결과를 수집하여 콜백으로 전달
            conversation.sendMessageAsync(prompt).collect { message ->
                val token = message.toString()
                builder.append(token)
                onTokenReceived?.invoke(token, false)
            }

            val finalResult = builder.toString()
            onTokenReceived?.invoke("", true) // 완료 표시 전달

            // 사용 완료 시 리소스 즉시 해제
            conversation.close()
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error generating analysis", e)
            onTokenReceived?.invoke("", true)
            "[AI 분석을 생성할 수 없습니다]\n이 기기는 온디바이스 AI에 필요한 GPU 라이브러리(OpenCL)를 지원하지 않거나 모델 파일이 손상되었습니다."
        }
    }

    /**
     * 일기를 바탕으로 온디바이스 2B LLM(Gemma IT)이 인지할 수 있는 양식의 프롬프트를 생성합니다.
     */
    fun buildPrompt(title: String, content: String, dateString: String): String {
        // system: 역할 및 말투 지정 (소형 모델에 맞추어 간결하게 지정)
        val systemPrompt = "당신은 따뜻하고 공감 능력이 뛰어난 AI 마음 일기 상담사예요. " +
                "반드시 100% 한국어(Korean)로만 답변해야 하며, 영어(English)나 다른 외국어는 절대 사용하지 마세요. " +
                "반드시 '~해요'체로만 답하고, 인사말·서론 없이 첫 번째 항목부터 바로 시작하세요."

        // user: 일기 정보 및 구체적인 레이블 포맷 강제 지시
        val userPrompt = buildString {
            append("[일기 정보]\n")
            append("- 날짜: $dateString\n")
            append("- 제목: ${title.ifBlank { "제목 없음" }}\n")
            append("- 내용: $content\n\n")
            append("※ 반드시 100% 한국어(Korean)로만 답변하고, 영어 나 다른 언어는 절대 섞어 쓰지 마세요. ")
            append("반드시 '~해요'체로, 인사말 없이 아래 지정된 세 개의 레이블 형식 그대로 문단을 나누어 출력하세요.\n\n")
            append("오늘의 감정 분석: (일기에 나타난 감정 상태를 분석해 2~3문장으로 분석해요)\n")
            append("따뜻한 마음 위로: (작성자의 마음에 깊이 공감하고 격려를 건네는 글을 3~4문장으로 작성해요)\n")
            append("내일을 위한 추천 조언: (내일을 더 의미 있고 편안하게 보내기 위한 실천 팁 1~2문장을 제시해요)")
        }

        return "<start_of_turn>system\n$systemPrompt<end_of_turn>\n" +
               "<start_of_turn>user\n$userPrompt<end_of_turn>\n" +
               "<start_of_turn>model\n"
    }

    /**
     * 리소스 정리
     */
    fun dispose() {
        try {
            engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
    }
}
