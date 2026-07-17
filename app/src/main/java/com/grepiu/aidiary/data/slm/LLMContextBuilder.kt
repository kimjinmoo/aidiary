package com.grepiu.aidiary.data.slm

import com.grepiu.aidiary.data.model.ContentBlock

/**
 * 온디바이스 2B 모델의 상하 문맥 추적 한계를 보완하기 위한
 * 통합 프롬프트 빌더.
 *
 * 모든 보조 액션(suggestTitle / proofread / decorate / classify / emotion /
 * briefing / chat / planner) 의 user prompt 를 동일한 계층 구조로 만들어
 *  - 일관된 도메인 컨텍스트
 *  - 인접(직전/직후) 블록 컨텍스트
 *  - 명시적 작업 정의 + 제약 + 예시
 * 를 강제한다.
 *
 * 2B 모델은 입력이 길어질수록 앞쪽 정보를 잊는 경향이 있어
 * "지금 해야 할 일" 과 "지금 입력" 은 항상 마지막에 배치한다.
 */
object LLMContextBuilder {

    // ===== 도메인 헤더 (모든 작업 공통) =====
    private const val DOMAIN_HEADER = "당신은 한국어 일기/플래너/장기목표 앱의 온디바이스 AI 어시스턴트입니다."

    // ===== 작업별 템플릿 =====

    /**
     * 제목 추천 (12자 이내). 세션 컨텍스트(본문 일부 + 현재 후보 제목) 를 함께 전달.
     */
    fun suggestTitle(
        content: String,
        currentTitle: String = "",
        contentTypeLabel: String = "일기"
    ): Pair<String, String> {
        val system = buildString {
            append(DOMAIN_HEADER).append(' ')
            append("사용자가 쓴 $contentTypeLabel 본문을 읽고 가장 핵심을 살린 한국어 제목을 1줄로 만드세요. ")
            append("12자 이내. 따옴표·접두사·이모지·마침표 없이 제목 텍스트만 출력하세요.")
        }
        val user = buildString {
            if (currentTitle.isNotBlank()) append("[현재 임시 제목] $currentTitle\n")
            append("[본문 일부]\n")
            append(truncateChars(content, 800))
            append("\n\n[지시] 위 본문을 대표하는 12자 이내 한국어 제목을 1줄로 답하세요. 제목:")
        }
        return system to user
    }

    /**
     * 글 타입 분류. 짧고 결정적이어야 하므로 단순화.
     */
    fun classifyContentType(content: String, currentTypeLabel: String? = null): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "주어진 본문을 보고 DIARY(개인 일기/회고), POST(정보/의견/아이디어), NOTE(짧은 메모) 중 하나로만 분류하세요. " +
                "설명 없이 분류 키 한 단어만 출력하세요."
        val user = buildString {
            if (!currentTypeLabel.isNullOrBlank()) append("[현재 선택된 타입] $currentTypeLabel\n")
            append("[본문]\n")
            append(truncateChars(content, 600))
            append("\n\n분류:")
        }
        return system to user
    }

    /**
     * 본문 다듬기. 인접 컨텍스트(직전/직후 블록) 를 같이 주입해
     * 문체/어조 일관성을 보존한다.
     */
    fun proofreadText(
        targetText: String,
        previousTail: String? = null,
        nextHead: String? = null,
        sessionTitle: String? = null
    ): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 한국어 문장 다듬기 전문가입니다. " +
                "본문의 띄어쓰기·맞춤법·문법을 바로잡고 가독성을 높이세요. " +
                "의미·톤은 유지하고, 인접 문맥과 어조가 매끄럽게 이어지게 다듬으세요. " +
                "설명·주석·접두사·따옴표 없이 다듬어진 본문만 출력하세요."
        val user = buildString {
            if (!sessionTitle.isNullOrBlank()) append("[전체 글 제목] $sessionTitle\n\n")
            appendAdjacentContext(previousTail, nextHead)
            append("[다듬을 본문]\n")
            append(targetText)
            append("\n\n다듬은 본문:")
        }
        return system to user
    }

    /**
     * 본문 꾸미기. 인접 컨텍스트를 함께 전달해 강조가 어색하지 않게 한다.
     */
    fun decorateText(
        targetText: String,
        previousTail: String? = null,
        nextHead: String? = null,
        sessionTitle: String? = null
    ): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 한국어 일기 가독성 편집자입니다. " +
                "본문에서 의미 있는 단어/구절 2~6개만 골라 색상·굵게·이탤릭·밑줄·크기를 조합해 강조하세요. " +
                "(1) keyword 는 본문에 실제로 등장하는 문자열, " +
                "(2) 색상은 #D32F2F #E65100 #F9A825 #2E7D32 #0277BD #6A1B9A 중 하나, " +
                "(3) size 는 14/15/18/22/26 중 하나, " +
                "(4) bold/italic/underline 는 true/false, " +
                "(5) 동일 단어/구절이 인접 문맥과 의미 충돌하면 강조하지 마세요. " +
                "JSON 배열만 출력하고 다른 텍스트는 쓰지 마세요."
        val user = buildString {
            if (!sessionTitle.isNullOrBlank()) append("[전체 글 제목] $sessionTitle\n\n")
            appendAdjacentContext(previousTail, nextHead)
            append("[꾸밀 본문]\n")
            append(targetText)
            append("\n\n꾸미기 제안 JSON:")
        }
        return system to user
    }

    /**
     * 한국어 번역. (한국어면 다듬기, 외국어면 번역)
     */
    fun translateToKorean(content: String, sourceLangHint: String? = null): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 다국어 ↔ 한국어 번역가입니다. " +
                "본문이 한국어라면 자연스러운 한국어 문장으로 다듬어 그대로 반환하고, " +
                "외국어라면 의미/톤/뉘앙스를 살린 자연스러운 한국어로 번역하세요. " +
                "원문에 없는 내용을 추가하지 마세요. 마크다운·이모지·접두사·따옴표 없이 번역문만 출력하세요."
        val user = buildString {
            if (!sourceLangHint.isNullOrBlank()) append("[원문 언어 힌트] $sourceLangHint\n")
            append("[원문]\n")
            append(truncateChars(content, 1500))
            append("\n\n한국어 결과:")
        }
        return system to user
    }

    /**
     * 저장 시 1회 호출로 (분류 + 감정) 을 함께 받는 통합 프롬프트.
     * 두 번 LLM 을 부르던 기존 흐름을 1회로 줄여 일관성과 속도 모두 개선.
     */
    fun classifyAndDetectEmotion(
        title: String,
        content: String,
        dateString: String,
        currentTypeLabel: String
    ): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 한국어 일기 분석기입니다. " +
                "본문을 보고 (1) 글 타입, (2) 핵심 감정 을 동시에 답하세요. " +
                "글 타입은 DIARY/POST/NOTE 셋 중 하나, " +
                "감정은 기쁨/슬픔/분노/불안/평온 다섯 단어 중 하나입니다. " +
                "오직 JSON 한 줄로만 답하세요. 다른 텍스트/설명/마크다운은 절대 쓰지 마세요."
        val user = buildString {
            append("[일기 메타]\n")
            append("- 날짜: $dateString\n")
            append("- 제목: ${title.ifBlank { "(제목 없음)" }}\n")
            append("- 사용자가 미리 선택한 타입: $currentTypeLabel\n\n")
            append("[본문]\n")
            append(truncateChars(content, 1500))
            append("\n\n[출력 형식 (정확히 한 줄 JSON)]\n")
            append("{\"type\":\"DIARY|POST|NOTE\",\"emotion\":\"기쁨|슬픔|분노|불안|평온\"}")
        }
        return system to user
    }

    /**
     * 단일 감정 분류 (저장 외 다른 곳에서 단독 호출용).
     */
    fun detectEmotion(title: String, content: String, dateString: String): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 한국어 일기 감정 분류기입니다. " +
                "본문을 읽고 가장 가까운 감정 한 단어만 답하세요. " +
                "기쁨/슬픔/분노/불안/평온 다섯 단어 중에서만 선택하고, " +
                "설명·이유·접두사·따옴표 없이 단어만 출력하세요."
        val user = buildString {
            append("[일기]\n")
            append("- 날짜: $dateString\n")
            append("- 제목: ${title.ifBlank { "제목 없음" }}\n")
            append("- 내용: ${truncateChars(content, 1500)}\n\n")
            append("감정 한 단어:")
        }
        return system to user
    }

    /**
     * 플래너 할 일 1건 추천.
     */
    fun suggestPlannerTask(contextBlock: String): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 한국어 스마트 플래너 코치 AI입니다. " +
                "주어진 컨텍스트(날짜, 시간, 장소, 반복, 기존 계획, 장기 목표, 최근 일기) 를 종합해 " +
                "오늘의 플래너에 어울리는 한국어 할 일 1건을 추천하세요. " +
                "구체적이고 실행 가능해야 하며 기존 계획과 중복되지 않아야 합니다. " +
                "1줄 한국어 텍스트만. 따옴표·접두사·이모지·번호·마침표 없이 본문만 출력하세요."
        val user = "$contextBlock\n\n추천할 1개의 계획:"
        return system to user
    }

    /**
     * 탭별 브리핑. 추세 vs 현재 비교를 명시적으로 요청.
     */
    fun briefing(tabKey: String, contextBlock: String): Pair<String, String> {
        val (role, focus) = when (tabKey) {
            "DIARY" -> "한국어 일기 코치" to "(1) 최근 감정/분위기 추세, (2) 자주 등장하는 주제, (3) 지금 사용자에게 줄 한 가지 제안/격려"
            "PLANNER" -> "한국어 플래너 코치" to "(1) 오늘/이번 주 계획 분포, (2) 시간 밀도, (3) 지금 사용자에게 줄 한 가지 실행 제안"
            "GOALS" -> "한국어 목표 코치" to "(1) 전체 진행률과 활성 목표 요약, (2) 최근 일기/플래너와의 정합성, (3) 사용자에게 줄 한 가지 제안/격려"
            else -> "한국어 코치" to "주어진 데이터를 요약하고 1가지 제안"
        }
        val system = "$DOMAIN_HEADER " + "$role 입니다. " +
                "컨텍스트에 '직전 브리핑/이전 추세' 가 함께 주어지면 현재와 비교해 변화/개선점/주의점을 짚어주세요. " +
                "$focus. " +
                "한국어 2~4줄 한 단락. 마크다운·이모지·번호·접두사 없이 자연스러운 문장만 출력하세요."
        val user = "$contextBlock\n\n위 데이터를 분석해 1개의 단락으로 한국어 브리핑을 작성해 주세요."
        return system to user
    }

    /**
     * 챗봇(RAG) 프롬프트. 시스템 규칙 + (이전 요약) + (최근 원본 턴) + (오늘/선택일 + 매칭 컨텍스트) + (현재 질문) 구조.
     */
    fun chat(
        query: String,
        historySummary: String?,
        recentTurns: List<Pair<String, String>>,
        todayStr: String,
        selectedDateStr: String,
        todayTasks: List<String>,
        selectedDateTasks: List<String>,
        matchedDiaries: List<String>,
        matchedTasks: List<String>,
        matchedGoals: List<String>
    ): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 사용자의 일기/일정/목표를 기억하는 다이어리 AI 비서입니다. " +
                "아래 [컨텍스트] 와 [이전 대화] 에서만 근거를 가져와 답하고, " +
                "절대 상상으로 일정/일기를 지어내지 마세요. " +
                "오늘/선택일의 할 일이 비어 있으면 비어 있다고 솔직히 말하세요. " +
                "100% 한국어, '~해요'체, 간결하게."
        val user = buildString {
            if (!historySummary.isNullOrBlank()) {
                append("[이전 대화 요약]\n")
                append(historySummary.trim()).append("\n\n")
            }
            if (recentTurns.isNotEmpty()) {
                append("[최근 대화]\n")
                recentTurns.forEach { (role, text) ->
                    append(if (role == "USER") "사용자: " else "AI: ")
                    append(text.trim().take(500))
                    append('\n')
                }
                append('\n')
            }
            append("[컨텍스트]\n")
            append("- 기준 날짜 (오늘): $todayStr\n")
            append("- 선택된 캘린더 날짜: $selectedDateStr\n\n")
            append("■ 오늘($todayStr)의 실제 계획된 할 일:\n")
            if (todayTasks.isEmpty()) append("  - (등록된 할 일 없음)\n") else todayTasks.forEach { append("  - $it\n") }
            append('\n')
            if (selectedDateStr != todayStr) {
                append("■ 선택한 날짜($selectedDateStr)의 실제 계획된 할 일:\n")
                if (selectedDateTasks.isEmpty()) append("  - (등록된 할 일 없음)\n") else selectedDateTasks.forEach { append("  - $it\n") }
                append('\n')
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
            append("[현재 질문]\n").append(query).append("\n\n[답변]")
        }
        return system to user
    }

    /**
     * 목표 달성 축하 메시지.
     */
    fun congratulate(goalText: String): Pair<String, String> {
        val system = "$DOMAIN_HEADER " +
                "당신은 따뜻하게 사용자의 장기 목표 성취를 격려하는 AI 멘토입니다. " +
                "줄바꿈 없이 1줄, 정중하고 깊은 울림을 주는 존댓말 응원 메시지만 출력하세요."
        val user = "[달성한 목표: $goalText] 를 사용자가 마쳤습니다. 1줄 축하 멘트를 작성해 주세요."
        return system to user
    }

    /**
     * 직전 N 턴 + 그 이전 요약을 결합한 멀티턴 chat 컨텍스트 빌더.
     * 2B 모델(1024 토큰) 환경에서 토큰 폭주를 막기 위해 각 turn 200자로 제한한다.
     *
     * @param priorSummary 이전 N+1 번째 턴 이전의 누적 요약 (이미 60자 이내로 압축됨)
     * @param allTurns 전체 (USER,AI) 페어
     * @param maxRecentRaw 최근 raw 로 유지할 최대 (사용자,AI) 페어 수
     */
    fun buildChatMultiTurnContext(
        priorSummary: String?,
        allTurns: List<Pair<String, String>>,
        maxRecentRaw: Int = 1
    ): String {
        if (allTurns.isEmpty() && priorSummary.isNullOrBlank()) return ""
        val sb = StringBuilder()
        if (!priorSummary.isNullOrBlank()) {
            sb.append("[이전 대화 요약]\n").append(priorSummary.trim().take(120)).append("\n\n")
        }
        val olderForSummary = if (allTurns.size > maxRecentRaw) allTurns.dropLast(maxRecentRaw) else emptyList()
        if (olderForSummary.isNotEmpty() && priorSummary.isNullOrBlank()) {
            // priorSummary 가 비어 있으면 한 줄 fallback 요약을 만들어 둔다
            sb.append("[이전 대화 요약]\n")
            olderForSummary.takeLast(4).forEach { (role, text) ->
                sb.append(if (role == "USER") "사용자: " else "AI: ")
                sb.append(text.trim().take(80))
                sb.append('\n')
            }
            sb.append("\n")
        }
        val recent = allTurns.takeLast(maxRecentRaw)
        if (recent.isNotEmpty()) {
            sb.append("[최근 대화]\n")
            recent.forEach { (role, text) ->
                sb.append(if (role == "USER") "사용자: " else "AI: ")
                sb.append(text.trim().take(200))
                sb.append('\n')
            }
        }
        return sb.toString().trim()
    }

    // ===== 헬퍼 =====

    private fun StringBuilder.appendAdjacentContext(previousTail: String?, nextHead: String?) {
        val prev = previousTail?.trim().orEmpty()
        val next = nextHead?.trim().orEmpty()
        if (prev.isBlank() && next.isBlank()) return
        append("[인접 문맥 — 어조/주제 일관성 참고용]\n")
        if (prev.isNotBlank()) append("- 직전 블록 끝: ...${prev.takeLast(120)}\n")
        if (next.isNotBlank()) append("- 직후 블록 시작: ${next.take(120)}...\n")
        append('\n')
    }

    /**
     * 글자 수 기준 안전 truncate. 토큰 한도와 무관하게 2B 모델이
     * 한 번에 잘 소화하는 한국어 분량을 보수적으로 자른다.
     */
    fun truncateChars(s: String, maxChars: Int): String {
        if (s.length <= maxChars) return s
        val head = s.substring(0, (maxChars * 2 / 3).coerceAtLeast(0))
        val tail = s.substring(s.length - (maxChars / 3).coerceAtLeast(0))
        return "$head\n…(중간 생략)…\n$tail"
    }

    /**
     * [ContentBlock] 리스트에서 특정 블록의 직전/직후 텍스트를 추출.
     * 인접 컨텍스트 주입용 헬퍼.
     */
    fun extractAdjacentContext(
        blocks: List<ContentBlock>,
        targetId: String,
        prevChars: Int = 120,
        nextChars: Int = 120
    ): AdjacentContext {
        val idx = blocks.indexOfFirst { it.id == targetId }
        if (idx < 0) return AdjacentContext(null, null)
        val prevText = blocks.subList(0, idx)
            .mapNotNull { (it as? ContentBlock.TextBlock)?.text ?: (it as? ContentBlock.HeadingBlock)?.text ?: (it as? ContentBlock.QuoteBlock)?.text }
            .joinToString(" ")
            .let { if (it.length > prevChars) it.takeLast(prevChars) else it }
        val nextText = blocks.subList(idx + 1, blocks.size)
            .mapNotNull { (it as? ContentBlock.TextBlock)?.text ?: (it as? ContentBlock.HeadingBlock)?.text ?: (it as? ContentBlock.QuoteBlock)?.text }
            .joinToString(" ")
            .let { if (it.length > nextChars) it.take(nextChars) else it }
        return AdjacentContext(prevText.takeIf { it.isNotBlank() }, nextText.takeIf { it.isNotBlank() })
    }

    data class AdjacentContext(val previousTail: String?, val nextHead: String?)
}
