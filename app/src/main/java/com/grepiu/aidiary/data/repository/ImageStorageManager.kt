package com.grepiu.aidiary.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 일기에 첨부된 이미지를 앱 내부 저장소(filesDir/diary_images/)에 복사·관리하는 유틸리티.
 *
 * - 일기 삭제 시 해당 일기에서 참조 중인 이미지도 함께 정리합니다.
 * - 절대 경로 대신 상대 경로(filesDir 기준)를 DB/JSON에 저장해 기기 간 이식성을 확보합니다.
 */
class ImageStorageManager(private val context: Context) {

    private val baseDir: File by lazy {
        File(context.filesDir, IMAGE_DIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * [sourceUri] 가 가리키는 이미지를 앱 내부 저장소로 복사하고,
     * DB/JSON 에 저장할 상대 경로("diary_images/<uuid>.jpg") 를 반환합니다.
     *
     * FileProvider 의 content:// URI, file:// URI, 갤러리 content URI 등 모든 스킴을
     * [ContentResolver.openInputStream] 으로 통일 처리합니다.
     */
    suspend fun importFromUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(baseDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "이미지 스트림을 열 수 없습니다: $sourceUri" }
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            "${IMAGE_DIR}/${target.name}"
        }
    }

    /**
     * 상대 경로(예: "diary_images/uuid.jpg") 를 절대 [File] 로 변환합니다.
     * 파일이 없으면 null 을 반환합니다.
     */
    fun resolve(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val f = File(context.filesDir, relativePath)
        return if (f.exists()) f else null
    }

    /**
     * 단일 이미지를 삭제합니다. 파일이 없으면 성공으로 간주(no-op).
     */
    fun delete(relativePath: String) {
        if (relativePath.isBlank()) return
        val f = File(context.filesDir, relativePath)
        if (f.exists()) f.delete()
    }

    /**
     * 일기 항목과 연결된 모든 이미지를 삭제합니다.
     */
    fun deleteForEntry(entry: com.grepiu.aidiary.data.model.DiaryEntry) {
        entry.blocks.filterIsInstance<com.grepiu.aidiary.data.model.ContentBlock.ImageBlock>()
            .forEach { delete(it.relativePath) }
    }

    companion object {
        private const val IMAGE_DIR = "diary_images"
    }
}
