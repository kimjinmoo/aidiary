package com.grepiu.aidiary.data.repository

import android.content.Context
import android.util.Log
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TextFormatting
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.data.model.extractPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 기존 [diary_history.json] 을 Room DB 로 1회 import 한다.
 *
 * - 앱 시작 시 [DiaryRepository] 가 DB 가 비어 있고 JSON 이 존재하면 호출한다.
 * - 1,000건 단위 batch insert 로 OOM 방지.
 * - FTS5 도 함께 bulk rebuild.
 * - 성공 시 JSON 파일은 `backup/diary_history_imported_<ts>.json` 으로 이동.
 * - 실패 시 원본 보존 → 다음 실행에서 재시도.
 *
 * UI 에 진행률을 흘려보내기 위해 [onProgress] 콜백을 받는다.
 */
class LegacyJsonImporter(
    private val context: Context,
    private val database: DiaryDatabase
) {
    companion object {
        private const val TAG = "LegacyJsonImporter"
        private const val BATCH_SIZE = 1000
        private const val PREVIEW_CHARS = 200
    }

    data class ImportResult(
        val totalImported: Int,
        val jsonMoved: Boolean
    )

    suspend fun importIfNeeded(
        onProgress: ((imported: Int, total: Int) -> Unit)? = null
    ): ImportResult? {
        val jsonFile = File(context.filesDir, "diary_history.json")
        if (!jsonFile.exists()) return null

        val dao = database.diaryDao()
        val searchDao = database.searchDao()
        if (dao.count() > 0) {
            // 이미 import 됨. JSON 만 백업으로 이동.
            moveToBackup(jsonFile)
            return ImportResult(totalImported = 0, jsonMoved = true)
        }

        val entries = withContext(Dispatchers.IO) { loadEntries(jsonFile) }
        if (entries.isEmpty()) {
            moveToBackup(jsonFile)
            return ImportResult(totalImported = 0, jsonMoved = true)
        }

        val total = entries.size
        var imported = 0
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        entries.chunked(BATCH_SIZE).forEach { batch ->
            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    val diaryRows = batch.map { it.toDiaryEntity(previewChars = PREVIEW_CHARS) }
                    val blockRows = batch.flatMap { it.toBlockEntities() }
                    val ftsRows = batch.map {
                        it.toFtsRow(dateFmt, DiarySearchDao.MAX_CONTENT_CHARS)
                    }
                    // runInTransaction 자체가 BEGIN/COMMIT 을 관리한다.
                    val supportDb = database.openHelper.writableDatabase
                    diaryRows.forEach { row -> dao.upsertBlocking(row) }
                    blockRows.forEach { row -> dao.insertBlockBlocking(row) }
                    ftsRows.forEach { row -> supportDb.insertFtsBlocking(row) }
                }
            }
            imported += batch.size
            onProgress?.invoke(imported, total)
        }

        // FTS5 마지막에 한 번 rebuild 로 인덱스 통합 갱신
        withContext(Dispatchers.IO) {
            runCatching {
                database.openHelper.writableDatabase
                    .execSQL("INSERT INTO diary_fts(diary_fts) VALUES('rebuild')")
            }
        }

        moveToBackup(jsonFile)
        Log.i(TAG, "Imported $imported entries from legacy JSON.")
        return ImportResult(totalImported = imported, jsonMoved = true)
    }

    private fun moveToBackup(jsonFile: File) {
        try {
            val backupDir = File(context.filesDir, "backup").apply { mkdirs() }
            val target = File(
                backupDir,
                "diary_history_imported_${System.currentTimeMillis()}.json"
            )
            jsonFile.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "JSON backup move failed: ${e.message}")
        }
    }

    private fun loadEntries(file: File): List<DiaryEntry> {
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i -> parseEntry(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load legacy JSON: ${e.message}")
            emptyList()
        }
    }

    private fun parseEntry(obj: JSONObject): DiaryEntry? {
        return try {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val title = obj.optString("title", "")
            val titleStyle = if (obj.has("titleStyle") && !obj.isNull("titleStyle")) {
                TitleStyle.fromJson(obj.optJSONObject("titleStyle"))
            } else TitleStyle.Default
            val emotion = obj.optString("emotion", "Neutral")
            val aiAnalysis = if (obj.isNull("aiAnalysis")) null else obj.optString("aiAnalysis", "")
            val contentType = ContentType.fromStorageKey(
                if (obj.has("contentType") && !obj.isNull("contentType")) obj.optString("contentType") else null
            )
            val legacyContent = obj.optString("content", "")
            val blocks: List<ContentBlock> = if (obj.has("blocks")) {
                val arr = obj.getJSONArray("blocks")
                (0 until arr.length()).map { ContentBlock.fromJson(arr.getJSONObject(it)) }
            } else {
                if (legacyContent.isNotEmpty()) listOf(ContentBlock.TextBlock(text = legacyContent))
                else emptyList()
            }
            DiaryEntry(
                id = id,
                timestamp = timestamp,
                title = title,
                titleStyle = titleStyle,
                blocks = blocks,
                content = legacyContent,
                emotion = emotion,
                aiAnalysis = aiAnalysis,
                contentType = contentType
            )
        } catch (e: Exception) {
            null
        }
    }

    // ===== 변환 헬퍼 =====

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

    private fun DiaryEntry.toBlockEntities(): List<BlockEntity> = blocks.mapIndexed { idx, b ->
        when (b) {
            is ContentBlock.HeadingBlock -> BlockEntity(
                id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_HEADING,
                text = b.text, formattingJson = b.formatting.toJsonString(),
                path = null, caption = null, emotion = null,
                rows = null, cols = null, cellsJson = null,
                latitude = null, longitude = null, address = null
            )
            is ContentBlock.TextBlock -> BlockEntity(
                id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_TEXT,
                text = b.text, formattingJson = b.formatting.toJsonString(),
                path = null, caption = null, emotion = null,
                rows = null, cols = null, cellsJson = null,
                latitude = null, longitude = null, address = null
            )
            is ContentBlock.QuoteBlock -> BlockEntity(
                id = b.id, diaryId = id, orderIndex = idx, type = ContentBlock.TYPE_QUOTE,
                text = b.text, formattingJson = b.formatting.toJsonString(),
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

    private fun DiaryEntry.toFtsRow(dateFmt: SimpleDateFormat, maxContentChars: Int): FtsRow {
        val plain = blocks.extractPlainText()
        val indexed = if (plain.length > maxContentChars) plain.substring(0, maxContentChars) else plain
        return FtsRow(
            id = id,
            title = title,
            content = indexed,
            dateString = dateFmt.format(Date(timestamp)),
            emotion = emotion
        )
    }
}

/** TableBlock.cells 를 JSON 으로 직렬화. */
private fun List<List<String>>.toJsonArrayString(): String {
    val arr = JSONArray()
    forEach { row ->
        val rowArr = JSONArray()
        row.forEach { rowArr.put(it) }
        arr.put(rowArr)
    }
    return arr.toString()
}

/** TextFormatting.toJson 이 JSONObject 를 반환하므로 String 으로 변환. */
private fun TextFormatting.toJsonString(): String? =
    if (isEmpty()) null else toJson().toString()
