package com.grepiu.aidiary.data.slm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.app.ActivityManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * gemma-4-E2B-it.litertlm 온디바이스 거대 언어 모델 파일의 다운로드 및 관리를 담당하는 다운로더 클래스입니다.
 */
class ModelDownloaderV2(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloaderV2"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"         // 로컬 저장소에 저장될 모델 파일명
        private const val MODEL_ASSET_PATH = "model/$MODEL_FILENAME" // 에셋 폴더 내 모델 경로
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.MINUTES)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val modelDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "ml")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getModelFile(): File = File(modelDir, MODEL_FILENAME)

    fun isModelDownloaded(): Boolean = getModelFile().exists()

    fun deleteModelFile() {
        val file = getModelFile()
        if (file.exists()) file.delete()
    }

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isLowRamDevice(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // 6GB 기기는 커널/시스템 예약분 때문에 totalMem이 6GiB 미만으로 보고되므로 6GiB 기준으로 판별
        return memoryInfo.totalMem <= 6L * 1024 * 1024 * 1024
    }

    fun isModelInAssets(): Boolean {
        return try {
            context.assets.open(MODEL_ASSET_PATH).use { }
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 바이트 단위를 가독성 있는 용량 문자열(KB, MB, GB)로 변환해 주는 헬퍼 함수입니다.
     */
    fun toHumanReadableSize(bytes: Long): String {
        val kb = 1000.0
        val mb = kb * 1000
        val gb = mb * 1000

        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.2f KB", bytes / kb)
            else -> "$bytes Bytes"
        }
    }

    suspend fun copyFromAssets(onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val destFile = getModelFile()
                if (destFile.exists()) destFile.delete()

                val totalSize = try {
                    context.assets.openFd(MODEL_ASSET_PATH).use { it.length }
                } catch (e: Exception) {
                    context.assets.open(MODEL_ASSET_PATH).use { it.available().toLong() }
                }

                val inputStream = context.assets.open(MODEL_ASSET_PATH)
                val outputStream = FileOutputStream(destFile)

                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var bytesCopied: Long = 0
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            onProgress(bytesCopied, totalSize)
                        }
                    }
                }

                if (destFile.exists() && destFile.length() > 0) {
                    Result.success(destFile)
                } else {
                    Result.failure(IOException("Copied file is empty"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun copyFromLocalSource(sourcePath: String): File {
        val destFile = getModelFile()
        if (destFile.exists()) destFile.delete()

        FileInputStream(sourcePath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * 중계 API 또는 직접 URL로부터 모델 파일을 다운로드하여 로컬 내부 저장소에 저장합니다.
     */
    suspend fun downloadModel(apiUrl: String, onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val modelFile = getModelFile()
            val tempFile = File(modelDir, "$MODEL_FILENAME.tmp")
            try {
                if (modelFile.exists()) modelFile.delete()
                
                // 기존 다운로드 진행 중이던 임시 파일의 크기를 확인 (이어받기 기준점)
                val existingLength = if (tempFile.exists()) tempFile.length() else 0L

                val downloadUrl = apiUrl
                Log.d(TAG, "Starting stream download from: $downloadUrl, existingLength: $existingLength")

                // 이어받기 요청 헤더 추가
                val requestBuilder = Request.Builder().url(downloadUrl)
                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                // HTTP 416 Range Not Satisfiable 이나 404 에러 시에는 임시 파일이 깨졌을 수 있으므로 삭제 후 재시도
                if (!response.isSuccessful) {
                    if (response.code == 416 || response.code == 404) {
                        if (tempFile.exists()) tempFile.delete()
                    }
                    return@withContext Result.failure(
                        IOException("Download failed: HTTP ${response.code}")
                    )
                }

                // 206 Partial Content일 때만 이어받기 모드 적용. 200 OK이면 처음부터 다시 씀
                val isRange = response.code == 206
                val body = response.body
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                val contentLength = body.contentLength()
                val totalBytes = if (isRange) existingLength + contentLength else contentLength

                if (!isRange) {
                    if (tempFile.exists()) tempFile.delete()
                }

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile, isRange)

                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = if (isRange) existingLength else 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            bytesRead += read
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                }

                // 크기 검사 (2.3GB 미만은 모델 깨짐으로 판정)
                if (tempFile.length() < 2.0 * 1024 * 1024 * 1024) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Download incomplete or corrupt: file size is too small (${tempFile.length()} bytes)")
                    )
                }
                if (tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withContext Result.failure(IOException("Downloaded file is empty"))
                }

                // 최종 모델 경로로 이동
                if (!tempFile.renameTo(modelFile)) {
                    tempFile.delete()
                    return@withContext Result.failure(IOException("Failed to move model file into place"))
                }
                Result.success(modelFile)
            } catch (e: Exception) {
                // 다운로드 중 에러 발생 시 임시 파일은 유지하여 다음에 이어받을 수 있도록 함
                Result.failure(e)
            }
        }
}
