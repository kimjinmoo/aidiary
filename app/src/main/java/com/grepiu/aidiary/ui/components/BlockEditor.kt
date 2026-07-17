package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
) {
    val (typeLabel, typeIcon, accent) = blockTypeMeta(block)
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
        // 블록 헤더: 타입 라벨 + 이동/삭제 메뉴
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
        }
    }
}

/**
 * RichTextField + RichTextToolbar 를 묶어 텍스트+서식 편집을 처리하는 컴포저블.
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
    singleLine: Boolean = false
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    var tfv by remember(initialText) {
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
    }
    var formatting by remember(initialFormatting) {
        mutableStateOf(initialFormatting)
    }

    // 외부에서 텍스트/서식이 변경되면(예: 음성 전사 결과) 동기화
    if (tfv.text != initialText && initialText.length >= tfv.text.length) {
        // 단순히 동기화 (음성 전사 등)
        tfv = TextFieldValue(text = initialText, selection = TextRange(initialText.length))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        RichTextField(
            value = tfv,
            onValueChange = { newTfv ->
                val oldText = tfv.text
                tfv = newTfv
                formatting = formatting.shift(oldText, newTfv.text)
                onUpdate(newTfv.text, formatting)
            },
            formatting = formatting,
            textStyle = baseTextStyle.copy(color = baseColor),
            placeholder = placeholder,
            minLines = minLines,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth()
        )
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "블록 추가",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AddChip(icon = Icons.Default.Title, label = "제목", onClick = { onAdd(ContentBlock.HeadingBlock(text = "")) })
            AddChip(icon = Icons.Default.Notes, label = "본문", onClick = { onAdd(ContentBlock.TextBlock(text = "")) })
            AddChip(icon = Icons.Default.FormatQuote, label = "인용", onClick = { onAdd(ContentBlock.QuoteBlock(text = "")) })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
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
    is ContentBlock.TextBlock -> Triple("본문", Icons.Default.Notes, MaterialTheme.colorScheme.primary)
    is ContentBlock.QuoteBlock -> Triple("인용", Icons.Default.FormatQuote, Color(0xFF6A1B9A))
    is ContentBlock.ImageBlock -> Triple("이미지", Icons.Default.Image, Color(0xFF2E7D32))
    is ContentBlock.DividerBlock -> Triple("구분선", Icons.Default.HorizontalRule, MaterialTheme.colorScheme.outline)
}
