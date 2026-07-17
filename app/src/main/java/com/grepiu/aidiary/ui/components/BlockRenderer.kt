package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import java.io.File

/**
 * 일기 상세/목록 미리보기에서 사용되는 읽기 전용 블록 렌더러.
 */
@Composable
fun BlockRenderer(
    block: ContentBlock,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    imageScale: ContentScale = ContentScale.FillWidth,
    showImageCaption: Boolean = true
) {
    when (block) {
        is ContentBlock.HeadingBlock -> {
            val text = block.text.ifBlank { "제목 없음" }
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                lineHeight = 28.sp
            )
            Text(
                text = block.formatting.toAnnotatedString(text, textColor).let {
                    // 베이스 스타일을 머지
                    androidx.compose.ui.text.AnnotatedString(
                        text = it.text,
                        spanStyles = it.spanStyles,
                        paragraphStyles = it.paragraphStyles
                    )
                },
                style = base,
                modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
        is ContentBlock.TextBlock -> {
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = textColor
            )
            Text(
                text = block.formatting.toAnnotatedString(block.text, textColor),
                style = base,
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        is ContentBlock.QuoteBlock -> {
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontStyle = FontStyle.Italic,
                color = textColor
            )
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = block.formatting.toAnnotatedString(block.text, textColor),
                    style = base
                )
            }
        }
        is ContentBlock.ImageBlock -> {
            val context = LocalContext.current
            val file: File? = remember(block.relativePath) {
                if (block.relativePath.isBlank()) null
                else File(context.filesDir, block.relativePath).takeIf { it.exists() }
            }
            Column(
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = "일기 첨부 이미지",
                        contentScale = imageScale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                if (showImageCaption && block.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = block.caption,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        is ContentBlock.DividerBlock -> {
            HorizontalDivider(
                modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 블록 여러 개를 위→아래로 순서대로 렌더링.
 */
@Composable
fun BlockList(
    blocks: List<ContentBlock>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        blocks.forEach { block ->
            BlockRenderer(block = block, textColor = textColor)
        }
    }
}
