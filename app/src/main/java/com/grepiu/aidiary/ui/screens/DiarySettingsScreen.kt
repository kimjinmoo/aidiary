package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.ui.components.OpenSourceLicenseModalDialog

/**
 * 2030 세대 타깃의 프리미엄 전문가 수준 설정(Settings) 페이지입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiarySettingsScreen(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // 시스템 파일 관리자 백업 생성 런처 (Google Drive / 클라우드 / 내장메모리 저장 위치 선택)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val jsonContent = org.json.JSONObject().apply {
                    put("version", "1.0")
                    put("exportedAt", timestamp)
                    put("diaryCount", state.diaries.size)
                    put("plannerCount", state.plannerTasks.size)
                    put("goalCount", state.goals.size)
                }.toString(2)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                onIntent(DiaryIntent.ExportBackupData)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "백업 저장 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 시스템 파일 관리자 복원 열기 런처 (Google Drive / 클라우드 / 내장메모리 파일 가져오기)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!jsonContent.isNullOrBlank()) {
                    onIntent(DiaryIntent.ImportBackupData(jsonContent))
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "파일 가져오기 실패: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (state.showLicenseDialog) {
        OpenSourceLicenseModalDialog(
            onDismiss = { onIntent(DiaryIntent.ShowLicenseDialog(false)) }
        )
    }

    // 1) Wi-Fi 경고 다이얼로그 (모바일 데이터 다운로드 시)
    if (state.showWifiWarning) {
        val isSherpa = state.wifiWarningSource == "sherpa"
        val modelName = if (isSherpa) "음성인식 모델" else "AI 언어 모델"
        val downloadSize = if (isSherpa) "약 1.0GB" else "약 2.3GB"
        AlertDialog(
            onDismissRequest = { onIntent(DiaryIntent.ShowWifiWarning(false)) },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100)) },
            title = { Text("Wi-Fi 연결 확인 ⚠️", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "현재 Wi-Fi 에 연결되어 있지 않아요.\n" +
                    "모바일 데이터로 $modelName($downloadSize)을 다운로드하시겠습니까?\n\n" +
                    "(※ 모바일 데이터 사용료가 발생할 수 있습니다.)",
                    fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onIntent(DiaryIntent.ShowWifiWarning(false))
                        if (isSherpa) {
                            onIntent(DiaryIntent.StartSherpaDownload)
                        } else {
                            onIntent(DiaryIntent.StartDownload)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Text("모바일 데이터로 다운로드", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(DiaryIntent.ShowWifiWarning(false)) }) {
                    Text("취소")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // NOTE: LLM(Gemma)/Sherpa 다운로드 안내는 설정 진입 시 자동 모달로 띄우지 않는다.
    // 안내는 (1) 목록 상단 알림 배너(AiStatusBar), (2) 각 페이지 다운로드, (3) 아래 설정 모델 카드 탭으로 제공.

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정 ⚙️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 1. 데이터 백업 및 복원 섹션
            SettingsSectionHeader(title = "데이터 백업 및 복원 📦")
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.FileUpload,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "다이어리 데이터 백업하기",
                        subtitle = "Google Drive 또는 원하는 클라우드/스토리지에 백업합니다.",
                        onClick = {
                            val fileName = "AIDiary_Backup_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.json"
                            createDocumentLauncher.launch(fileName)
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    SettingsActionRow(
                        icon = Icons.Default.FileDownload,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "데이터 복원하기",
                        subtitle = "Google Drive 또는 백업 파일 위치에서 가져와 복구합니다.",
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    )
                }
            }

            // 최근 백업 안내 칩
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "최근 성공 백업: ${state.lastBackupDate ?: "아직 백업 기록이 없어요"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 앱 및 온디바이스 AI 시스템 정보 섹션
            val packageInfo = remember(context) {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            }
            val appVersionName = packageInfo?.versionName ?: "1.0.0"
            val appVersionCode = packageInfo?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    it.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            } ?: 1L

            SettingsSectionHeader(title = "앱 & 시스템 정보 📱")
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    SettingsInfoRow(
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "앱 버전 정보",
                        badgeText = "최신 버전 ✓",
                        value = "v$appVersionName (Build $appVersionCode)"
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    val isDownloadingAny = state.isDownloadingModel || state.isExtractingModel
                    val isLlmUnsupported = state.isLowRamDevice || state.isDeviceUnsupported

                    val llmBadgeText = when {
                        isLlmUnsupported -> "사양 제한"
                        state.isModelReady -> "설치 완료"
                        isDownloadingAny -> "다운로드 진행 중"
                        else -> "설치 필요"
                    }
                    val llmBadgeColor = when {
                        isLlmUnsupported -> MaterialTheme.colorScheme.error
                        state.isModelReady -> MaterialTheme.colorScheme.tertiary
                        isDownloadingAny -> Color.Gray
                        else -> Color(0xFFE65100)
                    }
                    val llmSubtitle = when {
                        isLlmUnsupported -> "Gemma 4 (하드웨어 사양제한)"
                        state.isModelReady -> "Gemma 4 2B-IT (준비 완료)"
                        isDownloadingAny -> "Gemma 4 2B-IT (다른 다운로드 진행 중)"
                        else -> "Gemma 4 2B-IT (약 2.3GB - 터치하여 다운로드)"
                    }

                    SettingsInfoRow(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = llmBadgeColor,
                        title = "온디바이스 AI 언어 모델",
                        badgeText = llmBadgeText,
                        badgeColor = llmBadgeColor,
                        value = llmSubtitle,
                        onClick = {
                            if (isLlmUnsupported) {
                                onIntent(DiaryIntent.UnsupportedDeviceClose)
                            } else if (state.isModelReady) {
                                android.widget.Toast.makeText(context, "Gemma 4 AI 언어 모델이 준비되어 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (isDownloadingAny) {
                                android.widget.Toast.makeText(context, "현재 다른 AI 모델 다운로드가 진행 중입니다. 완료 후 시도해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onIntent(DiaryIntent.StartDownload)
                            }
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    val isSherpaReady = state.isSherpaModelReady
                    val sherpaBadgeText = when {
                        isSherpaReady -> "설치 완료"
                        isDownloadingAny -> "다운로드 진행 중"
                        else -> "설치 필요"
                    }
                    val sherpaBadgeColor = when {
                        isSherpaReady -> MaterialTheme.colorScheme.tertiary
                        isDownloadingAny -> Color.Gray
                        else -> Color(0xFFE65100)
                    }
                    val sherpaSubtitle = when {
                        isSherpaReady -> "Sherpa-ONNX SenseVoice (2024-07-17, 준비 완료)"
                        isDownloadingAny -> "Sherpa-ONNX SenseVoice (다른 다운로드 진행 중)"
                        else -> "Sherpa-ONNX SenseVoice (약 1.0GB - 터치하여 다운로드)"
                    }

                    SettingsInfoRow(
                        icon = Icons.Default.Mic,
                        iconTint = sherpaBadgeColor,
                        title = "음성 인식(STT) 엔진",
                        badgeText = sherpaBadgeText,
                        badgeColor = sherpaBadgeColor,
                        value = sherpaSubtitle,
                        onClick = {
                            if (isSherpaReady) {
                                android.widget.Toast.makeText(context, "Sherpa-Onnx 오프라인 음성 인식 모델이 준비되어 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (isDownloadingAny) {
                                android.widget.Toast.makeText(context, "현재 다른 AI 모델 다운로드가 진행 중입니다. 완료 후 시도해주세요.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onIntent(DiaryIntent.StartSherpaDownload)
                            }
                        }
                    )

                    if (state.isDownloadingModel || state.isExtractingModel) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.modelDownloadSizeText ?: "다운로드 진행 중...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(state.modelDownloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { state.modelDownloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "진행 중에는 화면을 끄거나 앱을 종료하지 마세요! (초기화 위험)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. 오픈소스 & 라이선스 고지 섹션
            SettingsSectionHeader(title = "오픈소스 & 서비스 약관 📜")
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.Code,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "오픈소스 라이선스 고지",
                        subtitle = "사용된 라이브러리 및 오픈소스 정보를 확인합니다.",
                        onClick = { onIntent(DiaryIntent.ShowLicenseDialog(true)) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    SettingsInfoRow(
                        icon = Icons.Default.Security,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "개인정보 처리방침 🔒",
                        badgeText = "온디바이스 100%",
                        badgeColor = MaterialTheme.colorScheme.secondary,
                        value = "grepiu.com/ai_diary_privacy.html (터치하여 열기)",
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.grepiu.com/ai_diary_privacy.html")
                                )
                                context.startActivity(intent)
                            }.onFailure {
                                android.widget.Toast.makeText(context, "웹 브라우저를 열 수 없습니다: https://www.grepiu.com/ai_diary_privacy.html", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    badgeText: String,
    badgeColor: Color = iconTint,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
