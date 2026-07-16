package com.grepiu.aidiary.mvi.intent

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
    data class UpdateDraft(val title: String? = null, val content: String? = null, val emotion: String? = null) : DiaryIntent
    data object SaveDiary : DiaryIntent
    data class DeleteDiary(val id: String) : DiaryIntent
    data object AnalyzeDiary : DiaryIntent
    data class ShowDownloadNotice(val show: Boolean) : DiaryIntent
    data class ShowWifiWarning(val show: Boolean) : DiaryIntent
    data object StartRecording : DiaryIntent
    data object StopRecording : DiaryIntent
}
