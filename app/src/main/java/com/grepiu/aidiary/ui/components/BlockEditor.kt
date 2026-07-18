package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.TextFormatting
import java.io.File

/**
 * 일기 작성 화면에서 사용되는 블록 에디터.
 * 각 블록의 종류에 따라 입력 UI 가 달라집니다.
 */
@Composable
fun BlockEditor(
    block: ContentBlock,
    index: Int,
    totalCount: Int,
    onUpdateText: (String, TextFormatting) -> Unit,
    onUpdateCaption: (String) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isProofreading: Boolean = false,
    isDecorating: Boolean = false,
    aiAssistEnabled: Boolean = true,
    onProofread: () -> Unit = {},
    onDecorate: () -> Unit = {},
    onTableCellChange: (Int, Int, String) -> Unit = { _, _, _ -> },
    onTableAddRow: () -> Unit = {},
    onTableRemoveRow: (Int) -> Unit = {},
    onTableAddColumn: () -> Unit = {},
    onTableRemoveColumn: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (typeLabel, typeIcon, accent) = blockTypeMeta(block)
    val isTagAi = block is ContentBlock.TagAiBlock
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        // 블록 헤더: 타입 라벨 + 이동/삭제 메뉴 (TAG AI 는 자체 헤더 사용)
        if (!isTagAi) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = typeLabel,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = typeLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(modifier = Modifier.weight(1f))
                if (block is ContentBlock.TextBlock ||
                    block is ContentBlock.HeadingBlock ||
                    block is ContentBlock.QuoteBlock
                ) {
                    BlockAiMenu(
                        enabled = aiAssistEnabled,
                        isProofreading = isProofreading,
                        isDecorating = isDecorating,
                        hasText = (block as? ContentBlock.TextBlock)?.text?.isNotBlank() == true ||
                            (block as? ContentBlock.HeadingBlock)?.text?.isNotBlank() == true ||
                            (block as? ContentBlock.QuoteBlock)?.text?.isNotBlank() == true,
                        onProofread = onProofread,
                        onDecorate = onDecorate
                    )
                }
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "위로",
                        tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "아래로",
                        tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "블록 삭제",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 블록 본문 (타입별 입력 UI)
        when (block) {
            is ContentBlock.HeadingBlock -> {
                RichTextEditorBody(
                    initialText = block.text,
                    initialFormatting = block.formatting,
                    placeholder = "섹션 제목",
                    baseTextStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 26.sp
                    ),
                    onUpdate = onUpdateText,
                    showToolbar = true,
                    minLines = 1,
                    singleLine = true
                )
            }
            is ContentBlock.TextBlock -> {
                RichTextEditorBody(
                    initialText = block.text,
                    initialFormatting = block.formatting,
                    placeholder = "오늘의 이야기를 자유롭게 적어보세요…",
                    baseTextStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                    onUpdate = onUpdateText,
                    showToolbar = true,
                    minLines = 3
                )
            }
            is ContentBlock.QuoteBlock -> {
                RichTextEditorBody(
                    initialText = block.text,
                    initialFormatting = block.formatting,
                    placeholder = "기억에 남는 한마디",
                    baseTextStyle = TextStyle(
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    ),
                    onUpdate = onUpdateText,
                    showToolbar = true,
                    minLines = 2
                )
            }
            is ContentBlock.ImageBlock -> {
                ImageBlockEditor(
                    block = block,
                    onUpdateCaption = onUpdateCaption
                )
            }
            is ContentBlock.DividerBlock -> {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            }
            is ContentBlock.TagAiBlock -> {
                TagAiBlockEditor(block = block, onRemove = onRemove)
            }
            is ContentBlock.TableBlock -> {
                TableBlockEditor(
                    block = block,
                    onCellChange = onTableCellChange,
                    onAddRow = onTableAddRow,
                    onRemoveRow = onTableRemoveRow,
                    onAddColumn = onTableAddColumn,
                    onRemoveColumn = onTableRemoveColumn
                )
            }
            is ContentBlock.LocationBlock -> {
                LocationBlockEditor(block = block)
            }
        }
    }
}

/**
 * 에디터 내 위치 블록 렌더러.
 * 현재 주소와 위경도 좌표를 깔끔하게 출력합니다.
 */
@Composable
private fun LocationBlockEditor(
    block: ContentBlock.LocationBlock,
    modifier: Modifier = Modifier
) {
    val isLoading = block.latitude == 0.0 && block.longitude == 0.0 && block.address == "위치 정보를 가져오는 중..."

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "위치",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.address.ifBlank { "알 수 없는 주소" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!isLoading) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "위도: ${String.format(java.util.Locale.US, "%.5f", block.latitude)}, 경도: ${String.format(java.util.Locale.US, "%.5f", block.longitude)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * TAG AI 블록 에디터. AI 가 자동 생성한 감정 태그 1줄만 보여주며,
 * 우측 삭제 버튼으로만 제거 가능합니다. 본문 편집 불가.
 */
@Composable
private fun TagAiBlockEditor(
    block: ContentBlock.TagAiBlock,
    onRemove: () -> Unit
) {
    val (emotionLabel, emotionColor) = tagEmotionUi(block.emotion)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "TAG AI",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = emotionLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = emotionColor,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(emotionColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "AI 자동 생성 · 편집 불가",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "TAG AI 블록 삭제",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun tagEmotionUi(label: String): Pair<String, Color> = when (label.trim()) {
    "기쁨" -> "😊 기쁨" to Color(0xFFD4AF37)
    "평온" -> "🌿 평온" to Color(0xFF2E7D32)
    "슬픔" -> "😢 슬픔" to Color(0xFF1565C0)
    "불안" -> "😰 불안" to Color(0xFF7B1FA2)
    "분노" -> "😡 분노" to Color(0xFFC62828)
    else -> "평온" to Color(0xFF2E7D32)
}

/* ========================== 표 블록 ========================== */

/**
 * 표 블록 에디터.
 *
 * - 셀은 [BasicTextField] 로 직접 인라인 편집
 * - 상단 툴바: 행/열 추가/삭제 (마지막 1행·1열은 삭제 불가)
 * - 첫 행은 헤더로 시각 구분 (굵게 + 옅은 배경)
 */
@Composable
private fun TableBlockEditor(
    block: ContentBlock.TableBlock,
    onCellChange: (Int, Int, String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: (Int) -> Unit
) {
    val cellBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val primaryBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, cellBorder, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
    ) {
        // 툴바: 행/열 추가·삭제
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            TableToolButton(
                icon = Icons.Default.Add,
                label = "행",
                onClick = onAddRow
            )
            TableToolButton(
                icon = Icons.Default.Remove,
                label = "행",
                onClick = { onRemoveRow(block.rows - 1) },
                enabled = block.rows > 1
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(1.dp, 16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            TableToolButton(
                icon = Icons.Default.Add,
                label = "열",
                onClick = onAddColumn
            )
            TableToolButton(
                icon = Icons.Default.Remove,
                label = "열",
                onClick = { onRemoveColumn(block.cols - 1) },
                enabled = block.cols > 1
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${block.rows} × ${block.cols}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        // 헤더 (첫 행)
        TableRowEditor(
            row = block.cells.firstOrNull().orEmpty(),
            rowIndex = 0,
            isHeader = true,
            border = cellBorder,
            headerBg = headerBg,
            onCellChange = onCellChange
        )

        // 본문 행
        block.cells.drop(1).forEachIndexed { idx, row ->
            TableRowEditor(
                row = row,
                rowIndex = idx + 1,
                isHeader = false,
                border = cellBorder,
                headerBg = headerBg,
                onCellChange = onCellChange
            )
        }
    }
}

@Composable
private fun TableRowEditor(
    row: List<String>,
    rowIndex: Int,
    isHeader: Boolean,
    border: Color,
    headerBg: Color,
    onCellChange: (Int, Int, String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEachIndexed { colIdx, cellText ->
            val cellModifier = Modifier
                .weight(1f)
                .background(if (isHeader) headerBg else Color.Transparent)
                .border(width = 0.5.dp, color = border)
            TableCellEditor(
                modifier = cellModifier,
                value = cellText,
                isHeader = isHeader,
                onChange = { onCellChange(rowIndex, colIdx, it) }
            )
        }
    }
}

@Composable
private fun TableCellEditor(
    modifier: Modifier,
    value: String,
    isHeader: Boolean,
    onChange: (String) -> Unit
) {
    // IME 조합(한글 등) 보호: 외부 value 와 로컬 tfv 가 항상 일치하지 않을 수 있으므로
    // tfv 자체는 컴포저블 수명 동안 유지하고, 외부 동기화는 LaunchedEffect 에서
    // 조합 중이 아닐 때만 덮어씁니다. (RichTextEditorBody 와 동일 패턴)
    var tfv by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }
    var lastAppliedExternalText by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (value != lastAppliedExternalText && tfv.composition == null) {
            if (tfv.text != value) {
                tfv = TextFieldValue(
                    text = value,
                    selection = TextRange(value.length)
                )
            }
            lastAppliedExternalText = value
        } else if (value != lastAppliedExternalText && tfv.composition != null) {
            // 조합 중이라면 다음 프레임에 재시도하도록 플래그만 갱신
            lastAppliedExternalText = value
        }
    }

    BasicTextField(
        value = tfv,
        onValueChange = { newTfv ->
            val oldText = tfv.text
            tfv = newTfv
            if (newTfv.text != oldText) {
                onChange(newTfv.text)
            }
            // 사용자가 직접 입력한 경우 외부 동기화 플래그를 현재 텍스트로 맞춤
            lastAppliedExternalText = newTfv.text
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = false,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (tfv.text.isEmpty()) {
                    Text(
                        text = if (isHeader) "열 이름" else "내용 입력",
                        fontSize = 12.sp,
                        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun TableToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}


/**
 * RichTextField + RichTextToolbar 를 묶어 텍스트+서식 편집을 처리하는 컴포저블.
 *
 * 외부(예: 음성 전사)에서 [initialText] 가 바뀌었을 때만 로컬 [tfv] 상태를
 * 동기화합니다. 사용자가 한글 IME 로 입력 중인 경우(조합 중)에는 동기화를
 * 건너뛰어 조합 중인 문자가 사라지지 않도록 보호합니다.
 */
@Composable
private fun RichTextEditorBody(
    initialText: String,
    initialFormatting: TextFormatting,
    placeholder: String,
    baseTextStyle: TextStyle,
    onUpdate: (String, TextFormatting) -> Unit,
    showToolbar: Boolean,
    minLines: Int = 1,
    singleLine: Boolean = false,
    maxChars: Int = 600
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    // tfv 는 컴포저블 수명 동안 유지 (initialText 가 바뀌어도 재생성하지 않음).
    // 외부 동기화는 아래 LaunchedEffect 에서 처리.
    var tfv by remember {
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
    }
    var formatting by remember(initialFormatting) {
        mutableStateOf(initialFormatting)
    }
    // 마지막으로 외부값을 적용한 시점의 initialText. 이 값과 다른 경우에만 동기화.
    var lastAppliedExternalText by remember { mutableStateOf(initialText) }

    // 외부 텍스트가 변경되었을 때만 동기화 (사용자 타이핑으로 인한 내부 루프 방지).
    // 한글 IME 조합 중(tfv.composition != null)이라면 강제로 덮어쓰지 않음.
    LaunchedEffect(initialText) {
        if (initialText != lastAppliedExternalText && tfv.composition == null) {
            // 단순 동기화: 로컬 상태가 외부와 다르고, 조합 중이 아닐 때만 덮어쓰기
            if (tfv.text != initialText) {
                tfv = TextFieldValue(
                    text = initialText,
                    selection = TextRange(initialText.length)
                )
            }
            lastAppliedExternalText = initialText
        } else if (initialText != lastAppliedExternalText && tfv.composition != null) {
            // 조합 중이라면 다음 프레임에 재시도하도록 플래그만 갱신
            lastAppliedExternalText = initialText
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        RichTextField(
            value = tfv,
            onValueChange = { newTfv ->
                if (newTfv.text.length <= maxChars || newTfv.text.length < tfv.text.length) {
                    val oldText = tfv.text
                    tfv = newTfv
                    formatting = formatting.shift(oldText, newTfv.text)
                    onUpdate(newTfv.text, formatting)
                    // 사용자가 직접 입력한 경우, 외부 동기화 플래그를 현재 텍스트로 맞춤
                    lastAppliedExternalText = newTfv.text
                }
            },
            formatting = formatting,
            textStyle = baseTextStyle.copy(color = baseColor),
            placeholder = placeholder,
            minLines = minLines,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            val charCount = tfv.text.length
            Text(
                text = "$charCount / ${maxChars}자",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (charCount >= maxChars) MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        if (showToolbar) {
            Spacer(modifier = Modifier.height(6.dp))
            RichTextToolbar(
                formatting = formatting,
                selection = tfv.selection,
                onToggleBold = {
                    formatting = FormattingToggles.toggleBold(formatting, tfv.selection)
                    onUpdate(tfv.text, formatting)
                },
                onToggleItalic = {
                    formatting = FormattingToggles.toggleItalic(formatting, tfv.selection)
                    onUpdate(tfv.text, formatting)
                },
                onToggleUnderline = {
                    formatting = FormattingToggles.toggleUnderline(formatting, tfv.selection)
                    onUpdate(tfv.text, formatting)
                },
                onToggleStrikethrough = {
                    formatting = FormattingToggles.toggleStrikethrough(formatting, tfv.selection)
                    onUpdate(tfv.text, formatting)
                },
                onSetColor = { hex ->
                    formatting = FormattingToggles.setColor(formatting, tfv.selection, hex)
                    onUpdate(tfv.text, formatting)
                },
                onSetSize = { size ->
                    formatting = FormattingToggles.setSize(formatting, tfv.selection, size)
                    onUpdate(tfv.text, formatting)
                }
            )
        }
    }
}

@Composable
private fun ImageBlockEditor(
    block: ContentBlock.ImageBlock,
    onUpdateCaption: (String) -> Unit
) {
    val context = LocalContext.current
    val file: File? = remember(block.relativePath) {
        if (block.relativePath.isBlank()) null
        else File(context.filesDir, block.relativePath).takeIf { it.exists() }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = "첨부 이미지",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "이미지를 찾을 수 없어요",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = block.caption,
            onValueChange = onUpdateCaption,
            placeholder = { Text("이미지 설명(선택)", fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 12.sp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AddBlockBar(
    canAddImage: Boolean,
    onAdd: (ContentBlock) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onAddLocation: () -> Unit,
    hasHeading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            AddChip(
                icon = Icons.Default.Title,
                label = if (hasHeading) "섹션 제목(추가됨)" else "섹션 제목",
                onClick = { onAdd(ContentBlock.HeadingBlock(text = "")) },
                enabled = !hasHeading
            )
            AddChip(icon = Icons.AutoMirrored.Filled.Notes, label = "본문", onClick = { onAdd(ContentBlock.TextBlock(text = "")) })
            AddChip(icon = Icons.Default.FormatQuote, label = "인용", onClick = { onAdd(ContentBlock.QuoteBlock(text = "")) })
            AddChip(
                icon = Icons.Default.GridOn,
                label = "표",
                onClick = { onAdd(ContentBlock.TableBlock(rows = 2, cols = 2, cells = listOf(listOf("", ""), listOf("", "")))) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            AddChip(
                icon = Icons.Default.Image,
                label = "갤러리",
                onClick = onPickGallery,
                enabled = canAddImage
            )
            AddChip(
                icon = Icons.Default.Image,
                label = "카메라",
                onClick = onTakePhoto,
                enabled = canAddImage
            )
            AddChip(
                icon = Icons.Default.HorizontalRule,
                label = "구분선",
                onClick = { onAdd(ContentBlock.DividerBlock()) }
            )
            AddChip(
                icon = Icons.Default.Place,
                label = "위치",
                onClick = onAddLocation
            )
        }
    }
}

@Composable
private fun AddChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}

@Composable
private fun blockTypeMeta(block: ContentBlock): Triple<String, ImageVector, Color> = when (block) {
    is ContentBlock.HeadingBlock -> Triple("섹션 제목", Icons.Default.Title, Color(0xFF1565C0))
    is ContentBlock.TextBlock -> Triple("본문", Icons.AutoMirrored.Filled.Notes, MaterialTheme.colorScheme.primary)
    is ContentBlock.QuoteBlock -> Triple("인용", Icons.Default.FormatQuote, Color(0xFF6A1B9A))
    is ContentBlock.ImageBlock -> Triple("이미지", Icons.Default.Image, Color(0xFF2E7D32))
    is ContentBlock.DividerBlock -> Triple("구분선", Icons.Default.HorizontalRule, MaterialTheme.colorScheme.outline)
    is ContentBlock.TagAiBlock -> Triple("AI 태그", Icons.Default.AutoAwesome, Color(0xFF7B1FA2))
    is ContentBlock.TableBlock -> Triple("표", Icons.Filled.GridOn, Color(0xFF455A64))
    is ContentBlock.LocationBlock -> Triple("현재 위치", Icons.Default.Place, MaterialTheme.colorScheme.secondary)
}

/**
 * 블록 우측 상단의 AI 보조 메뉴 (점 세개 버튼 → 드롭다운).
 *
 * - "AI 보정(오타·띄어쓰기)": 한국어 맞춤법·띄어쓰기 정리
 * - "AI 꾸미기":       색상·굵게·이탤릭·밑줄·크기 조합으로 본문 가독성 향상
 */
@Composable
private fun BlockAiMenu(
    enabled: Boolean,
    isProofreading: Boolean,
    isDecorating: Boolean,
    hasText: Boolean,
    onProofread: () -> Unit,
    onDecorate: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isBusy = isProofreading || isDecorating

    Box {
        IconButton(
            onClick = { menuOpen = true },
            enabled = enabled && !isBusy,
            modifier = Modifier.size(32.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI 보조",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("AI 보정(오타·띄어쓰기)", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                enabled = hasText,
                onClick = {
                    menuOpen = false
                    onProofread()
                }
            )
            DropdownMenuItem(
                text = { Text("AI 꾸미기 (색·크기·밑줄)", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                enabled = hasText,
                onClick = {
                    menuOpen = false
                    onDecorate()
                }
            )
        }
    }
}
