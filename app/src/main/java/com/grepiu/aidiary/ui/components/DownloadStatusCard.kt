package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.mvi.state.DiaryState

/**
 * AI 모델 다운로드 상태를 모델별 독립 카드로 보여주는 컴포넌트입니다.
 * Gemma(LLM)과 Sherpa(STT) 다운로드 안내를 동시에 표시합니다.
 */
@Composable
fun DownloadStatusCard(
    state: DiaryState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onStartSherpaDownload: () -> Unit = {},
    onDismissSherpaNotice: () -> Unit = {},
    onUnsupportedDeviceClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 1. 기기 최소 사양 미달 상태 (전역)
        if (state.isDeviceUnsupported) {
            DeviceUnsupportedSection(state, onUnsupportedDeviceClose)
        }

        // 2. 다운로드 진행 중 (전역, 하나의 다운로드만 진행)
        if (state.isDownloadingModel || state.isExtractingModel) {
            DownloadProgressSection(
                state = state,
                onCancelDownload = onCancelDownload
            )
        }

        // 3. AI 엔진 초기화 중 (전역)
        if (state.isModelInitializing) {
            InitializingSection()
        }

        // 4. Wi-Fi 경고 (전역)
        if (state.showWifiWarning) {
            WifiWarningSection(
                state = state,
                onDismissWifiWarning = onDismissWifiWarning,
                onStartDownload = onStartDownload,
                onStartSherpaDownload = onStartSherpaDownload
            )
        }

        // 5. 모델별 다운로드 안내 (독립 카드, 동시 표시 가능)
        if (state.showDownloadNotice && !state.isDownloadingModel && !state.isExtractingModel) {
            LlmNoticeSection(
                state = state,
                onDismissNotice = onDismissNotice,
                onStartDownload = onStartDownload
            )
        }

        if (state.showSherpaDownloadNotice && !state.isDownloadingModel && !state.isExtractingModel) {
            SherpaNoticeSection(
                onDismissSherpaNotice = onDismissSherpaNotice,
                onStartSherpaDownload = onStartSherpaDownload
            )
        }

        // 6. 모델 준비 완료 (전역)
        if (state.isModelReady && !state.isDownloadingModel && !state.isExtractingModel) {
            ReadySection()
        }
    }
}

@Composable
private fun DeviceUnsupportedSection(state: DiaryState, onClose: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📱 AI 언어 기능 사양 안내",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("닫기", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.deviceUnsupportedReason ?: "현재 스마트폰의 하드웨어 사양으로는 온디바이스 AI 언어 모델 구동이 어려워요.\n\n(※ 음성인식 STT 모델은 정상적으로 이용할 수 있어요.)",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadProgressSection(
    state: DiaryState,
    onCancelDownload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.isExtractingModel) "\uD83E\uDD16 모델 압축 해제 중..." else "\uD83E\uDD16 온디바이스 AI 모델 다운로드 중...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!state.isExtractingModel) {
                    Text(
                        text = "${(state.modelDownloadProgress * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isExtractingModel) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.modelDownloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.modelDownloadSizeText ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onCancelDownload) {
                    Text(text = "취소", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun InitializingSection() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "AI 엔진을 기기 메모리에 로딩 중...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "초기 1회 수 초 소요",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WifiWarningSection(
    state: DiaryState,
    onDismissWifiWarning: () -> Unit,
    onStartDownload: () -> Unit,
    onStartSherpaDownload: () -> Unit
) {
    val isSherpa = state.wifiWarningSource == "sherpa"
    val downloadSize = if (isSherpa) "~1.05GB" else "2.3GB"
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "\uD83D\uDCE1 모바일 데이터 다운로드 경고",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "현재 Wi-Fi에 연결되어 있지 않습니다. 모바일 데이터로 모델 파일($downloadSize)을 다운로드할 경우 데이터 요금이 많이 발생할 수 있습니다.\n\n계속 진행하시겠습니까?",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismissWifiWarning) { Text("취소") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = if (isSherpa) onStartSherpaDownload else onStartDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Text("데이터로 다운로드", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LlmNoticeSection(
    state: DiaryState,
    onDismissNotice: () -> Unit,
    onStartDownload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("\u2728", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "온디바이스 AI (Gemma)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "네트워크 없이 기기 내부에서 일기를 분석하는 온디바이스 AI예요. 모든 데이터가 외부로 유출되지 않아요.\n\n최초 1회 모델 파일(~2.3 GB) 다운로드가 필요합니다.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isLowRamDevice) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "\uD83D\uDCA1 RAM 6GB 이하 기기로 구동 시 지연 발생 가능",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismissNotice) { Text("나중에") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onStartDownload) { Text("다운로드 (2.3GB)") }
            }
        }
    }
}

@Composable
private fun SherpaNoticeSection(
    onDismissSherpaNotice: () -> Unit,
    onStartSherpaDownload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("\uD83C\uDF99\uFE0F", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "음성인식 모델 (Sherpa)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7C4DFF)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "음성으로 기록할 수 있는 온디바이스 음성인식 모델이에요. 네트워크 없이 기기 내부에서 음성을 텍스트로 변환해요.\n\n최초 1회 모델 파일(~1.05GB) 다운로드가 필요합니다.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismissSherpaNotice) { Text("나중에") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStartSherpaDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                ) { Text("다운로드 (~1.05GB)") }
            }
        }
    }
}

@Composable
private fun ReadySection() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "온디바이스 AI 사용 가능 (100% 오프라인)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
