package com.grepiu.aidiary.mvi.state

import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TitleStyle
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
    val draftTitle: String = "",                            // 상단 입력란의 글 제목 (키보드/AI 모두 이 필드로 갱신)
    val draftBlocks: List<ContentBlock> = emptyList(),      // 블록 기반 작성 본문 (HeadingBlock 은 본문 내 '섹션 제목' 용도)
    val draftTitleStyle: TitleStyle = TitleStyle.Default,   // 제목 스타일 (색상/크기)
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

    // 저장 시 AI TAG 자동 생성 진행 상태
    val isGeneratingAnalysis: Boolean = false,               // 저장 흐름에서 AI 분석 + TAG AI 블록 생성이 진행 중인지 여부
    /** 저장 시 AI 글 타입 재확인 분류가 진행 중인지 여부 (사용자 응답 대기 전 1단계) */
    val isClassifyingTypeOnSave: Boolean = false,
    /** 저장 직전 AI 가 기존 선택과 다른 타입을 제안한 경우, 사용자 응답을 기다리는 1회성 상태. null 이면 다이얼로그 미표시 */
    val pendingContentTypeChange: PendingContentTypeChange? = null,

    // 작성 보조 AI 액션 상태
    /** true: 제목 자동 생성 중 */
    val isSuggestingTitle: Boolean = false,
    /** true: 본문 분석 → 타입 분류 중 */
    val isClassifyingType: Boolean = false,
    /** true: 특정 블록의 텍스트 다듬기(교정/띄어쓰기) 진행 중 */
    val isProofreadingBlockId: String? = null,
    /** true: 특정 블록의 강조(색/굵게) 추천 진행 중 */
    val isDecoratingBlockId: String? = null,
    /** true: 플래너 할 일명 AI 추천 진행 중 */
    val isSuggestingPlannerTask: Boolean = false,
    /** AI 가 추천한 플래너 할 일명 (1회성, UI 에서 소비 후 클리어) */
    val suggestedPlannerTaskText: String? = null,

    // 탭별 AI 브리핑 (기록/플래너/목표)
    /** 기록 탭 AI 브리핑 결과 텍스트 */
    val diaryBriefing: String? = null,
    /** 플래너 탭 AI 브리핑 결과 텍스트 */
    val plannerBriefing: String? = null,
    /** 목표 탭 AI 브리핑 결과 텍스트 */
    val goalsBriefing: String? = null,
    /** true: 기록 탭 브리핑 생성 중 */
    val isBriefingDiary: Boolean = false,
    /** true: 플래너 탭 브리핑 생성 중 */
    val isBriefingPlanner: Boolean = false,
    /** true: 목표 탭 브리핑 생성 중 */
    val isBriefingGoals: Boolean = false,

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
     * 세션 제목 = 상단 입력란의 [draftTitle]. 저장 차단 트리거에 사용.
     * (예전에는 첫 HeadingBlock 의 텍스트였으나, 상단 별도 입력란으로 분리됨)
     */
    val sessionTitle: String
        get() = draftTitle.trim()

    /**
     * 본문 블록 내에 '섹션 제목' 으로 쓰인 HeadingBlock 존재 여부.
     * 메인 제목은 더 이상 HeadingBlock 이 아니므로, 이 플래그는 본문 섹션 제목을 위한 용도로만 사용.
     */
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

/**
 * 저장 시 AI 가 추천한 글 타입이 사용자 선택과 다를 때,
 * 사용자의 응답을 받기 전까지 보관하는 1회성 데이터.
 *
 * @param currentType 사용자가 현재 선택해 둔 글 타입
 * @param suggestedType AI 가 본문 분석으로 추천한 글 타입
 */
data class PendingContentTypeChange(
    val currentType: ContentType,
    val suggestedType: ContentType
)
