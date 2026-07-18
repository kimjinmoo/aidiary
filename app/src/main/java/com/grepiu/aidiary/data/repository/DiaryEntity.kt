package com.grepiu.aidiary.data.repository

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일기 메인 row. 본문 블록은 [BlockEntity] 의 1:N 으로 분리.
 *
 * 본문 전문(plain text)은 저장하지 않음 — [contentPreview] (200자) 만 보관해
 * 목록·AI 폴백용으로 쓰고, 본문 전체는 [BlockEntity] 들에서 lazy 조립.
 *
 * 메타 정보(id/timestamp/title/emotion/contentType)만 들고 있어
 * 2만건 규모에서도 인메모리 부담이 적다 (≈ 200B × 2만 = 4MB).
 */
@Entity(tableName = "diary")
data class DiaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "timestamp", index = true)
    val timestamp: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "title_style_json")
    val titleStyleJson: String,
    @ColumnInfo(name = "emotion", index = true)
    val emotion: String,
    @ColumnInfo(name = "ai_analysis")
    val aiAnalysis: String?,
    @ColumnInfo(name = "content_type", index = true)
    val contentType: String,
    /**
     * 본문 평문 미리보기. 200자 또는 1줄. extractPlainText 결과의 일부.
     * AI 비서 폴백·목록 UI 에서 사용.
     */
    @ColumnInfo(name = "content_preview")
    val contentPreview: String,
)
