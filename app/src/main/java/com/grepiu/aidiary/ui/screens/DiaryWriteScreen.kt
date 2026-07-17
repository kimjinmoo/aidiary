package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.ui.components.AddBlockBar
import com.grepiu.aidiary.ui.components.BlockEditor

/**
 * 일기 작성 및 수정, AI 분석 요청을 진행하는 화면 컴포저블입니다.
 *
 * - 본문은 블록(제목/본문/인용/이미지/구분선) 단위로 구성됩니다.
 * - 음성 녹음은 마지막 본문(TextBlock) 의 끝에 텍스트로 이어붙입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryWriteScreen(
    state: DiaryState,
    onContentTypeChange: (ContentType) -> Unit,
    onAddBlock: (ContentBlock) -> Unit,
    onInsertBlock: (Int, ContentBlock) -> Unit,
    onUpdateBlockText: (String, String, com.grepiu.aidiary.data.model.TextFormatting) -> Unit,
    onUpdateBlockCaption: (String, String) -> Unit,
    onRemoveBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onAnalyzeDiary: () -> Unit,
    onSaveDiary: () -> Unit,
    onBack: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSuggestTitle: () -> Unit,
    onClassifyType: () -> Unit,
    onProofreadBlock: (String) -> Unit,
    onDecorateBlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val view = LocalView.current
    view.keepScreenOn = state.isRecording

    val canSave = state.sessionTitle.isNotBlank() &&
        state.draftBlocks.any { block ->
            when (block) {
                is ContentBlock.TextBlock -> block.text.isNotBlank()
                is ContentBlock.QuoteBlock -> block.text.isNotBlank()
                else -> false
            }
        } && !state.isGeneratingAnalysis

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val (icon, label) = contentTypeMeta(state.draftContentType)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${label} 작성", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveDiary, enabled = canSave) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "저장",
                            tint = if (canSave) MaterialTheme.colorScheme.primary else Color.Gray
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
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // (제목은 블록 에디터의 첫 HeadingBlock 으로 통합 — 상단 별도 입력란 제거)


            // 1.5 콘텐츠 타입 선택 + AI 자동 분류 버튼
            ContentTypeSelector(
                selected = state.draftContentType,
                onSelect = onContentTypeChange,
                isClassifying = state.isClassifyingType,
                onClassifyClick = onClassifyType
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 블록 에디터
            if (state.draftBlocks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "본문은 블록 단위로 구성돼요. 첫 블록은 '제목' (한 글에 1개) 으로 시작해서 글의 성격을 정해주세요.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AiAssistIconButton(
                                label = "AI 제목",
                                loading = state.isSuggestingTitle,
                                enabled = state.isModelReady && !state.isSuggestingTitle,
                                onClick = onSuggestTitle
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "본문 없이도 AI 가 추천 제목을 만들어 첫 블록으로 추가해요.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.draftBlocks.forEachIndexed { index, block ->
                        BlockEditor(
                            block = block,
                            index = index,
                            totalCount = state.draftBlocks.size,
                            isProofreading = state.isProofreadingBlockId == block.id,
                            isDecorating = state.isDecoratingBlockId == block.id,
                            aiAssistEnabled = state.isModelReady,
                            onUpdateText = { newText, newFmt -> onUpdateBlockText(block.id, newText, newFmt) },
                            onUpdateCaption = { newCap -> onUpdateBlockCaption(block.id, newCap) },
                            onRemove = { onRemoveBlock(block.id) },
                            onMoveUp = { onMoveBlock(block.id, -1) },
                            onMoveDown = { onMoveBlock(block.id, +1) },
                            onProofread = { onProofreadBlock(block.id) },
                            onDecorate = { onDecorateBlock(block.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2.5 음성 녹음 영역
            VoiceRecordingRow(
                state = state,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2.6 블록 추가 바
            AddBlockBar(
                canAddImage = !state.isImportingImage,
                onAdd = onAddBlock,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
                hasHeading = state.hasHeadingBlock
            )

            if (state.isImportingImage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "이미지를 가져오는 중…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))

            // 3. AI 분석 영역 (일기 타입일 때만 노출)
            if (state.draftContentType.supportsAiAnalysis) {
                AIAnalysisSection(
                    state = state,
                    onAnalyzeDiary = onAnalyzeDiary
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (state.draftContentType) {
                            ContentType.POST -> "💡 새 글은 AI 마음 분석 없이 자유롭게 기록해요."
                            ContentType.NOTE -> "💡 메모는 간단히 남기는 글입니다."
                            else -> ""
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun VoiceRecordingRow(
    state: DiaryState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            state.isRecording -> {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF333333))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.recordingVolume)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        state.recordingVolume < 0.3f -> Color(0xFF4CAF50)
                                        state.recordingVolume < 0.7f -> Color(0xFFFFC107)
                                        else -> Color(0xFFE53935)
                                    }
                                )
                        )
                    }
                }
            }
            state.isTranscribing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "음성 변환 중...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.isSherpaModelReady -> {
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
                    text = "음성으로 일기 쓰기 (마지막 본문 블록에 추가)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.isDownloadingModel -> {
                Text(
                    text = "음성 인식 모델 다운로드 중...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AIAnalysisSection(
    state: DiaryState,
    onAnalyzeDiary: () -> Unit
) {
    if (state.isModelReady) {
        Button(
            onClick = onAnalyzeDiary,
            enabled = state.draftPlainText.isNotBlank() && !state.isGeneratingAnalysis,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

/**
 * 콘텐츠 타입 선택 가로 칩바.
 */
@Composable
private fun ContentTypeSelector(
    selected: ContentType,
    onSelect: (ContentType) -> Unit,
    isClassifying: Boolean,
    onClassifyClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 6.dp)
        ) {
            Text(
                text = "글 타입",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            AiAssistTextButton(
                label = if (isClassifying) "분류 중…" else "AI 자동 분류",
                loading = isClassifying,
                onClick = onClassifyClick
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ContentType.values().forEach { type ->
                val (icon, label) = contentTypeMeta(type)
                ContentTypeChip(
                    icon = icon,
                    label = label,
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContentTypeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val containerColor = if (selected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/**
 * 콘텐츠 타입별 아이콘/라벨 매핑.
 */
private fun contentTypeMeta(type: ContentType): Pair<ImageVector, String> = when (type) {
    ContentType.DIARY -> Icons.AutoMirrored.Filled.MenuBook to ContentType.DIARY.label
    ContentType.POST -> Icons.Default.EditNote to ContentType.POST.label
    ContentType.NOTE -> Icons.Default.NoteAlt to ContentType.NOTE.label
}

/**
 * 보조 입력 옆에 붙는 작은 아이콘형 AI 버튼.
 *
 * - [loading] 이 true 면 진행 표시 스피너 노출 + 비활성
 * - [enabled] 가 false 면 회색
 */
@Composable
fun AiAssistIconButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val active = enabled && !loading
    val containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val borderColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val contentColor = if (active) MaterialTheme.colorScheme.primary else Color.Gray

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 86.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
                color = contentColor
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 글 타입 라벨 우측에 붙는 인라인 텍스트형 AI 버튼.
 */
@Composable
fun AiAssistTextButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    val active = !loading
    val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp),
                color = contentColor
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = contentColor
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
