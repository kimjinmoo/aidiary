package com.grepiu.aidiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.mvi.state.DiaryState

/**
 * AI 모델 다운로드 상태 및 기기 지원 여부를 미려하게 보여주는 컴포넌트 카드입니다.
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            when {
                // 1. 기기 최소 사양 미달 상태
                state.isDeviceUnsupported -> {
                    Text(
                        text = "⚠️ 기기 사양 부족 안내",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = state.deviceUnsupportedReason ?: "이 기기는 온디바이스 AI 구동을 지원하지 않습니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 2. 모델 다운로드 중인 상태
                state.isDownloadingModel -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🤖 온디바이스 AI 모델 다운로드 중...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(state.modelDownloadProgress * 100).toInt()}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LinearProgressIndicator(
                        progress = { state.modelDownloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    
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

                // 3. AI 엔진 초기화(메모리 바인딩) 중인 상태
                state.isModelInitializing -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "AI 엔진을 기기 메모리에 로딩하는 중입니다...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "초기 1회는 수 초가량 소요될 수 있습니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. 모델이 정상적으로 로드되어 AI 사용 준비가 완료된 상태
                state.isModelReady -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "온디바이스 AI 사용 가능 (개인정보 100% 안전 오프라인)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // 5. 다운로드 안내 팝업 노출 상태
                state.showDownloadNotice -> {
                    Text(
                        text = "✨ 온디바이스 AI 활성화 안내",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "이 앱은 네트워크 연결 없이 기기 내부에서 스스로 일기를 분석하는 '온디바이스 AI(Gemma 2B)'를 사용해요. " +
                                "본인의 소중한 일기 데이터가 외부 서버로 한 바이트도 유출되지 않아 완벽하게 프라이버시가 보장됩니다.\n\n" +
                                "최초 1회 AI 모델 파일(~2.3 GB) 다운로드가 필요합니다.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (state.isLowRamDevice) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 안내: 현재 기기의 RAM 사양이 다소 낮아(6GB) 구동 시 약간의 지연이 발생할 수 있습니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismissNotice) {
                            Text(text = "나중에")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onStartDownload) {
                            Text(text = "모델 다운로드 (2.3GB)")
                        }
                    }
                }

                // 5.5. Sherpa 음성인식 모델 다운로드 안내
                state.showSherpaDownloadNotice -> {
                    Text(
                        text = "🎙️ 음성인식 모델 활성화",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "음성으로 기록할 수 있는 'Sherpa 온디바이스 음성인식' 모델을 사용할 수 있어요. " +
                                "네트워크 없이 기기 내부에서 음성을 텍스트로 변환하므로 녹음 내용이 외부로 유출되지 않아요.\n\n" +
                                "최초 1회 음성인식 모델 파일(~90MB) 다운로드가 필요합니다.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismissSherpaNotice) {
                            Text(text = "나중에")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onStartSherpaDownload) {
                            Text(text = "모델 다운로드 (~90MB)")
                        }
                    }
                }

                // 6. LTE/5G 경고 상태
                state.showWifiWarning -> {
                    val isSherpa = state.wifiWarningSource == "sherpa"
                    val downloadSize = if (isSherpa) "~90MB" else "2.3GB"
                    Text(
                        text = "📡 모바일 데이터 다운로드 경고",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "현재 Wi-Fi에 연결되어 있지 않습니다. 모바일 데이터(LTE/5G)로 모델 파일($downloadSize)을 다운로드할 경우 데이터 요금이 많이 발생할 수 있습니다.\n\n" +
                                "다운로드를 계속 진행하시겠습니까?",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismissWifiWarning) {
                            Text(text = "취소")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = if (isSherpa) onStartSherpaDownload else onStartDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                        ) {
                            Text(text = "데이터로 다운로드", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
