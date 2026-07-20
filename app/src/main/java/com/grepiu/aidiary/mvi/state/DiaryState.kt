package com.grepiu.aidiary.mvi.state

import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.data.model.extractPlainText
import com.grepiu.aidiary.data.repository.DiaryMeta
import com.grepiu.aidiary.data.repository.Goal
import com.grepiu.aidiary.data.repository.PlannerTask

/**
 * 일기 앱의 화면 단계(Phase)를 정의하는 열거형입니다.
 */
enum class DiaryPhase {
    SPLASH,  // 스플래시 화면 단계
    WELCOME, // 온보딩(약관 및 권한) 단계
    LIST,    // 일기 목록 및 대시보드 화면
    WRITE,   // 새 일기 작성 화면
    DETAIL   // 일기 상세 보기 및 AI 해석 결과 감상 화면
}

/**
 * MVI 단방향 데이터 흐름을 위한 불변(Immutable) 상태 데이터 클래스입니다.
 */
data class DiaryState(
    val phase: DiaryPhase = DiaryPhase.SPLASH,               // 현재 화면 단계
    /**
     * 저장된 일기 메타 목록 (본문 제외). v3.1 부터 페이지 단위로 채워지며
     * 본문이 필요해지면 [selectedDiary] 또는 [loadFullDiary] 인텐트로 별도 로드.
     * 2만건 이상에서도 인메모리 ≈ 4~6MB.
     */
    val diaries: List<DiaryMeta> = emptyList(),
    /** 기록이 1건 이상 있는 날짜(yyyy-MM-dd) 집합 — 달력 도트용. observeMetas 파생. */
    val diaryDates: Set<String> = emptySet(),
    /** 전체 기록 메타(경량, 페이지네이션 무관) — 전역 블로그/달력 통합 보기용. observeMetas 파생. */
    val allDiaryMetas: List<DiaryMeta> = emptyList(),
    /** 달력에서 선택한 날짜의 기록(페이지네이션 무관 조회 결과). */
    val selectedDateDiaries: List<DiaryMeta> = emptyList(),
    val selectedDiary: DiaryEntry? = null,                   // 상세 보기용 풀 DiaryEntry (lazy)
    
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
    val isExtractingModel: Boolean = false,                  // 다운로드 완료 후 압축 해제 진행 중
    val showDownloadNotice: Boolean = true,                 // 다운로드 안내 상단 배너 표시 여부
    val showSherpaDownloadNotice: Boolean = true,            // Sherpa 음성인식 모델 다운로드 안내 상단 배너 표시 여부
    /** Wi-Fi 경고가 어느 다운로드에서 발생했는지 추적 (null = none, "llm" = Gemma, "sherpa" = 음성인식) */
    val wifiWarningSource: String? = null,
    val isLowRamDevice: Boolean = false,                     // 저사양 기기(RAM 6GB 이하) 여부
    val showWifiWarning: Boolean = false,                    // Wi-Fi 경고 창 표시 여부
    val isSettingsOpen: Boolean = false,                     // 설정 페이지 화면 표시 여부
    val lastBackupDate: String? = null,                      // 마지막 성공 백업 일시
    val showLicenseDialog: Boolean = false,                  // 오픈소스 라이선스 고지 모달 표시 여부
    val isModelInitializing: Boolean = false,                // 모델 메모리 로드 진행 여부
    val isDeviceUnsupported: Boolean = false,                // 디바이스 온디바이스 AI 구동 지원 불가 여부
    val deviceUnsupportedReason: String? = null,             // 지원 불가 사유 텍스트
    val showDeviceUnsupportedDialog: Boolean = false,        // 기기 미지원 상세 안내 다이얼로그 표시 여부

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
    /** true: 특정 블록의 텍스트 오타·띄어쓰기 다듬기 진행 중 */
    val isProofreadingBlockId: String? = null,
    /** true: 특정 블록의 AI 꾸미기(색/굵게/이탤릭/밑줄/크기) 추천 진행 중 */
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
    /** Sherpa 음성 인식 언어. "auto" | "ko" | "en" | "ja" | "zh" | "yue" */
    val voiceLanguage: String = "auto",
    /** true: 사용자가 선택한 언어로 Sherpa 엔진을 재초기화하는 중 (UX 로딩 표시용) */
    val isChangingVoiceLanguage: Boolean = false,

    // 이미지 픽업/촬영 진행 상태
    val isImportingImage: Boolean = false,

    // 데이터 백업 및 복원 진행 상태
    val isBackupProcessing: Boolean = false,                 // 백업/복원 진행 여부
    val backupProgressMessage: String? = null,               // 로딩 중 노출할 메시지
    val backupSuccessMessage: String? = null,                // 백업/복원 성공 시 팝업에 노출할 메시지 (null이 아니면 팝업 표시)

    // 본문 AI 한글 번역
    /** true: 본문 → 한글 번역 LLM 호출 중 */
    val isTranslatingDraft: Boolean = false,
    /** LLM 이 생성한 한글 번역 결과. UI 에서 다이얼로그로 노출 후 ClearTranslatedDraft 로 소비 */
    val translatedDraft: String? = null,
    /** 개별 블록 번역 진행 상태 (블록 ID 셋) */
    val translatingBlockIds: Set<String> = emptySet(),

    // 온디바이스 AI 챗봇 대화 데이터 추가
    val chatMessages: List<ChatMessage> = emptyList(),       // 챗봇 대화 기록
    val isGeneratingChat: Boolean = false,                   // 챗봇 대답 생성 진행 여부

    // ===== 일기 검색 (FTS5) =====
    /** 현재 활성 검색어. 비어 있으면 검색 모드 아님. */
    val searchQuery: String = "",
    /** 검색 결과 로딩 중. */
    val isSearching: Boolean = false,

    // ===== 구버전 JSON → Room 자동 import =====
    /** 첫 실행 시 [diary_history.json] → Room 마이그레이션 진행 중 여부. */
    val isImportingLegacy: Boolean = false,
    /** import 진행률 0..1. Splash/첫 진입 화면에 progress bar 로 표시 가능. */
    val legacyImportProgress: Float = 0f,

    // ===== 페이지네이션 (v3.1) =====
    /** 페이지 당 메타 로드 개수. */
    val diaryPageSize: Int = 50,
    /** 현재까지 로드된 페이지 수. 1부터 시작. */
    val diaryPageCount: Int = 0,
    /** 추가 페이지 존재 여부. */
    val diaryHasMore: Boolean = true,
    /** 다음 페이지 로딩 중. */
    val isLoadingMoreDiaries: Boolean = false,
    /** 전체 일기 개수 (메타 페이지네이션 판단용). */
    val diaryTotalCount: Int = 0
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
