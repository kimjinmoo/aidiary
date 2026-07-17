package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.ui.components.BlockRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 일기 상세 내역 및 AI 피드백을 조회하는 화면 컴포저블입니다.
 * 앱 전체의 일관성 있고 깔끔한 Material 3 테마를 기반으로 한 단순성(Simplicity)을 강조합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    diary: DiaryEntry,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 상세 날짜 포맷
    val dateFullText = remember(diary.timestamp) {
        SimpleDateFormat("yyyy년 M월 d일 (EEEE)", Locale.KOREAN).format(Date(diary.timestamp))
    }

    // 첫 이미지 블록을 인라인 커버 이미지로 사용
    val firstImageBlock = remember(diary.id) {
        diary.blocks.firstOrNull { it is ContentBlock.ImageBlock } as? ContentBlock.ImageBlock
    }
    val headerImageFile: File? = remember(firstImageBlock?.relativePath) {
        val rel = firstImageBlock?.relativePath
        if (rel.isNullOrBlank()) null
        else File(context.filesDir, rel).takeIf { it.exists() }
    }

    val (typeIcon, typeLabel, typeColor) = getContentTypeUI(diary.contentType)
    val isDark = isSystemInDarkTheme()
    val cleanBackgroundColor = if (isDark) MaterialTheme.colorScheme.background else Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${typeLabel} 상세", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cleanBackgroundColor
                )
            )
        },
        containerColor = cleanBackgroundColor,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. 헤더 이미지 (이미지가 있을 경우에만 깔끔하게 상단 라운드 카드로 표시)
            if (headerImageFile != null) {
                AsyncImage(
                    model = headerImageFile,
                    contentDescription = "일기 커버 이미지",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. 날짜 및 감정 태그 행
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = dateFullText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                val (emotionText, emotionColor) = getEmotionUI(diary.emotion)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = emotionColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = emotionText,
                        color = emotionColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 일기 제목
            Text(
                text = diary.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // 4. 일기 본문 (블록 렌더링, 12.dp 간격으로 가독성 있고 일관되게 정렬)
            val finalBlocks = remember(diary.blocks, diary.title) {
                diary.blocks.dropWhile {
                    it is ContentBlock.HeadingBlock && it.text.trim() == diary.title.trim()
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                finalBlocks.forEach { block ->
                    BlockRenderer(block = block, textColor = MaterialTheme.colorScheme.onSurface)
                }
            }

            // 5. AI 피드백 구역 (일치하는 카드 디자인)
            if (diary.contentType.supportsAiAnalysis && diary.aiAnalysis != null) {
                val (_, emotionColor) = getEmotionUI(diary.emotion)
                Spacer(modifier = Modifier.height(28.dp))
                
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = emotionColor.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "✨", fontSize = 14.sp)
                            Text(
                                text = "AI 마음 멘토의 공감 리포트",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = emotionColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = diary.aiAnalysis,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (diary.contentType.supportsAiAnalysis) {
                Spacer(modifier = Modifier.height(28.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "이 일기는 아직 AI 마음 분석 리포트가 생성되지 않았습니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

