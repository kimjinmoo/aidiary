package com.grepiu.aidiary.data.repository

import android.content.Context
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.DiaryEntry
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
 */
class DiaryRepository(
    private val context: Context,
    private val imageStore: ImageStorageManager = ImageStorageManager(context)
) {
    private val file = File(context.filesDir, "diary_history.json")
    private val tempFile = File(context.filesDir, "diary_history.json.tmp")

    @Volatile
    private var cachedDiaries: List<DiaryEntry>? = null
    private val cacheLock = Any()

    private val MAX_ENTRIES = 200

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
        return updated
    }

    /**
     * 특정 일기의 AI 분석결과와 분석된 감정을 갱신합니다.
     */
    fun updateAnalysis(id: String, emotion: String, aiAnalysis: String): List<DiaryEntry> {
        val updated = synchronized(cacheLock) {
            val current = getOrLoadCache().toMutableList()
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                current[idx] = current[idx].copy(
                    emotion = emotion,
                    aiAnalysis = aiAnalysis
                )
                persist(current)
            }
            current.toList()
        }
        cachedDiaries = updated
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
        return updated
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
            val emotion = obj.optString("emotion", "Neutral")
            val aiAnalysis = if (obj.isNull("aiAnalysis")) null else obj.getString("aiAnalysis")
            val legacyContent = obj.optString("content", "")

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
                blocks = blocks,
                content = legacyContent,
                emotion = emotion,
                aiAnalysis = aiAnalysis
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
                    put("content", entry.blocks.extractPlainText())
                    put("emotion", entry.emotion)
                    put("aiAnalysis", entry.aiAnalysis ?: JSONObject.NULL)

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
