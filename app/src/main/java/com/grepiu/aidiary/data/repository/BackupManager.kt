package com.grepiu.aidiary.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 다형성 역직렬화 에러를 회피하기 위해, DB 엔티티 목록을 평평하게 담는 백업용 래퍼 클래스.
 */
data class BackupData(
    val diaries: List<DiaryEntity>,
    val blocks: List<BlockEntity>
)

/**
 * 다이어리 전체 데이터 (Room DB + 내부 저장소 미디어 파일) 백업 및 복원 매니저.
 */
class BackupManager(
    private val context: Context,
    private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_JSON_NAME = "backup_data.json"
        
        // 실제 저장되는 미디어 폴더명
        private const val DIR_IMAGES = "diary_images"
        private const val DIR_VIDEOS = "diary_videos"
    }

    private val gson = Gson()

    /**
     * 전체 백업 데이터를 생성하여 사용자가 지정한 URI(ZIP)에 출력합니다.
     */
    suspend fun exportToZip(targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // 1) 모든 다이어리 & 블록 로우 가져오기
            val (diaries, blocks) = repository.getRawBackupData()
            val backupData = BackupData(diaries = diaries, blocks = blocks)
            val jsonString = gson.toJson(backupData)

            // 2) ZIP 생성 스트림 열기
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                    // 2-1) JSON 데이터 압축 추가
                    val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
                    val jsonEntry = ZipEntry(BACKUP_JSON_NAME)
                    zos.putNextEntry(jsonEntry)
                    zos.write(jsonBytes)
                    zos.closeEntry()

                    // 2-2) 로컬 미디어 파일(diary_images, diary_videos) 압축 추가
                    val imagesDir = File(context.filesDir, DIR_IMAGES)
                    if (imagesDir.exists() && imagesDir.isDirectory) {
                        imagesDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                addFileToZip(zos, file, "$DIR_IMAGES/${file.name}")
                            }
                        }
                    }

                    val videosDir = File(context.filesDir, DIR_VIDEOS)
                    if (videosDir.exists() && videosDir.isDirectory) {
                        videosDir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                addFileToZip(zos, file, "$DIR_VIDEOS/${file.name}")
                            }
                        }
                    }
                }
            } ?: throw IllegalStateException("백업 출력 스트림을 열 수 없습니다.")
        }
    }

    /**
     * 선택한 백업 ZIP 파일로부터 데이터를 파싱하여 복원합니다.
     */
    suspend fun importFromZip(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var restoredCount = 0
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry: ZipEntry? = zis.getNextEntry()
                    var jsonContent: String? = null

                    while (entry != null) {
                        val name = entry.name
                        if (name == BACKUP_JSON_NAME) {
                            // JSON 역직렬화용 임시 버퍼 로드
                            jsonContent = zis.bufferedReader(Charsets.UTF_8).readText()
                        } else if (name.startsWith("$DIR_IMAGES/") || name.startsWith("$DIR_VIDEOS/")) {
                            // 미디어 복구 대상 폴더에 덮어쓰기 복사
                            val targetFile = File(context.filesDir, name)
                            // 부모 폴더가 존재하지 않으면 자동 생성
                            targetFile.parentFile?.let {
                                if (!it.exists()) it.mkdirs()
                            }
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.getNextEntry()
                    }

                    // JSON 데이터 복원 처리
                    if (!jsonContent.isNullOrBlank()) {
                        val listType = object : TypeToken<BackupData>() {}.type
                        val backupData: BackupData = gson.fromJson(jsonContent, listType)
                        if (backupData.diaries.isNotEmpty()) {
                            repository.restoreRawBackupData(
                                diaries = backupData.diaries,
                                blocks = backupData.blocks
                            )
                            restoredCount = backupData.diaries.size
                        }
                    } else {
                        throw IllegalArgumentException("백업 데이터 파일(backup_data.json)을 찾을 수 없거나 비어 있습니다.")
                    }
                }
            } ?: throw IllegalStateException("백업 입력 스트림을 열 수 없습니다.")
            restoredCount
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, zipPath: String) {
        try {
            FileInputStream(file).use { fis ->
                val entry = ZipEntry(zipPath)
                zos.putNextEntry(entry)
                val buffer = ByteArray(8192)
                var read = fis.read(buffer)
                while (read != -1) {
                    zos.write(buffer, 0, read)
                    read = fis.read(buffer)
                }
                zos.closeEntry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "ZIP 압축 추가 실패 ($zipPath): ${e.message}")
        }
    }
}
