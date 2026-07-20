package com.grepiu.aidiary.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 일기 메인 DAO.
 *
 * 성능 원칙:
 *  - [observeDiaries] 는 메타만 흘려보낸다 (블록 미포함)
 *  - [loadFull] 만 본문 블록을 함께 가져온다 (상세 화면 진입 시점)
 *  - 페이지네이션은 [paged] / [count] 로 명시적 호출
 *  - 검색은 FTS5 DAO([DiarySearchDao]) 에 위임
 */
@Dao
interface DiaryDao {

    @Upsert
    suspend fun upsert(entity: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<BlockEntity>)

    @Query("DELETE FROM block WHERE diary_id = :diaryId")
    suspend fun deleteBlocksFor(diaryId: String)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun loadFull(id: String): DiaryWithBlocks?

    /**
     * 메타만 반환. 본문은 lazy 로딩.
     */
    @Query("SELECT * FROM diary ORDER BY timestamp DESC")
    fun observeDiaries(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diary ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun paged(limit: Int, offset: Int): List<DiaryEntity>

    @Query("SELECT COUNT(*) FROM diary")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM diary")
    fun observeCount(): Flow<Int>

    /**
     * 단일 메타. 상세 진입 시 한 번 호출.
     */
    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun meta(id: String): DiaryEntity?

    @Transaction
    suspend fun upsertWithBlocks(diary: DiaryEntity, blocks: List<BlockEntity>) {
        upsert(diary)
        deleteBlocksFor(diary.id)
        if (blocks.isNotEmpty()) insertBlocks(blocks)
    }

    // ===== 동기 (Blocking) — runInTransaction 내부 전용 =====

    @Upsert
    fun upsertBlocking(entity: DiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlockBlocking(block: BlockEntity)

    @Query("DELETE FROM block WHERE diary_id = :diaryId")
    fun deleteBlocksForBlocking(diaryId: String)

    @Query("DELETE FROM diary WHERE id = :id")
    fun deleteByIdBlocking(id: String)

    @Query("SELECT * FROM diary WHERE id = :id")
    fun metaBlocking(id: String): DiaryEntity?

    @Query("SELECT * FROM diary ORDER BY timestamp DESC")
    fun allBlocking(): List<DiaryEntity>

    @Query("SELECT * FROM block ORDER BY diary_id, order_index")
    fun allBlocksBlocking(): List<BlockEntity>

    /**
     * 화면 표시용 경량 메타만. 본문 / 서식 JSON / 블록 제외.
     * 2만건 이상에서도 Flow 가 흘려보내도 부담이 적다.
     */
    @Query("SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary ORDER BY timestamp DESC")
    fun observeMetas(): Flow<List<DiaryMetaRow>>

    @Query("SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun metasForDateRange(start: Long, end: Long): List<DiaryMetaRow>

    @Query("SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun pagedMetas(limit: Int, offset: Int): List<DiaryMetaRow>

    @Query("SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary WHERE id = :id")
    suspend fun metaRow(id: String): DiaryMetaRow?

    /**
     * 폴백 LIKE 검색 (FTS5 미지원 OEM 기기용). 제목 또는 미리보기에서 부분 매칭.
     */
    @Query("""
        SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary
        WHERE title LIKE :pattern OR content_preview LIKE :pattern
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchByLike(pattern: String, limit: Int): List<DiaryMetaRow>

    /**
     * 폴백 멀티-토큰 LIKE 검색. 각 토큰이 title/content_preview 중 어디든 부분 매칭되면 포함.
     * 토큰은 OR 결합. 한국어 본문도 substring 매칭이 동작한다.
     */
    @Query("""
        SELECT id, timestamp, title, emotion, content_type, content_preview FROM diary
        WHERE title LIKE :p1 OR content_preview LIKE :p1
           OR title LIKE :p2 OR content_preview LIKE :p2
           OR title LIKE :p3 OR content_preview LIKE :p3
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchByLikeMulti(
        p1: String,
        p2: String,
        p3: String,
        limit: Int
    ): List<DiaryMetaRow>
}
