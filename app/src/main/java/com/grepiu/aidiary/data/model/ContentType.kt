package com.grepiu.aidiary.data.model

/**
 * 글 작성 시 사용자가 선택하는 콘텐츠 타입입니다.
 *
 * - [DIARY]: 일기 (감정/AI 분석 활성화)
 * - [POST]: 자유 글/포스트 (AI 분석은 사용자가 명시적으로 요청 시에만)
 * - [NOTE]: 간단 메모 (AI 분석 비활성)
 *
 * @param storageKey JSON 에 영구 저장되는 문자열 키. enum 이름이 바뀌어도
 *                   기존 데이터 호환을 위해 안정적입니다.
 */
enum class ContentType(val storageKey: String, val label: String) {
    DIARY("DIARY", "일기"),
    POST("POST", "새 글"),
    NOTE("NOTE", "메모");

    /** AI 분석 트리거 버튼을 노출할지 여부. */
    val supportsAiAnalysis: Boolean
        get() = this == DIARY

    companion object {
        /** 알 수 없는 저장 키에 대한 안전한 폴백. */
        fun fromStorageKey(key: String?): ContentType =
            values().firstOrNull { it.storageKey == key } ?: DIARY
    }
}
