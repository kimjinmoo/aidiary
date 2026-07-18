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
}

private class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
