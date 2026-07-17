package com.grepiu.aidiary.mvi.state

import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.extractPlainText
import com.grepiu.aidiary.data.repository.Goal
import com.grepiu.aidiary.data.repository.PlannerTask

/**
 * 일기 앱의 화면 단계(Phase)를 정의하는 열거형입니다.
 */
enum class DiaryPhase {
    SPLASH, // 스플래시 화면 단계
    LIST,   // 일기 목록 및 대시보드 화면
    WRITE,  // 새 일기 작성 화면
    DETAIL  // 일기 상세 보기 및 AI 해석 결과 감상 화면
}

/**
 * MVI 단방향 데이터 흐름을 위한 불변(Immutable) 상태 데이터 클래스입니다.
 */
data class DiaryState(
    val phase: DiaryPhase = DiaryPhase.SPLASH,               // 현재 화면 단계
    val diaries: List<DiaryEntry> = emptyList(),             // 저장된 일기 목록
    val selectedDiary: DiaryEntry? = null,                   // 상세 보기용 선택된 일기
    
    // 플래너 및 목표 기록 관련 상태 추가
    val goals: List<Goal> = emptyList(),                     // 사용자 목표 목록
    val plannerTasks: List<PlannerTask> = emptyList(),       // 사용자 할 일 목록
    val selectedDateString: String = "",                     // 선택된 날짜 (포맷: yyyy-MM-dd)
    val activeTab: String = "DIARY",                         // 현재 활성화된 탭 (DIARY, PLANNER, GOALS)

    // 작성/수정 중인 임시 일기 상태
    val draftBlocks: List<ContentBlock> = emptyList(),      // 블록 기반 작성 본문 (HeadingBlock 첫 항목이 세션 제목)
    val draftEmotion: String = "Neutral",
    val draftContentType: ContentType = ContentType.DIARY,

    // 온디바이스 AI 모델 및 다운로드 관련 상태
    val isModelReady: Boolean = false,                       // 모델 사용 가능 여부
    val isDownloadingModel: Boolean = false,                 // 다운로드 진행 여부
    val modelDownloadProgress: Float = 0f,                   // 다운로드 진행률 (0.0 ~ 1.0f)
    val modelDownloadSizeText: String? = null,               // 다운로드 용량 표기
    val showDownloadNotice: Boolean = false,                 // 다운로드 안내 팝업 표시 여부
    val isLowRamDevice: Boolean = false,                     // 저사양 기기(RAM 6GB 이하) 여부
    val showWifiWarning: Boolean = false,                    // Wi-Fi 경고 창 표시 여부
    val isModelInitializing: Boolean = false,                // 모델 메모리 로드 진행 여부
    val isDeviceUnsupported: Boolean = false,                // 디바이스 온디바이스 AI 구동 지원 불가 여부
    val deviceUnsupportedReason: String? = null,             // 지원 불가 사유 텍스트

    // AI 일기 분석 진행 및 스트리밍 결과 상태
    val aiAnalysisText: String? = null,                      // 실시간 스트리밍 분석 텍스트
    val isGeneratingAnalysis: Boolean = false,               // AI 분석 추론 진행 여부

    // 작성 보조 AI 액션 상태
    /** true: 제목 자동 생성 중 */
    val isSuggestingTitle: Boolean = false,
    /** true: 본문 분석 → 타입 분류 중 */
    val isClassifyingType: Boolean = false,
    /** true: 특정 블록의 텍스트 다듬기(교정/띄어쓰기) 진행 중 */
    val isProofreadingBlockId: String? = null,
    /** true: 특정 블록의 강조(색/굵게) 추천 진행 중 */
    val isDecoratingBlockId: String? = null,

    // Sherpa 음성 녹음 및 변환 상태
    val isSherpaModelReady: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val recordingVolume: Float = 0f,
    val isTranscribing: Boolean = false,

    // 이미지 픽업/촬영 진행 상태
    val isImportingImage: Boolean = false,

    // 온디바이스 AI 챗봇 대화 데이터 추가
    val chatMessages: List<ChatMessage> = emptyList(),       // 챗봇 대화 기록
    val isGeneratingChat: Boolean = false                    // 챗봇 대답 생성 진행 여부
) {
    /**
     * AI 분석에 넘길 평문. 블록에서 자동 추출.
     */
    val draftPlainText: String
        get() = draftBlocks.extractPlainText()

    /**
     * 세션 제목으로 사용되는 첫 HeadingBlock 의 텍스트.
     * 없으면 빈 문자열 (저장 차단 트리거에 사용).
     */
    val sessionTitle: String
        get() = (draftBlocks.firstOrNull { it is ContentBlock.HeadingBlock } as? ContentBlock.HeadingBlock)
            ?.text
            ?.trim()
            .orEmpty()

    /** 첫 HeadingBlock 존재 여부 (AddBlockBar 의 제목 칩 활성/비활성 가드). */
    val hasHeadingBlock: Boolean
        get() = draftBlocks.any { it is ContentBlock.HeadingBlock }
}

/**
 * 챗봇 대화 메시지를 표현하는 데이터 클래스입니다.
 */
data class ChatMessage(
    val sender: String, // "USER" 또는 "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
