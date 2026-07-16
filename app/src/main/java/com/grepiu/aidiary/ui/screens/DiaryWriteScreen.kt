package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.mvi.state.DiaryState

/**
 * 일기 작성 및 수정, AI 분석 요청을 진행하는 화면 컴포저블입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryWriteScreen(
    state: DiaryState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onAnalyzeDiary: () -> Unit,
    onSaveDiary: () -> Unit,
    onBack: () -> Unit,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "일기 작성", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveDiary,
                        enabled = state.draftContent.isNotBlank() && !state.isGeneratingAnalysis
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "저장",
                            tint = if (state.draftContent.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
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
            Spacer(modifier = Modifier.height(8.dp))

            // 1. 일기 제목 입력 필드
            OutlinedTextField(
                value = state.draftTitle,
                onValueChange = onTitleChange,
                label = { Text("일기 제목") },
                placeholder = { Text("오늘 하루의 한 줄 키워드") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 일기 본문 입력 필드
            OutlinedTextField(
                value = state.draftContent,
                onValueChange = onContentChange,
                label = { Text("오늘의 이야기") },
                placeholder = { Text("오늘 어떤 일이 있었나요? 내 생각과 내면의 감정을 자유롭게 써 보세요.") },
                minLines = 8,
                maxLines = 15,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2.5. 음성 녹음 버튼
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRecording) {
                    // 녹음 중 UI
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    ) {
                        IconButton(onClick = onStopRecording) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "녹음 중지",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = "녹음 중... ${state.recordingSeconds}초",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFE53935)
                        )
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFE53935)
                        )
                    }
                } else if (state.isTranscribing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("음성 변환 중...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (state.isWhisperModelReady) {
                    FilledTonalIconButton(
                        onClick = onStartRecording,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "음성 녹음",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "음성으로 일기 쓰기",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.isDownloadingModel) {
                    Text(
                        text = "음성 인식 모델 다운로드 중...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. 온디바이스 AI 마음 분석 영역
            if (state.isModelReady) {
                // AI 분석 버튼
                Button(
                    onClick = onAnalyzeDiary,
                    enabled = state.draftContent.isNotBlank() && !state.isGeneratingAnalysis,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (state.isGeneratingAnalysis) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("AI가 내면의 감정을 읽는 중...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("✨ 온디바이스 AI 마음 일기 분석받기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // AI 실시간 스트리밍 분석 출력 패널
                AnimatedVisibility(visible = state.aiAnalysisText != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = "💌 AI 일기 코칭 리포트",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Text(
                                text = state.aiAnalysisText ?: "",
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            if (state.isGeneratingAnalysis) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI 분석이 작성되고 있어요...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // AI 모델 미구비 시 유도 문구 노출
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "💡 온디바이스 AI 마음 분석 일기 안내",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "목록 화면 상단에서 온디바이스 AI 모델을 다운로드하시면, 작성하신 일기를 분석해 감정을 분류하고 다정하게 공감해주는 AI 마음 코칭 기능을 무료로 사용하실 수 있습니다.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
