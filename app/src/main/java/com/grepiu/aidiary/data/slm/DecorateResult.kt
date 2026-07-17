package com.grepiu.aidiary.data.slm

import com.grepiu.aidiary.data.model.TextFormatting
import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM 보조 액션의 결과 모델들.
 *
 *  - [DecorateSuggestion] : 꾸미기 키워드 한 건 (start/end 인덱스, bold/italic/underline/color/size)
 *  - [DecorateResult]     : 본문과 꾸미기 제안 묶음
 */
data class DecorateSuggestion(
    val keyword: String,
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val color: String? = null,
    val sizeSp: Int? = null
)

data class DecorateResult(
    val text: String,
    val suggestions: List<DecorateSuggestion>
)

/**
 * LLM 의 JSON 출력 ([suggestions] 부분) 을 안전하게 파싱해
 * [DecorateResult] 로 변환합니다. 잘못된 응답은 무시하고 원본 텍스트만 반환합니다.
 */
object DecorateResultParser {

    private val allowedColors = setOf(
        "#D32F2F", "#E65100", "#F9A825", "#2E7D32", "#0277BD", "#6A1B9A"
    )

    /** 가독성 좋은 사이즈 화이트리스트 (sp). 기본 본문 ~ 강조/제목 단계. */
    private val allowedSizes = setOf(14, 15, 18, 22, 26)

    fun parse(rawJson: String, originalText: String): DecorateResult {
        val arr = extractJsonArray(rawJson) ?: return DecorateResult(originalText, emptyList())
        val out = mutableListOf<DecorateSuggestion>()
        for (i in 0 until arr.length()) {
            val obj: JSONObject = arr.optJSONObject(i) ?: continue
            val keyword = obj.optString("keyword", "").trim()
            if (keyword.isEmpty()) continue
            val bold = obj.optBoolean("bold", false)
            val italic = obj.optBoolean("italic", false)
            val underline = obj.optBoolean("underline", false)
            val colorRaw = obj.optString("color", "").trim().uppercase()
            val color = colorRaw.takeIf { it in allowedColors }
            val sizeRaw = if (obj.has("size") && !obj.isNull("size")) obj.optInt("size") else -1
            val sizeSp = sizeRaw.takeIf { it in allowedSizes }

            val start = obj.optInt("start", -1)
            val end = obj.optInt("end", -1)
            val resolved = resolveRange(keyword, start, end, originalText)
            if (resolved == null) continue
            out.add(
                DecorateSuggestion(
                    keyword = originalText.substring(resolved.first, resolved.second),
                    start = resolved.first,
                    end = resolved.second - 1, // TextFormatting 은 end inclusive
                    bold = bold,
                    italic = italic,
                    underline = underline,
                    color = color,
                    sizeSp = sizeSp
                )
            )
        }
        return DecorateResult(originalText, out)
    }

    /**
     * 응답 텍스트에서 JSON 배열 부분만 추출합니다. 코드펜스/잡문 섞여있어도 첫 '[' 부터 매칭되는 ']' 까지 자릅니다.
     */
    private fun extractJsonArray(raw: String): JSONArray? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try {
            JSONArray(raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * start/end 가 유효하면 그대로 사용, 아니면 본문에서 [keyword] 의 첫 등장 위치를 찾습니다.
     * 마지막 폴백으로는 substring 으로 검증합니다.
     */
    private fun resolveRange(
        keyword: String,
        start: Int,
        end: Int,
        originalText: String
    ): Pair<Int, Int>? {
        val len = originalText.length
        // 1) 모델이 준 범위 검증
        if (start in 0..len && end in start..len) {
            if (end - start == keyword.length) {
                if (originalText.regionMatches(start, keyword, 0, keyword.length, ignoreCase = false)) {
                    return start to end
                }
            }
        }
        // 2) 본문에서 키워드 검색
        val found = originalText.indexOf(keyword)
        if (found >= 0) return found to (found + keyword.length)
        return null
    }
}

/**
 * [DecorateResult.suggestions] 를 [TextFormatting] 으로 변환합니다.
 *
 * 각 제안의 6가지 스타일(bold / italic / underline / color / size) 을 독립적으로 적용하며,
 * 같은 범위에 여러 스타일이 누적되면 모두 합쳐진다.
 */
fun DecorateResult.toTextFormatting(): TextFormatting {
    var fmt = TextFormatting.Empty
    suggestions.forEach { s ->
        val range = s.start..s.end
        if (s.bold) fmt = fmt.toggleBold(range)
        if (s.italic) fmt = fmt.toggleItalic(range)
        if (s.underline) fmt = fmt.toggleUnderline(range)
        if (s.color != null) fmt = fmt.setColor(range, s.color)
        if (s.sizeSp != null) fmt = fmt.setSize(range, s.sizeSp)
    }
    return fmt
}
