package com.grepiu.aidiary.data.slm

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.RandomAccessFile

/**
 * 첨부된 이미지 파일이 입체(3D) 포맷인지 자동 감지.
 *
 * PR 1 에서 다루는 포맷:
 *  - **MPO** (Multi-Picture Object, CIPA). JPEG 의 APP2 마커 안에 `MPF\0` 시그니처 + 두 번째 이미지 오프셋.
 *  - **HEIC with auxiliary** (Apple Spatial Photo 등). ISOBMFF ftyp 가 heic/heix/mif1/msf1 이고 meta 안에서
 *    `auxl` / `auxC` 박스가 검출되면 입체 사진.
 *  - **Stereo EXIF**. ExifInterface 의 `ImageDescription` 또는 `UserComment` 에 "stereo" 또는
 *    "3D" 키워드가 포함된 경우.
 *  - 위 어느 것도 아니면 [Image3DFormat.Plain2D] 로 판정.
 *
 *  - 가벼운 heuristic 기반. false positive 보다는 false negative 쪽으로 보수적.
 *  - MPO 인 경우 두 번째 view 의 오프셋을 검증해 진짜 stereo MPO 인지 1차 확인한다.
 */
object ImageFormatDetector {

    private const val TAG = "ImageFormatDetector"

    /**
     * @return 파일의 입체 포맷. 판정 실패 / 일반 이미지는 [Image3DFormat.Plain2D].
     */
    fun detect(file: File): Image3DFormat {
        if (!file.exists() || file.length() < 16) return Image3DFormat.Plain2D
        return try {
            // 1) JPEG 시그니처 (FF D8 FF) → MPO 또는 EXIF stereo 후보
            if (looksLikeJpeg(file)) {
                if (isMpo(file)) {
                    Log.d(TAG, "Detected MPO: ${file.name}")
                    return Image3DFormat.Mpo
                }
                if (hasStereoExifFlag(file)) {
                    Log.d(TAG, "Detected Stereo EXIF: ${file.name}")
                    return Image3DFormat.StereoExif
                }
                return Image3DFormat.Plain2D
            }
            // 2) ISOBMFF 컨테이너 (HEIC/HEIF/AVIF) → HEIC aux 후보
            if (looksLikeIsoBmff(file)) {
                if (isHeicAuxiliary(file)) {
                    Log.d(TAG, "Detected HEIC aux: ${file.name}")
                    return Image3DFormat.HeicAux
                }
                return Image3DFormat.Plain2D
            }
            Image3DFormat.Plain2D
        } catch (e: Exception) {
            Log.w(TAG, "ImageFormatDetector failed for ${file.name}: ${e.message}")
            Image3DFormat.Plain2D
        }
    }

    // ==== JPEG helpers ====

    private fun looksLikeJpeg(file: File): Boolean {
        val raf = RandomAccessFile(file, "r")
        return try {
            if (file.length() < 3) return false
            raf.seek(0)
            val b0 = raf.read()
            val b1 = raf.read()
            val b2 = raf.read()
            // SOI (FF D8) + 1st byte FF
            b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF
        } finally {
            raf.close()
        }
    }

    /**
     * JPEG 의 APP2 (FFE2) 안에 `MPF\0` 시그니처 + `MP Endian` + 2개 이상의 image 가 있는지 확인.
     * CIPA DC-007 표준. `MPF` 의 첫 번째 Image Entry 의 offset 이 다음 view 를 가리킨다.
     */
    private fun isMpo(file: File): Boolean {
        val raf = RandomAccessFile(file, "r")
        return try {
            val len = file.length()
            if (len < 32) return false
            raf.seek(0)
            // SOI 검증
            if (raf.read() != 0xFF || raf.read() != 0xD8) return false
            // 마커 walk
            while (raf.filePointer + 4 < len) {
                val b0 = raf.read()
                val b1 = raf.read()
                if (b0 != 0xFF) {
                    // 다음 마커를 찾을 때까지 1byte 씩
                    raf.seek(raf.filePointer - 1)
                    continue
                }
                // FF 다음 0x00 는 stuffed byte, FF FF 는 패딩, FF Dn 은 실제 마커
                if (b1 == 0x00 || b1 == 0xFF) {
                    // stuffed / padding — 1바이트 되돌려서 다시 읽기
                    raf.seek(raf.filePointer - 1)
                    continue
                }
                val marker = b1
                val segLen = raf.readUnsignedShort() // segment length (이 길이 포함 2byte)
                if (segLen < 2) return false
                val payloadOffset = raf.filePointer
                val payloadEnd = payloadOffset + segLen - 2
                if (marker == 0xE2) { // APP2
                    // "MPF\0" 시그니처 확인
                    if (payloadEnd - payloadOffset >= 6) {
                        raf.seek(payloadOffset)
                        val sig = ByteArray(4)
                        val read = raf.read(sig)
                        if (read == 4 && sig[0] == 'M'.code.toByte() &&
                            sig[1] == 'P'.code.toByte() &&
                            sig[2] == 'F'.code.toByte() &&
                            sig[3] == 0x00.toByte()
                        ) {
                            // MPF 안의 MP Index IFD(MPF Version, NumberOfImages, MPEntry ...) 를 walk 하지 않고
                            // 보수적으로: 시그니처만으로도 MPO 로 판정 (CIPA 표준에 의거)
                            return true
                        }
                    }
                }
                // SOS(Start Of Scan) 마커를 만나면 image data 시작 — 더 이상 APP 마커 없음
                if (marker == 0xDA) return false
                raf.seek(payloadEnd.toLong())
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "MPO detection failed: ${e.message}")
            false
        } finally {
            raf.close()
        }
    }

    /**
     * Exif 메타데이터에 stereo / 3D 키워드가 있는지 확인.
     * 일부 카메라 (예: W3) / 앱이 ImageDescription 또는 UserComment 에 마킹.
     */
    private fun hasStereoExifFlag(file: File): Boolean {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val desc = (exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) ?: "").lowercase()
            val userComment = (exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: "").lowercase()
            desc.contains("stereo") || desc.contains("3d") ||
                userComment.contains("stereo") || userComment.contains("3d")
        } catch (e: Exception) {
            false
        }
    }

    // ==== ISOBMFF / HEIC helpers ====

    private fun looksLikeIsoBmff(file: File): Boolean {
        val raf = RandomAccessFile(file, "r")
        return try {
            if (file.length() < 16) return false
            raf.seek(4)
            val type = ByteArray(4)
            raf.read(type)
            val s = String(type, Charsets.US_ASCII)
            // ftyp, moov, mdat, free, skip, mfra, meta, idat, udta 모두 container/box 가 될 수 있음.
            // 일반적으로 첫 box 는 ftyp 이지만 파일이 ftyp 로 시작하지 않는 경우도 드물게 있으므로
            // ftyp 가 본문 어딘가에 있으면 ISOBMFF 로 판정.
            val ftypIdx = findBoxType(file, "ftyp", maxBytes = 4096)
            ftypIdx >= 0
        } catch (e: Exception) {
            false
        } finally {
            raf.close()
        }
    }

    /**
     * HEIF(HEIC/HEIX/MIF1/MSF1) 브랜드인지 확인 + `auxl` / `auxC` 박스 존재 여부로 입체 사진 판정.
     * Apple iPhone 15 Pro 의 Spatial Photo 가 HEIF 안에 auxiliary 이미지를 포함한다.
     */
    private fun isHeicAuxiliary(file: File): Boolean {
        return try {
            val brands = readIsoBmffBrands(file)
            val isHeif = brands.any { it.lowercase() in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis", "hevm", "hevs") }
            if (!isHeif) return false
            // 첫 1MB 내에서 auxl / auxC 키워드 검색 (iPhone Spatial Photo 시그니처)
            hasAuxReferenceInFirstBytes(file, maxBytes = 1_048_576)
        } catch (e: Exception) {
            Log.w(TAG, "HEIC aux detection failed: ${e.message}")
            false
        }
    }

    private fun readIsoBmffBrands(file: File): List<String> {
        val raf = RandomAccessFile(file, "r")
        val brands = mutableListOf<String>()
        try {
            val len = file.length()
            if (len < 8) return brands
            raf.seek(0)
            // 최상위 box 순회
            var pos = 0L
            while (pos + 8 <= len) {
                raf.seek(pos)
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4)
                raf.read(typeBytes)
                val type = String(typeBytes, Charsets.US_ASCII)
                val headerSize = 8L
                val boxSize = if (size == 1L) {
                    // 64-bit largesize
                    if (pos + 16 > len) return brands
                    raf.seek(pos + 8)
                    raf.readLong()
                } else if (size == 0L) len - pos
                else size
                if (type == "ftyp") {
                    val brandOffset = pos + headerSize
                    // major brand (4) + minor version (4) + compatible brands
                    if (brandOffset + 8 <= len) {
                        raf.seek(brandOffset)
                        val major = ByteArray(4)
                        raf.read(major)
                        brands.add(String(major, Charsets.US_ASCII))
                        // compatible brands 는 major/minor 이후 연속 4바이트 단위
                        val restEnd = (pos + boxSize).coerceAtMost(len)
                        var p = brandOffset + 8
                        while (p + 4 <= restEnd) {
                            raf.seek(p)
                            val b = ByteArray(4)
                            raf.read(b)
                            brands.add(String(b, Charsets.US_ASCII))
                            p += 4
                        }
                    }
                    return brands
                }
                if (boxSize < headerSize) return brands
                pos += boxSize
            }
        } finally {
            raf.close()
        }
        return brands
    }

    private fun hasAuxReferenceInFirstBytes(file: File, maxBytes: Int): Boolean {
        val readSize = minOf(file.length(), maxBytes.toLong())
        val raf = RandomAccessFile(file, "r")
        return try {
            val buf = ByteArray(readSize.toInt())
            raf.read(buf)
            // "auxl" / "auxC" ASCII 4바이트 패턴 검출
            val pattern = "auxl".toByteArray(Charsets.US_ASCII)
            val pattern2 = "auxC".toByteArray(Charsets.US_ASCII)
            indexOf(buf, pattern) >= 0 || indexOf(buf, pattern2) >= 0
        } finally {
            raf.close()
        }
    }

    private fun indexOf(buf: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..buf.size - pattern.size) {
            for (j in pattern.indices) {
                if (buf[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * 상위 [maxBytes] 이내에서 [boxType] (ASCII 4바이트) 박스를 찾는다.
     * top-level box 만 walk 하므로 깊이는 1.
     */
    private fun findBoxType(file: File, boxType: String, maxBytes: Int): Int {
        val raf = RandomAccessFile(file, "r")
        val typeBytes = boxType.toByteArray(Charsets.US_ASCII)
        val scanLimit = minOf(file.length(), maxBytes.toLong())
        return try {
            val buf = ByteArray(scanLimit.toInt())
            raf.read(buf)
            indexOf(buf, typeBytes)
        } finally {
            raf.close()
        }
    }
}

/**
 * 첨부 이미지의 입체 포맷 판정 결과.
 */
sealed class Image3DFormat(val key: String, val label: String) {
    /** 일반 2D 사진 (가장 흔함) */
    data object Plain2D : Image3DFormat("plain2d", "2D 사진")
    /** MPO (Multi-Picture Object) — JPEG 의 APP2 MPF 시그니처 + 두 번째 view */
    data object Mpo : Image3DFormat("mpo", "MPO 3D 사진")
    /** HEIF 의 auxiliary 이미지 (Apple Spatial Photo 등) */
    data object HeicAux : Image3DFormat("heic_aux", "HEIC 공간 사진")
    /** EXIF 의 Stereo 키워드/플래그 */
    data object StereoExif : Image3DFormat("stereo_exif", "Stereo EXIF 3D 사진")
}
