package com.grepiu.aidiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.data.model.ContentBlock
import com.grepiu.aidiary.data.model.ContentType
import com.grepiu.aidiary.data.model.TitleStyle
import com.grepiu.aidiary.mvi.intent.DiaryIntent
import com.grepiu.aidiary.mvi.state.DiaryState
import com.grepiu.aidiary.mvi.state.PendingContentTypeChange
import com.grepiu.aidiary.ui.components.AddBlockBar
import com.grepiu.aidiary.ui.components.BlockEditor

/**
 * 일기/포스트/메모 작성 화면. 기업용 SaaS 톤의 정제된 레이아웃을 제공합니다.
 *
 *  구성 (위→아래)
 *  1. 스크롤 진행 바 + 스크롤 그림자 TopAppBar (뒤로/저장)
 *  2. 글 타입 필터칩 + AI 자동 분류 인라인 액션
 *  3. 히어로 제목 입력 (AI 추천 trailing)
 *  4. 제목 스타일 (컬러 팔레트 + 사이즈 칩, 제목이 비어있지 않을 때만)
 *  5. 본문 섹션 — 블록 에디터 + 비어있을 때의 작성 가이드
 *  6. 블록 추가 섹션 — AddBlockBar
 *  7. 음성 입력 섹션
 *  8. 저장 안내 알림 (AI TAG 안내 / 저장 진행 상태)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryWriteScreen(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit = {},
    onContentTypeChange: (ContentType) -> Unit = {},
    onUpdateTitleStyle: (TitleStyle) -> Unit = {},
    onAddBlock: (ContentBlock) -> Unit = {},
    onInsertBlock: (Int, ContentBlock) -> Unit = { _, _ -> },
    onUpdateBlockText: (String, String, com.grepiu.aidiary.data.model.TextFormatting) -> Unit = { _, _, _ -> },
    onUpdateBlockCaption: (String, String) -> Unit = { _, _ -> },
    onRemoveBlock: (String) -> Unit = {},
    onMoveBlock: (String, Int) -> Unit = { _, _ -> },
    onPickGallery: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onSaveDiary: () -> Unit = {},
    onBack: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onSuggestTitle: () -> Unit = {},
    onUpdateTitle: (String) -> Unit = {},
    onClassifyType: () -> Unit = {},
    onProofreadBlock: (String) -> Unit = {},
    onDecorateBlock: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val view = LocalView.current
    view.keepScreenOn = state.isRecording

    var showAiGuide by remember { mutableStateOf(true) }

    val canSave = state.sessionTitle.isNotBlank() &&
        state.draftBlocks.any { block ->
            when (block) {
                is ContentBlock.TextBlock -> block.text.isNotBlank()
                is ContentBlock.QuoteBlock -> block.text.isNotBlank()
                else -> false
            }
        } && !state.isGeneratingAnalysis

    val scrollProgress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue
    } else 0f

    val (typeIcon, typeLabel, typeColor) = getContentTypeUI(state.draftContentType)

    Scaffold(
        topBar = {
            WriteTopBar(
                typeIcon = typeIcon,
                typeLabel = typeLabel,
                typeColor = typeColor,
                canSave = canSave,
                isSaving = state.isGeneratingAnalysis,
                scrollProgress = scrollProgress,
                onBack = onBack,
                onSave = onSaveDiary
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
            // 1) 글 타입 선택
            SectionLabel(
                icon = Icons.Outlined.Lightbulb,
                label = "글 타입",
                trailing = {
                    InlineAiAction(
                        label = if (state.isClassifyingType) "분류 중…" else "AI 자동 분류",
                        loading = state.isClassifyingType,
                        enabled = !state.isClassifyingType,
                        onClick = onClassifyType
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            TypeFilterChips(
                selected = state.draftContentType,
                onSelect = onContentTypeChange
            )

            if (showAiGuide) {
                Spacer(Modifier.height(16.dp))
                AiWritingGuideCard(onDismiss = { showAiGuide = false })
            }

            // 2) 히어로 제목 입력
            Spacer(Modifier.height(24.dp))
            SectionLabel(icon = Icons.AutoMirrored.Filled.MenuBook, label = "제목")
            Spacer(Modifier.height(8.dp))
            TitleHeroField(
                title = state.draftTitle,
                titleStyle = state.draftTitleStyle,
                isModelReady = state.isModelReady,
                isSuggesting = state.isSuggestingTitle,
                hasBody = state.draftPlainText.isNotBlank(),
                onValueChange = onUpdateTitle,
                onSuggestClick = onSuggestTitle
            )

            // 3) 제목 스타일 (제목이 입력됐을 때만)
            if (state.draftTitle.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                TitleStyleInline(
                    currentStyle = state.draftTitleStyle,
                    onStyleChange = onUpdateTitleStyle
                )
            }

            // 4) 본문 섹션
            Spacer(Modifier.height(24.dp))
            SectionLabel(
                icon = Icons.Filled.EditNote,
                label = "본문"
            )
            Spacer(Modifier.height(8.dp))
            BodySection(
                state = state,
                onIntent = onIntent,
                onUpdateBlockText = onUpdateBlockText,
                onUpdateBlockCaption = onUpdateBlockCaption,
                onRemoveBlock = onRemoveBlock,
                onMoveBlock = onMoveBlock,
                onProofreadBlock = onProofreadBlock,
                onDecorateBlock = onDecorateBlock,
                onTableCellChange = { id, r, c, t ->
                    onIntent(DiaryIntent.UpdateTableCell(id, r, c, t))
                },
                onTableAddRow = { id -> onIntent(DiaryIntent.AddTableRow(id)) },
                onTableRemoveRow = { id, r -> onIntent(DiaryIntent.RemoveTableRow(id, r)) },
                onTableAddColumn = { id -> onIntent(DiaryIntent.AddTableColumn(id)) },
                onTableRemoveColumn = { id, c -> onIntent(DiaryIntent.RemoveTableColumn(id, c)) }
            )

            // 5) 블록 추가
            Spacer(Modifier.height(24.dp))
            SectionLabel(icon = Icons.Outlined.Add, label = "블록 추가")
            Spacer(Modifier.height(8.dp))
            AddBlockBar(
                canAddImage = !state.isImportingImage,
                onAdd = onAddBlock,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
                onAddLocation = { onIntent(DiaryIntent.RequestLocationBlock) },
                hasHeading = state.hasHeadingBlock,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            if (state.isImportingImage) {
                Spacer(Modifier.height(10.dp))
                InlineStatusRow(text = "이미지를 가져오는 중…")
            }

            // 6) 음성 입력
            Spacer(Modifier.height(24.dp))
            SectionLabel(icon = Icons.Filled.Mic, label = "음성 입력")
            Spacer(Modifier.height(8.dp))
            VoiceCard(
                state = state,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onLanguageChange = { lang -> onIntent(DiaryIntent.UpdateVoiceLanguage(lang)) }
            )

            // 7) 저장 안내 알림
            Spacer(Modifier.height(28.dp))
            SaveHintCard(
                isModelReady = state.isModelReady,
                supportsAiAnalysis = state.draftContentType.supportsAiAnalysis,
                isSavingWithAi = state.isGeneratingAnalysis
            )

            Spacer(Modifier.height(48.dp))
        }

            // 8) 저장 시 AI 가 추천한 글 타입이 현재 선택과 다를 때 표시되는 3버튼 다이얼로그
        state.pendingContentTypeChange?.let { pending ->
            ContentTypeChangeDialog(
                pending = pending,
                onConfirm = { onIntent(DiaryIntent.ConfirmContentTypeChange(pending.suggestedType)) },
                onKeep = { onIntent(DiaryIntent.KeepCurrentContentTypeAndSave) },
                onCancel = { onIntent(DiaryIntent.CancelContentTypeChange) }
            )
        }
    }
}

/* ====================== 내부 컴포저블 ====================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteTopBar(
    typeIcon: ImageVector,
    typeLabel: String,
    typeColor: Color,
    canSave: Boolean,
    isSaving: Boolean,
    scrollProgress: Float,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val elevation = if (scrollProgress > 0.02f) 4.dp else 0.dp
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (scrollProgress > 0f) {
                LinearProgressIndicator(
                    progress = { scrollProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = typeColor,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Butt
                )
            }
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = typeColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = typeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${typeLabel} 작성",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
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
                    if (isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButtonSave(enabled = canSave, onClick = onSave)
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
private fun TextButtonSave(enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "저장",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "저장",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun SectionLabel(
    icon: ImageVector,
    label: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.3.sp
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun TypeFilterChips(
    selected: ContentType,
    onSelect: (ContentType) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        ContentType.values().forEach { type ->
            val (icon, label) = contentTypeMeta(type)
            val isSelected = type == selected
            TypeFilterChip(
                icon = icon,
                label = label,
                selected = isSelected,
                onClick = { onSelect(type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = if (selected) accent.copy(alpha = 0.14f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val border = if (selected) accent.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val content = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = content
            )
        }
    }
}

@Composable
private fun TitleHeroField(
    title: String,
    titleStyle: TitleStyle,
    isModelReady: Boolean,
    isSuggesting: Boolean,
    hasBody: Boolean,
    onValueChange: (String) -> Unit,
    onSuggestClick: () -> Unit
) {
    val titleColor = titleStyle.color?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.onSurface
    val titleSize = (titleStyle.sizeSp ?: 24).coerceIn(18, 34)

    val maxChars = 50
    val charCount = title.length

    OutlinedTextField(
        value = title,
        onValueChange = { if (it.length <= maxChars) onValueChange(it) },
        placeholder = {
            Text(
                text = "오늘의 제목을 입력하세요",
                fontSize = titleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        },
        textStyle = TextStyle(
            fontSize = titleSize.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = (titleSize + 6).sp,
            color = titleColor
        ),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        trailingIcon = {
            TitleAiTrailing(
                isModelReady = isModelReady,
                isSuggesting = isSuggesting,
                hasBody = hasBody,
                onClick = onSuggestClick
            )
        },
        supportingText = {
            Text(
                text = "$charCount / $maxChars",
                fontSize = 11.sp,
                color = if (charCount >= maxChars) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}

@Composable
private fun TitleAiTrailing(
    isModelReady: Boolean,
    isSuggesting: Boolean,
    hasBody: Boolean,
    onClick: () -> Unit
) {
    val active = isModelReady && hasBody && !isSuggesting
    val tint = when {
        isSuggesting -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    val description = when {
        isSuggesting -> "AI 제목 생성 중"
        !isModelReady -> "AI 모델 미준비"
        !hasBody -> "본문을 먼저 작성해주세요"
        else -> "AI 제목 추천"
    }
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
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
private fun TitleStyleInline(
    currentStyle: TitleStyle,
    onStyleChange: (TitleStyle) -> Unit
) {
    val colorOptions = listOf(
        null,
        "#D32F2F",
        "#E65100",
        "#388E3C",
        "#1976D2",
        "#7B1FA2",
        "#00838F",
        "#C2185B"
    )
    val sizeOptions = listOf(
        null to "M",
        18 to "S",
        22 to "M",
        26 to "L",
        30 to "XL"
    ).distinctBy { it.second }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // 색상
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            colorOptions.forEach { hex ->
                val isSelected = currentStyle.color == hex
                val swatchColor = hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (swatchColor != null) swatchColor else Color.Transparent)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else if (hex == null) MaterialTheme.colorScheme.outlineVariant
                            else (swatchColor ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onStyleChange(currentStyle.copy(color = hex)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (hex == null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // 사이즈
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sizeOptions.forEach { (sizeSp, label) ->
                    val isSelected = currentStyle.sizeSp == sizeSp
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .width(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onStyleChange(currentStyle.copy(sizeSp = sizeSp)) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodySection(
    state: DiaryState,
    onIntent: (DiaryIntent) -> Unit,
    onUpdateBlockText: (String, String, com.grepiu.aidiary.data.model.TextFormatting) -> Unit,
    onUpdateBlockCaption: (String, String) -> Unit,
    onRemoveBlock: (String) -> Unit,
    onMoveBlock: (String, Int) -> Unit,
    onProofreadBlock: (String) -> Unit,
    onDecorateBlock: (String) -> Unit,
    onTableCellChange: (String, Int, Int, String) -> Unit,
    onTableAddRow: (String) -> Unit,
    onTableRemoveRow: (String, Int) -> Unit,
    onTableAddColumn: (String) -> Unit,
    onTableRemoveColumn: (String, Int) -> Unit
) {
    if (state.draftBlocks.isEmpty()) {
        EmptyBodyHint()
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            state.draftBlocks.forEachIndexed { index, block ->
                BlockEditor(
                    block = block,
                    index = index,
                    totalCount = state.draftBlocks.size,
                    isProofreading = state.isProofreadingBlockId == block.id,
                    isDecorating = state.isDecoratingBlockId == block.id,
                    isTranslating = state.translatingBlockIds.contains(block.id),
                    aiAssistEnabled = state.isModelReady,
                    onUpdateText = { newText, newFmt -> onUpdateBlockText(block.id, newText, newFmt) },
                    onUpdateCaption = { newCap -> onUpdateBlockCaption(block.id, newCap) },
                    onRemove = { onRemoveBlock(block.id) },
                    onMoveUp = { onMoveBlock(block.id, -1) },
                    onMoveDown = { onMoveBlock(block.id, +1) },
                    onProofread = { onProofreadBlock(block.id) },
                    onDecorate = { onDecorateBlock(block.id) },
                    onCopy = { onIntent(DiaryIntent.CopyBlockToClipboard(block.id)) },
                    onTranslate = { onIntent(DiaryIntent.TranslateBlock(block.id)) },
                    onTableCellChange = { r, c, t -> onTableCellChange(block.id, r, c, t) },
                    onTableAddRow = { onTableAddRow(block.id) },
                    onTableRemoveRow = { r -> onTableRemoveRow(block.id, r) },
                    onTableAddColumn = { onTableAddColumn(block.id) },
                    onTableRemoveColumn = { c -> onTableRemoveColumn(block.id, c) }
                )
            }
        }
    }
}

@Composable
private fun EmptyBodyHint() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "본문을 시작해 볼까요?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "아래 '블록 추가'에서 본문·인용·이미지·구분선을 선택해 쌓거나, '음성 입력'으로 말하며 작성할 수 있어요. 각 블록의 ✦ 메뉴에서 AI 번역·보정·꾸미기도 이용할 수 있어요.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceCard(
    state: DiaryState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onLanguageChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    state.isRecording -> RecordingActiveBody(
                        seconds = state.recordingSeconds,
                        volume = state.recordingVolume,
                        onStop = onStopRecording,
                        modifier = Modifier.weight(1f)
                    )
                    state.isTranscribing -> TranscribingBody()
                    state.isSherpaModelReady -> RecordIdleBody(
                        onStart = onStartRecording,
                        modifier = Modifier.weight(1f)
                    )
                    state.isDownloadingModel -> DownloadingModelBody(modifier = Modifier.weight(1f))
                }
            }
            // 다국어 음성 인식 선택 (모델 준비 완료 시)
            if (state.isSherpaModelReady && !state.isRecording && !state.isTranscribing) {
                Spacer(Modifier.height(10.dp))
                VoiceLanguageChipRow(
                    selected = state.voiceLanguage,
                    isChanging = state.isChangingVoiceLanguage,
                    onSelect = onLanguageChange
                )
            }
        }
    }
}

/** Sherpa 음성 인식 언어 선택 칩 행. */
@Composable
private fun VoiceLanguageChipRow(
    selected: String,
    isChanging: Boolean,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        "auto" to "자동",
        "ko" to "한국어",
        "en" to "English",
        "ja" to "日本語",
        "zh" to "中文"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = "언어",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )
        options.forEach { (code, label) ->
            val isSel = code == selected
            // 로딩 중에는 선택된 칩을 제외한 다른 칩만 비활성화 → 사용자가 또 다른 언어로 끊김없이 변경 가능
            val enabled = !isSel
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(code) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 클릭 즉시 강조 + 그 칩 안에서 로딩 스피너가 돌아서 처리중임을 알림
                    if (isSel && isChanging) {
                        Spacer(Modifier.width(4.dp))
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingActiveBody(
    seconds: Int,
    volume: Float,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recColor = Color(0xFFE53935)
    val maxSeconds = 120
    val progress = (seconds / maxSeconds.toFloat()).coerceIn(0f, 1f)
    val remainingSeconds = (maxSeconds - seconds).coerceAtLeast(0)
    val minutesStr = String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    val remainingStr = String.format(java.util.Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(recColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onStop) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "녹음 중지",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(recColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "녹음 중 · $minutesStr",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = recColor
                )
            }
            Text(
                text = "남은 시간: $remainingStr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        // 2분 한도 게이지 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(4.dp))
        // 볼륨 미터 바 (얇게)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF333333).copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(volume)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        when {
                            volume < 0.3f -> Color(0xFF4CAF50)
                            volume < 0.7f -> Color(0xFFFFC107)
                            else -> Color(0xFFE53935)
                        }
                    )
            )
        }
    }
}

@Composable
private fun TranscribingBody() {
    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(2.dp))
    Text(
        text = "음성을 텍스트로 변환하고 있어요…",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RecordIdleBody(
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onStart,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "음성 녹음 시작",
            modifier = Modifier.size(20.dp)
        )
    }
    Column(modifier = modifier) {
        Text(
            text = "음성으로 기록하기",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "마이크를 누르고 말하면 마지막 본문 블록 끝에 자동 추가돼요.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadingModelBody(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(
        text = "음성 인식 모델을 다운로드하고 있어요…",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun InlineStatusRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaveHintCard(
    isModelReady: Boolean,
    supportsAiAnalysis: Boolean,
    isSavingWithAi: Boolean
) {
    val (icon, message, container, content) = when {
        isSavingWithAi -> Quadruple(
            Icons.Default.AutoAwesome,
            "AI 가 감정을 분석하고 있어요. 잠시만 기다려 주세요…",
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        supportsAiAnalysis && isModelReady -> Quadruple(
            Icons.Default.AutoAwesome,
            "저장 시 AI 가 기쁨/슬픔/평온/불안/분노 중 하나로 감정을 태그해 'TAG AI' 블록을 자동 추가해요.",
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        supportsAiAnalysis && !isModelReady -> Quadruple(
            Icons.Outlined.Info,
            "목록 화면에서 온디바이스 AI 모델을 다운로드하면, 저장 시 AI 가 감정을 자동으로 태그해 줘요.",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Quadruple(
            Icons.Outlined.Info,
            "저장하면 일기가 목록에 기록돼요.",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = content
            )
        }
    }
}

@Composable
private fun AiWritingGuideCard(onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI 글쓰기 도우미",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "글 타입은 'AI 자동 분류', 제목은 ✨ 버튼으로 AI 자동 생성, 본문은 각 블록의 ✦ 메뉴에서 번역·보정·꾸미기를 이용할 수 있어요.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun InlineAiAction(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val active = enabled && !loading
    val contentColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
                color = contentColor
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/* ----- 공유 헬퍼: ContentType 메타 (List/Detail 과 동일 룩 유지) ----- */
private fun contentTypeMeta(type: ContentType): Pair<ImageVector, String> = when (type) {
    ContentType.DIARY -> Icons.AutoMirrored.Filled.MenuBook to ContentType.DIARY.label
    ContentType.POST -> Icons.Default.EditNote to ContentType.POST.label
    ContentType.NOTE -> Icons.Default.NoteAlt to ContentType.NOTE.label
}

/* ----- legacy API 호환 (이전 화면 코드에서 import 가능했던 public composable) ----- */
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
        Spacer(Modifier.width(4.dp))
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
 * 저장 직전 AI 가 본문을 분석해 추천한 글 타입이 사용자가 선택한 타입과 다를 때 표시되는 3버튼 다이얼로그.
 * - "변경하고 저장" : [DiaryIntent.ConfirmContentTypeChange] → 추천 타입으로 저장 진행
 * - "원래 타입 저장" : [DiaryIntent.KeepCurrentContentTypeAndSave] → 현재 선택 유지하고 저장
 * - "취소" : [DiaryIntent.CancelContentTypeChange] → 저장 자체를 취소
 */
@Composable
private fun ContentTypeChangeDialog(
    pending: PendingContentTypeChange,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "글 타입이 다른 것 같아요",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            val currentLabel = pending.currentType.label
            val suggestedLabel = pending.suggestedType.label
            Text(
                text = "AI 가 본문을 분석한 결과 \"$suggestedLabel\" 으로 보여요.\n" +
                        "현재 선택한 타입은 \"$currentLabel\" 입니다.\n\n" +
                        "어떤 타입으로 저장할까요?",
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "\"${pending.suggestedType.label}\" 으로 변경",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onKeep) {
                    Text("\"${pending.currentType.label}\" 유지")
                }
                TextButton(onClick = onCancel) {
                    Text("취소")
                }
            }
        }
    )
}
