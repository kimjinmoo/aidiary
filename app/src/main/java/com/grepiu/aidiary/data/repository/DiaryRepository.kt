package com.grepiu.aidiary.data.repository

import android.content.Context
import com.grepiu.aidiary.data.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 일기 기록의 로컬 저장소 캐싱 및 파일 쓰기를 관리하는 레포지토리 클래스입니다.
 */
class DiaryRepository(private val context: Context) {
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
     * 특정 일기를 삭제합니다.
     */
    fun deleteEntry(id: String): List<DiaryEntry> {
        val updated = synchronized(cacheLock) {
            val current = getOrLoadCache().filter { it.id != id }
            persist(current)
            current
        }
        cachedDiaries = updated
        return updated
    }

    /**
     * 전체 일기 데이터를 비웁니다.
     */
    fun clearAll() {
        synchronized(cacheLock) {
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
            (0 until arr.length()).map { i ->
                parseEntry(arr.getJSONObject(i))
            }.filterNotNull().sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseEntry(obj: JSONObject): DiaryEntry? {
        return try {
            DiaryEntry(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                title = obj.getString("title"),
                content = obj.getString("content"),
                emotion = obj.optString("emotion", "Neutral"),
                aiAnalysis = if (obj.isNull("aiAnalysis")) null else obj.getString("aiAnalysis")
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
                    put("content", entry.content)
                    put("emotion", entry.emotion)
                    put("aiAnalysis", entry.aiAnalysis ?: JSONObject.NULL)
                }
                arr.put(obj)
            }
            tempFile.writeText(arr.toString())
            tempFile.renameTo(file)
        } catch (_: Exception) {}
    }
}
