package com.grepiu.aidiary.data.repository

import com.grepiu.aidiary.data.model.ContentType

/**
 * 일기 1건의 화면 표시용 메타데이터. 본문 블록은 포함하지 않는다.
 *
 * v3 확장성: 2만건 이상에서도 인메모리 ≈ 4MB (id 36B + ts 8B + title ~50B + emotion 10B + type 6B + preview 200B ≈ 310B × 2만 = 6MB)
 * Room 의 [DiaryEntity] 에서 본문/서식 JSON 등을 제외한 가벼운 view-model.
 *
 * 본문 전체가 필요해지면 [DiaryRepository.loadFullDiary] 로 풀 [com.grepiu.aidiary.data.model.DiaryEntry] 를
 * 1건만 로드한다 (상세 진입 시점).
 */
data class DiaryMeta(
    val id: String,
    val timestamp: Long,
    val title: String,
    val emotion: String,
    val contentType: ContentType,
    val contentPreview: String,
)
