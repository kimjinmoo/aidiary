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

    companion object {
        const val TYPE_HEADING = "heading"
        const val TYPE_TEXT = "text"
        const val TYPE_QUOTE = "quote"
        const val TYPE_IMAGE = "image"
        const val TYPE_DIVIDER = "divider"

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
}

/**
 * 블록 목록에서 AI 분석용 평문 텍스트만 추출합니다.
 * ImageBlock / DividerBlock 은 제외됩니다.
 */
fun List<ContentBlock>.extractPlainText(): String =
    mapNotNull { block ->
        when (block) {
            is ContentBlock.TextBlock -> block.text
            is ContentBlock.HeadingBlock -> block.text
            is ContentBlock.QuoteBlock -> block.text
            else -> null
        }
    }.joinToString(separator = "\n")
