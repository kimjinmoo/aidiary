package com.grepiu.aidiary.data.model

import java.util.UUID

/**
 * 일기 항목 데이터를 표현하는 불변 데이터 클래스입니다.
 */
data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val content: String,
    val emotion: String = "Neutral", // 감정 태그: 기쁨(Joy), 슬픔(Sadness), 분노(Anger), 불안(Anxiety), 평온(Calm), 보통(Neutral) 등
    val aiAnalysis: String? = null // AI 일기 피드백 및 상세 분석 결과 텍스트
)
