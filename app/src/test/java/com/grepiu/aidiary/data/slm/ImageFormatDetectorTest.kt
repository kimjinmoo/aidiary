package com.grepiu.aidiary.data.slm

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageFormatDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `empty file returns Plain2D`() {
        val file = File(tmp.root, "empty.jpg")
        file.createNewFile()
        assertEquals(Image3DFormat.Plain2D, ImageFormatDetector.detect(file))
    }

    @Test
    fun `non-existent file returns Plain2D`() {
        val file = File(tmp.root, "missing.jpg")
        assertEquals(Image3DFormat.Plain2D, ImageFormatDetector.detect(file))
    }

    @Test
    fun `plain JPEG without MPF returns Plain2D`() {
        val file = File(tmp.root, "plain.jpg")
        // SOI + EOI 만 있는 최소 JPEG
        file.outputStream().use { out ->
            out.write(0xFF); out.write(0xD8); out.write(0xFF); out.write(0xD9)
        }
        assertEquals(Image3DFormat.Plain2D, ImageFormatDetector.detect(file))
    }

    @Test
    fun `MPO with APP2 MPF signature returns Mpo`() {
        val file = File(tmp.root, "stereo.mpo")
        file.outputStream().use { out ->
            // SOI
            out.write(0xFF); out.write(0xD8)
            // APP2 marker
            out.write(0xFF); out.write(0xE2)
            // segment length (2 bytes): payload + 2. payload 는 12 바이트 (4+8)
            out.write(0x00) // high byte
            out.write(0x0E) // low byte = 14 (segLen 자체 2 바이트 포함)
            // "MPF\0"
            out.write("MPF".toByteArray())
            out.write(0x00)
            // payload padding 8 바이트
            repeat(8) { out.write(0x00) }
            // APP2 끝난 후 padding (SOI 검출을 32+ 바이트로 만들기 위해)
            repeat(20) { out.write(0x00) }
            // 가짜 image data 후 EOI
            out.write(0xFF); out.write(0xD9)
        }
        assertEquals(Image3DFormat.Mpo, ImageFormatDetector.detect(file))
    }

    @Test
    fun `plain JPEG with APP2 but no MPF signature returns Plain2D`() {
        val file = File(tmp.root, "app2-plain.jpg")
        file.outputStream().use { out ->
            out.write(0xFF); out.write(0xD8)
            out.write(0xFF); out.write(0xE2)
            out.write(0x00); out.write(0x08)
            out.write("EXIF".toByteArray())
            repeat(4) { out.write(0x00) }
            out.write(0xFF); out.write(0xD9)
        }
        assertEquals(Image3DFormat.Plain2D, ImageFormatDetector.detect(file))
    }

    @Test
    fun `ISOBMFF with ftyp but no aux returns Plain2D`() {
        val file = File(tmp.root, "plain.heic")
        file.outputStream().use { out ->
            // ftyp box: size(4) + 'ftyp' + 'heic' + minor(4)
            out.write(intBytes(12))
            out.write("ftyp".toByteArray())
            out.write("heic".toByteArray())
            out.write(intBytes(0))
        }
        // ftyp 만 있고 meta 가 없으므로 ISOBMFF 로 판정되지만 heic aux 가 아니라서 Plain2D
        assertEquals(Image3DFormat.Plain2D, ImageFormatDetector.detect(file))
    }

    @Test
    fun `ISOBMFF HEIC with auxl returns HeicAux`() {
        val file = File(tmp.root, "spatial.heic")
        file.outputStream().use { out ->
            // ftyp
            out.write(intBytes(12))
            out.write("ftyp".toByteArray())
            out.write("heic".toByteArray())
            out.write(intBytes(0))
            // meta box containing 'auxl' string (heuristic)
            val metaContent = "some meta data with auxl reference".toByteArray()
            val metaSize = 8 + metaContent.size
            out.write(intBytes(metaSize))
            out.write("meta".toByteArray())
            out.write(metaContent)
        }
        assertEquals(Image3DFormat.HeicAux, ImageFormatDetector.detect(file))
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
