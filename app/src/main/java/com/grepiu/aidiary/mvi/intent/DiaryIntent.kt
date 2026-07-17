package com.grepiu.aidiary.mvi.intent

import android.net.Uri
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.DiaryEntry
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
        val title: String? = null,
        val content: String? = null,
        val emotion: String? = null
    ) : DiaryIntent
    data object SaveDiary : DiaryIntent
    data class DeleteDiary(val id: String) : DiaryIntent
    data object AnalyzeDiary : DiaryIntent
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
    /** PhotoPicker 등 외부에서 받은 이미지 URI 를 내부 저장소로 가져오고 ImageBlock 으로 추가합니다. */
    data class ImagePicked(val uri: Uri) : DiaryIntent
    /** 카메라 촬영이 완료된 임시 파일을 내부 저장소로 가져와 ImageBlock 으로 추가합니다. */
    data class CameraImageCaptured(val tempFilePath: String) : DiaryIntent
}
