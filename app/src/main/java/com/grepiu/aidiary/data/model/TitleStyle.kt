package com.grepiu.aidiary.data.model

import org.json.JSONObject

/**
 * 세션(저장된 일기) 제목의 인라인 스타일.
 *
 * - [color] : #RRGGBB hex (예: "#D32F2F"). null 이면 테마 기본색.
 * - [sizeSp]: 텍스트 크기 (sp). null 이면 기본 22sp.
 */
data class TitleStyle(
    val color: String? = null,
    val sizeSp: Int? = null
) {
    /** JSON 직렬화. */
    fun toJson(): JSONObject = JSONObject().apply {
        if (color != null) put("color", color) else put("color", JSONObject.NULL)
        if (sizeSp != null) put("sizeSp", sizeSp) else put("sizeSp", JSONObject.NULL)
    }

    companion object {
        val Default = TitleStyle()

        fun fromJson(obj: JSONObject?): TitleStyle {
            if (obj == null) return Default
            return TitleStyle(
                color = if (obj.isNull("color")) null else obj.optString("color").takeIf { it.isNotBlank() },
                sizeSp = if (obj.isNull("sizeSp")) null else obj.optInt("sizeSp").takeIf { it > 0 }
            )
        }
    }
}
