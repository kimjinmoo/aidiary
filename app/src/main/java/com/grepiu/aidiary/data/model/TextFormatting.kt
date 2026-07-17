package com.grepiu.aidiary.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * 일기 본문 텍스트의 인라인 서식.
 *
 * 각 스타일은 [IntRange] (start 포함, end 포함) 의 리스트로 표현됩니다.
 * - bold/italic/underline/strikethrough: 단순 범위
 * - colorRanges / sizeRanges: (IntRange, 값) 페어
 *
 * 범위는 텍스트 변경 시 [shiftFormatting] 으로 이동/분할/병합됩니다.
 */
data class TextFormatting(
    val boldRanges: List<IntRange> = emptyList(),
    val italicRanges: List<IntRange> = emptyList(),
    val underlineRanges: List<IntRange> = emptyList(),
    val strikethroughRanges: List<IntRange> = emptyList(),
    val colorRanges: List<Pair<IntRange, String>> = emptyList(),
    val sizeRanges: List<Pair<IntRange, Int>> = emptyList(),
) {
    fun isBoldAt(pos: Int): Boolean = boldRanges.any { pos in it }
    fun isItalicAt(pos: Int): Boolean = italicRanges.any { pos in it }
    fun isUnderlineAt(pos: Int): Boolean = underlineRanges.any { pos in it }
    fun isStrikethroughAt(pos: Int): Boolean = strikethroughRanges.any { pos in it }
    fun colorAt(pos: Int): String? = colorRanges.firstOrNull { pos in it.first }?.second
    fun sizeAt(pos: Int): Int? = sizeRanges.firstOrNull { pos in it.first }?.second

    fun isAllBold(start: Int, end: Int): Boolean =
        isStyleActive(boldRanges, start, end)

    fun isAllItalic(start: Int, end: Int): Boolean =
        isStyleActive(italicRanges, start, end)

    fun isAllUnderline(start: Int, end: Int): Boolean =
        isStyleActive(underlineRanges, start, end)

    fun isAllStrikethrough(start: Int, end: Int): Boolean =
        isStyleActive(strikethroughRanges, start, end)

    /**
     * 서식이 비어있는지 (저장 직전 필터링에 사용).
     */
    fun isEmpty(): Boolean = boldRanges.isEmpty() &&
        italicRanges.isEmpty() &&
        underlineRanges.isEmpty() &&
        strikethroughRanges.isEmpty() &&
        colorRanges.isEmpty() &&
        sizeRanges.isEmpty()

    // ===== 변경 헬퍼 =====

    fun toggleBold(range: IntRange): TextFormatting =
        copy(boldRanges = toggleRange(boldRanges, range, isAllBold(range.first, range.last + 1)))

    fun toggleItalic(range: IntRange): TextFormatting =
        copy(italicRanges = toggleRange(italicRanges, range, isAllItalic(range.first, range.last + 1)))

    fun toggleUnderline(range: IntRange): TextFormatting =
        copy(underlineRanges = toggleRange(underlineRanges, range, isAllUnderline(range.first, range.last + 1)))

    fun toggleStrikethrough(range: IntRange): TextFormatting =
        copy(strikethroughRanges = toggleRange(strikethroughRanges, range, isAllStrikethrough(range.first, range.last + 1)))

    fun setColor(range: IntRange, hex: String?): TextFormatting = copy(
        colorRanges = setPairRange(colorRanges, range, hex)
    )

    fun setSize(range: IntRange, sizeSp: Int?): TextFormatting = copy(
        sizeRanges = setPairRange(sizeRanges, range, sizeSp)
    )

    /**
     * 텍스트가 [oldText] -> [newText] 로 변경될 때 서식 범위를 정렬합니다.
     * - 단순 삽입: start 이상 범위는 +delta 만큼 이동
     * - 단순 삭제: start 이상 범위는 -delta 만큼 이동, 겹치는 범위는 축소/분할
     */
    fun shift(oldText: String, newText: String): TextFormatting {
        val diff = newText.length - oldText.length
        if (diff == 0) return this

        // 변경 지점(좌측 prefix 길이) 찾기
        var prefixLen = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefixLen < maxPrefix && oldText[prefixLen] == newText[prefixLen]) prefixLen++

        val oldEnd = oldText.length
        val newEnd = newText.length
        // 우측 suffix 길이 찾기
        var suffixLen = 0
        while (
            suffixLen < (oldEnd - prefixLen) &&
            suffixLen < (newEnd - prefixLen) &&
            oldText[oldEnd - 1 - suffixLen] == newText[newEnd - 1 - suffixLen]
        ) suffixLen++

        val deleteStart = prefixLen
        val deleteEnd = oldEnd - suffixLen
        val insertStart = prefixLen
        val insertEnd = newEnd - suffixLen

        fun shiftRange(r: IntRange): IntRange? = shiftOne(r, deleteStart, deleteEnd, insertStart, insertEnd)

        return TextFormatting(
            boldRanges = boldRanges.mapNotNull(::shiftRange),
            italicRanges = italicRanges.mapNotNull(::shiftRange),
            underlineRanges = underlineRanges.mapNotNull(::shiftRange),
            strikethroughRanges = strikethroughRanges.mapNotNull(::shiftRange),
            colorRanges = colorRanges.mapNotNull { (r, v) -> shiftRange(r)?.let { it to v } },
            sizeRanges = sizeRanges.mapNotNull { (r, v) -> shiftRange(r)?.let { it to v } },
        )
    }

    /**
     * 현재 서식을 Compose [AnnotatedString] 으로 변환합니다.
     */
    fun toAnnotatedString(text: String, baseColor: Color): AnnotatedString {
        if (text.isEmpty() || isEmpty()) {
            return AnnotatedString(text)
        }
        val len = text.length
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
        var pos = 0
        while (pos < len) {
            val isBold = isBoldAt(pos)
            val isItalic = isItalicAt(pos)
            val isUnder = isUnderlineAt(pos)
            val isStrike = isStrikethroughAt(pos)
            val color = colorAt(pos)
            val size = sizeAt(pos)
            if (!isBold && !isItalic && !isUnder && !isStrike && color == null && size == null) {
                pos++
                continue
            }
            // 같은 서식이 이어지는 구간을 찾아 한 번에 addStyle
            var end = pos + 1
            while (end < len &&
                isBoldAt(end) == isBold &&
                isItalicAt(end) == isItalic &&
                isUnderlineAt(end) == isUnder &&
                isStrikethroughAt(end) == isStrike &&
                colorAt(end) == color &&
                sizeAt(end) == size
            ) end++
            val span = SpanStyle(
                fontWeight = if (isBold) FontWeight.Bold else null,
                fontStyle = if (isItalic) FontStyle.Italic else null,
                textDecoration = when {
                    isUnder && isStrike -> TextDecoration.Underline + TextDecoration.LineThrough
                    isUnder -> TextDecoration.Underline
                    isStrike -> TextDecoration.LineThrough
                    else -> null
                },
                color = color?.let { parseHexColor(it) ?: baseColor } ?: Color.Unspecified,
                fontSize = if (size != null) size.sp else TextUnit.Unspecified,
            )
            builder.addStyle(span, pos, end)
            pos = end
        }
        return builder.toAnnotatedString()
    }

    // ===== JSON =====

    fun toJson(): JSONObject = JSONObject().apply {
        put("bold", boldRanges.toJsonIntRanges())
        put("italic", italicRanges.toJsonIntRanges())
        put("underline", underlineRanges.toJsonIntRanges())
        put("strikethrough", strikethroughRanges.toJsonIntRanges())
        put("color", colorRanges.toJsonPairRanges())
        put("size", sizeRanges.toJsonPairRangesInt())
    }

    companion object {
        val Empty = TextFormatting()

        fun fromJson(obj: JSONObject?): TextFormatting {
            if (obj == null) return Empty
            return TextFormatting(
                boldRanges = obj.optJSONArray("bold").toIntRanges(),
                italicRanges = obj.optJSONArray("italic").toIntRanges(),
                underlineRanges = obj.optJSONArray("underline").toIntRanges(),
                strikethroughRanges = obj.optJSONArray("strikethrough").toIntRanges(),
                colorRanges = obj.optJSONArray("color").toPairRanges(),
                sizeRanges = obj.optJSONArray("size").toPairRangesInt(),
            )
        }
    }
}

// ===== 유틸 함수 =====

private fun isStyleActive(ranges: List<IntRange>, start: Int, endExclusive: Int): Boolean {
    // [start, endExclusive) 의 모든 위치가 boldRanges 안에 있어야 true
    for (pos in start until endExclusive) {
        if (ranges.any { pos in it }) continue
        return false
    }
    return true
}

/**
 * [range] 범위에 대해 [wasActive] 가 true 면 제거, false 면 추가.
 * 단순한 "선택 영역에 일괄 적용" 시멘틱.
 */
private fun toggleRange(ranges: List<IntRange>, range: IntRange, wasActive: Boolean): List<IntRange> {
    val target: IntRange = range.first..range.last
    return if (wasActive) {
        // 기존 범위에서 target 영역을 제거
        val out = mutableListOf<IntRange>()
        for (r in ranges) {
            val pieces = subtractRange(r, target)
            out.addAll(pieces)
        }
        out.filter { it.first <= it.last }
    } else {
        // target 을 추가하고 인접한 동일 범위와 병합
        val combined: List<IntRange> = ranges + listOf(target)
        mergeRanges(combined)
    }
}

private fun setPairRange(
    pairs: List<Pair<IntRange, String>>,
    range: IntRange,
    value: String?
): List<Pair<IntRange, String>> {
    if (value == null) {
        // 값 제거: 해당 영역의 페어 제거
        val out = mutableListOf<Pair<IntRange, String>>()
        val target = range.first..range.last
        for ((r, v) in pairs) {
            val pieces = subtractRange(r, target)
            pieces.filter { it.first <= it.last }.forEach { out.add(it to v) }
        }
        return mergePairRanges(out)
    }
    val newPairs = mutableListOf<Pair<IntRange, String>>()
    val target = range.first..range.last
    for ((r, v) in pairs) {
        val pieces = subtractRange(r, target)
        pieces.filter { it.first <= it.last }.forEach { newPairs.add(it to v) }
    }
    newPairs.add(target to value)
    return mergePairRanges(newPairs)
}

/**
 * value 페어를 받는 setPairRange 오버로드 (Int size 용).
 */
private fun setPairRange(
    pairs: List<Pair<IntRange, Int>>,
    range: IntRange,
    value: Int?
): List<Pair<IntRange, Int>> {
    if (value == null) {
        val out = mutableListOf<Pair<IntRange, Int>>()
        val target = range.first..range.last
        for ((r, v) in pairs) {
            val pieces = subtractRange(r, target)
            pieces.filter { it.first <= it.last }.forEach { out.add(it to v) }
        }
        return mergePairRanges(out)
    }
    val newPairs = mutableListOf<Pair<IntRange, Int>>()
    val target = range.first..range.last
    for ((r, v) in pairs) {
        val pieces = subtractRange(r, target)
        pieces.filter { it.first <= it.last }.forEach { newPairs.add(it to v) }
    }
    newPairs.add(target to value)
    return mergePairRanges(newPairs)
}

private fun subtractRange(source: IntRange, target: IntRange): List<IntRange> {
    if (source.last < target.first || source.first > target.last) return listOf(source)
    val pieces = mutableListOf<IntRange>()
    if (source.first < target.first) pieces.add(source.first until target.first)
    if (source.last > target.last) pieces.add(target.last + 1..source.last)
    return pieces
}

private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return ranges
    val sorted = ranges.sortedBy { it.first }
    val out = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val last = out.last()
        val cur = sorted[i]
        if (cur.first <= last.last + 1) {
            out[out.lastIndex] = last.first..maxOf(last.last, cur.last)
        } else {
            out.add(cur)
        }
    }
    return out
}

private fun <T> mergePairRanges(pairs: List<Pair<IntRange, T>>): List<Pair<IntRange, T>> {
    if (pairs.isEmpty()) return pairs
    val sorted = pairs.sortedWith(compareBy({ it.first.first }, { it.first.last }))
    val out = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val (lr, lv) = out.last()
        val (cr, cv) = sorted[i]
        if (cv == lv && cr.first <= lr.last + 1) {
            out[out.lastIndex] = (lr.first..maxOf(lr.last, cr.last)) to lv
        } else {
            out.add(sorted[i])
        }
    }
    return out
}

/**
 * 텍스트 변경에 따른 단일 범위 이동.
 * - [deleteStart, deleteEnd) 가 삭제되고, [insertStart, insertEnd) 가 삽입됨.
 */
private fun shiftOne(
    r: IntRange,
    deleteStart: Int,
    deleteEnd: Int,
    insertStart: Int,
    insertEnd: Int,
): IntRange? {
    val insertLen = insertEnd - insertStart
    val deleteLen = deleteEnd - deleteStart
    val delta = insertLen - deleteLen
    return when {
        r.last < deleteStart -> {
            // 변경 영역 전부 앞: 이동 없음 (단, end >= 0 확인)
            r
        }
        r.first > deleteEnd - 1 -> {
            // 변경 영역 전부 뒤: delta 만큼 이동
            (r.first + delta)..(r.last + delta)
        }
        r.first >= deleteStart && r.last < deleteEnd -> {
            // 변경 영역에 완전 포함: 새 영역에 매핑 (start=insertStart, 길이는 기존 길이 유지)
            val newLen = (r.last - r.first + 1)
            if (newLen <= 0) null
            else insertStart until (insertStart + newLen)
        }
        r.first < deleteStart && r.last >= deleteEnd -> {
            // 변경 영역을 감싸는 경우: 끝만 이동
            r.first..(r.last + delta)
        }
        r.first < deleteStart && r.last < deleteEnd -> {
            // 시작은 변경 영역 전, 끝은 변경 영역 안
            val newEnd = insertStart
            if (r.first > newEnd) null else r.first..newEnd
        }
        r.first >= deleteStart && r.first < deleteEnd && r.last >= deleteEnd -> {
            // 시작은 변경 영역 안, 끝은 변경 영역 후
            val newStart = insertEnd
            if (newStart > r.last + delta) null
            else newStart..(r.last + delta)
        }
        else -> r
    }
}

// ===== 직렬화 유틸 =====

private fun List<IntRange>.toJsonIntRanges(): JSONArray {
    val arr = JSONArray()
    forEach { r ->
        arr.put(JSONArray().put(r.first).put(r.last))
    }
    return arr
}

private fun List<Pair<IntRange, String>>.toJsonPairRanges(): JSONArray {
    val arr = JSONArray()
    forEach { (r, v) ->
        arr.put(JSONArray().put(r.first).put(r.last).put(v))
    }
    return arr
}

private fun List<Pair<IntRange, Int>>.toJsonPairRangesInt(): JSONArray {
    val arr = JSONArray()
    forEach { (r, v) ->
        arr.put(JSONArray().put(r.first).put(r.last).put(v))
    }
    return arr
}

private fun JSONArray?.toIntRanges(): List<IntRange> {
    if (this == null) return emptyList()
    val out = mutableListOf<IntRange>()
    for (i in 0 until length()) {
        val a = getJSONArray(i)
        if (a.length() >= 2) out.add(a.getInt(0)..a.getInt(1))
    }
    return out
}

private fun JSONArray?.toPairRanges(): List<Pair<IntRange, String>> {
    if (this == null) return emptyList()
    val out = mutableListOf<Pair<IntRange, String>>()
    for (i in 0 until length()) {
        val a = getJSONArray(i)
        if (a.length() >= 3) out.add(a.getInt(0)..a.getInt(1) to a.getString(2))
    }
    return out
}

private fun JSONArray?.toPairRangesInt(): List<Pair<IntRange, Int>> {
    if (this == null) return emptyList()
    val out = mutableListOf<Pair<IntRange, Int>>()
    for (i in 0 until length()) {
        val a = getJSONArray(i)
        if (a.length() >= 3) out.add(a.getInt(0)..a.getInt(1) to a.getInt(2))
    }
    return out
}

/**
 * #RRGGBB / #AARRGGBB hex color 를 Compose Color 로 변환.
 * 실패 시 null 반환.
 */
fun parseHexColor(hex: String): Color? = try {
    val v = hex.removePrefix("#")
    when (v.length) {
        6 -> Color(android.graphics.Color.parseColor("#FF$v"))
        8 -> Color(android.graphics.Color.parseColor("#$v"))
        else -> null
    }
} catch (_: Exception) {
    null
}
