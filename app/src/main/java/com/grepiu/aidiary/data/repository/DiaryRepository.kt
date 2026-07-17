package com.grepiu.aidiary.data.repository

import android.content.Context
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.data.model.extractPlainText
import com.grepiu.aidiary.data.model.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 일기 기록의 로컬 저장소 캐싱 및 파일 쓰기를 관리하는 레포지토리 클래스입니다.
 *
 * - 직렬화 포맷: 파일 상단에 `schemaVersion` 을 두어 추후 마이그레이션에 대비합니다.
 * - 이미지 파일 관리는 [ImageStorageManager] 에 위임합니다.
 * - AI 비서 검색용 SQLite FTS5 인덱스는 [searchDb] 가 관리하며, add/update/delete 시 동기화됩니다.
 */
class DiaryRepository(
    private val context: Context,
    private val imageStore: ImageStorageManager = ImageStorageManager(context),
    private val searchDb: DiarySearchDatabase = DiarySearchDatabase(context)
) {
    private val file = File(context.filesDir, "diary_history.json")
    private val tempFile = File(context.filesDir, "diary_history.json.tmp")

    @Volatile
    private var cachedDiaries: List<DiaryEntry>? = null
    private val cacheLock = Any()

    private val MAX_ENTRIES = 200

    init {
        // FTS 인덱스가 비어 있으면(JSON 에는 데이터가 있는데 인덱스만 없는 신규 설치/마이그레이션)
        // 백그라운드 스레드에서 일괄 동기화한다. 2,000건 기준 수십 ms.
        Thread {
            try {
                if (searchDb.readableDatabase
                        .rawQuery("SELECT COUNT(*) FROM ${DiarySearchDatabase.TABLE_NAME}", null)
                        .use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                ) return@Thread
                getDiaries().forEach { searchDb.upsert(it) }
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * 전체 일기 목록을 반환합니다 (역시간순 정렬).
     */
    fun getDiaries(): List<DiaryEntry> {
        return getOrLoadCache()
    }

    /**
     * 일기를 추가하고 갱신된 일기 목록을 반환합니다.
     */
    fun addEntry(entry: DiaryEntry): List<DiaryEntry> {
        val updated = synchronized(cacheLock) {
            val current = getOrLoadCache().toMutableList()
            current.add(0, entry)
            if (current.size > MAX_ENTRIES) {
                current.subList(MAX_ENTRIES, current.size).forEach { imageStore.deleteForEntry(it) }
                current.subList(MAX_ENTRIES, current.size).clear()
            }
            persist(current)
            current.toList()
        }
        cachedDiaries = updated
        try { searchDb.upsert(entry) } catch (_: Exception) {}
        return updated
    }

    /**
     * 특정 일기의 AI 분석결과와 분석된 감정을 갱신합니다.
     */
    fun updateAnalysis(id: String, emotion: String, aiAnalysis: String): List<DiaryEntry> {
        var updatedEntry: DiaryEntry? = null
        val updated = synchronized(cacheLock) {
            val current = getOrLoadCache().toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val newEntry = current[idx].copy(
                    emotion = emotion,
                    aiAnalysis = aiAnalysis
                )
                current[idx] = newEntry
                updatedEntry = newEntry
                persist(current)
            }
            current.toList()
        }
        cachedDiaries = updated
        updatedEntry?.let {
            try { searchDb.upsert(it) } catch (_: Exception) {}
        }
        return updated
    }

    /**
     * 특정 일기를 삭제합니다. 첨부 이미지도 함께 정리됩니다.
     */
    fun deleteEntry(id: String): List<DiaryEntry> {
        val updated = synchronized(cacheLock) {
            val current = getOrLoadCache()
            val target = current.firstOrNull { it.id == id }
            if (target != null) imageStore.deleteForEntry(target)
            val filtered = current.filter { it.id != id }
            persist(filtered)
            filtered
        }
        cachedDiaries = updated
        try { searchDb.delete(id) } catch (_: Exception) {}
        return updated
    }

    /**
     * AI 비서가 사용하는 부분 문자열 + 날짜 가중치 검색.
     * 결과는 [DiarySearchHit.relevance] 내림차순으로 정렬되어 반환된다.
     * 검색 자체는 빠르게(인덱스 기반) 동작하므로 2,000건 이상에서도 실용적이다.
     */
    fun searchDiaries(query: String, limit: Int = 30): List<DiarySearchHit> {
        return try {
            searchDb.search(query, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * FTS 인덱스를 전체 일기로 강제 재구축한다. (디버그/마이그레이션 용)
     */
    fun rebuildSearchIndex() {
        try {
            searchDb.clear()
            getDiaries().forEach { searchDb.upsert(it) }
        } catch (_: Exception) {
        }
    }

    /**
     * 전체 일기 데이터를 비웁니다(개발/디버그 용도).
     */
    fun clearAll() {
        synchronized(cacheLock) {
            getOrLoadCache().forEach { imageStore.deleteForEntry(it) }
            cachedDiaries = emptyList()
            if (file.exists()) file.delete()
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun getOrLoadCache(): List<DiaryEntry> {
        cachedDiaries?.let { return it }
        synchronized(cacheLock) {
            cachedDiaries?.let { return it }
            cachedDiaries = loadFromDisk()
            return cachedDiaries!!
        }
    }

    private fun loadFromDisk(): List<DiaryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return emptyList()
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { i ->
                parseEntry(arr.getJSONObject(i))
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseEntry(obj: JSONObject): DiaryEntry? {
        return try {
            val id = obj.getString("id")
            val timestamp = obj.getLong("timestamp")
            val title = obj.getString("title")
            val titleStyle = if (obj.has("titleStyle") && !obj.isNull("titleStyle")) {
                TitleStyle.fromJson(obj.optJSONObject("titleStyle"))
            } else {
                TitleStyle.Default
            }
            val emotion = obj.optString("emotion", "Neutral")
            val aiAnalysis = if (obj.isNull("aiAnalysis")) null else obj.getString("aiAnalysis")
            val legacyContent = obj.optString("content", "")
            // contentType 은 구버전 데이터에 없을 수 있으므로 안전하게 폴백
            val contentType = ContentType.fromStorageKey(
                if (obj.has("contentType") && !obj.isNull("contentType")) obj.getString("contentType") else null
            )

            val blocks: List<ContentBlock> = if (obj.has("blocks")) {
                val blocksArr = obj.getJSONArray("blocks")
                (0 until blocksArr.length()).map { ContentBlock.fromJson(blocksArr.getJSONObject(it)) }
            } else {
                // 구버전(텍스트 only) 데이터 호환: content 를 단일 TextBlock 으로 변환
                if (legacyContent.isNotEmpty()) {
                    listOf(ContentBlock.TextBlock(text = legacyContent))
                } else emptyList()
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

    private fun persist(list: List<DiaryEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("title", entry.title)
                    put("titleStyle", entry.titleStyle.toJson())
                    put("content", entry.blocks.extractPlainText())
                    put("emotion", entry.emotion)
                    put("aiAnalysis", entry.aiAnalysis ?: JSONObject.NULL)
                    put("contentType", entry.contentType.storageKey)

                    val blocksArr = JSONArray()
                    entry.blocks.forEach { blocksArr.put(it.toJson()) }
                    put("blocks", blocksArr)
                }
                arr.put(obj)
            }
            tempFile.writeText(arr.toString())
            tempFile.renameTo(file)
        } catch (_: Exception) {
        }
    }
}
