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

    // 현재 사용 중인 백엔드 (GPU 또는 CPU)
    var backendType: String = "Unknown"
        private set

    companion object {
        private const val TAG = "DiaryLLMEngine"

        /** [classifyContentType] 응답 매핑용 키 (ContentType.storageKey 와 동일). */
        object ContentTypeKeys {
            const val DIARY = "DIARY"
            const val POST = "POST"
            const val NOTE = "NOTE"
        }

        /**
         * 지정된 로컬 모델 파일 경로(.litertlm)를 읽어와 LiteRT-LM Engine 인스턴스를 빌드하고 엔진을 생성합니다.
         * GPU(OpenCL) 초기화 실패 시 CPU 백엔드로 자동 폴백합니다.
         */
        fun create(context: Context, modelPath: String): DiaryLLMEngine {
            return tryCreate(context, modelPath, Backend.GPU(), "GPU")
                ?: tryCreate(context, modelPath, Backend.CPU(), "CPU")
                ?: throw IllegalStateException("Failed to initialize LiteRT-LM engine with both GPU and CPU backends")
        }

        private fun tryCreate(context: Context, modelPath: String, backend: Backend, label: String): DiaryLLMEngine? {
            return try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = 1024
                )
                val engine = Engine(config)
                engine.initialize()
                Log.d(TAG, "LiteRT-LM Engine initialized with $label backend")
                DiaryLLMEngine(engine).apply { backendType = label }
            } catch (e: Exception) {
                Log.w(TAG, "$label backend init failed: ${e.message}")
                null
            }
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
            Log.e(TAG, "Error generating analysis (backend=$backendType)", e)
            onTokenReceived?.invoke("", true)
            "[AI 분석을 생성할 수 없습니다]\n현재 백엔드: $backendType\n오류: ${e.message?.take(80)}"
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
     * 본문 내용을 분석해 12자 이내의 한국어 제목을 한 줄로 생성합니다.
     * 모델은 [TitleAssist] JSON 만 출력하도록 강제합니다.
     */
    suspend fun suggestTitle(
        content: String
    ): String = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext ""
        val systemPrompt = "당신은 한국어 일기 제목을 만들어주는 AI입니다. " +
                "주어진 본문을 요약해 12자 이내의 한국어 제목을 한 줄로만 만드세요. " +
                "설명·따옴표·접두어 없이 제목 텍스트만 출력하세요."
        val userPrompt = "본문:\n$content\n\n제목:"

        runSinglePrompt(systemPrompt, userPrompt, maxTokens = 64, temperature = 0.4)
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '《', '》')
            .let { stripPrefixes(it) }
            .let { if (it.length > 24) it.substring(0, 24) else it }
    }

    /**
     * 본문 톤/길이/구조를 보고 적절한 글 타입 (DIARY / POST / NOTE) 을 결정합니다.
     * 결과는 [ContentType.storageKey] 만 반환합니다.
     */
    suspend fun classifyContentType(
        content: String
    ): String = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext ContentTypeKeys.DIARY
        val systemPrompt = "당신은 한국어 글의 종류를 분류하는 AI입니다. " +
                "주어진 본문을 보고 다음 세 가지 중 하나로만 답하세요. 다른 텍스트는 절대 쓰지 마세요.\n" +
                "- DIARY : 개인 감정·하루 이야기·일상 회고 (1인칭, 시간 흐름, 감정 묘사)\n" +
                "- POST  : 정보 전달·의견·아이디어·자유 글 (정보성, 객관적, 발행 목적)\n" +
                "- NOTE  : 짧은 메모·할 일·아이디어 단편 (간결, 1~2문장)"
        val userPrompt = "본문:\n$content\n\n분류:"

        val raw = runSinglePrompt(systemPrompt, userPrompt, maxTokens = 8, temperature = 0.1)
            .trim()
            .uppercase()
        when {
            raw.contains("DIARY") -> ContentTypeKeys.DIARY
            raw.contains("POST") -> ContentTypeKeys.POST
            raw.contains("NOTE") -> ContentTypeKeys.NOTE
            else -> ContentTypeKeys.DIARY
        }
    }

    /**
     * 본문의 한국어 오탈자/띄어쓰기/문법/문체 를 정리하고 가독성을 높인
     * 다듬어진 텍스트를 반환합니다. 의미/톤은 유지합니다.
     */
    suspend fun proofreadText(
        text: String
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext text
        val systemPrompt = "당신은 한국어 문장 다듬기 전문가입니다. " +
                "주어진 본문의 띄어쓰기·맞춤법·문법 오류를 수정하고 가독성을 높이세요. " +
                "의미와 말투는 유지하되, 불필요한 수식은 제거하세요. " +
                "설명·주석·접두어 없이 다듬어진 본문만 출력하세요."
        val userPrompt = "본문:\n$text\n\n다듬은 본문:"

        runSinglePrompt(systemPrompt, userPrompt, maxTokens = 512, temperature = 0.2)
            .trim()
            .let { stripCodeFences(it) }
    }

    /**
     * 본문 내용에 어울리는 강조(굵게) 와 색상 강조 위치를 결정해
     * 텍스트와 서식을 함께 반환합니다. [DecorateResult].
     */
    suspend fun decorateText(
        text: String
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext text
        val systemPrompt = "당신은 한국어 글의 핵심 단어/구절을 강조하는 편집자입니다. " +
                "주어진 본문에서 가장 의미 있는 1~3개 단어나 짧은 구절을 골라 " +
                "아래 JSON 배열 형식으로만 답하세요. 설명은 쓰지 마세요. " +
                "각 항목의 start/end 는 0-based half-open 인덱스(원본 텍스트 기준)입니다. " +
                "키워드는 본문에 실제로 등장하는 그대로의 문자열이어야 합니다. " +
                "색상은 #D32F2F(빨강) #E65100(주황) #F9A825(노랑) #2E7D32(초록) " +
                "#0277BD(파랑) #6A1B9A(보라) 중에서만 골라주세요.\n" +
                "출력 예: [{\"keyword\":\"행복\",\"bold\":true,\"color\":\"#2E7D32\"}]"
        val userPrompt = "본문:\n$text\n\n강조 키워드 JSON:"

        runSinglePrompt(systemPrompt, userPrompt, maxTokens = 256, temperature = 0.3)
            .trim()
            .let { stripCodeFences(it) }
    }

    /**
     * 단발성 프롬프트를 보내고 결과 텍스트를 한 번에 돌려받습니다.
     * (스트리밍 없이 결과만 사용 — 보조 액션 응답 속도 우선)
     */
    private suspend fun runSinglePrompt(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): String {
        val conversation = try {
            engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 25,
                        topP = 0.7,
                        temperature = temperature
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "runSinglePrompt: conversation create failed", e)
            return ""
        }

        val prompt = "<start_of_turn>system\n$systemPrompt<end_of_turn>\n" +
                "<start_of_turn>user\n$userPrompt<end_of_turn>\n" +
                "<start_of_turn>model\n"
        val builder = StringBuilder()
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                builder.append(message.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "runSinglePrompt: sendMessage failed", e)
        } finally {
            try { conversation.close() } catch (_: Exception) {}
        }
        return builder.toString()
    }

    private fun stripPrefixes(s: String): String =
        s.trim().removePrefix("제목:").removePrefix("제목 :").removePrefix("-")
            .removePrefix("·").removePrefix("*").trim()

    private fun stripCodeFences(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            t = t.removeSuffix("```").trim()
        }
        return t
    }

    /**
     * 비동기 방식으로 RAG 조합 프롬프트를 전송하여 챗봇 답변 결과를 스트리밍 형태로 콜백 전달합니다.
     */
    suspend fun generateChatResponse(
        prompt: String
    ): String = withContext(Dispatchers.Default) {
        val builder = StringBuilder()
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 25,
                        topP = 0.7,
                        temperature = 0.4
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

            conversation.close()
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error generating chat response (backend=$backendType)", e)
            onTokenReceived?.invoke("", true)
            "[오류가 발생하여 답변할 수 없어요. 모델 연결 상태를 확인해 주세요]"
        }
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
