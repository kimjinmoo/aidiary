package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.data.model.DiaryEntry
import com.grepiu.aidiary.mvi.state.DiaryPhase
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.ui.components.DownloadStatusCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 일기 목록 및 대시보드 화면 컴포저블입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    state: DiaryState,
    onSelectDiary: (DiaryEntry) -> Unit,
    onWriteDiary: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "마음 기록 일기장",
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "내 마음에 귀를 기울이는 시간",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onWriteDiary,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "새 일기 쓰기",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 80.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 대시보드 요약부 (감정 빈도 분석 현황판)
            item {
                MoodStatisticsDashboard(state.diaries)
            }

            // 2. AI 다운로드 상태 카드 (준비가 완전히 완료되지 않았을 때만 노출)
            if (!state.isModelReady) {
                item {
                    DownloadStatusCard(
                        state = state,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDismissNotice = onDismissNotice,
                        onDismissWifiWarning = onDismissWifiWarning
                    )
                }
            }

            // 3. 일기 목록 헤더
            item {
                Text(
                    text = "최근 기록들",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // 4. 저장된 일기 데이터 리스트
            if (state.diaries.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Text(
                            text = "아직 기록된 일기가 없어요.\n우측 하단의 버튼을 눌러 오늘을 기록해 보세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(state.diaries, key = { it.id }) { diary ->
                    DiaryListItemCard(diary = diary, onClick = { onSelectDiary(diary) })
                }
            }
        }
    }
}

/**
 * 대시보드 요약 컴포저블: 기록된 일기 속 감정들의 현황을 시각화합니다.
 */
@Composable
fun MoodStatisticsDashboard(diaries: List<DiaryEntry>) {
    val totalCount = diaries.size
    val moodCounts = diaries.groupingBy { it.emotion }.eachCount()

    val joyCount = moodCounts["Joy"] ?: 0
    val calmCount = moodCounts["Calm"] ?: 0
    val sadnessCount = moodCounts["Sadness"] ?: 0
    val anxietyCount = moodCounts["Anxiety"] ?: 0
    val angerCount = moodCounts["Anger"] ?: 0

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "내 마음 통계 현황 (총 ${totalCount}회)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MoodStatBar(label = "😊 기쁨", count = joyCount, total = totalCount, color = Color(0xFFFBC02D))
                MoodStatBar(label = "🌿 평온", count = calmCount, total = totalCount, color = Color(0xFF4CAF50))
                MoodStatBar(label = "😢 슬픔", count = sadnessCount, total = totalCount, color = Color(0xFF2196F3))
                MoodStatBar(label = "😰 불안", count = anxietyCount, total = totalCount, color = Color(0xFF9C27B0))
                MoodStatBar(label = "😡 분노", count = angerCount, total = totalCount, color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun RowScope.MoodStatBar(label: String, count: Int, total: Int, color: Color) {
    val ratio = if (total > 0) count.toFloat() / total else 0f
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .height(80.dp)
                .width(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(ratio)
                    .align(Alignment.BottomCenter)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${count}회",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 개별 일기 리스트 아이템 카드 컴포저블
 */
@Composable
fun DiaryListItemCard(diary: DiaryEntry, onClick: () -> Unit) {
    val dateText = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(diary.timestamp))
    
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dateText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val (emotionText, emotionColor) = getEmotionUI(diary.emotion)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = emotionColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = emotionText,
                            color = emotionColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = diary.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = diary.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                
                if (diary.aiAnalysis != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ AI 일기 피드백 완료",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 영어 감정 코드명에 상응하는 한글 라벨과 색상 테마를 정의하는 헬퍼 함수
 */
fun getEmotionUI(emotion: String): Pair<String, Color> {
    return when (emotion) {
        "Joy" -> Pair("😊 기쁨", Color(0xFFD4AF37))
        "Calm" -> Pair("🌿 평온", Color(0xFF2E7D32))
        "Sadness" -> Pair("😢 슬픔", Color(0xFF1565C0))
        "Anxiety" -> Pair("😰 불안", Color(0xFF7B1FA2))
        "Anger" -> Pair("😡 분노", Color(0xFFC62828))
        else -> Pair("⚪ 보통", Color(0xFF555555))
    }
}
