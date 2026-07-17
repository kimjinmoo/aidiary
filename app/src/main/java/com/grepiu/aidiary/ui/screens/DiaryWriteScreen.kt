package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.TitleStyle
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
    onUpdateTitleStyle: (TitleStyle) -> Unit,
    onAddBlock: (ContentBlock) -> Unit,
    onInsertBlock: (Int, ContentBlock) -> Unit,
    onUpdateBlockText: (String, String, com.grepiu.aidiary.data.model.TextFormatting) -> Unit,
    onUpdateBlockCaption: (String, String) -> Unit,
    onRemoveBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSaveDiary: () -> Unit,
    onBack: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSuggestTitle: () -> Unit,
    onUpdateTitle: (String) -> Unit,
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

            // 1. 상단 제목 입력란 (키보드 입력 기본, 우측 AI 버튼으로 보조)
            TitleInputField(
                title = state.draftTitle,
                titleStyle = state.draftTitleStyle,
                isModelReady = state.isModelReady,
                isSuggesting = state.isSuggestingTitle,
                hasBody = state.draftPlainText.isNotBlank(),
                onValueChange = onUpdateTitle,
                onSuggestClick = onSuggestTitle
            )

            // 1.5 콘텐츠 타입 선택 + AI 자동 분류 버튼
            ContentTypeSelector(
                selected = state.draftContentType,
                onSelect = onContentTypeChange,
                isClassifying = state.isClassifyingType,
                onClassifyClick = onClassifyType
            )

            if (state.draftTitle.isNotBlank()) {
                TitleStylePicker(
                    currentStyle = state.draftTitleStyle,
                    onStyleChange = onUpdateTitleStyle
                )
            }

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
                            text = "제목은 상단 입력란에, 본문은 아래 블록으로 자유롭게 작성해 보세요. '섹션 제목' 블록을 추가해 본문 안에서 소제목으로 활용할 수도 있어요.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // 3. 저장 안내 + AI 자동 TAG 안내 (일기 타입일 때만 AI TAG 자동 추가 안내)
            SaveHintSection(
                isModelReady = state.isModelReady,
                supportsAiAnalysis = state.draftContentType.supportsAiAnalysis,
                isSavingWithAi = state.isGeneratingAnalysis
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SaveHintSection(
    isModelReady: Boolean,
    supportsAiAnalysis: Boolean,
    isSavingWithAi: Boolean
) {
    val (icon, message) = when {
        isSavingWithAi -> "✨" to "AI 가 감정을 분석하고 있어요. 잠시만 기다려 주세요…"
        supportsAiAnalysis && isModelReady -> "✨" to "저장하면 AI 가 기쁨/슬픔/평온/불안/분노 중 하나로 감정을 태그해 'TAG AI' 블록을 추가해요. (AI 모델 준비 완료)"
        supportsAiAnalysis && !isModelReady -> "💡" to "목록 화면에서 온디바이스 AI 모델을 다운로드하면, 저장 시 AI 가 감정을 자동으로 태그해 줘요."
        else -> "💡" to "저장하면 일기가 목록에 기록돼요."
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
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

/**
 * 상단 제목 입력란. 키보드 입력이 기본이고, 우측 트레일링 아이콘의 AI 버튼으로
 * 본문 기반 제목을 추천받을 수 있습니다.
 */
@Composable
private fun TitleInputField(
    title: String,
    titleStyle: TitleStyle,
    isModelReady: Boolean,
    isSuggesting: Boolean,
    hasBody: Boolean,
    onValueChange: (String) -> Unit,
    onSuggestClick: () -> Unit
) {
    val titleColor = titleStyle.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.onSurface
    val titleSize = (titleStyle.sizeSp ?: 22).coerceIn(14, 32)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "제목",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = title,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "글의 제목을 입력하세요",
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            textStyle = TextStyle(
                fontSize = titleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = (titleSize + 6).sp,
                color = titleColor
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            trailingIcon = {
                TitleAiButton(
                    isModelReady = isModelReady,
                    isSuggesting = isSuggesting,
                    hasBody = hasBody,
                    onClick = onSuggestClick
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 제목 입력란 우측에 붙는 AI 추천 버튼.
 * - 모델 준비 + 본문 존재 + 진행중이 아닐 때만 활성
 * - 비활성 시에도 안내용으로 보이도록 흐릿하게 표시
 */
@Composable
private fun TitleAiButton(
    isModelReady: Boolean,
    isSuggesting: Boolean,
    hasBody: Boolean,
    onClick: () -> Unit
) {
    val active = isModelReady && hasBody && !isSuggesting
    val tint = when {
        isSuggesting -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val description = when {
        isSuggesting -> "AI 제목 생성 중"
        !isModelReady -> "AI 모델 미준비"
        !hasBody -> "본문을 먼저 작성해주세요"
        else -> "AI 제목 추천"
    }
    IconButton(onClick = onClick, enabled = active) {
        if (isSuggesting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = tint
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TitleStylePicker(
    currentStyle: TitleStyle,
    onStyleChange: (TitleStyle) -> Unit
) {
    val colorOptions = listOf(
        null to Color.Unspecified,
        "#D32F2F" to Color(0xFFD32F2F),
        "#E65100" to Color(0xFFE65100),
        "#388E3C" to Color(0xFF388E3C),
        "#1976D2" to Color(0xFF1976D2),
        "#7B1FA2" to Color(0xFF7B1FA2),
        "#00838F" to Color(0xFF00838F),
        "#C2185B" to Color(0xFFC2185B)
    )

    val sizeOptions = listOf(
        null to "M",
        18 to "S",
        22 to "M",
        26 to "L",
        30 to "XL"
    ).distinctBy { it.second }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = "제목 스타일",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colorOptions.forEach { (hex, color) ->
                val isSelected = currentStyle.color == hex
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (hex != null) color else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (hex == null) MaterialTheme.colorScheme.outlineVariant
                                    else color.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onStyleChange(currentStyle.copy(color = hex)) }
                ) {
                    if (hex == null) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sizeOptions.forEach { (sizeSp, label) ->
                val isSelected = currentStyle.sizeSp == sizeSp
                val displaySz = (sizeSp ?: 22).coerceIn(11, 30)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .width(44.dp)
                        .clickable { onStyleChange(currentStyle.copy(sizeSp = sizeSp)) }
                ) {
                    Text(
                        text = label,
                        fontSize = displaySz.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
