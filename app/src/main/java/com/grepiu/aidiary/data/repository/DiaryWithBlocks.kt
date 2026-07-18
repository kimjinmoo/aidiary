package com.grepiu.aidiary.data.repository

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 일기 1건 + 본문 블록 N개를 한 번에 가져오는 매핑.
 *
 * Room 의 `@Relation` 으로 [BlockEntity] 들을 [DiaryEntity] 와 조인하며,
 * [BlockEntity.orderIndex] ASC 정렬로 본문 순서를 보장한다.
 */
data class DiaryWithBlocks(
    @Embedded
    val diary: DiaryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "diary_id"
    )
    val blocks: List<BlockEntity>
)
