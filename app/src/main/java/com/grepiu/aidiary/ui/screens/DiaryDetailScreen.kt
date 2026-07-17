package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.data.model.DiaryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 일기 상세 내역 및 AI 피드백을 조회하는 화면 컴포저블입니다.
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
    val dateFullText = remember(diary.timestamp) {
        SimpleDateFormat("yyyy년 M월 d일 (EEEE)", Locale.KOREAN).format(Date(diary.timestamp))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "일기 상세", fontWeight = FontWeight.Bold) },
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
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. 날짜 및 감정 태그 표시
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

            // 2. 일기 제목
            Text(
                text = diary.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // 3. 일기 본문 (블록 렌더러)
            com.grepiu.aidiary.ui.components.BlockList(
                blocks = diary.blocks,
                textColor = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. AI 피드백 구역
            if (diary.aiAnalysis != null) {
                val (_, emotionColor) = getEmotionUI(diary.emotion)
                
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    emotionColor.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "💌 AI 마음 멘토의 공감 리포트",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = emotionColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Text(
                            text = diary.aiAnalysis,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "이 일기는 AI 분석 기록이 없습니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
