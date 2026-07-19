package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.grepiu.aidiary.data.model.TextFormatting
import kotlinx.coroutines.launch

/**
 * 인라인 서식이 적용된 텍스트 에디터.
 *
 * - 뒤쪽에 [Text] 로 포맷된 [AnnotatedString] 을 깔고, 그 위에 [BasicTextField] 를
 *   투명 텍스트/투명 indicator 로 겹쳐 입력 중에도 서식이 인라인으로 보이게 합니다.
 * - 선택 영역은 [selection] 으로 받고, 변경 시 [onValueChange] 로 부모에 알립니다.
 * - 포커스 획득/텍스트 변경 시 [BringIntoViewRequester] 로 부모 스크롤이
 *   해당 필드를 가시 영역으로 끌어올려 키보드에 가려지지 않도록 합니다.
 */
@Composable
fun RichTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    formatting: TextFormatting,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    placeholder: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    val baseColor = textStyle.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    val annotated = remember(value.text, formatting, baseColor) {
        formatting.toAnnotatedString(value.text, baseColor)
    }
    val placeholderStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
    val inputStyle = textStyle.copy(color = Color.Transparent)
    val cursorColor = MaterialTheme.colorScheme.primary

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // 텍스트/커서 변화(타이핑, 엔터, 붙여넣기 등) 시 부모 스크롤이
    // 필드를 가시 영역으로 끌어올림 → 키보드에 가려지지 않도록 함.
    LaunchedEffect(value.text, value.selection) {
        bringIntoViewRequester.bringIntoView()
    }

    Box(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester)
    ) {
        // 포맷된 텍스트를 배경에 깔아 인라인 프리뷰를 제공
        Text(
            text = annotated,
            modifier = Modifier
                .fillMaxWidth()
                .padding(textStyle.paddingOrZero()),
            style = textStyle.copy(color = baseColor),
        )
        // 실제 입력은 투명 텍스트의 BasicTextField 가 처리 (커서만 보임)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = inputStyle,
            cursorBrush = SolidColor(cursorColor),
            singleLine = singleLine,
            minLines = minLines,
            modifier = Modifier
                .fillMaxWidth()
                .padding(textStyle.paddingOrZero())
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )
        if (value.text.isEmpty() && placeholder != null) {
            Text(
                text = placeholder,
                style = placeholderStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(textStyle.paddingOrZero())
            )
        }
    }
}

@Composable
private fun TextStyle.paddingOrZero(): androidx.compose.foundation.layout.PaddingValues {
    // 단순화를 위해 padding 은 호출자가 modifier 로 처리하도록 0 반환
    return androidx.compose.foundation.layout.PaddingValues(0.dp)
}

/**
 * 도구 모음에서 사용할 서식 토글 헬퍼.
 */
object FormattingToggles {
    fun toggleBold(fmt: TextFormatting, range: TextRange): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.toggleBold(r)
    }

    fun toggleItalic(fmt: TextFormatting, range: TextRange): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.toggleItalic(r)
    }

    fun toggleUnderline(fmt: TextFormatting, range: TextRange): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.toggleUnderline(r)
    }

    fun toggleStrikethrough(fmt: TextFormatting, range: TextRange): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.toggleStrikethrough(r)
    }

    fun setColor(fmt: TextFormatting, range: TextRange, hex: String?): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.setColor(r, hex)
    }

    fun setSize(fmt: TextFormatting, range: TextRange, sizeSp: Int?): TextFormatting {
        if (range.collapsed) return fmt
        val r = range.start..(range.end - 1)
        return fmt.setSize(r, sizeSp)
    }
}
