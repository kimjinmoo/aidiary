package com.grepiu.aidiary.data.slm

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoFormatDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `empty file returns Plain2D`() {
        val file = File(tmp.root, "empty.mp4")
        file.createNewFile()
        assertEquals(Video3DFormat.Plain2D, VideoFormatDetector.detect(file))
    }

    @Test
    fun `file without ftyp returns Plain2D`() {
        val file = File(tmp.root, "weird.mp4")
        file.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x08, 'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()))
        assertEquals(Video3DFormat.Plain2D, VideoFormatDetector.detect(file))
    }

    @Test
    fun `MP4 with single video track returns Plain2D`() {
        val file = File(tmp.root, "single.mp4")
        writeMp4(file, videoTrackCount = 1, withSt3d = false)
        assertEquals(Video3DFormat.Plain2D, VideoFormatDetector.detect(file))
    }

    @Test
    fun `MP4 with two video tracks returns StereoMp4`() {
        val file = File(tmp.root, "stereo.mp4")
        writeMp4(file, videoTrackCount = 2, withSt3d = false)
        assertEquals(Video3DFormat.StereoMp4, VideoFormatDetector.detect(file))
    }

    @Test
    fun `MP4 with single video track and st3d atom returns MovSpatial`() {
        val file = File(tmp.root, "mov-spatial.mp4")
        writeMp4(file, videoTrackCount = 1, withSt3d = true)
        assertEquals(Video3DFormat.MovSpatial, VideoFormatDetector.detect(file))
    }

    @Test
    fun `MP4 with single HEVC track hvcC profile_idc=6 returns MvHevc`() {
        val file = File(tmp.root, "mv-hevc.mp4")
        writeMp4WithHevc(file, generalProfileIdc = 6)
        assertEquals(Video3DFormat.MvHevc, VideoFormatDetector.detect(file))
    }

    @Test
    fun `MP4 with single HEVC track hvcC profile_idc=1 returns Plain2D`() {
        val file = File(tmp.root, "hevc-main.mp4")
        writeMp4WithHevc(file, generalProfileIdc = 1)
        assertEquals(Video3DFormat.Plain2D, VideoFormatDetector.detect(file))
    }

    // ===== 테스트 픽스처 빌더 =====

    /**
     * 간단한 ISOBMFF MP4 픽스처를 작성한다.
     * @param videoTrackCount 'trak' 박스 수. trak/mdia/hdlr handler_type='vide' 임.
     * @param withSt3d true 이면 st3d 박스를 moov 안에 포함.
     */
    private fun writeMp4(file: File, videoTrackCount: Int, withSt3d: Boolean) {
        file.outputStream().use { out ->
            // 1) ftyp
            writeBox(out, "ftyp") { payload ->
                payload.write("mp42".toByteArray())
                payload.write(intBytes(0)) // minor version
            }
            // 2) moov
            val trakBuilders = (1..videoTrackCount).map { trakIdx ->
                ByteArrayOutputStream().also { trakBuf ->
                    writeBox(trakBuf, "trak") { trakPayload ->
                        // trak/mdia
                        writeBox(trakPayload, "mdia") { mdiaPayload ->
                            // mdia/hdlr
                            writeBox(mdiaPayload, "hdlr") { hdlrPayload ->
                                // version(1) + flags(3) = 4 bytes
                                hdlrPayload.write(intBytes(0))
                                // pre_defined(4)
                                hdlrPayload.write(intBytes(0))
                                // handler_type(4) = 'vide'
                                hdlrPayload.write("vide".toByteArray())
                            }
                        }
                    }
                }.toByteArray()
            }
            val moovBuf = ByteArrayOutputStream()
            writeBox(moovBuf, "moov") { moovPayload ->
                // trak 박스들
                trakBuilders.forEach { moovPayload.write(it) }
                // st3d 박스 (선택)
                if (withSt3d) {
                    writeBox(moovPayload, "st3d") { st3dPayload ->
                        // stereo mode (1 byte) = 1 (left/right)
                        st3dPayload.write(0x01)
                    }
                }
            }
            out.write(moovBuf.toByteArray())
        }
    }

    private fun writeBox(out: java.io.OutputStream, type: String, writePayload: (java.io.OutputStream) -> Unit) {
        val payloadBuf = ByteArrayOutputStream()
        writePayload(payloadBuf)
        val payload = payloadBuf.toByteArray()
        out.write(intBytes(8 + payload.size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    /**
     * 단일 HEVC 트랙을 가진 MP4 픽스처. hvcC 박스의 general_profile_idc 를 지정값으로 설정.
     * (실제 iPhone 15 Pro MV-HEVC 의 profile_idc=6 / 일반 HEVC=1)
     */
    private fun writeMp4WithHevc(file: File, generalProfileIdc: Int) {
        file.outputStream().use { out ->
            // 1) ftyp
            writeBox(out, "ftyp") { payload ->
                payload.write("mp42".toByteArray())
                payload.write(intBytes(0))
            }
            // 2) moov
            val trakBuf = ByteArrayOutputStream()
            writeBox(trakBuf, "trak") { trakPayload ->
                // tkhd
                writeBox(trakPayload, "tkhd") { /* 20 byte padding */ payload ->
                    payload.write(ByteArray(20))
                }
                // mdia
                writeBox(trakPayload, "mdia") { mdiaPayload ->
                    // mdhd
                    writeBox(mdiaPayload, "mdhd") { payload ->
                        payload.write(ByteArray(20))
                    }
                    // hdlr = vide
                    writeBox(mdiaPayload, "hdlr") { hdlrPayload ->
                        hdlrPayload.write(intBytes(0)) // version + flags
                        hdlrPayload.write(intBytes(0)) // pre_defined
                        hdlrPayload.write("vide".toByteArray())
                    }
                    // minf
                    writeBox(mdiaPayload, "minf") { minfPayload ->
                        // vmhd
                        writeBox(minfPayload, "vmhd") { payload ->
                            payload.write(intBytes(0))
                            payload.write(ByteArray(8))
                        }
                        // dinf
                        writeBox(minfPayload, "dinf") { payload ->
                            writeBox(payload, "dref") { payload2 ->
                                payload2.write(intBytes(0))
                                payload2.write(intBytes(0))
                            }
                        }
                        // stbl
                        writeBox(minfPayload, "stbl") { stblPayload ->
                            // stsd
                            writeBox(stblPayload, "stsd") { stsdPayload ->
                                stsdPayload.write(intBytes(0)) // version + flags
                                stsdPayload.write(intBytes(1)) // entry_count
                                // sample_entry: hvc1
                                writeBox(stsdPayload, "hvc1") { hvc1Payload ->
                                    // sample_entry 공통 헤더 8 byte (6 byte reserved + 2 byte data_reference_index) + hvcC
                                    hvc1Payload.write(ByteArray(6))
                                    hvc1Payload.write(intBytes(1)) // data_reference_index
                                    // hvcC
                                    writeHvcC(hvc1Payload, generalProfileIdc)
                                    // width/height (4 bytes each, big endian)
                                    hvc1Payload.write(intBytes(1920))
                                    hvc1Payload.write(intBytes(1080))
                                    // horizresolution 4 + vertresolution 4 + reserved 4 + frame_count 2
                                    hvc1Payload.write(intBytes(0x00480000))
                                    hvc1Payload.write(intBytes(0x00480000))
                                    hvc1Payload.write(intBytes(0))
                                    hvc1Payload.write(intBytes(0)) // frame_count (2 bytes, but as int4)
                                }
                            }
                        }
                    }
                }
            }
            val moovBuf = ByteArrayOutputStream()
            writeBox(moovBuf, "moov") { moovPayload ->
                moovPayload.write(trakBuf.toByteArray())
            }
            out.write(moovBuf.toByteArray())
        }
    }

    /**
     * HEVCDecoderConfigurationRecord (22+ bytes) 를 [out] 에 쓴다.
     * - byte 0: configurationVersion = 1
     * - byte 1: (general_profile_space 2) | (general_tier_flag 1) | (general_profile_idc 5)
     * - byte 2..5: general_profile_compatibility_flags
     * - byte 6..11: 6 constraint indicator flags
     * - byte 12..13: general_level_idc
     * - byte 14..15: min_spatial_segmentation_idc (4 reserved bits + 12 idc)
     * - byte 16: parallelismType
     * - byte 17: chromaFormat
     * - byte 18: bitDepthLumaMinus8
     * - byte 19: bitDepthChromaMinus8
     * - byte 20..21: avgFrameRate
     * - byte 22: constantFrameRate(2) | numTemporalLayers(3) | temporalIdNested(1) | lengthSizeMinusOne(2)
     * - byte 23+: numOfArrays + (각 array: NAL type, numNalus, sizes, NAL units)
     */
    private fun writeHvcC(out: java.io.OutputStream, profileIdc: Int) {
        val payloadSize = 23 // VPS/SPS/PPS 0개 버전 (간소화)
        writeBox(out, "hvcC") { hvccPayload ->
            hvccPayload.write(0x01) // configurationVersion
            // general_profile_space=0, tier_flag=0, profile_idc=5bit
            hvccPayload.write(profileIdc and 0x1F)
            // general_profile_compatibility_flags (4 bytes)
            hvccPayload.write(intBytes(0x60000000))
            // 6 bytes constraint indicator flags
            repeat(6) { hvccPayload.write(0x00) }
            // general_level_idc (2 bytes)
            hvccPayload.write(0x5D) // level 3.1
            // min_spatial_segmentation_idc (2 bytes, 4 reserved bits 1111 + 12 idc = 0xFFF0 big-endian)
            hvccPayload.write(byteArrayOf(0xFF.toByte(), 0xF0.toByte()))
            // parallelismType
            hvccPayload.write(0xFC)
            // chromaFormat
            hvccPayload.write(0xFD)
            // bitDepthLumaMinus8
            hvccPayload.write(0xF8)
            // bitDepthChromaMinus8
            hvccPayload.write(0xF8)
            // avgFrameRate (2 bytes, value = 0)
            hvccPayload.write(ByteArray(2))
            // constantFrameRate(2) | numTemporalLayers(3) | temporalIdNested(1) | lengthSizeMinusOne(2) = 0b00_000_0_11
            hvccPayload.write(0x03)
            // numOfArrays = 0 (VPS/SPS/PPS 생략 — 단순 픽스처)
            hvccPayload.write(0x00)
        }
    }
}

private class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
