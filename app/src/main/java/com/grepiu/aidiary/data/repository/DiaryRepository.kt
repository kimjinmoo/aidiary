package com.grepiu.aidiary.data.repository

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TextFormatting
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.data.model.extractPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room 기반 일기 저장소.
 *
 * v2 변경:
 *  - JSON 파일 → Room DB (일기/블록/FTS5)
 *  - 200건 상한 제거 (무제한)
 *  - 검색 본문 2,000 → 6,000 자 확대
 *  - 외부 API 는 최대한 유지하여 ViewModel/UI 변경 최소화
 *  - 메모리엔 [DiaryEntity] 메타 + 본문 블록을 결합한 [DiaryEntry] 캐시
 *    (2만건 × 평균 200B = 4MB 메타 + 블록 = 10~20MB)
 *
 * 외부 API (호환):
 *  - getDiaries()
 *  - addEntry(entry) → List<DiaryEntry>
 *  - updateAnalysis(id, emotion, aiAnalysis) → List<DiaryEntry>
 *  - deleteEntry(id) → List<DiaryEntry>
 *  - searchDiaries(query, limit=30) → List<DiarySearchHit>
 *  - rebuildSearchIndex()
 *  - clearAll()
 */
class DiaryRepository(
    private val context: Context,
    private val imageStore: ImageStorageManager = ImageStorageManager(context),
    private val database: DiaryDatabase = DiaryDatabase.get(context)
) {
    companion object {
        private const val TAG = "DiaryRepository"
        private const val PREVIEW_CHARS = 200
    }

    private val dao = database.diaryDao()
    private val supportDb get() = database.openHelper.writableDatabase

    @Volatile
    private var cachedDiaries: List<DiaryEntry>? = null
    private val cacheLock = Any()

    /**
     * 전체 일기 목록 (역시간순 정렬). 캐시 우선, 없으면 Room 에서 로드.
     *
     * v3.1 (Lazy): 화면에서는 [observeMetas] / [pagedMetas] 의 경량 메타를 사용하고,
     * 본문이 필요한 시점에만 [loadFullDiary] 로 풀 [DiaryEntry] 를 1건 로드한다.
     */
    fun getDiaries(): List<DiaryEntry> = getOrLoadCache()

    /**
     * 화면 표시용 메타만 흘려보내는 Flow. Room 의 [androidx.room.Query] 가 변경 시 자동 emit.
     * 본문 블록 / 서식 JSON / 셀 JSON 등을 제외해 2만건 ≈ 4~6MB 메모리.
     */
    fun observeMetas(): kotlinx.coroutines.flow.Flow<List<DiaryMeta>> =
        dao.observeMetas().map { rows -> rows.map { it.toDiaryMeta() } }

    /**
     * 페이지 단위 메타 로드. [offset] 부터 [limit] 건. 1페이지 = 50건 권장.
     */
    suspend fun pagedMetas(limit: Int, offset: Int): List<DiaryMeta> =
        dao.pagedMetas(limit, offset).map { it.toDiaryMeta() }

    /**
     * 단건 메타. 존재하지 않으면 null.
     */
    suspend fun metaOf(id: String): DiaryMeta? = dao.metaRow(id)?.toDiaryMeta()

    /**
     * 일기 총 개수 (메타 페이지네이션 판단용). 캐시 무효화 후 다시 호출하면 최신.
     */
    suspend fun daoCount(): Int = dao.count()

    /**
     * 1건의 풀 [DiaryEntry] (메타 + 본문 블록 전체) 를 lazy 로드.
     * 상세 진입 / RAG 컨텍스트 빌드 / 임의 1건 수정 시점에만 호출.
     */
    suspend fun loadFullDiary(id: String): DiaryEntry? = withContext(Dispatchers.IO) {
        val row = dao.loadFull(id) ?: return@withContext null
        val entity = row.diary
        val blocks = row.blocks.sortedBy { it.orderIndex }.mapNotNull { it.toContentBlock() }
        entity.toDiaryEntry(blocks = blocks)
    }

    /**
     * 일기 1건 + 본문 블록을 DB 에 저장한다.
     * - DiaryEntity upsert
     * - BlockEntity 일괄 replace
     * - FTS5 row upsert
     */
    suspend fun addEntry(entry: DiaryEntry): List<DiaryEntry> {
        val updated = withContext(Dispatchers.IO) {
            val diaryEntity = entry.toDiaryEntity(PREVIEW_CHARS)
            val blockEntities = entry.toBlockEntities()
            val ftsRow = entry.toFtsRow(maxContentChars = DiarySearchDao.MAX_CONTENT_CHARS)
            database.runInTransaction {
                dao.upsertBlocking(diaryEntity)
                dao.deleteBlocksForBlocking(diaryEntity.id)
                blockEntities.forEach { dao.insertBlockBlocking(it) }
                supportDb.insertFtsBlocking(ftsRow)
            }
            // 캐시 갱신
            val current = getOrLoadCache().toMutableList()
            current.removeAll { it.id == entry.id }
            current.add(0, entry)
            invalidateCache()
            current
        }
        invalidateCache()
        return updated
    }

    /**
     * AI 분석 결과 / 감정 코드 갱신.
     */
    suspend fun updateAnalysis(id: String, emotion: String, aiAnalysis: String): List<DiaryEntry> {
        val updated = withContext(Dispatchers.IO) {
            database.runInTransaction {
                val meta = dao.metaBlocking(id) ?: return@runInTransaction
                val newEntity = meta.copy(emotion = emotion, aiAnalysis = aiAnalysis)
                dao.upsertBlocking(newEntity)
            }
            invalidateCache()
            getOrLoadCache()
        }
        return updated
    }

    /**
     * 특정 일기 삭제. 첨부 이미지도 함께 정리되며, 블록은 CASCADE 로 자동 삭제.
     */
    suspend fun deleteEntry(id: String): List<DiaryEntry> {
        val updated = withContext(Dispatchers.IO) {
            val current = getOrLoadCache()
            val target = current.firstOrNull { it.id == id }
            if (target != null) imageStore.deleteForEntry(target)
            database.runInTransaction {
                dao.deleteByIdBlocking(id)
                supportDb.deleteFtsBlocking(id)
            }
            invalidateCache()
            getOrLoadCache()
        }
        return updated
    }

    /**
     * FTS5 기반 부분 문자열 + 날짜 가중치 검색.
     * 폴백(FTS5 미지원 / 쿼리 실패) 시 Room 의 일반 [diary] 테이블에서 LIKE 검색.
     */
    suspend fun searchDiaries(query: String, limit: Int = 30): List<DiarySearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        try {
            val tokens = sanitizeQuery(query)
            if (tokens.isEmpty()) return@withContext emptyList()
            val ftsQuery = tokens.joinToString(" OR ") { "\"$it\"" }
            val sql = """
                SELECT id, title, content, date_string, emotion, rank
                FROM diary_fts
                WHERE diary_fts MATCH ?
                ORDER BY rank
                LIMIT ?
            """.trimIndent()
            val rows: List<DiaryFtsRow> = try {
                val cursor = supportDb.query(
                    SimpleSQLiteQuery(sql, arrayOf<Any?>(ftsQuery, limit))
                )
                val list = mutableListOf<DiaryFtsRow>()
                cursor.use { c ->
                    while (c.moveToNext()) {
                        list.add(
                            DiaryFtsRow(
                                id = c.getString(0) ?: continue,
                                title = c.getString(1) ?: "",
                                content = c.getString(2) ?: "",
                                dateString = c.getString(3) ?: "",
                                emotion = c.getString(4) ?: "Neutral",
                                ftsRank = c.getDouble(5)
                            )
                        )
                    }
                }
                list
            } catch (e: Exception) {
                Log.w(TAG, "FTS5 search failed: ${e.message}. Falling back to Room LIKE search.")
                return@withContext fallbackLikeSearch(tokens, limit)
            }

            if (rows.isEmpty()) {
                Log.d(TAG, "FTS5 search returned 0 results. Falling back to Room LIKE search.")
                return@withContext fallbackLikeSearch(tokens, limit)
            }

            val now = System.currentTimeMillis()
            rows.map { row ->
                val dateWeight = computeDateWeight(row.dateString, now)
                DiarySearchHit(
                    id = row.id,
                    title = row.title,
                    content = row.content,
                    dateString = row.dateString,
                    emotion = row.emotion,
                    ftsRank = row.ftsRank,
                    dateWeight = dateWeight,
                    relevance = (1.0 / (1.0 + Math.abs(row.ftsRank))) * dateWeight
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchDiaries error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Room 일반 [diary] 테이블 LIKE 폴백. FTS5 모듈이 없거나 쿼리가 실패할 때 호출.
     * 토큰 3개까지 OR 매칭하며, 각 결과의 dateWeight 만으로 relevance 를 계산한다.
     */
    private suspend fun fallbackLikeSearch(tokens: List<String>, limit: Int): List<DiarySearchHit> = try {
        val padded = (tokens + List(3) { "" }).take(3).map { "%$it%" }
        val rows = dao.searchByLikeMulti(padded[0], padded[1], padded[2], limit)
        val now = System.currentTimeMillis()
        rows.map { row ->
            val dateWeight = computeDateWeight(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(row.timestamp)),
                now
            )
            DiarySearchHit(
                id = row.id,
                title = row.title,
                content = row.contentPreview,
                dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(row.timestamp)),
                emotion = row.emotion,
                ftsRank = 0.0,
                dateWeight = dateWeight,
                relevance = dateWeight
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "fallbackLikeSearch failed: ${e.message}")
        emptyList()
    }

    fun rebuildSearchIndex() {
        try {
            // FTS5 rebuild
            supportDb.execSQL("INSERT INTO diary_fts(diary_fts) VALUES('rebuild')")
        } catch (e: Exception) {
            Log.w(TAG, "rebuildSearchIndex failed: ${e.message}")
        }
    }

    fun clearAll() {
        synchronized(cacheLock) {
            getOrLoadCache().forEach { imageStore.deleteForEntry(it) }
            try { supportDb.execSQL("DELETE FROM diary") } catch (e: Exception) { Log.w(TAG, "clear diary: ${e.message}") }
            try { supportDb.execSQL("DELETE FROM diary_fts") } catch (e: Exception) { Log.w(TAG, "clear diary_fts: ${e.message}") }
            cachedDiaries = emptyList()
        }
    }

    /**
     * 캐시 무효화. 다음 [getDiaries] 호출 시 Room 에서 다시 로드.
     */
    fun invalidateCache() {
        synchronized(cacheLock) { cachedDiaries = null }
    }

    private fun getOrLoadCache(): List<DiaryEntry> {
        cachedDiaries?.let { return it }
        return synchronized(cacheLock) {
            cachedDiaries?.let { return it }
            val loaded = try {
                loadAllFromRoom()
            } catch (e: Exception) {
                Log.w(TAG, "loadFromRoom failed: ${e.message}")
                emptyList()
            }
            cachedDiaries = loaded
            loaded
        }
    }

    private fun loadAllFromRoom(): List<DiaryEntry> {
        // 메타만 가져오기 (paged LIMIT 없이 모두)
        val metas = dao.allBlocking()
        if (metas.isEmpty()) return emptyList()
        // 블록 일괄 로드 (전체 본문 필요)
        val blocks = dao.allBlocksBlocking()
        val blocksByDiary = blocks.groupBy { it.diaryId }
        return metas.mapNotNull { meta ->
            val blocksForThis = blocksByDiary[meta.id].orEmpty()
                .sortedBy { it.orderIndex }
                .mapNotNull { it.toContentBlock() }
            meta.toDiaryEntry(blocks = blocksForThis)
        }
    }

    // ===== 쿼리 헬퍼 =====

    private fun sanitizeQuery(query: String): List<String> {
        val cleaned = query
            .replace(Regex("[\\\"'(){}\\[\\]:*+\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(" ")
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun computeDateWeight(dateString: String, nowMillis: Long): Double {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = fmt.parse(dateString) ?: return 1.0
            val daysAgo = (nowMillis - date.time) / (1000.0 * 60 * 60 * 24)
            if (daysAgo < 0) 1.0
            else 1.0 / (1.0 + daysAgo / 365.0 * 1.2)
        } catch (e: Exception) {
            1.0
        }
    }
}

// ===== DiaryEntity ↔ DiaryEntry 변환 =====

// ===== DiaryMetaRow ↔ DiaryMeta =====

private fun DiaryMetaRow.toDiaryMeta(): DiaryMeta = DiaryMeta(
    id = id,
    timestamp = timestamp,
    title = title,
    emotion = emotion,
    contentType = ContentType.fromStorageKey(contentType),
    contentPreview = contentPreview
)

private fun DiaryEntry.toDiaryEntity(previewChars: Int): DiaryEntity {
    val plain = blocks.extractPlainText()
    val preview = if (plain.length > previewChars) plain.substring(0, previewChars) else plain
    return DiaryEntity(
        id = id,
        timestamp = timestamp,
        title = title,
        titleStyleJson = titleStyle.toJson().toString(),
        emotion = emotion,
        aiAnalysis = aiAnalysis,
        contentType = contentType.storageKey,
        contentPreview = preview
    )
}

private fun DiaryEntity.toDiaryEntry(blocks: List<ContentBlock>): DiaryEntry? {
    val titleStyle = runCatching {
        TitleStyle.fromJson(JSONObject(titleStyleJson))
    }.getOrElse { TitleStyle.Default }
    val contentType = ContentType.fromStorageKey(contentType)
    return DiaryEntry(
        id = id,
        timestamp = timestamp,
        title = title,
        titleStyle = titleStyle,
        blocks = blocks,
        content = contentPreview,
        emotion = emotion,
        aiAnalysis = aiAnalysis,
        contentType = contentType
    )
}

private fun DiaryEntry.toBlockEntities(): List<BlockEntity> = blocks.mapIndexed { idx, b ->
    when (b) {
        is ContentBlock.HeadingBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_HEADING,
            text = b.text, formattingJson = b.formatting.toJsonStringNullable(),
            path = null, caption = null, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.TextBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_TEXT,
            text = b.text, formattingJson = b.formatting.toJsonStringNullable(),
            path = null, caption = null, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.QuoteBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_QUOTE,
            text = b.text, formattingJson = b.formatting.toJsonStringNullable(),
            path = null, caption = null, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.ImageBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_IMAGE,
            text = null, formattingJson = null,
            path = b.relativePath, caption = b.caption, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.DividerBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_DIVIDER,
            text = null, formattingJson = null,
            path = null, caption = null, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.TagAiBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_TAG_AI,
            text = null, formattingJson = null,
            path = null, caption = null, emotion = b.emotion,
            rows = null, cols = null, cellsJson = null,
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.TableBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_TABLE,
            text = null, formattingJson = null,
            path = null, caption = null, emotion = null,
            rows = b.rows, cols = b.cols,
            cellsJson = b.cells.toJsonArrayString(),
            latitude = null, longitude = null, address = null
        )
        is ContentBlock.LocationBlock -> BlockEntity(
            id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_LOCATION,
            text = null, formattingJson = null,
            path = null, caption = null, emotion = null,
            rows = null, cols = null, cellsJson = null,
            latitude = b.latitude, longitude = b.longitude, address = b.address
        )
    }
}

private fun BlockEntity.toContentBlock(): ContentBlock? {
    val fmt = formattingJson?.let { runCatching { TextFormatting.fromJson(JSONObject(it)) }.getOrNull() } ?: TextFormatting.Empty
    return when (type) {
        ContentBlock.TYPE_HEADING -> ContentBlock.HeadingBlock(id = id, text = text ?: "", formatting = fmt)
        ContentBlock.TYPE_TEXT -> ContentBlock.TextBlock(id = id, text = text ?: "", formatting = fmt)
        ContentBlock.TYPE_QUOTE -> ContentBlock.QuoteBlock(id = id, text = text ?: "", formatting = fmt)
        ContentBlock.TYPE_IMAGE -> ContentBlock.ImageBlock(id = id, relativePath = path ?: "", caption = caption ?: "")
        ContentBlock.TYPE_DIVIDER -> ContentBlock.DividerBlock(id = id)
        ContentBlock.TYPE_TAG_AI -> ContentBlock.TagAiBlock(id = id, emotion = emotion ?: "평온")
        ContentBlock.TYPE_TABLE -> {
            val r = (rows ?: 2).coerceAtLeast(1)
            val c = (cols ?: 2).coerceAtLeast(1)
            val cells = cellsJson?.let { parseCells(it, r, c) } ?: List(r) { List(c) { "" } }
            ContentBlock.TableBlock(id = id, rows = r, cols = c, cells = cells)
        }
        ContentBlock.TYPE_LOCATION -> ContentBlock.LocationBlock(
            id = id, latitude = latitude ?: 0.0, longitude = longitude ?: 0.0, address = address ?: ""
        )
        else -> null
    }
}

private fun DiaryEntry.toFtsRow(maxContentChars: Int): FtsRow {
    val plain = blocks.extractPlainText()
    val indexed = if (plain.length > maxContentChars) plain.substring(0, maxContentChars) else plain
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return FtsRow(
        id = id,
        title = title,
        content = indexed,
        dateString = dateFmt.format(Date(timestamp)),
        emotion = emotion
    )
}

// ===== 유틸 =====

private fun TextFormatting.toJsonStringNullable(): String? =
    if (isEmpty()) null else toJson().toString()

private fun List<List<String>>.toJsonArrayString(): String {
    val arr = JSONArray()
    forEach { row ->
        val rowArr = JSONArray()
        row.forEach { rowArr.put(it) }
        arr.put(rowArr)
    }
    return arr.toString()
}

private fun parseCells(json: String, rows: Int, cols: Int): List<List<String>> {
    return try {
        val arr = JSONArray(json)
        (0 until rows).map { r ->
            (0 until cols).map { c ->
                arr.optJSONArray(r)?.optString(c, "") ?: ""
            }
        }
    } catch (e: Exception) {
        List(rows) { List(cols) { "" } }
    }
}
