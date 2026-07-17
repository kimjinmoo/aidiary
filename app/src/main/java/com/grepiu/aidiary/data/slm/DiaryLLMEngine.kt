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
         * 블록 단위 AI 액션([proofreadText], [decorateText]) 입력 안전 한도.
         *
         *  - 모델 컨텍스트: [maxNumTokens] = 1024
         *  - 한국어 토큰화 비율 약 1.5~2 chars/token (보수적으로 1.5)
         *  - 출력 예약: proofread 512 / decorate 256
         *  - 시스템/유저 프롬프트 오버헤드: ~150 tokens
         *  - 따라서 입력 안전 한도는 (1024 - 512 - 150) × 1.5 ≈ 543 chars
         *  - 안전 마진을 더해 600 chars 로 고정
         */
        const val MAX_BLOCK_AI_INPUT_CHARS = 600

        /**
         * [detectEmotion] 결과. 5종 감정 라벨 중 하나와 보정된 정규화 라벨.
         */
        data class EmotionResult(
            val raw: String,        // 모델이 그대로 출력한 텍스트
            val emotion: String     // 5종 중 정규화된 라벨 (기본: "평온")
        )

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
     * 일기 본문을 읽고 다섯 가지 감정(기쁨/슬픔/분노/불안/평온) 중 하나로 분류합니다.
     * 저장 시 자동 호출되어 TAG AI 블록의 [emotion] 값을 만듭니다.
     * (위로/조언 본문 생성은 제거 — 가벼운 단일 토큰 추론만 수행)
     */
    suspend fun detectEmotion(
        title: String,
        content: String,
        dateString: String
    ): EmotionResult = withContext(Dispatchers.Default) {
        if (content.isBlank()) {
            return@withContext EmotionResult("평온", "평온")
        }
        val prompt = buildEmotionPrompt(title, content, dateString)
        Log.d(TAG, "Emotion prompt (${prompt.length} chars)")

        val builder = StringBuilder()
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 15,
                        topP = 0.6,
                        temperature = 0.1
                    )
                )
            )
            conversation.sendMessageAsync(prompt).collect { message ->
                builder.append(message.toString())
            }
            conversation.close()
            val raw = builder.toString().trim()
            EmotionResult(raw = raw, emotion = normalizeEmotion(raw))
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting emotion (backend=$backendType)", e)
            EmotionResult("평온", "평온")
        }
    }

    /**
     * 5 종 감정 라벨 중 하나로 정규화합니다.
     * 매칭이 안 되면 "평온" 으로 폴백합니다.
     */
    private fun normalizeEmotion(raw: String): String {
        if (raw.isBlank()) return "평온"
        val cleaned = raw
            .replace(".", "")
            .replace(",", "")
            .replace(":", "")
            .replace(":", "")
            .trim()
        val allowed = listOf("기쁨", "슬픔", "분노", "불안", "평온")
        // 완전 일치 우선
        allowed.firstOrNull { it == cleaned }?.let { return it }
        // 부분 일치 (예: "분노(화남)" 처럼 답한 경우)
        allowed.firstOrNull { cleaned.contains(it) }?.let { return it }
        return "평온"
    }

    /**
     * 감정 분류용 간결 프롬프트. 5 종 감정 중 하나만 출력하도록 강제합니다.
     */
    private fun buildEmotionPrompt(title: String, content: String, dateString: String): String {
        val systemPrompt = "당신은 한국어 일기의 핵심 감정을 분류하는 AI입니다. " +
                "주어진 본문을 읽고 가장 가까운 감정 한 단어만 답하세요. " +
                "설명·이유·접두어·따옴표 없이 단어만 출력하세요."

        val userPrompt = buildString {
            append("[일기]\n")
            append("- 날짜: $dateString\n")
            append("- 제목: ${title.ifBlank { "제목 없음" }}\n")
            append("- 내용: $content\n\n")
            append("위 일기의 핵심 감정을 다음 다섯 단어 중에서만 골라 한 단어로 답하세요.\n")
            append("기쁨 / 슬픔 / 분노 / 불안 / 평온\n")
            append("감정:")
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
     * 사용자의 날짜/기존 계획/장기 목표/최근 일기 맥락을 보고,
     * 오늘의 플래너에 어울리는 1건의 한국어 할 일을 추천합니다.
     */
    suspend fun suggestPlannerTaskName(
        context: String
    ): String = withContext(Dispatchers.Default) {
        if (context.isBlank()) return@withContext ""
        val systemPrompt = "당신은 한국어 스마트 플래너 코치 AI입니다. " +
                "주어진 날짜(요일), 같은 날 이미 등록된 계획(시간·장소 포함), " +
                "사용자의 장기 목표, 최근 일기 내용을 종합적으로 고려해 " +
                "오늘의 플래너에 추가할 1건의 한국어 할 일을 추천하세요. " +
                "구체적이고 실행 가능해야 하며, 기존 계획과 중복되지 않아야 합니다. " +
                "출력은 1줄 한국어 텍스트만. 따옴표·접두사·이모지·번호·마침표 없이 본문 텍스트만 출력하세요."
        val userPrompt = "$context\n\n추천할 1개의 계획:"

        runSinglePrompt(systemPrompt, userPrompt, maxTokens = 48, temperature = 0.6)
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '《', '》')
            .let { stripPrefixes(it) }
            .let { if (it.length > 30) it.substring(0, 30) else it }
    }

    /**
     * 각 탭(기록/플래너/목표) 의 AI 브리핑을 1문단 한국어로 생성합니다.
     * 시스템 프롬프트가 tabKey 별로 분기되어 각 탭에 맞는 분석/제안/격려를 만듭니다.
     *
     * @param tabKey "DIARY" | "PLANNER" | "GOALS"
     * @param context 미리 빌드된 탭별 데이터 컨텍스트
     */
    suspend fun generateBriefing(
        tabKey: String,
        context: String
    ): String = withContext(Dispatchers.Default) {
        if (context.isBlank()) return@withContext ""
        val (systemPrompt, maxTokens) = when (tabKey) {
            "DIARY" -> "당신은 한국어 일기 코치 AI입니다. " +
                    "사용자의 최근 일기 데이터를 분석해 (1) 최근 감정/분위기 트렌드, " +
                    "(2) 자주 등장하는 주제·관심사, (3) 사용자에게 도움이 될 한 가지 제안이나 격려를 " +
                    "한국어 2~4줄의 한 단락으로 자연스럽게 요약하세요. " +
                    "마크다운·이모지·번호·접두사 없이 자연스러운 한국어 문장만 출력하세요." to 256
            "PLANNER" -> "당신은 한국어 플래너 코치 AI입니다. " +
                    "사용자의 오늘/이번 주 계획을 분석해 (1) 오늘 등록된 계획과 반복 계획 개요, " +
                    "(2) 시간대별 분포나 밀도, (3) 사용자에게 도움이 될 한 가지 실행 제안을 " +
                    "한국어 2~4줄의 한 단락으로 자연스럽게 요약하세요. " +
                    "마크다운·이모지·번호·접두사 없이 자연스러운 한국어 문장만 출력하세요." to 256
            "GOALS" -> "당신은 한국어 목표 코치 AI입니다. " +
                    "사용자의 장기 목표와 진행 상황을 분석해 (1) 전체 진행률과 활성 목표 요약, " +
                    "(2) 최근 일기/플래너와의 정합성, (3) 사용자에게 도움이 될 한 가지 제안이나 격려를 " +
                    "한국어 2~4줄의 한 단락으로 자연스럽게 요약하세요. " +
                    "마크다운·이모지·번호·접두사 없이 자연스러운 한국어 문장만 출력하세요." to 256
            else -> "당신은 한국어 코치 AI입니다. 주어진 데이터를 분석해 2~4줄로 요약하세요." to 256
        }
        val userPrompt = "$context\n\n위 데이터를 분석해 1개의 단락으로 한국어 브리핑을 작성해 주세요."

        runSinglePrompt(systemPrompt, userPrompt, maxTokens = maxTokens, temperature = 0.5)
            .trim()
            .let { stripCodeFences(it) }
            .let { if (it.length > 600) it.substring(0, 600) else it }
    }

    /**
     * 완료된 장기 목표를 축하하는 온디바이스 AI 멘토의 응원 한마디를 생성합니다.
     */
    suspend fun generateCongratulation(
        goalText: String
    ): String = withContext(Dispatchers.Default) {
        val systemPrompt = "당신은 따뜻하고 다정하게 유저의 장기 다짐/목표 성취를 격려해주는 AI 멘토입니다. 줄바꿈 없이 반드시 1줄의 정중하고 깊은 울림을 주는 존댓말 응원 메시지만 심플하게 작성해 주세요."
        val userPrompt = "[달성한 목표: $goalText] 를 유저가 성공적으로 마쳤습니다. 달성을 축하하고, 더 성장할 앞날을 응원하는 1줄 멘토 코멘트를 적어주세요."
        runSinglePrompt(systemPrompt, userPrompt, maxTokens = 128, temperature = 0.5)
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("\'")
            .trim()
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
