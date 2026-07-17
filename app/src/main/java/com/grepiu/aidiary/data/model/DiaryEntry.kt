package com.grepiu.aidiary.data.model

import java.util.UUID

/**
 * 일기 항목 데이터를 표현하는 불변 데이터 클래스입니다.
 *
 * 본문은 [blocks] 리스트로 구성되며, AI 분석·목록 미리보기 등 평문이 필요한
 * 모든 경로에서는 [contentText] / [extractPlainText] 를 사용합니다.
 */
data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val titleStyle: TitleStyle = TitleStyle.Default,
    val blocks: List<ContentBlock> = emptyList(),
    /**
     * AI 분석·목록 미리보기 호환을 위한 평문 캐시. 일반적으로 [blocks] 와 동기화되지만,
     * 직렬화 시점에만 사용되며 렌더 시에는 [blocks] 가 단일 진실 공급원입니다.
     */
    val content: String = "",
    val emotion: String = "Neutral", // 기쁨(Joy), 슬픔(Sadness), 분노(Anger), 불안(Anxiety), 평온(Calm), 보통(Neutral)
    val aiAnalysis: String? = null,
    val contentType: ContentType = ContentType.DIARY
) {
    /**
     * AI 분석/목록 미리보기용 평문. [content] 가 비어 있고 [blocks] 만 있을 경우를 위해 폴백 제공.
     */
    val contentText: String
        get() = content.ifBlank { blocks.extractPlainText() }
}
