package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.data.model.TextFormatting
import com.grepiu.aidiary.data.model.parseHexColor

/**
 * 인라인 텍스트 서식 도구 모음.
 * - B/I/U/S 토글
 * - 색상 팔레트 (8색)
 * - 크기 셀렉터 (12/15/18/22)
 */
@Composable
fun RichTextToolbar(
    formatting: TextFormatting,
    selection: TextRange,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onSetColor: (String?) -> Unit,
    onSetSize: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBold = remember(formatting, selection) {
        if (selection.collapsed) false
        else formatting.isAllBold(selection.start, selection.end)
    }
    val isItalic = remember(formatting, selection) {
        if (selection.collapsed) false
        else formatting.isAllItalic(selection.start, selection.end)
    }
    val isUnder = remember(formatting, selection) {
        if (selection.collapsed) false
        else formatting.isAllUnderline(selection.start, selection.end)
    }
    val isStrike = remember(formatting, selection) {
        if (selection.collapsed) false
        else formatting.isAllStrikethrough(selection.start, selection.end)
    }
    val currentColor = remember(formatting, selection) {
        if (selection.collapsed) null
        else (selection.start until selection.end).map { formatting.colorAt(it) }.toSet().singleOrNull()
    }
    val currentSize = remember(formatting, selection) {
        if (selection.collapsed) null
        else (selection.start until selection.end).map { formatting.sizeAt(it) }.toSet().singleOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            ToolbarIconButton(
                icon = Icons.Default.FormatBold,
                label = "B",
                labelBold = true,
                active = isBold,
                onClick = onToggleBold
            )
            ToolbarIconButton(
                icon = Icons.Default.FormatItalic,
                label = "I",
                labelItalic = true,
                active = isItalic,
                onClick = onToggleItalic
            )
            ToolbarIconButton(
                icon = Icons.Default.FormatUnderlined,
                label = "U",
                labelUnderline = true,
                active = isUnder,
                onClick = onToggleUnderline
            )
            ToolbarIconButton(
                icon = Icons.Default.FormatStrikethrough,
                label = "S",
                labelStrike = true,
                active = isStrike,
                onClick = onToggleStrikethrough
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 색상 팔레트
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            ColorSwatch(
                hex = null,
                label = "기본",
                selected = currentColor == null && !selection.collapsed,
                onClick = { onSetColor(null) }
            )
            PresetColors.forEach { hex ->
                ColorSwatch(
                    hex = hex,
                    selected = currentColor == hex,
                    onClick = { onSetColor(hex) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 크기 셀렉터
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = "크기",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
            SizeChip(label = "12", value = 12, selected = currentSize == 12, onClick = { onSetSize(12) })
            SizeChip(label = "15", value = 15, selected = currentSize == 15, onClick = { onSetSize(15) })
            SizeChip(label = "18", value = 18, selected = currentSize == 18, onClick = { onSetSize(18) })
            SizeChip(label = "22", value = 22, selected = currentSize == 22, onClick = { onSetSize(22) })
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    labelBold: Boolean = false,
    labelItalic: Boolean = false,
    labelUnderline: Boolean = false,
    labelStrike: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = if (active) accent.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (active) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (labelItalic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                labelUnderline && labelStrike -> TextDecoration.Underline + TextDecoration.LineThrough
                labelUnderline -> TextDecoration.Underline
                labelStrike -> TextDecoration.LineThrough
                else -> null
            },
            color = if (active) accent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ColorSwatch(
    hex: String?,
    label: String = "",
    selected: Boolean,
    onClick: () -> Unit
) {
    val color: Color = hex?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(borderWidth, borderColor, CircleShape)
                .background(if (hex == null) Color.Transparent else color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (hex == null) {
                Text(
                    text = "A",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SizeChip(label: String, value: Int, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface
        )
    }
}

private val PresetColors = listOf(
    "#D32F2F", // red
    "#E65100", // orange
    "#F9A825", // amber
    "#2E7D32", // green
    "#0277BD", // blue
    "#1565C0", // deep blue
    "#6A1B9A", // purple
    "#AD1457", // pink
    "#424242", // gray
)
