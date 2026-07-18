package com.grepiu.aidiary.data.repository

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * FTS5 가상테이블 `diary_fts` 접근용 DAO.
 *
 * Room 은 FTS5 가상테이블을 직접 매핑하는 어노테이션을 제공하지 않으므로
 * [RawQuery] 로만 다룬다. 일반 insert/delete 는 [androidx.sqlite.db.SupportSQLiteDatabase]
 * 경유.
 *
 * 본문 길이 상한: [MAX_CONTENT_CHARS] = 6,000 자.
 *  - 2,000 자 → 긴 본문에서 꼬리 검색 누락
 *  - 6,000 자 → 평균 일기 90% 이상을 인덱싱하면서도 2만건 ≈ 120MB 이내
 */
@Dao
interface DiarySearchDao {

    @RawQuery(observedEntities = [])
    suspend fun rawQuery(query: SupportSQLiteQuery): List<DiaryFtsRow>

    companion object {
        /** 본문 인덱싱 상한. 2,000 → 6,000 으로 확대 (확장성 개선). */
        const val MAX_CONTENT_CHARS = 6000
    }
}

/**
 * FTS5 검색 결과 1건. rank 는 음수 (작을수록 더 관련도 높음).
 */
data class DiaryFtsRow(
    val id: String,
    val title: String,
    val content: String,
    val dateString: String,
    val emotion: String,
    val ftsRank: Double
)

/**
 * FTS5 입력용 평문 row. 가상테이블은 Room Entity 로 매핑하지 않으므로
 * 일반 data class 로만 두고, SQL 은 raw execSQL/insert 로 다룬다.
 */
data class FtsRow(
    val id: String,
    val title: String,
    val content: String,
    val dateString: String,
    val emotion: String
)

/**
 * 화면 표시용 메타 row. [observeMetas] / [pagedMetas] / [metaRow] / [searchByLike] 의 결과.
 * 본문 평문 일부(content_preview) 만 포함해 검색 폴백에서도 활용한다.
 */
data class DiaryMetaRow(
    @androidx.room.ColumnInfo(name = "id") val id: String,
    @androidx.room.ColumnInfo(name = "timestamp") val timestamp: Long,
    @androidx.room.ColumnInfo(name = "title") val title: String,
    @androidx.room.ColumnInfo(name = "emotion") val emotion: String,
    @androidx.room.ColumnInfo(name = "content_type") val contentType: String,
    @androidx.room.ColumnInfo(name = "content_preview") val contentPreview: String
)
