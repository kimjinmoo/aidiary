package com.grepiu.aidiary.data.slm

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * 첨부된 영상 파일이 입체(3D) 포맷인지 자동 감지.
 *
 * PR 1 에서 다루는 포맷:
 *  - **Stereo MP4** — ISOBMFF 의 `moov` 안에 비디오 트랙(`trak[mdia/hdlr] = vide`) 이 2개 이상.
 *  - **MOV Spatial** — Apple QuickTime 의 `st3d` stereoscopic atom 검출.
 *  - **MV-HEVC** (H.265 Annex G) — PR 1 에선 검출하지 않음 (HEVC VPS 파싱은 PR 2 예정).
 *  - 위 어느 것도 아니면 [Video3DFormat.Plain2D].
 *
 *  - 외부 라이브러리 없이 ISOBMFF 박스 파서를 직접 작성. AGENTS.md 정책상 외부 MP4 의존성 추가는
 *    보수적이어야 하므로 ~250줄의 자체 파서로 충분한 케이스를 커버한다.
 *  - `moov` 가 파일 끝에 있는 경우 (faststart 미적용) 에는 false negative 가능.
 *    `moov` 가 앞쪽에 있는 일반 인코더 출력(대부분의 갤/아이폰)에서 동작한다.
 *  - 기본 scan 상한 4MB. `moov` 가 보통 그 안에 들어간다.
 */
object VideoFormatDetector {

    private const val TAG = "VideoFormatDetector"
    private const val DEFAULT_SCAN_LIMIT = 4L * 1024L * 1024L // 4MB

    fun detect(file: File): Video3DFormat {
        if (!file.exists() || file.length() < 16) return Video3DFormat.Plain2D
        return try {
            val scanLimit = minOf(file.length(), DEFAULT_SCAN_LIMIT)
            val raf = RandomAccessFile(file, "r")
            try {
                val top = readBoxes(raf, 0L, scanLimit)
                val moov = top.firstOrNull { it.type == "moov" }
                if (moov == null) {
                    return Video3DFormat.Plain2D
                }
                val traks = moov.children.filter { it.type == "trak" }
                val videoTrackCount = traks.count { isVideoTrack(raf, it) }
                if (videoTrackCount >= 2) {
                    Log.d(TAG, "Detected stereo MP4 (video tracks=$videoTrackCount): ${file.name}")
                    return Video3DFormat.StereoMp4
                }
                if (containsBoxTypeDeep(moov, "st3d")) {
                    Log.d(TAG, "Detected MOV st3d: ${file.name}")
                    return Video3DFormat.MovSpatial
                }
                Video3DFormat.Plain2D
            } finally {
                raf.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "VideoFormatDetector failed for ${file.name}: ${e.message}")
            Video3DFormat.Plain2D
        }
    }

    // ===== ISOBMFF 박스 파서 =====

    private data class Box(
        val type: String,
        val start: Long,
        val end: Long,
        val children: List<Box> = emptyList()
    )

    /**
     * [start, end) 범위 안의 박스들을 재귀적으로 읽는다.
     */
    private fun readBoxes(raf: RandomAccessFile, start: Long, end: Long): List<Box> {
        val boxes = mutableListOf<Box>()
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            val read = raf.read(typeBytes)
            if (read != 4) break
            val type = String(typeBytes, Charsets.US_ASCII)
            if (!isValidBoxType(type)) break
            val headerSize: Long
            val size: Long
            if (size32 == 1L) {
                if (pos + 16 > end) break
                raf.seek(pos + 8)
                val large = raf.readLong()
                if (large < 16) break
                headerSize = 16
                size = large
            } else if (size32 == 0L) {
                size = end - pos
                headerSize = 8
            } else {
                size = size32
                headerSize = 8
            }
            if (size < headerSize) break
            val boxEnd = pos + size
            if (boxEnd > end) break
            val children = if (isContainer(type)) {
                // 'meta' 는 FullBox (version+flags 4 byte 가 헤더 다음에 옴)
                val childStart = if (type == "meta") pos + headerSize + 4 else pos + headerSize
                readBoxes(raf, childStart, boxEnd)
            } else emptyList()
            boxes.add(Box(type = type, start = pos, end = boxEnd, children = children))
            pos = boxEnd
        }
        return boxes
    }

    private fun isContainer(type: String): Boolean = when (type) {
        "moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta",
        "moof", "traf", "mfra", "mvex", "strd", "strk", "meta" -> true
        else -> false
    }

    private fun isValidBoxType(type: String): Boolean {
        if (type.length != 4) return false
        for (c in type) {
            if (c.code < 0x20 || c.code > 0x7E) return false
        }
        return true
    }

    /**
     * `mdia/hdlr` 의 `handler_type` 이 `"vide"` 인지 확인.
     */
    private fun isVideoTrack(raf: RandomAccessFile, trak: Box): Boolean {
        val mdia = trak.children.firstOrNull { it.type == "mdia" } ?: return false
        val hdlr = mdia.children.firstOrNull { it.type == "hdlr" } ?: return false
        // hdlr: FullBox(4) + pre_defined(4) + handler_type(4) + reserved(12) + name
        // = box header 8 + FullBox header 4 + 4(pre_defined) → handler_type 은 box 시작 + 12
        val handlerTypeOffset = hdlr.start + 8 + 4 + 4
        if (handlerTypeOffset + 4 > hdlr.end) return false
        return try {
            raf.seek(handlerTypeOffset)
            val type = ByteArray(4)
            raf.read(type)
            String(type, Charsets.US_ASCII) == "vide"
        } catch (e: Exception) {
            false
        }
    }

    private fun containsBoxTypeDeep(root: Box, target: String): Boolean {
        if (root.type == target) return true
        for (c in root.children) {
            if (containsBoxTypeDeep(c, target)) return true
        }
        return false
    }
}

/**
 * 첨부 영상의 입체 포맷 판정 결과.
 */
sealed class Video3DFormat(val key: String, val label: String) {
    /** 일반 2D 영상 (가장 흔함) */
    data object Plain2D : Video3DFormat("plain2d", "2D 영상")
    /** MP4 의 비디오 트랙 2개 (좌/우) */
    data object StereoMp4 : Video3DFormat("stereo_mp4", "Stereo MP4 입체 영상")
    /** QuickTime 의 st3d stereoscopic atom */
    data object MovSpatial : Video3DFormat("mov_spatial", "MOV 공간 영상")
}
