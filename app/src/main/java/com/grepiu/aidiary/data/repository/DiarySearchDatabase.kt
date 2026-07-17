package com.grepiu.aidiary.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.grepiu.aidiary.data.model.DiaryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 일기 텍스트를 SQLite 로 인덱싱하여 부분 문자열/접두사 검색을 빠르게 수행한다.
 *
 * v2 핵심 변경 (FTS5 미지원 기기 대응):
 *  - 기기 SQLite 에 FTS5 모듈이 있으면 FTS5 + trigram 가상 테이블 사용 (고속 + 한국어 부분 매칭)
 *  - 없으면 자동으로 일반 테이블 + LIKE 검색으로 폴백
 *  - 가용 여부는 앱 시작 시 인메모리 DB 에서 1회 테스트 후 SharedPreferences 에 캐시
 *  - 같은 DB 안에서 [TABLE_NAME] / [FALLBACK_TABLE_NAME] 중 활성 테이블만 사용
 *
 *  - 토크나이저: FTS5 사용 시 `trigram` (3-gram 슬라이딩 윈도우) → 한국어·일본어·중국어처럼
 *    공백으로 단어가 구분되지 않는 언어에서 부분 문자열 매칭이 가능하다.
 *  - 정렬: FTS5 경로는 rank + 날짜 가중치, LIKE 경로는 최신 일기 우선 + 날짜 가중치.
 *  - 원본 데이터는 [DiaryRepository] 가 JSON 으로 영속화하므로 이 클래스는 "검색용 인덱스" 역할만 한다.
 *
 * 주의:
 *  - 일기 본문이 비어 있거나 인덱스가 비어 있으면 결과가 0건 → 호출자에서 폴백 처리 필요.
 *  - `rank` 값은 음수(더 좋을수록 더 큰 음수)이며, [DiarySearchHit.relevance] 에서
 *    `1 / (1 + |rank|) * dateWeight` 로 0~1 사이 점수화한다.
 */
class DiarySearchDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    companion object {
        private const val TAG = "DiarySearchDB"
        private const val DB_NAME = "diary_search.db"
        private const val DB_VERSION = 1
        const val TABLE_NAME = "diary_fts"

        /** FTS5 미지원 기기에서 폴백하는 일반 테이블. */
        private const val FALLBACK_TABLE_NAME = "diary_search"

        /** FTS5 가용 여부 캐시용 SharedPreferences. */
        private const val PREFS_NAME = "diary_search_prefs"
        private const val KEY_FTS5_AVAILABLE = "fts5_available"

        /** 본문 너무 길면 인덱스 비대화. 컨텍스트 주입 시점에도 어차피 잘라 쓰므로 2000자면 충분. */
        private const val MAX_CONTENT_CHARS = 2000

        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * FTS5 가용 여부. 인메모리 DB 에서 1회만 테스트하고 SharedPreferences 에 캐시.
     * 일부 OEM/특수 빌드 Android 는 시스템 SQLite 에 FTS5 모듈이 빠져있다.
     */
    val fts5Available: Boolean = run {
        if (prefs.contains(KEY_FTS5_AVAILABLE)) {
            prefs.getBoolean(KEY_FTS5_AVAILABLE, false)
        } else {
            val available = try {
                val testDb = SQLiteDatabase.openOrCreateDatabase(":memory:", null)
                try {
                    testDb.execSQL("CREATE VIRTUAL TABLE fts5_test USING fts5(x)")
                    true
                } finally {
                    try { testDb.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "FTS5 test failed: ${e.message}. Falling back to LIKE search.")
                false
            }
            prefs.edit().putBoolean(KEY_FTS5_AVAILABLE, available).apply()
            available
        }
    }

    /** 현재 사용 가능한 테이블 이름. FTS5 가 false 면 [FALLBACK_TABLE_NAME]. */
    private val activeTable: String
        get() = if (fts5Available) TABLE_NAME else FALLBACK_TABLE_NAME

    override fun onCreate(db: SQLiteDatabase) {
        if (fts5Available) {
            try {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE $TABLE_NAME USING fts5(
                        id UNINDEXED,
                        title,
                        content,
                        dateString UNINDEXED,
                        emotion UNINDEXED,
                        tokenize='trigram'
                    )
                    """.trimIndent()
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "FTS5 CREATE failed despite detection: ${e.message}. Falling back.")
                prefs.edit().putBoolean(KEY_FTS5_AVAILABLE, false).apply()
                // 트랜잭션은 살아있으므로 아래 폴백 CREATE 계속 진행 가능
            }
        }
        // 폴백: 일반 테이블 + LIKE 검색. 200건 한도라 인덱스 없이도 충분.
        db.execSQL(
            """
            CREATE TABLE $FALLBACK_TABLE_NAME (
                id TEXT PRIMARY KEY,
                title TEXT,
                content TEXT,
                dateString TEXT,
                emotion TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_${FALLBACK_TABLE_NAME}_date ON $FALLBACK_TABLE_NAME(dateString)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $FALLBACK_TABLE_NAME")
        onCreate(db)
    }

    /**
     * 단건 upsert. 동일 id 가 있으면 통째 교체.
     */
    fun upsert(entry: DiaryEntry) {
        val values = ContentValues().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("content", entry.contentText.take(MAX_CONTENT_CHARS))
            put("dateString", DATE_FMT.format(Date(entry.timestamp)))
            put("emotion", entry.emotion)
        }
        writableDatabase.insertWithOnConflict(
            activeTable,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun delete(id: String) {
        writableDatabase.delete(activeTable, "id = ?", arrayOf(id))
    }

    fun clear() {
        writableDatabase.delete(activeTable, null, null)
    }

    /**
     * 검색 진입점. FTS5 가용 여부에 따라 분기.
     */
    fun search(query: String, limit: Int = 30): List<DiarySearchHit> {
        return if (fts5Available) searchFts(query, limit) else searchLike(query, limit)
    }

    private fun searchFts(query: String, limit: Int): List<DiarySearchHit> {
        val cleanQuery = sanitizeFtsQuery(query)
        if (cleanQuery.isBlank()) return emptyList()

        val cursor = try {
            readableDatabase.rawQuery(
                """
                SELECT id, title, content, dateString, emotion, rank
                FROM $TABLE_NAME
                WHERE $TABLE_NAME MATCH ?
                ORDER BY rank
                LIMIT ?
                """.trimIndent(),
                arrayOf(cleanQuery, limit.toString())
            )
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 search failed: ${e.message}")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val results = mutableListOf<DiarySearchHit>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val title = it.getString(1) ?: ""
                val content = it.getString(2) ?: ""
                val dateString = it.getString(3) ?: ""
                val emotion = it.getString(4) ?: "Neutral"
                val ftsRank = it.getDouble(5)
                val dateWeight = computeDateWeight(dateString, now)
                val relevance = (1.0 / (1.0 + Math.abs(ftsRank))) * dateWeight
                results.add(
                    DiarySearchHit(
                        id = id,
                        title = title,
                        content = content,
                        dateString = dateString,
                        emotion = emotion,
                        ftsRank = ftsRank,
                        dateWeight = dateWeight,
                        relevance = relevance
                    )
                )
            }
        }
        return results.sortedByDescending { it.relevance }
    }

    /**
     * FTS5 미지원 폴백. 토큰 2개 이상만 매칭, 각 토큰이 title 또는 content 의 부분 문자열로 등장.
     * 한국어는 공백이 없어도 substring 매칭이 동작한다 (LIKE '%부산%').
     * 토큰들은 OR 결합 (어느 하나라도 매칭되면 포함).
     */
    private fun searchLike(query: String, limit: Int): List<DiarySearchHit> {
        val tokens = sanitizeLikeTokens(query)
        if (tokens.isEmpty()) return emptyList()

        val whereClauses = tokens.joinToString(" OR ") { "(title LIKE ? OR content LIKE ?)" }
        val args = tokens.flatMap { token -> listOf("%$token%", "%$token%") }.toMutableList()
        args.add(limit.toString())

        val cursor = try {
            readableDatabase.rawQuery(
                """
                SELECT id, title, content, dateString, emotion
                FROM $FALLBACK_TABLE_NAME
                WHERE $whereClauses
                ORDER BY dateString DESC
                LIMIT ?
                """.trimIndent(),
                args.toTypedArray()
            )
        } catch (e: Exception) {
            Log.w(TAG, "LIKE search failed: ${e.message}")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val results = mutableListOf<DiarySearchHit>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val title = it.getString(1) ?: ""
                val content = it.getString(2) ?: ""
                val dateString = it.getString(3) ?: ""
                val emotion = it.getString(4) ?: "Neutral"
                val dateWeight = computeDateWeight(dateString, now)
                // FTS rank 가 없으므로 단순히 날짜 가중치만 점수로 사용
                val relevance = dateWeight
                results.add(
                    DiarySearchHit(
                        id = id,
                        title = title,
                        content = content,
                        dateString = dateString,
                        emotion = emotion,
                        ftsRank = 0.0,
                        dateWeight = dateWeight,
                        relevance = relevance
                    )
                )
            }
        }
        return results.sortedByDescending { it.relevance }
    }

    /**
     * FTS5 MATCH 전용 안전 변환.
     * - 따옴표/괄호/콜론 등 FTS5 연산자 의미가 있는 문자를 제거한다.
     * - 토큰을 공백으로 split 한 뒤 큰따옴표로 감싸 OR 결합한다.
     *   ("부산 여행" → "\"부산\" OR \"여행\"")
     * - FTS5 trigram 토크나이저는 3자 이상 토큰만 매칭하므로 2자 이하는 제거한다.
     */
    private fun sanitizeFtsQuery(query: String): String {
        val cleaned = query
            .replace(Regex("[\\\"'(){}\\[\\]:*+\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return ""
        val tokens = cleaned.split(" ")
            .filter { it.length >= 3 }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "\"$it\"" }
    }

    /**
     * LIKE 검색용 토큰 추출. FTS 와 달리 2자 이상 매칭 (substring 이라 2자도 의미 있음).
     * 따옴표/괄호 등 SQL LIKE 의미가 있는 문자 제거 후 공백 split.
     */
    private fun sanitizeLikeTokens(query: String): List<String> {
        val cleaned = query
            .replace(Regex("[\\\"'(){}\\[\\]:*+\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(" ")
            .filter { it.length >= 2 }
            .distinct()
    }

    /**
     * 날짜 가중치: 1년 지난 기록은 ~0.45, 2년 지난 기록은 ~0.29 로 감쇠.
     * 미래 날짜(타임스탬프 오차)는 1.0 으로 처리.
     */
    private fun computeDateWeight(dateString: String, nowMillis: Long): Double {
        return try {
            val date = DATE_FMT.parse(dateString) ?: return 1.0
            val daysAgo = (nowMillis - date.time) / (1000.0 * 60 * 60 * 24)
            if (daysAgo < 0) 1.0
            else 1.0 / (1.0 + daysAgo / 365.0 * 1.2)
        } catch (e: Exception) {
            1.0
        }
    }
}

/**
 * 검색 1건의 결과. 호출자는 [relevance] 내림차순으로 정렬된 리스트를 받는다.
 */
data class DiarySearchHit(
    val id: String,
    val title: String,
    val content: String,
    val dateString: String,
    val emotion: String,
    val ftsRank: Double,
    val dateWeight: Double,
    val relevance: Double
)
