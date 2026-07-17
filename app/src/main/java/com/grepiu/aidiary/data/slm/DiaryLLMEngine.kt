@file:Suppress("DEPRECATION")
package com.grepiu.aidiary.data.slm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * LiteRT-LM SDK API를 활용하여 온디바이스 일기 분석 피드백 결과를 생성하는 언어 모델 엔진.
 *
 * v2 핵심 변경:
 *  - 모든 user prompt 가 [LLMContextBuilder] 를 거치도록 통일 (계층적 도메인/제약/예시)
 *  - proofread / decorate 가 인접 블록 컨텍스트를 함께 받음 → 어조 일관성 보존
 *  - 저장 흐름의 (분류 + 감정) 을 1회 호출로 통합 → 응답 지연 50% 감소 + 일관성↑
 *  - 챗봇은 멀티턴 Conversation 을 재사용하고 최근 raw N턴 + 그 이전 요약 슬라이딩 윈도우 적용
 *  - 작업별 SamplerConfig 를 분리 (분류는 결정적, 생성은 약간 창의적)
 */
class DiaryLLMEngine private constructor(private val engine: Engine) {

    // 스트리밍 방식으로 텍스트 토큰이 도착할 때마다 호출될 실시간 콜백
    var onTokenReceived: ((String, Boolean) -> Unit)? = null

    // 현재 사용 중인 백엔드 (GPU 또는 CPU)
    var backendType: String = "Unknown"
        private set

    // ===== 챗봇 멀티턴 상태 =====
    private val chatConversation = AtomicReference<Conversation?>(null)
    private val chatHistory = mutableListOf<Pair<String, String>>() // (role, text)
    private var chatPriorSummary: String? = null

    companion object {
        private const val TAG = "DiaryLLMEngine"

        /** [classifyContentType] 응답 매핑용 키 (ContentType.storageKey 와 동일). */
        object ContentTypeKeys {
            const val DIARY = "DIARY"
            const val POST = "POST"
            const val NOTE = "NOTE"
        }

        /**
         * 블록 단위 AI 액션 입력 안전 한도. 한국어 토큰화 비율 1.5 chars/token 가정.
         */
        const val MAX_BLOCK_AI_INPUT_CHARS = 600

        /** 챗봇 멀티턴 윈도우: 최근 raw 로 유지할 (user,ai) 페어 수. */
        const val CHAT_RECENT_RAW_TURNS = 2

        /** 챗봇 이력 누적 임계치. 이보다 커지면 앞쪽을 1줄 요약으로 압축. */
        const val CHAT_SUMMARY_TRIGGER_TURNS = 6

        /**
         * 통합 (분류+감정) 결과.
         */
        data class ClassifyAndEmotion(
            val typeKey: String,
            val emotion: String,
            val raw: String
        )

        /**
         * 감정 분류 단독 결과.
         */
        data class EmotionResult(
            val raw: String,
            val emotion: String
        )

        /**
         * 지정된 로컬 모델 파일 경로(.litertlm)를 읽어와 LiteRT-LM Engine 인스턴스를 빌드.
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

    // ===== 저장 시 통합 (분류+감정) =====

    /**
     * 본문에서 (글 타입, 핵심 감정) 을 1회 호출로 추출.
     * 기존 saveDiaryDraft 의 2-stage (classify → emotion) 를 1-stage 로 단축.
     */
    suspend fun classifyAndDetectEmotion(
        title: String,
        content: String,
        dateString: String,
        currentTypeLabel: String
    ): ClassifyAndEmotion = withContext(Dispatchers.Default) {
        if (content.isBlank()) {
            return@withContext ClassifyAndEmotion(ContentTypeKeys.DIARY, "평온", "")
        }
        val (system, user) = LLMContextBuilder.classifyAndDetectEmotion(
            title = title,
            content = content,
            dateString = dateString,
            currentTypeLabel = currentTypeLabel
        )
        val raw = runSinglePrompt(
            systemPrompt = system,
            userPrompt = user,
            sampler = SamplerPresets.CLASSIFY,
            maxTokens = 64
        ).trim().let { stripCodeFences(it) }
        parseClassifyAndEmotion(raw)
    }

    private fun parseClassifyAndEmotion(raw: String): ClassifyAndEmotion {
        if (raw.isBlank()) return ClassifyAndEmotion(ContentTypeKeys.DIARY, "평온", raw)
        // JSON 한 줄 파싱 시도
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val json = raw.substring(start, end + 1)
            try {
                val obj = JSONObject(json)
                val type = obj.optString("type", "").uppercase()
                val emo = obj.optString("emotion", "").trim()
                val mappedType = when {
                    type.contains("DIARY") -> ContentTypeKeys.DIARY
                    type.contains("POST") -> ContentTypeKeys.POST
                    type.contains("NOTE") -> ContentTypeKeys.NOTE
                    else -> ContentTypeKeys.DIARY
                }
                val mappedEmo = normalizeEmotion(emo)
                return ClassifyAndEmotion(mappedType, mappedEmo, raw)
            } catch (_: Exception) {
                // JSON 파싱 실패 → 텍스트 폴백
            }
        }
        // 텍스트 폴백: 키워드 포함 매칭
        val upper = raw.uppercase()
        val type = when {
            upper.contains("DIARY") -> ContentTypeKeys.DIARY
            upper.contains("POST") -> ContentTypeKeys.POST
            upper.contains("NOTE") -> ContentTypeKeys.NOTE
            else -> ContentTypeKeys.DIARY
        }
        val emo = normalizeEmotion(raw)
        return ClassifyAndEmotion(type, emo, raw)
    }

    // ===== 단일 작업 메서드들 (모두 LLMContextBuilder 사용) =====

    suspend fun detectEmotion(
        title: String,
        content: String,
        dateString: String
    ): EmotionResult = withContext(Dispatchers.Default) {
        if (content.isBlank()) {
            return@withContext EmotionResult("평온", "평온")
        }
        val (system, user) = LLMContextBuilder.detectEmotion(title, content, dateString)
        val raw = runSinglePrompt(system, user, SamplerPresets.CLASSIFY, maxTokens = 16)
            .trim()
            .let { stripCodeFences(it) }
        EmotionResult(raw = raw, emotion = normalizeEmotion(raw))
    }

    suspend fun suggestTitle(
        content: String,
        currentTitle: String = "",
        contentTypeLabel: String = "일기"
    ): String = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext ""
        val (system, user) = LLMContextBuilder.suggestTitle(content, currentTitle, contentTypeLabel)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_SHORT, maxTokens = 64)
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '《', '》')
            .let { stripPrefixes(it) }
            .let { if (it.length > 24) it.substring(0, 24) else it }
    }

    suspend fun translateToKorean(
        content: String,
        sourceLangHint: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext ""
        val (system, user) = LLMContextBuilder.translateToKorean(content, sourceLangHint)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_LONG, maxTokens = 1024)
            .trim()
            .let { stripCodeFences(it) }
            .let { it.trimStart().removePrefix("한국어 번역:").removePrefix("번역:").removePrefix("한국어 결과:").trim() }
    }

    suspend fun classifyContentType(
        content: String,
        currentTypeLabel: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext ContentTypeKeys.DIARY
        val (system, user) = LLMContextBuilder.classifyContentType(content, currentTypeLabel)
        val raw = runSinglePrompt(system, user, SamplerPresets.CLASSIFY, maxTokens = 8)
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
     * 인접 컨텍스트 포함 오타/띄어쓰기 다듬기.
     * [previousTail] / [nextHead] 는 동일 글의 직전/직후 블록 발췌.
     */
    suspend fun proofreadText(
        text: String,
        previousTail: String? = null,
        nextHead: String? = null,
        sessionTitle: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext text
        val (system, user) = LLMContextBuilder.proofreadText(text, previousTail, nextHead, sessionTitle)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_LONG, maxTokens = 512)
            .trim()
            .let { stripCodeFences(it) }
    }

    /**
     * 인접 컨텍스트 포함 꾸미기 추천.
     */
    suspend fun decorateText(
        text: String,
        previousTail: String? = null,
        nextHead: String? = null,
        sessionTitle: String? = null
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext text
        val (system, user) = LLMContextBuilder.decorateText(text, previousTail, nextHead, sessionTitle)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_MEDIUM, maxTokens = 512)
            .trim()
            .let { stripCodeFences(it) }
    }

    suspend fun suggestPlannerTaskName(
        context: String
    ): String = withContext(Dispatchers.Default) {
        if (context.isBlank()) return@withContext ""
        val (system, user) = LLMContextBuilder.suggestPlannerTask(context)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_SHORT, maxTokens = 48)
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '《', '》')
            .let { stripPrefixes(it) }
            .let { if (it.length > 30) it.substring(0, 30) else it }
    }

    suspend fun generateBriefing(
        tabKey: String,
        context: String
    ): String = withContext(Dispatchers.Default) {
        if (context.isBlank()) return@withContext ""
        val (system, user) = LLMContextBuilder.briefing(tabKey, context)
        val raw = runSinglePrompt(system, user, SamplerPresets.GENERATE_MEDIUM, maxTokens = 256)
            .trim()
            .let { stripCodeFences(it) }
        if (raw.length > 600) raw.substring(0, 600) else raw
    }

    suspend fun generateCongratulation(
        goalText: String
    ): String = withContext(Dispatchers.Default) {
        val (system, user) = LLMContextBuilder.congratulate(goalText)
        runSinglePrompt(system, user, SamplerPresets.GENERATE_MEDIUM, maxTokens = 128)
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }

    // ===== 챗봇 멀티턴 =====

    /**
     * RAG 컨텍스트를 주입한 멀티턴 챗봇 응답. 동일 Conversation 을 재사용하여
     * 최근 raw N턴 + 그 이전 요약을 컨텍스트로 보존한다.
     *
     * @param contextBlock [LLMContextBuilder.chat] 형식으로 빌드된 컨텍스트
     * @param userQuery 현재 사용자 질문
     * @return AI 응답
     */
    suspend fun generateChatResponse(
        contextBlock: String,
        userQuery: String
    ): String = withContext(Dispatchers.Default) {
        // 1) Conversation 확보/재생성
        val conversation = ensureChatConversation()
        // 2) 사용자 발화 누적
        chatHistory.add("USER" to userQuery)

        // 3) 임계치 초과 시 앞쪽을 1줄 요약으로 압축
        if (chatHistory.size > CHAT_SUMMARY_TRIGGER_TURNS) {
            compressChatHistory()
        }

        // 4) 현재 컨텍스트 블록 (이전 요약 + 최근 raw 턴 + RAG + 현재 질문)
        val rollingContext = LLMContextBuilder.buildChatMultiTurnContext(
            priorSummary = chatPriorSummary,
            allTurns = chatHistory.takeLast(CHAT_RECENT_RAW_TURNS * 2),
            maxRecentRaw = CHAT_RECENT_RAW_TURNS
        )
        val system = "당신은 사용자의 일기 내용과 일정을 기억하는 다이어리 인공지능 비서예요. " +
                "제공되는 [컨텍스트] 와 [이전 대화] 에 기반하여 사용자의 질문에만 정직하게 대답해야 해요. " +
                "**필독 규칙**: 절대로 사용자의 일정이나 일기를 상상해서 지어내어 거짓으로 답변하지 마세요. " +
                "오늘의 할 일 목록에 해당하는 일정이 없다면, 가상 일정을 만들지 말고 '오늘 계획된 일정이 등록되어 있지 않아요'라고 솔직하게 대답하세요. " +
                "반드시 100% 한국어로만 답변하고, '~해요'체로 상냥하고 간결하게 대답하세요."

        val userPrompt = buildString {
            if (rollingContext.isNotBlank()) {
                append(rollingContext).append("\n\n")
            }
            append(contextBlock).append("\n\n")
            append("[현재 질문]\n").append(userQuery).append("\n\n[답변]")
        }

        val prompt = "<start_of_turn>system\n$system<end_of_turn>\n" +
                "<start_of_turn>user\n$userPrompt<end_of_turn>\n" +
                "<start_of_turn>model\n"

        val builder = StringBuilder()
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                val token = message.toString()
                builder.append(token)
                onTokenReceived?.invoke(token, false)
            }
            onTokenReceived?.invoke("", true)
            val final = builder.toString()
            chatHistory.add("AI" to final)
            final
        } catch (e: Exception) {
            Log.e(TAG, "Error generating chat response (backend=$backendType)", e)
            onTokenReceived?.invoke("", true)
            // 실패 시 컨버세이션 리셋하여 다음 턴부터 깨끗하게 시작
            try { conversation.close() } catch (_: Exception) {}
            chatConversation.set(null)
            "[오류가 발생하여 답변할 수 없어요. 모델 연결 상태를 확인해 주세요]"
        }
    }

    /**
     * 챗봇 컨텍스트를 외부에서 직접 빌드하고 싶을 때 사용하는 헬퍼.
     */
    fun buildChatContextBlock(
        todayStr: String,
        selectedDateStr: String,
        todayTasks: List<String>,
        selectedDateTasks: List<String>,
        matchedDiaries: List<String>,
        matchedTasks: List<String>,
        matchedGoals: List<String>
    ): String = buildString {
        append("- 기준 날짜 (오늘): $todayStr\n")
        append("- 선택된 캘린더 날짜: $selectedDateStr\n\n")
        append("■ 오늘($todayStr)의 실제 계획된 할 일 목록:\n")
        if (todayTasks.isEmpty()) append("  - (오늘 계획된 할 일이 등록되어 있지 않습니다)\n\n")
        else {
            todayTasks.forEach { append("  - $it\n") }
            append('\n')
        }
        if (selectedDateStr != todayStr) {
            append("■ 선택한 날짜($selectedDateStr)의 실제 계획된 할 일 목록:\n")
            if (selectedDateTasks.isEmpty()) append("  - (이날 계획된 할 일이 등록되어 있지 않습니다)\n\n")
            else {
                selectedDateTasks.forEach { append("  - $it\n") }
                append('\n')
            }
        }
        if (matchedDiaries.isNotEmpty()) {
            append("■ 연관 일기:\n")
            matchedDiaries.forEach { append("  - $it\n") }
            append('\n')
        }
        if (matchedTasks.isNotEmpty()) {
            append("■ 연관 기타 할 일:\n")
            matchedTasks.forEach { append("  - $it\n") }
            append('\n')
        }
        if (matchedGoals.isNotEmpty()) {
            append("■ 연관 장기 목표:\n")
            matchedGoals.forEach { append("  - $it\n") }
            append('\n')
        }
    }

    /**
     * 챗봇 이력 초기화 (ClearChatHistory 인텐트에서 호출).
     */
    fun clearChat() {
        try { chatConversation.get()?.close() } catch (_: Exception) {}
        chatConversation.set(null)
        chatHistory.clear()
        chatPriorSummary = null
    }

    private fun ensureChatConversation(): Conversation {
        chatConversation.get()?.let { return it }
        val cfg = ConversationConfig(samplerConfig = SamplerPresets.GENERATE_MEDIUM.toSamplerConfig())
        val conv = engine.createConversation(cfg)
        chatConversation.set(conv)
        return conv
    }

    /**
     * 오래된 턴을 1줄 요약으로 압축. 2B 모델이므로 1줄 bullet 요약을 만들어
     * priorSummary 앞에 붙인다.
     */
    private suspend fun compressChatHistory() {
        if (chatHistory.size < 4) return
        val older = chatHistory.dropLast(CHAT_RECENT_RAW_TURNS * 2)
        if (older.isEmpty()) return
        val summarySource = older.joinToString("\n") { (role, text) ->
            "${if (role == "USER") "사용자" else "AI"}: ${text.trim().take(120)}"
        }
        val (system, user) = LLMContextBuilder.chat(
            query = "[위 대화를 한국어 1줄(40자 이내)로 요약. 주제와 결론만. 접두사/따옴표 없이 본문만.]",
            historySummary = null,
            recentTurns = emptyList(),
            todayStr = "-", selectedDateStr = "-",
            todayTasks = emptyList(), selectedDateTasks = emptyList(),
            matchedDiaries = emptyList(), matchedTasks = emptyList(), matchedGoals = emptyList()
        )
        val fakeUser = buildString {
            if (!chatPriorSummary.isNullOrBlank()) append("[기존 요약] $chatPriorSummary\n\n")
            append("[요약 대상 대화]\n").append(summarySource.take(1200))
            append("\n\n위 대화를 1줄(40자 이내)로 압축해 주세요.")
        }
        val newSummary = runSinglePrompt(system, fakeUser, SamplerPresets.GENERATE_SHORT, maxTokens = 48)
            .trim()
            .let { stripCodeFences(it) }
            .let { stripPrefixes(it) }
            .let { if (it.length > 60) it.substring(0, 60) else it }
        chatPriorSummary = if (chatPriorSummary.isNullOrBlank()) newSummary
        else "${chatPriorSummary} | $newSummary"
        // 오래된 턴 제거
        chatHistory.subList(0, chatHistory.size - CHAT_RECENT_RAW_TURNS * 2).clear()
    }

    // ===== 내부 유틸 =====

    private suspend fun runSinglePrompt(
        systemPrompt: String,
        userPrompt: String,
        sampler: SamplerPresets,
        maxTokens: Int
    ): String {
        val conversation = try {
            engine.createConversation(ConversationConfig(samplerConfig = sampler.toSamplerConfig()))
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

    private fun normalizeEmotion(raw: String): String {
        if (raw.isBlank()) return "평온"
        val cleaned = raw
            .replace(".", "")
            .replace(",", "")
            .replace(":", "")
            .replace(":", "")
            .trim()
        val allowed = listOf("기쁨", "슬픔", "분노", "불안", "평온")
        allowed.firstOrNull { it == cleaned }?.let { return it }
        allowed.firstOrNull { cleaned.contains(it) }?.let { return it }
        return "평온"
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

    fun dispose() {
        try { chatConversation.getAndSet(null)?.close() } catch (_: Exception) {}
        chatHistory.clear()
        chatPriorSummary = null
        try {
            engine.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
    }
}

/**
 * 작업별 Sampler 프리셋. 분류는 결정적, 생성은 약간 다양성.
 */
enum class SamplerPresets {
    CLASSIFY { override fun toSamplerConfig() = SamplerConfig(topK = 10, topP = 0.5, temperature = 0.05) },
    GENERATE_SHORT { override fun toSamplerConfig() = SamplerConfig(topK = 25, topP = 0.7, temperature = 0.4) },
    GENERATE_MEDIUM { override fun toSamplerConfig() = SamplerConfig(topK = 30, topP = 0.75, temperature = 0.5) },
    GENERATE_LONG { override fun toSamplerConfig() = SamplerConfig(topK = 25, topP = 0.7, temperature = 0.2) };

    abstract fun toSamplerConfig(): SamplerConfig
}
