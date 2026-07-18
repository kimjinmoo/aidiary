package com.grepiu.aidiary.data.model

import org.json.JSONObject
import java.util.UUID

/**
 * 일기 본문을 구성하는 콘텐츠 블록의 봉인 클래스입니다.
 * Notion 스타일의 블록 기반 에디터/렌더러에 사용됩니다.
 *
 * - [HeadingBlock]: 큰 제목
 * - [TextBlock]: 본문 문단
 * - [QuoteBlock]: 인용문
 * - [ImageBlock]: 이미지(앱 내부 저장소 경로 보유)
 * - [DividerBlock]: 가로 구분선
 *
 * 텍스트 기반 블록(Heading/Text/Quote) 은 인라인 [TextFormatting] 을 가집니다.
 */
sealed class ContentBlock {
    abstract val id: String

    data class HeadingBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val formatting: TextFormatting = TextFormatting.Empty
    ) : ContentBlock()

    data class TextBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val formatting: TextFormatting = TextFormatting.Empty
    ) : ContentBlock()

    data class QuoteBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val formatting: TextFormatting = TextFormatting.Empty
    ) : ContentBlock()

    /**
     * [relativePath] 는 앱 내부 저장소(filesDir/diary_images/...) 기준의 상대 경로.
     */
    data class ImageBlock(
        override val id: String = UUID.randomUUID().toString(),
        val relativePath: String,
        val caption: String = ""
    ) : ContentBlock()

    data class DividerBlock(
        override val id: String = UUID.randomUUID().toString()
    ) : ContentBlock()

    /**
     * 저장 시 AI가 자동 생성한 'TAG AI' 블록.
     *
     * - [emotion]: 한국어 감정 라벨 (기쁨/슬픔/분노/불안/평온 중 하나)
     *
     * 사용자 본문이 아닌 AI 생성 결과이므로 [extractPlainText] 에서는 제외되어
     * 재분석 시 입력 피드백 루프가 발생하지 않습니다.
     */
    data class TagAiBlock(
        override val id: String = UUID.randomUUID().toString(),
        val emotion: String
    ) : ContentBlock()

    /**
     * 표 블록. 2D 텍스트 셀 배열을 가지며 첫 행을 헤더로 간주합니다.
     *
     * - [cells] 의 길이는 항상 [rows] 와 같고, 각 내부 리스트 길이는 [cols] 와 같습니다.
     * - [rows] / [cols] 의 최소값은 1, 권장 최대값은 20x8 (UI 안전 범위).
     */
    data class TableBlock(
        override val id: String = UUID.randomUUID().toString(),
        val rows: Int,
        val cols: Int,
        val cells: List<List<String>>
    ) : ContentBlock()

    data class LocationBlock(
        override val id: String = UUID.randomUUID().toString(),
        val latitude: Double,
        val longitude: Double,
        val address: String
    ) : ContentBlock()

    /**
     * 입체(3D) 미디어 블록. 자동 포맷 감지 결과가 3D 일 때 생성되며,
     * 일반 [ImageBlock] / 비디오 블록과 분리해 다뤄진다.
     *
     * - [mediaType]: 사진(PHOTO) 또는 영상(VIDEO)
     * - [paths]: 앱 내부 저장소(filesDir/) 기준 상대 경로 리스트.
     *      - PHOTO: [왼쪽(또는 메인), 오른쪽(또는 보조)] 2장
     *      - VIDEO: [단일 mv-hevc / stereo mp4] 1개
     * - [captureMode]: 자동 감지된 3D 포맷 종류
     * - [caption]: 사용자가 입력한 설명 (선택)
     */
    data class SpatialMediaBlock(
        override val id: String = UUID.randomUUID().toString(),
        val mediaType: SpatialMediaType,
        val paths: List<String>,
        val captureMode: SpatialCaptureMode,
        val caption: String = ""
    ) : ContentBlock()

    /**
     * 해시태그 블록. 사용자가 직접 입력하거나 AI 가 자동 생성한 태그 목록.
     * 각 태그는 # 없는 순수 텍스트로 저장되며, 표시/검색 시 # 이 붙는다.
     */
    data class HashtagBlock(
        override val id: String = UUID.randomUUID().toString(),
        val tags: List<String> = emptyList()
    ) : ContentBlock()

    companion object {
        const val TYPE_HEADING = "heading"
        const val TYPE_TEXT = "text"
        const val TYPE_QUOTE = "quote"
        const val TYPE_IMAGE = "image"
        const val TYPE_DIVIDER = "divider"
        const val TYPE_TAG_AI = "tagAi"
        const val TYPE_TABLE = "table"
        const val TYPE_LOCATION = "location"
        const val TYPE_SPATIAL_MEDIA = "spatialMedia"
        const val TYPE_HASHTAG = "hashtag"

        /**
         * JSON 객체로부터 [ContentBlock] 인스턴스를 복원합니다.
         */
        fun fromJson(obj: JSONObject): ContentBlock {
            val type = obj.optString("type", TYPE_TEXT)
            val id = obj.optString("id", UUID.randomUUID().toString())
            return when (type) {
                TYPE_HEADING -> HeadingBlock(
                    id = id,
                    text = obj.optString("text", ""),
                    formatting = TextFormatting.fromJson(obj.optJSONObject("formatting"))
                )
                TYPE_TEXT -> TextBlock(
                    id = id,
                    text = obj.optString("text", ""),
                    formatting = TextFormatting.fromJson(obj.optJSONObject("formatting"))
                )
                TYPE_QUOTE -> QuoteBlock(
                    id = id,
                    text = obj.optString("text", ""),
                    formatting = TextFormatting.fromJson(obj.optJSONObject("formatting"))
                )
                TYPE_IMAGE -> ImageBlock(
                    id = id,
                    relativePath = obj.optString("path", ""),
                    caption = obj.optString("caption", "")
                )
                TYPE_DIVIDER -> DividerBlock(id = id)
                TYPE_TAG_AI -> TagAiBlock(
                    id = id,
                    emotion = obj.optString("emotion", "평온")
                )
                TYPE_TABLE -> {
                    val rows = obj.optInt("rows", 2).coerceIn(1, 50)
                    val cols = obj.optInt("cols", 2).coerceIn(1, 20)
                    val cellsArr = obj.optJSONArray("cells")
                    val cells: List<List<String>> = (0 until rows).map { r ->
                        (0 until cols).map { c ->
                            cellsArr?.optJSONArray(r)?.optString(c, "").orEmpty()
                        }
                    }
                    TableBlock(id = id, rows = rows, cols = cols, cells = cells)
                }
                TYPE_LOCATION -> LocationBlock(
                    id = id,
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    address = obj.optString("address", "")
                )
                TYPE_SPATIAL_MEDIA -> {
                    val mediaType = SpatialMediaType.fromKey(obj.optString("mediaType"))
                    val mode = SpatialCaptureMode.fromKey(obj.optString("captureMode"))
                    val pathsArr = obj.optJSONArray("paths")
                    val paths = if (pathsArr != null) (0 until pathsArr.length())
                        .mapNotNull { pathsArr.optString(it, null).takeIf { p -> p != null && p.isNotBlank() } }
                    else emptyList()
                    SpatialMediaBlock(
                        id = id,
                        mediaType = mediaType,
                        paths = paths,
                        captureMode = mode,
                        caption = obj.optString("caption", "")
                    )
                }
                TYPE_HASHTAG -> {
                    val tagsArr = obj.optJSONArray("tags")
                    val tags = if (tagsArr != null) (0 until tagsArr.length())
                        .mapNotNull { tagsArr.optString(it, null)?.takeIf { t -> t.isNotBlank() } }
                    else emptyList()
                    HashtagBlock(id = id, tags = tags)
                }
                else -> TextBlock(id = id, text = obj.optString("text", ""))
            }
        }
    }
}

/**
 * JSON 직렬화를 위한 확장. 각 블록 타입에 맞는 type 필드를 부여합니다.
 */
fun ContentBlock.toJson(): JSONObject = when (this) {
    is ContentBlock.HeadingBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_HEADING)
        put("id", id)
        put("text", text)
        if (!formatting.isEmpty()) put("formatting", formatting.toJson())
    }
    is ContentBlock.TextBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_TEXT)
        put("id", id)
        put("text", text)
        if (!formatting.isEmpty()) put("formatting", formatting.toJson())
    }
    is ContentBlock.QuoteBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_QUOTE)
        put("id", id)
        put("text", text)
        if (!formatting.isEmpty()) put("formatting", formatting.toJson())
    }
    is ContentBlock.ImageBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_IMAGE)
        put("id", id)
        put("path", relativePath)
        put("caption", caption)
    }
    is ContentBlock.DividerBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_DIVIDER)
        put("id", id)
    }
    is ContentBlock.TagAiBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_TAG_AI)
        put("id", id)
        put("emotion", emotion)
    }
    is ContentBlock.TableBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_TABLE)
        put("id", id)
        put("rows", rows)
        put("cols", cols)
        val cellsArr = org.json.JSONArray()
        cells.forEach { row ->
            val rowArr = org.json.JSONArray()
            row.forEach { rowArr.put(it) }
            cellsArr.put(rowArr)
        }
        put("cells", cellsArr)
    }
    is ContentBlock.LocationBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_LOCATION)
        put("id", id)
        put("latitude", latitude)
        put("longitude", longitude)
        put("address", address)
    }
    is ContentBlock.SpatialMediaBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_SPATIAL_MEDIA)
        put("id", id)
        put("mediaType", mediaType.key)
        val arr = org.json.JSONArray()
        paths.forEach { arr.put(it) }
        put("paths", arr)
        put("captureMode", captureMode.key)
        put("caption", caption)
    }
    is ContentBlock.HashtagBlock -> JSONObject().apply {
        put("type", ContentBlock.TYPE_HASHTAG)
        put("id", id)
        val tagsArr = org.json.JSONArray()
        tags.forEach { tagsArr.put(it) }
        put("tags", tagsArr)
    }
}

/**
 * [ContentBlock.SpatialMediaBlock.mediaType] 의 종류. 사진 / 영상.
 */
enum class SpatialMediaType(val key: String) {
    PHOTO("photo"),
    VIDEO("video");

    companion object {
        fun fromKey(key: String?): SpatialMediaType = when (key) {
            "video" -> VIDEO
            else -> PHOTO
        }
    }
}

/**
 * 자동 감지된 입체 미디어 포맷.
 *
 * - [MPO]: Multi-Picture Object (CIPA). JPEG 의 APP2 MPF 시그니처.
 * - [HEIC_AUX]: HEIF 컨테이너의 auxiliary 이미지 (Apple Spatial Photo 등).
 * - [STEREO_EXIF]: EXIF 의 Stereo 플래그 / 키워드.
 * - [STEREO_MP4]: ISOBMFF MP4 의 비디오 트랙 2개 (좌/우).
 * - [MOV_SPATIAL]: QuickTime 컨테이너의 st3d stereoscopic atom.
 * - [MV_HEVC]: 단일 트랙 MV-HEVC (H.265 Annex G). PR 1 에선 검출 안 됨, 향후 PR 에서 HEVC VPS 파싱.
 * - [PLAIN_2D_VIDEO]: 입체가 아닌 일반 2D 영상. 첨부는 가능하지만 3D 마크 없음.
 * - [PLAIN_2D_PHOTO]: 입체가 아닌 일반 2D 사진. 첨부는 가능하지만 3D 마크 없음.
 */
enum class SpatialCaptureMode(val key: String, val label: String) {
    MPO("mpo", "MPO 3D 사진"),
    HEIC_AUX("heic_aux", "HEIC 공간 사진"),
    STEREO_EXIF("stereo_exif", "Stereo EXIF 3D 사진"),
    STEREO_MP4("stereo_mp4", "Stereo MP4"),
    MOV_SPATIAL("mov_spatial", "MOV 공간 영상"),
    MV_HEVC("mv_hevc", "MV-HEVC 공간 영상"),
    PLAIN_2D_VIDEO("plain_2d_video", "영상"),
    PLAIN_2D_PHOTO("plain_2d_photo", "2D 사진");

    /** 3D 입체 미디어 (3D 배지 노출) 여부. PLAIN_2D_VIDEO/PLAIN_2D_PHOTO 는 3D 아님. */
    val is3D: Boolean
        get() = this != PLAIN_2D_VIDEO && this != PLAIN_2D_PHOTO

    companion object {
        fun fromKey(key: String?): SpatialCaptureMode = when (key) {
            "mpo" -> MPO
            "heic_aux" -> HEIC_AUX
            "stereo_exif" -> STEREO_EXIF
            "stereo_mp4" -> STEREO_MP4
            "mov_spatial" -> MOV_SPATIAL
            "mv_hevc" -> MV_HEVC
            "plain_2d_video" -> PLAIN_2D_VIDEO
            "plain_2d_photo" -> PLAIN_2D_PHOTO
            else -> MPO
        }
    }
}

/**
 * 블록 목록에서 AI 분석용 평문 텍스트만 추출합니다.
 * ImageBlock / DividerBlock / TagAiBlock / SpatialMediaBlock 은 제외됩니다.
 * (TagAiBlock 은 AI 생성 결과이므로 재분석 입력에 포함하지 않습니다)
 * TableBlock 은 셀 텍스트를 " | " 로 결합해 한 줄로 포함합니다.
 * SpatialMediaBlock 은 첨부 미디어(사진/영상) 의 caption 만 포함시켜 3D 라는 사실을 AI 가 인지하도록 합니다.
 */
fun List<ContentBlock>.extractPlainText(): String =
    mapNotNull { block ->
        when (block) {
            is ContentBlock.TextBlock -> block.text
            is ContentBlock.HeadingBlock -> block.text
            is ContentBlock.QuoteBlock -> block.text
            is ContentBlock.TableBlock -> block.cells.joinToString(" | ") { row ->
                row.joinToString(" | ")
            }.ifBlank { null }
            is ContentBlock.LocationBlock -> "📍 위치: ${block.address}"
            is ContentBlock.SpatialMediaBlock -> {
                val tag = if (block.mediaType == SpatialMediaType.PHOTO) "[3D 사진]" else "[3D 영상]"
                val text = block.caption.ifBlank { null }
                if (text != null) "$tag $text" else tag
            }
            is ContentBlock.HashtagBlock -> "#" + block.tags.joinToString(" #")
            else -> null
        }
    }.joinToString(separator = "\n")
