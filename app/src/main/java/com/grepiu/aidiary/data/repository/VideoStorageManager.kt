package com.grepiu.aidiary.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 일기에 첨부된 영상을 앱 내부 저장소(filesDir/diary_videos/)에 복사·관리하는 유틸리티.
 *
 * - [ImageStorageManager] 와 동일 패턴. 절대 경로 대신 상대 경로(filesDir 기준)를 DB 에 저장.
 * - 확장자는 ContentResolver 의 mime type 에서 추론. 알 수 없으면 `.bin` 으로 저장.
 * - 일기 삭제 시 해당 일기에서 참조 중인 영상을 일괄 정리합니다.
 * - 영상 길이 검증 ([MAX_VIDEO_DURATION_MS]) 은 [getVideoDurationMs] 로 확인.
 */
class VideoStorageManager(private val context: Context) {

    private val baseDir: File by lazy {
        File(context.filesDir, VIDEO_DIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * [sourceUri] 의 영상을 앱 내부 저장소로 복사하고 DB 에 저장할 상대 경로를 반환한다.
     * 확장자는 source URI 의 mime type 에서 추론.
     */
    suspend fun importFromUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ext = inferExtension(sourceUri)
            val target = File(baseDir, "${UUID.randomUUID()}.$ext")
            val fd = try {
                context.contentResolver.openAssetFileDescriptor(sourceUri, "r")
            } catch (e: Exception) {
                null
            }
            if (fd != null) {
                fd.createInputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                context.contentResolver.openInputStream(sourceUri).use { input ->
                    requireNotNull(input) { "영상 스트림을 열 수 없습니다: $sourceUri" }
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            "${VIDEO_DIR}/${target.name}"
        }
    }

    /**
     * 영상 길이(ms) 조회. [MediaMetadataRetriever] 사용. 파일 손상/형식 미지원 시 null.
     */
    suspend fun getVideoDurationMs(file: File): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "getVideoDurationMs failed for ${file.name}: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * 상대 경로를 절대 [File] 로 변환. 파일이 없으면 null.
     */
    fun resolve(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val f = File(context.filesDir, relativePath)
        return if (f.exists()) f else null
    }

    /**
     * 단일 영상을 삭제. 파일이 없으면 no-op.
     */
    fun delete(relativePath: String) {
        if (relativePath.isBlank()) return
        val f = File(context.filesDir, relativePath)
        if (f.exists()) f.delete()
    }

    /**
     * 일기 1건에서 참조 중인 모든 영상을 일괄 삭제.
     */
    fun deleteForEntry(entry: com.grepiu.aidiary.data.model.DiaryEntry) {
        entry.blocks
            .filterIsInstance<com.grepiu.aidiary.data.model.ContentBlock.SpatialMediaBlock>()
            .filter { it.mediaType == com.grepiu.aidiary.data.model.SpatialMediaType.VIDEO }
            .forEach { block ->
                block.paths.forEach { delete(it) }
            }
    }

    private fun inferExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        if (mime != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
            // 일부 mime type 은 직접 매핑
            when (mime.lowercase()) {
                "video/mp4" -> return "mp4"
                "video/quicktime" -> return "mov"
                "video/x-matroska", "video/mkv" -> return "mkv"
                "video/webm" -> return "webm"
                "video/3gpp" -> return "3gp"
            }
        }
        // URI 의 마지막 path segment 에서 확장자 추출 시도
        val last = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && it.length <= 5 }
        if (last != null) return last
        return "mp4"
    }

    companion object {
        private const val TAG = "VideoStorageManager"
        private const val VIDEO_DIR = "diary_videos"

        /** 첨부 가능한 영상 최대 길이 (30초). export 시에도 동일 기준. */
        const val MAX_VIDEO_DURATION_MS: Long = 30_000L
    }
}
