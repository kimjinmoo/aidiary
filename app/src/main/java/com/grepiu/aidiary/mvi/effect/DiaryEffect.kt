package com.grepiu.aidiary.mvi.effect

/**
 * 화면에서 처리할 1회성 사이드 이펙트(부수 효과)를 정의한 봉인 인터페이스입니다.
 */
sealed interface DiaryEffect {
    data class ShowToast(val message: String) : DiaryEffect
    data class AnalysisComplete(val result: String) : DiaryEffect
    data class TranscriptionResult(val text: String) : DiaryEffect
    data object RequestAudioPermission : DiaryEffect
}
