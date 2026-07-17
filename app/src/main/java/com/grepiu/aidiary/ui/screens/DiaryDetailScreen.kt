package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.ui.components.BlockRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 일기 상세 화면. 기업용 SaaS(Bear / Notion / Linear) 수준의 미니멀·정제된 레이아웃을 제공합니다.
 *
 *  구성 (위→아래)
 *  1. 상단 스크롤 진행 바 + 스크롤 시 그림자가 생기는 TopAppBar (뒤로/삭제 액션)
 *  2. 첫 첨부 이미지 기반 Hero (이미지가 있을 때만)
 *  3. 콘텐츠 타입 / 감정 칩 라인
 *  4. Display 스타일 제목
 *  5. 날짜 + 읽기 시간 메타 행
 *  6. 블록 단위 본문 (Heading/Text/Quote/Image/Divider/TagAI)
 *  7. 삭제 확인 다이얼로그 (즉시 삭제, 되돌릴 수 없음 안내)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    diary: DiaryEntry,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // --- 1. 파생 데이터: 날짜/읽기시간/본문 정리 ---
    val dateText = remember(diary.timestamp) {
        SimpleDateFormat("yyyy년 M월 d일 (EEEE)", Locale.KOREAN).format(Date(diary.timestamp))
    }
    val readTimeText = remember(diary.blocks) {
        val charCount = diary.blocks.sumOf { block ->
            when (block) {
                is ContentBlock.TextBlock -> block.text.length
                is ContentBlock.HeadingBlock -> block.text.length
                is ContentBlock.QuoteBlock -> block.text.length
                else -> 0
            }
        }
        val minutes = (charCount / 400).coerceAtLeast(1)
        "읽기 약 ${minutes}분"
    }
    // 제목이 본문 첫 HeadingBlock 으로 저장된 구버전 호환: 제목과 같은 첫 Heading 은 본문에서 제외
    val finalBlocks = remember(diary.blocks, diary.title) {
        diary.blocks.dropWhile {
            it is ContentBlock.HeadingBlock && it.text.trim() == diary.title.trim()
        }
    }
    val scrollProgress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue
    } else 0f

    val (typeIcon, typeLabel, typeColor) = getContentTypeUI(diary.contentType)
    val (emotionText, emotionColor) = getEmotionUI(diary.emotion)

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                typeLabel = typeLabel,
                typeColor = typeColor,
                scrollProgress = scrollProgress,
                onBack = onBack,
                onDelete = { showDeleteDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            // 1) 본문 컨테이너 (첨부 이미지는 본문 ImageBlock 으로 인라인 표시 — hero 별도 노출 안 함)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                // 2-1) 콘텐츠 타입 / 감정 칩
                DetailChipsRow(
                    typeIcon = typeIcon,
                    typeLabel = typeLabel,
                    typeColor = typeColor,
                    emotionText = emotionText,
                    emotionColor = emotionColor
                )

                Spacer(Modifier.height(20.dp))

                // 2-2) 디스플레이 제목
                Text(
                    text = diary.title.ifBlank { "제목 없음" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(14.dp))

                // 2-3) 날짜 + 읽기 시간
                DetailMetaRow(dateText = dateText, readTimeText = readTimeText)

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                Spacer(Modifier.height(24.dp))

                // 2-4) 본문 블록
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    finalBlocks.forEach { block ->
                        BlockRenderer(
                            block = block,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

/* ------------------------- 내부 컴포저블 ------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    typeLabel: String,
    typeColor: Color,
    scrollProgress: Float,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    // 스크롤 시 자연스러운 elevation 증가 (0dp → 4dp)
    val elevation = if (scrollProgress > 0.02f) 4.dp else 0.dp
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 2dp 스크롤 진행 바
            if (scrollProgress > 0f) {
                LinearProgressIndicator(
                    progress = { scrollProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = typeColor,
                    trackColor = Color.Transparent,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
            }
            TopAppBar(
                title = {
                    Text(
                        text = "${typeLabel} 상세",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun DetailChipsRow(
    typeIcon: ImageVector,
    typeLabel: String,
    typeColor: Color,
    emotionText: String,
    emotionColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetaChip(icon = typeIcon, label = typeLabel, color = typeColor)
        MetaChip(emoji = emotionText, color = emotionColor)
    }
}

@Composable
private fun MetaChip(
    color: Color,
    label: String? = null,
    icon: ImageVector? = null,
    emoji: String? = null
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            if (emoji != null) {
                Text(text = emoji, fontSize = 13.sp)
            }
            if (label != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DetailMetaRow(
    dateText: String,
    readTimeText: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MetaInlineIconText(
            icon = Icons.Filled.DateRange,
            text = dateText
        )
        MetaInlineIconText(
            icon = Icons.Filled.AccessTime,
            text = readTimeText
        )
    }
}

@Composable
private fun MetaInlineIconText(
    icon: ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "일기를 삭제할까요?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "삭제된 일기는 휴지통으로 이동되지 않고 즉시 사라져요. 되돌릴 수 없어요.",
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("삭제", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
