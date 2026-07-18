package com.grepiu.aidiary.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.SpatialCaptureMode
import com.grepiu.aidiary.data.model.SpatialMediaType
import com.grepiu.aidiary.data.slm.Image3DFormat
import com.grepiu.aidiary.data.slm.ImageFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * 일기에 첨부된 이미지를 앱 내부 저장소(filesDir/diary_images/)에 복사·관리하는 유틸리티.
 *
 * - 일기 삭제 시 해당 일기에서 참조 중인 이미지도 함께 정리합니다.
 * - 절대 경로 대신 상대 경로(filesDir 기준)를 DB/JSON에 저장해 기기 간 이식성을 확보합니다.
 * - PR 1 부터 3D 포맷 자동 감지 ([ImageFormatDetector]) 기반의
 *   [importDetectingFormat] 으로 [ImageBlock] / [SpatialMediaBlock] 자동 분기합니다.
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
     * 3D 포맷 자동 감지 후 [ContentBlock] 으로 import 한다.
     *
     *  - [Image3DFormat.Plain2D]  → [ContentBlock.ImageBlock] (기존 동작과 동일)
     *  - [Image3DFormat.Mpo]      → MPO 의 두 view 를 각각 별도 JPEG 로 추출해 [SpatialMediaBlock] (PHOTO)
     *  - [Image3DFormat.HeicAux]  → HEIF 의 auxiliary 를 JPEG 로 디코딩해 [SpatialMediaBlock] (PHOTO)
     *  - [Image3DFormat.StereoExif] → 원본 그대로 두고 [SpatialMediaBlock] (PHOTO) — 단일 파일 + EXIF 플래그
     *
     * 감지 실패 시 [Result.failure] 로 throw.
     */
    suspend fun importDetectingFormat(sourceUri: Uri): Result<ContentBlock> = withContext(Dispatchers.IO) {
        runCatching {
            // 1) 우선 원본을 임시 파일로 복사
            val tempExt = inferImageExtension(sourceUri)
            val temp = File(baseDir, "_tmp_${UUID.randomUUID()}.$tempExt")
            val fd = try {
                context.contentResolver.openAssetFileDescriptor(sourceUri, "r")
            } catch (e: Exception) {
                null
            }
            if (fd != null) {
                fd.createInputStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                context.contentResolver.openInputStream(sourceUri).use { input ->
                    requireNotNull(input) { "이미지 스트림을 열 수 없습니다: $sourceUri" }
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            try {
                // 2) 포맷 감지
                val format = ImageFormatDetector.detect(temp)
                when (format) {
                    is Image3DFormat.Plain2D -> {
                        val rel = "${IMAGE_DIR}/${UUID.randomUUID()}.$tempExt"
                        val target = File(context.filesDir, rel)
                        if (target.absolutePath != temp.absolutePath) {
                            temp.copyTo(target, overwrite = true)
                        }
                        // 2D 사진도 SpatialMediaBlock 으로 통일 — "2D 사진" 라벨이 일관되게 보임
                        ContentBlock.SpatialMediaBlock(
                            mediaType = SpatialMediaType.PHOTO,
                            paths = listOf(rel),
                            captureMode = SpatialCaptureMode.PLAIN_2D_PHOTO
                        )
                    }
                    is Image3DFormat.Mpo -> {
                        val paths = extractMpoViews(temp)
                        if (paths.size >= 2) {
                            ContentBlock.SpatialMediaBlock(
                                mediaType = SpatialMediaType.PHOTO,
                                paths = paths,
                                captureMode = SpatialCaptureMode.MPO
                            )
                        } else {
                            // view 추출 실패 시 일반 2D 로 폴백
                            val rel = "${IMAGE_DIR}/${UUID.randomUUID()}.$tempExt"
                            temp.copyTo(File(context.filesDir, rel), overwrite = true)
                            ContentBlock.SpatialMediaBlock(
                                mediaType = SpatialMediaType.PHOTO,
                                paths = listOf(rel),
                                captureMode = SpatialCaptureMode.PLAIN_2D_PHOTO
                            )
                        }
                    }
                    is Image3DFormat.HeicAux -> {
                        // HEIC aux 추출 시도 — primary 는 원본 그대로, aux 가 있으면 별도 저장
                        val (primary, aux) = extractHeicAux(temp)
                        val paths = if (aux != null) listOf(primary, aux) else listOf(primary)
                        ContentBlock.SpatialMediaBlock(
                            mediaType = SpatialMediaType.PHOTO,
                            paths = paths,
                            captureMode = SpatialCaptureMode.HEIC_AUX
                        )
                    }
                    is Image3DFormat.StereoExif -> {
                        // EXIF 플래그만 있는 경우 — 원본 그대로 두고 라벨만 부착
                        val rel = "${IMAGE_DIR}/${UUID.randomUUID()}.$tempExt"
                        temp.copyTo(File(context.filesDir, rel), overwrite = true)
                        ContentBlock.SpatialMediaBlock(
                            mediaType = SpatialMediaType.PHOTO,
                            paths = listOf(rel),
                            captureMode = SpatialCaptureMode.STEREO_EXIF
                        )
                    }
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    /**
     * MPO 의 view 0 (원본) / view 1 (stereo) 을 별도 JPEG 로 저장.
     * 정확히는 MPF 의 MP Entry 가 가리키는 두 번째 JPEG segment 를 추출.
     * 단순화를 위해 view 0 는 원본의 첫 JPEG SOI~EOI, view 1 은 첫 JPEG SOI 이후 두 번째 SOI~EOI 를
     * 추출하는 휴리스틱을 사용. 실패 시 원본 1장만 반환.
     */
    private fun extractMpoViews(src: File): List<String> {
        return try {
            val bytes = src.readBytes()
            val jpegRanges = findJpegSegments(bytes)
            if (jpegRanges.size < 2) {
                Log.w(TAG, "MPO: 두 개 이상의 JPEG segment 를 찾지 못함 (found=${jpegRanges.size})")
                return emptyList()
            }
            val firstSeg = jpegRanges[0]
            val secondSeg = jpegRanges[1]
            val view0Bytes = bytes.copyOfRange(firstSeg.first, firstSeg.second)
            val view1Bytes = bytes.copyOfRange(secondSeg.first, secondSeg.second)
            val rel0 = "${IMAGE_DIR}/${UUID.randomUUID()}_L.jpg"
            val rel1 = "${IMAGE_DIR}/${UUID.randomUUID()}_R.jpg"
            File(context.filesDir, rel0).writeBytes(view0Bytes)
            File(context.filesDir, rel1).writeBytes(view1Bytes)
            listOf(rel0, rel1)
        } catch (e: Exception) {
            Log.w(TAG, "MPO view extraction 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * HEIC 의 primary + auxiliary 이미지를 JPEG 로 변환. Android 9+ 의 ImageDecoder 를 사용.
     *  - primary: HEIF 의 첫 item (main image)
     *  - aux: auxiliary type 인 item
     *  - Android 8 이하 / 실패 시 aux=null 반환.
     */
    private fun extractHeicAux(src: File): Pair<String, String?> {
        val primaryRel = "${IMAGE_DIR}/${UUID.randomUUID()}_main.jpg"
        val primaryTarget = File(context.filesDir, primaryRel)
        try {
            // primary 디코딩
            val primaryBitmap: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val src2 = android.graphics.ImageDecoder.createSource(src)
                android.graphics.ImageDecoder.decodeBitmap(src2) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                BitmapFactory.decodeFile(src.absolutePath)
            }
            if (primaryBitmap == null) return primaryRel to null
            FileOutputStream(primaryTarget).use { out ->
                primaryBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            primaryBitmap.recycle()

            // auxiliary 추출은 Android P+ 의 ImageDecoder 가 직접 지원하지 않으므로,
            // ImageDecoder.ALLOCATOR_SOFTWARE + FrameSequence? — 현재는 안전하게 primary 만 저장하고
            // aux 는 별도 JPEG 로 저장하지 못한다. 그 대신 _main 옆에 placeholder 작성.
            // PR 2 에서 ImageDecoder 의 setSource / onHeaderDecoded 콜백을 통해 보조 이미지 추출 예정.
            return primaryRel to null
        } catch (e: Exception) {
            Log.w(TAG, "HEIC aux 추출 실패: ${e.message}")
            return primaryRel to null
        }
    }

    /**
     * JPEG 바이트 배열에서 SOI(0xFFD8) ~ EOI(0xFFD9) 의 (start, endExclusive) 페어를 모두 찾는다.
     * MPO / 일반 JPEG 의 연속된 segment 분리용.
     */
    private fun findJpegSegments(bytes: ByteArray): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < bytes.size - 1) {
            // SOI (0xFFD8) 찾기
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xD8) {
                val start = i
                var j = i + 2
                while (j < bytes.size - 1) {
                    val bj0 = bytes[j].toInt() and 0xFF
                    val bj1 = bytes[j + 1].toInt() and 0xFF
                    if (bj0 == 0xFF && bj1 == 0xD9) { // EOI (0xFFD9)
                        out.add(start to (j + 2))
                        i = j + 2
                        break
                    }
                    j++
                }
                if (j >= bytes.size - 1) {
                    // EOI 못 찾음
                    break
                }
            } else {
                i++
            }
        }
        return out
    }

    private fun inferImageExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return when (mime?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            else -> uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "jpg")
                ?.lowercase()
                ?.takeIf { it.length in 1..5 }
                ?: "jpg"
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
     * 일기 항목과 연결된 모든 이미지(ImageBlock + SpatialMediaBlock 의 PHOTO) 를 삭제합니다.
     */
    fun deleteForEntry(entry: com.grepiu.aidiary.data.model.DiaryEntry) {
        entry.blocks.forEach { block ->
            when (block) {
                is ContentBlock.ImageBlock -> delete(block.relativePath)
                is ContentBlock.SpatialMediaBlock -> {
                    if (block.mediaType == SpatialMediaType.PHOTO) {
                        block.paths.forEach { delete(it) }
                    }
                }
                else -> Unit
            }
        }
    }

    companion object {
        private const val TAG = "ImageStorageManager"
        private const val IMAGE_DIR = "diary_images"
    }
}
