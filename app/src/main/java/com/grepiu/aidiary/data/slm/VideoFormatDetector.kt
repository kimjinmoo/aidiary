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
            val scanLimit = file.length()
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
                if (containsBoxTypeDeep(moov, "st3d") || containsBoxTypeDeep(moov, "proj") || containsBoxTypeDeep(moov, "svpi")) {
                    Log.d(TAG, "Detected Spatial/VR Video (st3d/proj/svpi): ${file.name}")
                    return Video3DFormat.MovSpatial
                }
                // 단일 비디오 트랙일 때 MV-HEVC 인지 판정 (hvcC.box 의 general_profile_idc)
                if (isMvHevc(raf, moov)) {
                    Log.d(TAG, "Detected MV-HEVC: ${file.name}")
                    return Video3DFormat.MvHevc
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

    /**
     * 단일 트랙 HEVC 가 MV-HEVC 인지 판정.
     *
     * 알고리즘 (ISO/IEC 14496-15):
     *  1) moov/trak/mdia/minf/stbl/stsd 박스 진입
     *  2) stsd/sample_entry(hev1 또는 hvc1) 안의 **hvcC** (HEVC Decoder Configuration Record) 찾기
     *  3) hvcC 22 byte 안의 offset 2 = `general_profile_idc`
     *      - 6 (MultiView) → MV-HEVC
     *      - 7 (Scalable)   → MV-HEVC (rare)
     *      - 그 외 (Main=1 / Main 10=2 / Main Still Picture=3 / Range Extensions=4 등) → 일반 HEVC
     *
     * iPhone 15 Pro, Galaxy XR, Pixel XR 등 MV-HEVC 영상은 profile_idc=6 으로 저장되므로
     * 이 가벼운 휴리스틱으로 거의 모두 잡힘.
     */
    private fun isMvHevc(raf: RandomAccessFile, moov: Box): Boolean {
        val traks = moov.children.filter { it.type == "trak" }
        for (trak in traks) {
            val mdia = trak.children.firstOrNull { it.type == "mdia" } ?: continue
            if (!isVideoTrack(raf, trak)) continue
            val minf = mdia.children.firstOrNull { it.type == "minf" } ?: continue
            val stbl = minf.children.firstOrNull { it.type == "stbl" } ?: continue
            val stsd = stbl.children.firstOrNull { it.type == "stsd" } ?: continue
            // stsd: FullBox — box header(8) + version/flags(4) + entry_count(4) + entries...
            val entryCountOffset = stsd.start + 12
            if (entryCountOffset + 4 > stsd.end) continue
            raf.seek(entryCountOffset)
            val entryCount = raf.readInt()
            var entryPos = entryCountOffset + 4L // stsd.start + 16
            for (i in 0 until entryCount) {
                if (entryPos + 8 > stsd.end) break
                raf.seek(entryPos)
                val entrySize = raf.readInt().toLong() and 0xFFFFFFFFL
                if (entrySize < 8 || entryPos + entrySize > stsd.end) break
                val entryType = ByteArray(4)
                raf.read(entryType)
                val type = String(entryType, Charsets.US_ASCII)
                if (type != "hvc1" && type != "hev1") {
                    entryPos += entrySize; continue
                }
                // sample entry 안에서 "hvcC" 시그니처를 시그니처 스캔으로 찾는다.
                // SampleEntry(6 reserved + 2 data_ref_index) + VisualSampleEntry 고정 필드를
                // 건너뛰기 위해 단순 4CC 문자열 매칭을 수행한다.
                val entryEnd = entryPos + entrySize
                var scanPos = entryPos + 8
                while (scanPos + 12 <= entryEnd) {
                    raf.seek(scanPos + 4)
                    val fourCC = ByteArray(4)
                    raf.read(fourCC)
                    if (String(fourCC, Charsets.US_ASCII) == "hvcC") {
                        raf.seek(scanPos)
                        val boxSize = raf.readInt().toLong() and 0xFFFFFFFFL
                        if (boxSize >= 8 && scanPos + boxSize <= entryEnd) {
                            if (scanPos + 10 > entryEnd) break
                            raf.seek(scanPos + 8)
                            raf.read() // configurationVersion
                            val profileIdc = raf.read() and 0x1F
                            Log.d(TAG, "hvcC profile_idc=$profileIdc")
                            if (profileIdc == 6 || profileIdc == 7) return true
                        }
                        break
                    }
                    scanPos++
                }
                entryPos += entrySize
            }
        }
        return false
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
            if (type == "moov") {
                break
            }
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
    /** 단일 트랙 MV-HEVC (iPhone 15 Pro / Galaxy XR / Pixel XR). hvcC.profile_idc 6 or 7 */
    data object MvHevc : Video3DFormat("mv_hevc", "MV-HEVC 공간 영상")
}
