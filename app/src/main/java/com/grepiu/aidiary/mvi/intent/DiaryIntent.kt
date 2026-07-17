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
    data class NavigateTo(val phase: DiaryPhase, val selectedDiary: DiaryEntry? = null) : DiaryIntent
    data class UpdateDraft(
        val content: String? = null,
        val emotion: String? = null
    ) : DiaryIntent
    data object SaveDiary : DiaryIntent
    data class DeleteDiary(val id: String) : DiaryIntent
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

    // ===== 블록 기반 콘텐츠 =====
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

    // ===== 이미지 픽업/촬영 =====
    /** PhotoPicker 등 외부에서 받은 이미지 URI 목록(1개 이상) 을 내부 저장소로 가져와 각각 ImageBlock 으로 추가합니다. */
    data class ImagesPicked(val uris: List<Uri>) : DiaryIntent
    /**
     * 카메라 촬영이 완료된 이미지의 [content://] URI(FileProvider).
     * ContentResolver 로 스트림을 열어 내부 저장소로 복사합니다.
     */
    data class CameraImageCaptured(val capturedUri: Uri) : DiaryIntent

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
    data class AddGoal(val text: String) : DiaryIntent
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
        val location: String? = null
    ) : DiaryIntent
    /** 할 일의 완료 여부를 토글합니다. */
    data class TogglePlannerTask(val id: String) : DiaryIntent
    /** 특정 할 일을 삭제합니다. */
    data class DeletePlannerTask(val id: String) : DiaryIntent

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
    /** 특정 블록의 텍스트 다듬기(오탈자/띄어쓰기). */
    data class ProofreadBlock(val blockId: String) : DiaryIntent
    /** 특정 블록의 강조(색상/굵게) 추천 적용. */
    data class DecorateBlock(val blockId: String) : DiaryIntent
}
