package com.grepiu.aidiary.data.repository

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 일기 본문을 구성하는 블록 1개의 row.
 *
 * 모든 블록 타입이 한 테이블에 들어간다. 본문이 없는 블록(Image/Divider/TagAi) 은
 * 해당 컬럼이 null 이고, [type] 으로 분기한다.
 *
 * 외래키 [diaryId] 는 [DiaryEntity.id] 를 참조하며, 일기 삭제 시 CASCADE 로
 * 함께 정리된다.
 */
@Entity(
    tableName = "block",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["diary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("diary_id"),
        Index(value = ["diary_id", "order_index"])
    ]
)
data class BlockEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "diary_id")
    val diaryId: String,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "text")
    val text: String?,
    @ColumnInfo(name = "formatting_json")
    val formattingJson: String?,
    @ColumnInfo(name = "path")
    val path: String?,
    @ColumnInfo(name = "caption")
    val caption: String?,
    @ColumnInfo(name = "emotion")
    val emotion: String?,
    @ColumnInfo(name = "rows")
    val rows: Int?,
    @ColumnInfo(name = "cols")
    val cols: Int?,
    @ColumnInfo(name = "cells_json")
    val cellsJson: String?,
    @ColumnInfo(name = "latitude")
    val latitude: Double?,
    @ColumnInfo(name = "longitude")
    val longitude: Double?,
    @ColumnInfo(name = "address")
    val address: String?,
    // ==== 입체 미디어 (SpatialMediaBlock) ====
    @ColumnInfo(name = "spatial_type")
    val spatialType: String?,
    /** 파일 경로 JSON 배열. PHOTO=[L,R] / VIDEO=[단일]. 2D 일반은 null */
    @ColumnInfo(name = "spatial_paths_json")
    val spatialPathsJson: String?,
    @ColumnInfo(name = "spatial_capture_mode")
    val spatialCaptureMode: String?,
)
