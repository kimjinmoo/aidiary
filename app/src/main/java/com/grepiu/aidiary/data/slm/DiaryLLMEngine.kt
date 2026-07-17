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

        /** 챗봇 멀티턴 윈도우: 최근 raw 로 유지할 (user,ai) 페어 수. 2B 모델은 1 페어가 안전. */
        const val CHAT_RECENT_RAW_TURNS = 1

        /** 챗봇 이력 누적 임계치. 이보다 커지면 앞쪽을 1줄 요약으로 압축. */
        const val CHAT_SUMMARY_TRIGGER_TURNS = 4

        /**
         * 챗봇 user prompt 안전 상한 (문자수). 1024 토큰 한도에서
         *  - system 200자 / 출력 예약 200 토큰 / 여유 100 토큰 제외 →
         *  입력 가능 ≈ 700 토큰 ≈ 1000~1100자 (한국어 1.5 chars/token).
         */
        const val MAX_CHAT_PROMPT_CHARS = 1100

        /**
         * 챗봇 Conversation 내부 누적 토큰 폭주 방지용 리셋 주기.
         * N 턴마다 close + 재생성하여 LiteRT-LM Conversation 의 내부 history 를 비운다.
         */
        const val CHAT_CONVERSATION_RESET_TURNS = 4

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

    private var chatTurnCounter: Int = 0

    /**
     * RAG 컨텍스트를 주입한 멀티턴 챗봇 응답. 동일 Conversation 을 재사용하여
     * 최근 raw N턴 + 그 이전 요약을 컨텍스트로 보존한다.
     *
     * v2.1 안전장치:
     *  - 매 N 턴마다 Conversation 을 close + 재생성하여 LiteRT-LM 내부 history 누적 방지
     *  - user prompt 총 문자수가 [MAX_CHAT_PROMPT_CHARS] 를 넘으면
     *    1) 멀티턴 컨텍스트 제거 → 2) RAG 컨텍스트 절반 축소 → 3) 그래도 안되면 query 만 전송
     *    순으로 점진적 드롭하여 1024 토큰 한도 초과 방지
     *  - 각 전략은 fresh conversation 으로 시도 (이전 실패의 internal state 격리)
     *  - 토큰이 이미 방출된 후 실패하면 부분 결과로 수락 (재시도 시 토큰이 중복 노출되지 않음)
     *  - 모든 전략 실패 시에도 `onTokenReceived` 로 안전 메시지를 보내 UI 가 "생각 중..." 에 멈추지 않게 한다
     *
     * @param contextBlock [DiaryLLMEngine.buildChatContextBlock] 으로 빌드된 RAG 컨텍스트
     * @param userQuery 현재 사용자 질문
     * @return AI 응답
     */
    suspend fun generateChatResponse(
        contextBlock: String,
        userQuery: String
    ): String = withContext(Dispatchers.Default) {
        val system = "당신은 사용자의 일기 내용과 일정을 기억하는 다이어리 인공지능 비서예요. " +
                "제공되는 [컨텍스트] 와 [이전 대화] 에 기반하여 사용자의 질문에만 정직하게 대답해야 해요. " +
                "**필독 규칙**: 절대로 사용자의 일정이나 일기를 상상해서 지어내어 거짓으로 답변하지 마세요. " +
                "오늘의 할 일 목록에 해당하는 일정이 없다면, 가상 일정을 만들지 말고 '오늘 계획된 일정이 등록되어 있지 않아요'라고 솔직하게 대답하세요. " +
                "반드시 100% 한국어로만 답변하고, '~해요'체로 상냥하고 간결하게 대답하세요."

        // 1) 주기적 Conversation 리셋 (내부 history 누적 방지)
        if (chatTurnCounter >= CHAT_CONVERSATION_RESET_TURNS) {
            try { chatConversation.getAndSet(null)?.close() } catch (_: Exception) {}
            chatTurnCounter = 0
        }
        chatTurnCounter++

        // 2) 3단계 점진 드롭 (full → RAG only → query only)
        val strategies = listOf(
            ContextStrategy.FULL,
            ContextStrategy.RAG_ONLY,
            ContextStrategy.QUERY_ONLY
        )
        var lastError: Throwable? = null

        for ((idx, strategy) in strategies.withIndex()) {
            val userPrompt = buildChatUserPrompt(strategy, contextBlock, userQuery)
            val totalChars = system.length + userPrompt.length
            if (totalChars > MAX_CHAT_PROMPT_CHARS && strategy == ContextStrategy.QUERY_ONLY) {
                Log.w(TAG, "Chat prompt overflow: $totalChars > $MAX_CHAT_PROMPT_CHARS chars (query=${userQuery.length})")
                val overflowMsg = "질문이 너무 길거나 컨텍스트가 너무 커서 답변할 수 없어요. 짧게 다시 질문해 주세요."
                onTokenReceived?.invoke(overflowMsg, true)
                return@withContext overflowMsg
            }
            val prompt = "<start_of_turn>system\n$system<end_of_turn>\n" +
                    "<start_of_turn>user\n$userPrompt<end_of_turn>\n" +
                    "<start_of_turn>model\n"
            Log.d(TAG, "Chat prompt strategy=$strategy chars=$totalChars")

            // 각 전략은 fresh conversation 으로 시도 (이전 실패의 internal state 격리)
            if (idx > 0) {
                try { chatConversation.getAndSet(null)?.close() } catch (_: Exception) {}
            }
            val conv = ensureChatConversation()

            val builder = StringBuilder()
            var emittedAny = false
            try {
                conv.sendMessageAsync(prompt).collect { message ->
                    val token = message.toString()
                    builder.append(token)
                    emittedAny = true
                    onTokenReceived?.invoke(token, false)
                }
                onTokenReceived?.invoke("", true)
                val final = builder.toString()
                // 성공 시에만 history 갱신
                chatHistory.add("USER" to userQuery)
                chatHistory.add("AI" to final)
                if (chatHistory.size > CHAT_SUMMARY_TRIGGER_TURNS) {
                    compressChatHistory()
                }
                return@withContext final
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Chat prompt failed at strategy=$strategy (${e.message}); trying next fallback")
                if (emittedAny) {
                    // 토큰이 이미 노출된 상태면 부분 결과로 수락하고 종료
                    onTokenReceived?.invoke("", true)
                    val partial = builder.toString()
                    if (partial.isNotBlank()) {
                        chatHistory.add("USER" to userQuery)
                        chatHistory.add("AI" to partial)
                    }
                    return@withContext partial
                }
                // 아니면 다음 전략으로 진행
            }
        }

        // 모든 전략 실패 (어느 것도 토큰 방출 못함) → 컨버세이션 리셋 + 안전 메시지
        Log.e(TAG, "Error generating chat response (backend=$backendType)", lastError)
        try { chatConversation.getAndSet(null)?.close() } catch (_: Exception) {}
        chatTurnCounter = 0
        val errorMsg = "[오류가 발생하여 답변할 수 없어요. 잠시 후 다시 시도해 주세요]"
        onTokenReceived?.invoke(errorMsg, true)
        errorMsg
    }

    private enum class ContextStrategy { FULL, RAG_ONLY, QUERY_ONLY }

    private fun buildChatUserPrompt(
        strategy: ContextStrategy,
        contextBlock: String,
        userQuery: String
    ): String {
        if (strategy == ContextStrategy.QUERY_ONLY) {
            return "[현재 질문]\n$userQuery\n\n[답변]"
        }
        val rollingContext = if (strategy == ContextStrategy.FULL) {
            LLMContextBuilder.buildChatMultiTurnContext(
                priorSummary = chatPriorSummary,
                allTurns = chatHistory.takeLast(CHAT_RECENT_RAW_TURNS * 2),
                maxRecentRaw = CHAT_RECENT_RAW_TURNS
            )
        } else ""
        return buildString {
            if (rollingContext.isNotBlank()) append(rollingContext).append("\n\n")
            append(contextBlock).append("\n\n")
            append("[현재 질문]\n").append(userQuery).append("\n\n[답변]")
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
        try { chatConversation.getAndSet(null)?.close() } catch (_: Exception) {}
        chatConversation.set(null)
        chatHistory.clear()
        chatPriorSummary = null
        chatTurnCounter = 0
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
        chatTurnCounter = 0
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
