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
    /** 온보딩(환영) 화면에서 약관 동의 후 마이크·위치·카메라 권한을 일괄 요청합니다. */
    data object RequestAllWelcomePermissions : DiaryEffect
    /** 카메라 촬영을 위한 임시 출력 URI 를 UI 측에 요청합니다. */
    data class LaunchCamera(val targetUri: android.net.Uri) : DiaryEffect
    /** 비디오 picker 를 UI 측에 띄우도록 알립니다. */
    data object LaunchVideoPicker : DiaryEffect
    /** 클라우드 picker(시스템 파일 탐색기)를 UI 측에 띄우도록 알립니다. */
    data object LaunchCloudPicker : DiaryEffect
    /** 백업 ZIP 파일 저장을 위한 CreateDocument 런처 호출 이펙트 */
    data class LaunchExportBackupPicker(val fileName: String) : DiaryEffect
    /** 백업 ZIP 파일 불러오기를 위한 OpenDocument 런처 호출 이펙트 */
    data object LaunchImportBackupPicker : DiaryEffect
}
