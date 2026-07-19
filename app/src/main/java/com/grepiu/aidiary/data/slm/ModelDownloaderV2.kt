package com.grepiu.aidiary.data.slm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
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
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val SHERPA_ARCHIVE = "sherpa-korean.tar.bz2"
        const val SHERPA_MODEL_DIR = "sherpa-korean"
        private const val MODEL_ASSET_PATH = "model/$MODEL_FILENAME"
    }

    // --- Sherpa-Onnx model ---

    fun getSherpaModelDir(): File = File(modelDir, SHERPA_MODEL_DIR)

    fun isSherpaModelDownloaded(): Boolean {
        val dir = getSherpaModelDir()
        val markerFile = File(dir, "download_complete.txt")
        return dir.exists() && markerFile.exists()
    }

    private fun findModelSubdir(dir: File): File? {
        val subs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
        if (subs != null && subs.size == 1) {
            val sub = subs[0]
            val modelFile = sub.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            val tokensFile = File(sub, "tokens.txt")
            if (modelFile != null && tokensFile.exists()) return sub
        }
        val rootModelFile = dir.listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
        val rootTokensFile = File(dir, "tokens.txt")
        if (rootModelFile != null && rootTokensFile.exists()) return dir
        return null
    }

    suspend fun downloadSherpaModel(
        url: String,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
        onExtracting: (suspend (Long) -> Unit)? = null
    ): Result<File> {
        val archiveFile = File(modelDir, SHERPA_ARCHIVE)
        val extractDir = getSherpaModelDir()
        
        // 1. tar.bz2 다운로드
        val archiveResult = downloadModelTo(url, SHERPA_ARCHIVE, 120L * 1024 * 1024, onProgress)
        if (archiveResult.isFailure) {
            if (archiveFile.exists()) archiveFile.delete()
            return archiveResult
        }

        // 2. 압축 해제 — 시작 시점에 콜백 호출 (파일 크기 전달)
        val totalSize = archiveFile.length()
        onExtracting?.invoke(totalSize)

        return withContext(Dispatchers.IO) {
            try {
                if (extractDir.exists()) extractDir.deleteRecursively()
                extractDir.mkdirs()

                extractTarBz2(archiveFile, extractDir, totalSize) { bytesProcessed ->
                    onProgress(bytesProcessed, totalSize)
                }
                if (archiveFile.exists()) archiveFile.delete() // 압축 파일 삭제
                
                // 성공 마커 작성
                val markerFile = File(extractDir, "download_complete.txt")
                markerFile.writeText("SUCCESS")

                if (isSherpaModelDownloaded()) {
                    Log.d(TAG, "Sherpa model extracted and verified at $extractDir")
                    Result.success(extractDir)
                } else {
                    if (extractDir.exists()) extractDir.deleteRecursively()
                    Result.failure(IOException("Sherpa model verification failed (ONNX model missing or corrupted)"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sherpa model extraction failed", e)
                if (extractDir.exists()) extractDir.deleteRecursively()
                if (archiveFile.exists()) archiveFile.delete()
                Result.failure(e)
            }
        }
    }

    private fun extractTarBz2(archiveFile: File, destDir: File, totalSize: Long, onProgress: (bytesProcessed: Long) -> Unit) {
        ProgressInputStream(FileInputStream(archiveFile)) { bytesRead ->
            onProgress(bytesRead)
        }.use { countingStream ->
            BufferedInputStream(countingStream).use { bis ->
                BZip2CompressorInputStream(bis).use { bz2 ->
                    TarArchiveInputStream(bz2).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val outFile = File(destDir, entry.name)
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tar.copyTo(fos)
                                }
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }
        }
    }

    /**
     * 바이트 읽기를 카운팅하는 InputStream 래퍼. [onBytesRead] 로 일정 간격마다 콜백.
     */
    private class ProgressInputStream(
        private val delegate: java.io.InputStream,
        private val onBytesRead: (Long) -> Unit
    ) : java.io.InputStream() {
        private var bytesRead = 0L
        private var lastReported = 0L
        private val reportInterval = 512L * 1024L // 512KB 마다 콜백

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) count(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) count(n.toLong())
            return n
        }

        private fun count(n: Long) {
            bytesRead += n
            if (bytesRead - lastReported >= reportInterval) {
                lastReported = bytesRead
                onBytesRead(bytesRead)
            }
        }

        override fun close() {
            onBytesRead(bytesRead) // 마지막 콜백
            delegate.close()
        }
    }

    // --- Common ---

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
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: SecurityException) {
            Log.w(TAG, "ACCESS_NETWORK_STATE permission denied", e)
            false
        }
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
     * API 서버로부터 모델을 직접 다운로드할 수 있는 임시 다운로드용 Presigned URL을 요청하여 가져옵니다.
     */
    private fun fetchPresignedUrl(apiUrl: String): String {
        Log.d(TAG, "Fetching Presigned URL from: $apiUrl")
        val request = Request.Builder().url(apiUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to get presigned URL: HTTP ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw IOException("Empty response body from presigned URL API")
            val json = JSONObject(bodyString)
            if (json.optString("code") == "200") {
                return json.getString("data")
            } else {
                throw IOException("API error: ${json.optString("message")}")
            }
        }
    }

    /**
     * 중계 API 또는 직접 URL로부터 모델 파일을 다운로드하여 로컬 내부 저장소에 저장합니다.
     */
    suspend fun downloadModel(apiUrl: String, onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): Result<File> =
        downloadModelTo(apiUrl, MODEL_FILENAME, (2.3 * 1024 * 1024 * 1024).toLong(), onProgress)

    /**
     * 지정된 파일명으로 URL에서 모델을 다운로드합니다.
     * @param url 다운로드 URL
     * @param filename 저장할 파일명
     * @param minSize 최소 파일 크기 (bytes)
     * @param onProgress 진행률 콜백
     */
    private suspend fun downloadModelTo(
        url: String,
        filename: String,
        minSize: Long,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> =
        withContext(Dispatchers.IO) {
            val targetFile = File(modelDir, filename)
            val tempFile = File(modelDir, "$filename.tmp")
            try {
                if (targetFile.exists()) targetFile.delete()
                
                // 기존 다운로드 진행 중이던 임시 파일의 크기를 확인 (이어받기 기준점)
                val existingLength = if (tempFile.exists()) tempFile.length() else 0L

                // 1단계: API 서버에 요청하여 정식 임시 다운로드 URL을 획득 (S3 다운로드 시 활용, 현재는 Hugging Face 직접 다운로드 사용)
                // val presignedUrl = fetchPresignedUrl(apiUrl)
                // val downloadUrl = presignedUrl

                // Hugging Face 직접 다운로드 URL 사용
                val downloadUrl = url
                Log.d(TAG, "Starting stream download from: $downloadUrl, existingLength: $existingLength")

                // 2단계: 실제 모델 파일 스트림 다운로드 (이어받기 요청 헤더 추가)
                val requestBuilder = Request.Builder().url(downloadUrl)
                    .header("User-Agent", "AIDiary/1.0")
                    .header("Accept", "application/octet-stream, */*")
                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                // HTTP 416 Range Not Satisfiable 이나 404 에러 시에는 임시 파일이 깨졌을 수 있으므로 삭제 후 재시도 유도
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
                // isRange가 true이면 append 모드로 스트림 개방
                val outputStream = FileOutputStream(tempFile, isRange)

                var actualBytesWritten = 0L
                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = if (isRange) existingLength else 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            bytesRead += read
                            actualBytesWritten = bytesRead
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                }

                if (actualBytesWritten == 0L) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("서버로부터 데이터를 받지 못했습니다. 네트워크 연결을 확인하거나 Wi-Fi 환경에서 다시 시도해 주세요.")
                    )
                }

                if (tempFile.length() < minSize) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Download incomplete or corrupt: file size too small (${tempFile.length()} / min $minSize bytes)")
                    )
                }
                if (contentLength > 0 && tempFile.length() != contentLength) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Download incomplete: ${tempFile.length()}/$contentLength bytes")
                    )
                }
                if (tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withContext Result.failure(IOException("Downloaded file is empty"))
                }

                // 최종 모델 경로로 이동 (rename 실패 시 복사 후 삭제로 폴백)
                if (!tempFile.renameTo(targetFile)) {
                    try {
                        tempFile.inputStream().use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                    } catch (e: Exception) {
                        tempFile.delete()
                        return@withContext Result.failure(IOException("Failed to copy temp file to final location: ${e.message}"))
                    }
                }
                Result.success(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during downloadModel", e)
                tempFile.delete()
                Result.failure(e)
            }
        }
}
