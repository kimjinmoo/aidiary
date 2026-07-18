package com.grepiu.aidiary.mvi.effect

/**
 * 화면에서 처리할 1회성 사이드 이펙트(부수 효과)를 정의한 봉인 인터페이스입니다.
 */
sealed interface DiaryEffect {
    data class ShowToast(val message: String) : DiaryEffect
    data class TranscriptionResult(val text: String) : DiaryEffect
    data object RequestAudioPermission : DiaryEffect
    data object RequestCameraPermission : DiaryEffect
    data object RequestLocationPermission : DiaryEffect
    /** 카메라 촬영을 위한 임시 출력 URI 를 UI 측에 요청합니다. */
    data class LaunchCamera(val targetUri: android.net.Uri) : DiaryEffect
}
