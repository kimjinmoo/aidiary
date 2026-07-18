package com.grepiu.aidiary.data.repository

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 데이터베이스.
 *
 * - v1: 일기/블록. FTS5 는 `onCreate` / `onOpen` 에서 raw SQL 로 생성·호환성 검사.
 * - v3.1 마이그레이션 (entities 변경 없음, version 유지): v3.0 에서 Room Entity 로 잘못
 *      만들어진 `diary_fts` 일반 테이블이 남아있다면 [recreateFtsVirtualTableIfNeeded] 가
 *      DROP 후 FTS5 가상테이블로 재생성한다.
 * - FTS5 미지원 기기(저가 OEM)에서는 검색만 LIKE 폴백. 메인 CRUD 는 정상 동작.
 *
 * 주의:
 *  - 스키마 변경 시 `version` 올리고 `fallbackToDestructiveMigration` 은 사용하지 않는다.
 *    (사용자 데이터 손실 방지)
 */
@Database(
    entities = [DiaryEntity::class, BlockEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DiaryDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao
    abstract fun searchDao(): DiarySearchDao

    companion object {
        private const val TAG = "DiaryDatabase"
        const val DB_NAME = "diary.db"

        @Volatile
        private var instance: DiaryDatabase? = null

        fun get(context: Context): DiaryDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): DiaryDatabase {
            return Room.databaseBuilder(
                context,
                DiaryDatabase::class.java,
                DB_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        createFtsTable(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // v3.0 → v3.1 마이그레이션: 잘못된 일반 테이블 → FTS5 가상테이블
                        recreateFtsVirtualTableIfNeeded(db)
                    }
                })
                .build()
        }

        /**
         * v3.0 에서 `FtsRow` 가 Room @Entity 로 잘못 매핑되어 만들어진 일반 `diary_fts` 테이블이
         * 있으면 DROP 후 FTS5 가상테이블로 재생성한다. 이미 FTS5 가상테이블이거나 테이블이 없으면
         * 안전하게 [createFtsTable] 만 호출한다.
         */
        private fun recreateFtsVirtualTableIfNeeded(db: SupportSQLiteDatabase) {
            try {
                val cursor = db.query(
                    "SELECT type, sql FROM sqlite_master WHERE name = 'diary_fts' LIMIT 1",
                    arrayOf<Any?>()
                )
                val (type, sql) = cursor.use { c ->
                    if (c.moveToFirst()) c.getString(0) to c.getString(1) else null to null
                }
                if (type == null) {
                    // 테이블 없음 → 생성
                    createFtsTable(db)
                } else if (type != "table" || sql == null || !sql.contains("VIRTUAL")) {
                    // 일반 테이블이거나 알 수 없는 형태 → DROP 후 재생성
                    Log.w(TAG, "diary_fts 가 FTS5 가상테이블이 아닙니다 (type=$type). DROP 후 재생성합니다.")
                    try { db.execSQL("DROP TABLE IF EXISTS diary_fts") } catch (_: Exception) {}
                    createFtsTable(db)
                }
                // type == "table" && sql.contains("VIRTUAL") 이면 이미 FTS5 가상테이블 → 그대로
            } catch (e: Exception) {
                Log.w(TAG, "FTS5 호환성 검사 실패. 강제 재생성합니다: ${e.message}")
                try { db.execSQL("DROP TABLE IF EXISTS diary_fts") } catch (_: Exception) {}
                createFtsTable(db)
            }
        }

        private fun createFtsTable(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS diary_fts USING fts5(
                        id UNINDEXED,
                        title,
                        content,
                        date_string UNINDEXED,
                        emotion UNINDEXED,
                        tokenize='trigram'
                    )
                    """.trimIndent()
                )
            } catch (e: Exception) {
                // FTS5 미지원. 검색은 호출자(레포지토리)에서 LIKE 폴백.
                Log.w(TAG, "FTS5 가상테이블 생성 실패 (FTS5 미지원 OEM): ${e.message}")
            }
        }
    }
}

/**
 * FTS5 입력용 row 를 raw SupportSQLiteDatabase 로 일괄 삽입.
 * runInTransaction 내부(동기 컨텍스트)에서 사용.
 *
 * FTS5 가상테이블이 없거나 컬럼이 일치하지 않으면 SQLException 을 던질 수 있는데,
 * 이 경우 일기 자체 저장은 성공해야 하므로 catch 해서 로그만 남기고 무시한다.
 * (검색은 자동으로 LIKE 폴백이 동작한다)
 */
fun SupportSQLiteDatabase.insertFtsBlocking(row: FtsRow) {
    try {
        execSQL(
            "INSERT OR REPLACE INTO diary_fts(id, title, content, date_string, emotion) VALUES(?, ?, ?, ?, ?)",
            arrayOf(row.id, row.title, row.content, row.dateString, row.emotion)
        )
    } catch (e: Exception) {
        android.util.Log.w("DiaryDatabase", "FTS insert 실패 (검색 폴백): ${e.message}")
    }
}

/**
 * FTS5 row 삭제 (diary 삭제 시 호출). 실패해도 무시.
 */
fun SupportSQLiteDatabase.deleteFtsBlocking(id: String) {
    try {
        execSQL("DELETE FROM diary_fts WHERE id = ?", arrayOf(id))
    } catch (e: Exception) {
        android.util.Log.w("DiaryDatabase", "FTS delete 실패 (검색 폴백): ${e.message}")
    }
}

/**
 * FTS5 전체 재구축. `INSERT INTO diary_fts(diary_fts) VALUES('rebuild')` 로 인덱스 무결성 회복.
 * 실패해도 무시.
 */
fun SupportSQLiteDatabase.rebuildFtsBlocking() {
    try {
        execSQL("INSERT INTO diary_fts(diary_fts) VALUES('rebuild')")
    } catch (e: Exception) {
        android.util.Log.w("DiaryDatabase", "FTS rebuild 실패: ${e.message}")
    }
}
