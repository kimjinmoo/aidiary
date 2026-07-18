package com.grepiu.aidiary.mvi.intent

import android.net.Uri
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.mvi.state.DiaryPhase

/**
 * 사용자가 유발하는 의도(Action/Event)들을 정의한 봉인 인터페이스입니다.
 */
sealed interface DiaryIntent {
    data object LoadDiaries : DiaryIntent
    data object StartDownload : DiaryIntent
    data object CancelDownload : DiaryIntent
    data class NavigateTo(
        val phase: DiaryPhase,
        val selectedDiary: DiaryEntry? = null,
        /** Write 화면 진입 시 사전 선택할 콘텐츠 타입 (null = 기본값 유지) */
        val initialContentType: ContentType? = null
    ) : DiaryIntent
    data class UpdateDraft(
        val content: String? = null,
        val emotion: String? = null
    ) : DiaryIntent
    data object SaveDiary : DiaryIntent
    data class DeleteDiary(val id: String) : DiaryIntent
    /**
     * 저장 시 AI 가 추천한 글 타입(suggestedType) 으로 변경하고 저장을 이어서 진행합니다.
     * [DiaryState.pendingContentTypeChange] 가 non-null 일 때만 사용.
     */
    data class ConfirmContentTypeChange(val newType: ContentType) : DiaryIntent
    /** 현재 사용자가 선택한 글 타입을 그대로 유지하고 저장을 이어서 진행합니다. */
    data object KeepCurrentContentTypeAndSave : DiaryIntent
    /** AI 추천을 무시하고 저장 자체를 취소합니다 (다이얼로그만 닫음). */
    data object CancelContentTypeChange : DiaryIntent
    /** 작성 중인 글의 콘텐츠 타입(일기/포스트/메모)을 변경합니다. */
    data class UpdateDraftType(val contentType: ContentType) : DiaryIntent
    /** 작성 중인 글 제목의 스타일(색상/크기)을 변경합니다. */
    data class UpdateDraftTitleStyle(val style: TitleStyle) : DiaryIntent
    /** 상단 제목 입력란의 텍스트를 갱신합니다. */
    data class UpdateDraftTitle(val text: String) : DiaryIntent
    data class ShowDownloadNotice(val show: Boolean) : DiaryIntent
    data class ShowWifiWarning(val show: Boolean) : DiaryIntent
    data object StartRecording : DiaryIntent
    data object StopRecording : DiaryIntent
    /**
     * Sherpa 음성 인식 언어를 변경합니다.
     * @param language "auto" | "ko" | "en" | "ja" | "zh" | "yue"
     */
    data class UpdateVoiceLanguage(val language: String) : DiaryIntent
    /** 본문 평문을 AI 로 한국어로 번역하도록 요청. 결과는 [DiaryState.translatedDraft] 에 1회성 저장 */
    data object TranslateDraftToKorean : DiaryIntent
    /** 번역 결과를 현재 본문으로 적용. 본문 첫 TextBlock 의 text 를 번역문으로 교체 */
    data object ApplyTranslatedDraft : DiaryIntent
    /** 번역 결과 다이얼로그를 닫고 1회성 상태를 비움 */
    data object ClearTranslatedDraft : DiaryIntent
    /** 본문 평문을 시스템 클립보드에 복사 */
    data object CopyDraftToClipboard : DiaryIntent

    // ===== 블록 기반 콘텐츠 =====
    /** 현재 위치 정보를 가져와 LocationBlock 을 추가하도록 요청합니다. */
    data object RequestLocationBlock : DiaryIntent
    /** 새 블록을 마지막에 추가합니다. */
    data class AddBlock(val block: ContentBlock) : DiaryIntent
    /** 특정 위치에 새 블록을 삽입합니다. */
    data class InsertBlock(val index: Int, val block: ContentBlock) : DiaryIntent
    /** 블록의 텍스트 + 서식을 갱신합니다 (Heading/Text/Quote). */
    data class UpdateBlockText(
        val blockId: String,
        val text: String,
        val formatting: com.grepiu.aidiary.data.model.TextFormatting
    ) : DiaryIntent
    /** 이미지 캡션을 갱신합니다. */
    data class UpdateBlockCaption(val blockId: String, val caption: String) : DiaryIntent
    /** 블록을 제거합니다. 이미지가 포함되어 있으면 내부 저장소의 파일도 정리합니다. */
    data class RemoveBlock(val blockId: String) : DiaryIntent
    /** 블록을 위/아래로 이동합니다. direction = -1 (위) / +1 (아래) */
    data class MoveBlock(val blockId: String, val direction: Int) : DiaryIntent
    /** 개별 블록의 텍스트를 클립보드에 복사합니다. */
    data class CopyBlockToClipboard(val blockId: String) : DiaryIntent
    /** 개별 블록의 텍스트를 AI로 한국어 번역합니다. */
    data class TranslateBlock(val blockId: String) : DiaryIntent

    // ===== 이미지 픽업/촬영 =====
    /** PhotoPicker 등 외부에서 받은 이미지 URI 목록(1개 이상) 을 내부 저장소로 가져와 각각 ImageBlock 으로 추가합니다. */
    data class ImagesPicked(val uris: List<Uri>) : DiaryIntent
    /**
     * 카메라 촬영이 완료된 이미지의 [content://] URI(FileProvider).
     * ContentResolver 로 스트림을 열어 내부 저장소로 복사합니다.
     */
    data class CameraImageCaptured(val capturedUri: Uri) : DiaryIntent
    /**
     * 비디오 picker 에서 사용자가 선택한 [Uri] 1개. 내부 저장소로 복사한 뒤
     * [com.grepiu.aidiary.data.slm.VideoFormatDetector] 로 3D 포맷 여부를 자동 감지해
     * 3D 이면 [ContentBlock.SpatialMediaBlock] (VIDEO), 2D 이면 토스트로 안내.
     */
    data class VideoPicked(val uri: Uri) : DiaryIntent

    // ===== 표 =====
    /** 표의 (row, col) 셀 텍스트를 갱신합니다. */
    data class UpdateTableCell(
        val blockId: String,
        val row: Int,
        val col: Int,
        val text: String
    ) : DiaryIntent
    /** 표에 빈 행을 마지막에 추가합니다. */
    data class AddTableRow(val blockId: String) : DiaryIntent
    /** 표에서 [rowIndex] 행을 제거합니다 (헤더 포함). */
    data class RemoveTableRow(val blockId: String, val rowIndex: Int) : DiaryIntent
    /** 표에 빈 열을 마지막에 추가합니다. */
    data class AddTableColumn(val blockId: String) : DiaryIntent
    /** 표에서 [colIndex] 열을 제거합니다. */
    data class RemoveTableColumn(val blockId: String, val colIndex: Int) : DiaryIntent

    // ===== 플래너 및 목표 기록 =====
    /** 선택한 캘린더 날짜를 변경합니다. */
    data class SelectDate(val dateString: String) : DiaryIntent
    /** 활성화된 탭을 변경합니다 (DIARY, PLANNER, GOALS). */
    data class ChangeTab(val tab: String) : DiaryIntent
    /** 새로운 목표를 추가합니다. */
    data class AddGoal(val text: String, val category: String) : DiaryIntent
    /** 목표의 완료 여부를 토글합니다. */
    data class ToggleGoal(val id: String) : DiaryIntent
    /** 특정 목표를 삭제합니다. */
    data class DeleteGoal(val id: String) : DiaryIntent
    /** 특정 날짜에 새로운 할 일을 추가합니다. */
    data class AddPlannerTask(
        val text: String,
        val dateString: String,
        val startTime: String? = null,
        val endTime: String? = null,
        val location: String? = null,
        val isRepeat: Boolean = false,
        val repeatDays: List<Int> = emptyList(),
        val repeatEndDateString: String? = null
    ) : DiaryIntent
    /** 할 일의 완료 여부를 토글합니다. */
    data class TogglePlannerTask(val id: String) : DiaryIntent
    /** 특정 할 일을 삭제합니다. */
    data class DeletePlannerTask(val id: String) : DiaryIntent
    /** 반복 계획 일괄 등록 시리즈 전체를 삭제합니다. */
    data class DeletePlannerTaskSeries(val seriesId: String) : DiaryIntent
    /**
     * AI 가 선택 날짜 + 사용자가 지금 입력 중인 시간/장소/반복 조건(1순위),
     * 같은 날 기존 계획/장기 목표/최근 일기(2~4순위)를 보고
     * 오늘의 플래너 할 일 1건을 추천하도록 요청합니다.
     */
    data class SuggestPlannerTask(
        val startTime: String? = null,
        val endTime: String? = null,
        val location: String? = null,
        val isRepeat: Boolean = false,
        val repeatDays: List<Int> = emptyList(),
        val repeatEndDateString: String? = null
    ) : DiaryIntent
    /** AI 가 추천한 플래너 할 일을 입력란에 반영한 뒤, 상태의 1회성 추천 텍스트를 비웁니다. */
    data object ClearSuggestedPlannerTask : DiaryIntent
    /**
     * 선택된 탭의 AI 브리핑을 생성/재생성합니다.
     * @param tab "DIARY" | "PLANNER" | "GOALS"
     */
    data class RequestBriefing(val tab: String) : DiaryIntent

    // ===== 온디바이스 AI 챗봇 =====
    /** 챗봇에게 메시지를 전송합니다. */
    data class SendChatMessage(val text: String) : DiaryIntent
    /** 챗봇 대화 기록을 초기화합니다. */
    data object ClearChatHistory : DiaryIntent

    // ===== 작성 보조 AI 액션 =====
    /** 본문으로 한국어 제목 자동 생성. */
    data object SuggestTitle : DiaryIntent
    /** 본문을 보고 콘텐츠 타입 자동 분류 (DIARY/POST/NOTE). */
    data object ClassifyContentType : DiaryIntent
    /** 특정 블록의 오타·띄어쓰기 다듬기. */
    data class ProofreadBlock(val blockId: String) : DiaryIntent
    /** 특정 블록의 AI 꾸미기(색상·굵게·이탤릭·밑줄·크기) 추천 적용. */
    data class DecorateBlock(val blockId: String) : DiaryIntent
    /** 개인정보 처리방침 동의 완료 후 진행을 처리하는 인텐트입니다. */
    data object AcceptTermsAndProceed : DiaryIntent
    /** 온보딩 권한 요청이 모두 완료된 후 목록 화면으로 전환합니다. */
    data object AllPermissionsResolved : DiaryIntent

    // ===== 일기 검색 (FTS5) =====
    /**
     * 키워드로 일기를 검색합니다. 부분 문자열 + 날짜 가중치로 정렬된 결과를
     * [DiaryState.diaries] 에 1회성으로 채워넣고, [DiaryState.searchQuery] 가
     * non-blank 인 동안 검색 모드로 유지됩니다.
     */
    data class SearchDiaries(val query: String) : DiaryIntent
    /** 검색 모드를 해제하고 원래 전체 일기 목록으로 복귀합니다. */
    data object ClearDiarySearch : DiaryIntent

    // ===== 페이지네이션 (v3.1) =====
    /** 다음 페이지 메타를 추가로 로드합니다. LazyColumn 끝에 도달 시 발행. */
    data object LoadMoreDiaries : DiaryIntent
    /** 특정 일기 1건의 풀 [com.grepiu.aidiary.data.model.DiaryEntry] 를 lazy 로드. */
    data class LoadFullDiary(val id: String) : DiaryIntent
}
