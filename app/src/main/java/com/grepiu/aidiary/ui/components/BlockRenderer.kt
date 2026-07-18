package com.grepiu.aidiary.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.xr.compose.platform.LocalSpatialCapabilities
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import java.io.File
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.widget.Toast
import androidx.core.content.FileProvider
import com.grepiu.aidiary.data.model.SpatialMediaType
import java.io.FileOutputStream

/**
 * 일기 상세/목록 미리보기에서 사용되는 읽기 전용 블록 렌더러.
 */
@Composable
fun BlockRenderer(
    block: ContentBlock,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    imageScale: ContentScale = ContentScale.FillWidth,
    showImageCaption: Boolean = true
) {
    when (block) {
        is ContentBlock.HeadingBlock -> {
            val text = block.text.ifBlank { "제목 없음" }
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                lineHeight = 28.sp
            )
            Text(
                text = block.formatting.toAnnotatedString(text, textColor).let {
                    // 베이스 스타일을 머지
                    androidx.compose.ui.text.AnnotatedString(
                        text = it.text,
                        spanStyles = it.spanStyles,
                        paragraphStyles = it.paragraphStyles
                    )
                },
                style = base,
                modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
        is ContentBlock.TextBlock -> {
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = textColor
            )
            Text(
                text = block.formatting.toAnnotatedString(block.text, textColor),
                style = base,
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        is ContentBlock.QuoteBlock -> {
            val base = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontStyle = FontStyle.Italic,
                color = textColor
            )
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = block.formatting.toAnnotatedString(block.text, textColor),
                    style = base
                )
            }
        }
        is ContentBlock.ImageBlock -> {
            val context = LocalContext.current
            val file: File? = remember(block.relativePath) {
                if (block.relativePath.isBlank()) null
                else File(context.filesDir, block.relativePath).takeIf { it.exists() }
            }
            // 구 데이터 호환: ImageBlock 은 항상 2D 사진으로 간주 — "2D 사진" 라벨 표시
            val legacy2DAccent = MaterialTheme.colorScheme.outline
            Column(
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = legacy2DAccent.copy(alpha = 0.10f),
                        border = BorderStroke(0.5.dp, legacy2DAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "2D 사진",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = legacy2DAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = "일기 첨부 이미지",
                        contentScale = imageScale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "이미지를 찾을 수 없어요",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showImageCaption && block.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = block.caption,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        is ContentBlock.DividerBlock -> {
            HorizontalDivider(
                modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
        is ContentBlock.TagAiBlock -> {
            TagAiBlockView(
                block = block,
                modifier = modifier
            )
        }
        is ContentBlock.TableBlock -> {
            TableBlockView(
                block = block,
                textColor = textColor,
                modifier = modifier
            )
        }
        is ContentBlock.LocationBlock -> {
            LocationBlockView(
                block = block,
                textColor = textColor,
                modifier = modifier
            )
        }
        is ContentBlock.SpatialMediaBlock -> {
            SpatialMediaBlockView(
                block = block,
                textColor = textColor,
                showImageCaption = showImageCaption,
                modifier = modifier
            )
        }
    }
}

/**
 * 위치 블록 읽기 전용 렌더링. 지도 핀 아이콘과 주소, 좌표 정보를 깔끔하고 이쁘게 보여줍니다.
 */
@Composable
private fun LocationBlockView(
    block: ContentBlock.LocationBlock,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val isLoading = block.latitude == 0.0 && block.longitude == 0.0 && block.address == "위치 정보를 가져오는 중..."

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "위치",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.address.ifBlank { "알 수 없는 위치" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (!isLoading) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "위도: ${String.format(java.util.Locale.US, "%.5f", block.latitude)}, 경도: ${String.format(java.util.Locale.US, "%.5f", block.longitude)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 표 블록 읽기 전용 렌더링. 첫 행을 헤더로 스타일링 (볼드 + 옅은 배경),
 * 셀 간 0.5dp 보더 + 균등 분할 너비.
 */
@Composable
private fun TableBlockView(
    block: ContentBlock.TableBlock,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val cellBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val headerTextColor = MaterialTheme.colorScheme.onSurface
    val bodyTextColor = textColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(0.5.dp, cellBorder, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        block.cells.forEachIndexed { rowIdx, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIdx, cellText ->
                    val isHeader = rowIdx == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isHeader) headerBg else Color.Transparent)
                            .border(
                                width = 0.5.dp,
                                color = cellBorder
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = cellText.ifBlank { if (isHeader) "열 ${colIdx + 1}" else "" },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isHeader) headerTextColor
                            else if (cellText.isBlank() && !isHeader) bodyTextColor.copy(alpha = 0.35f)
                            else bodyTextColor,
                            maxLines = 6,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * TAG AI 블록 렌더링. 'TAG AI' 배지 + 감정 핀 한 줄로 간결하게 보여줍니다.
 */
@Composable
private fun TagAiBlockView(
    block: ContentBlock.TagAiBlock,
    modifier: Modifier = Modifier
) {
    val (emotionLabel, emotionColor) = emotionUi(block.emotion)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Text(
                text = "TAG AI",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.size(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = emotionColor.copy(alpha = 0.15f),
            border = BorderStroke(0.5.dp, emotionColor.copy(alpha = 0.4f))
        ) {
            Text(
                text = emotionLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = emotionColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * 감정 라벨을 한글 표시용 문자열과 테마 색상으로 매핑합니다.
 */
private fun emotionUi(label: String): Pair<String, Color> = when (label.trim()) {
    "기쁨" -> "😊 기쁨" to Color(0xFFD4AF37)
    "평온" -> "🌿 평온" to Color(0xFF2E7D32)
    "슬픔" -> "😢 슬픔" to Color(0xFF1565C0)
    "불안" -> "😰 불안" to Color(0xFF7B1FA2)
    "분노" -> "😡 분노" to Color(0xFFC62828)
    else -> "평온" to Color(0xFF2E7D32)
}

/**
 * 블록 여러 개를 위→아래로 순서대로 렌더링.
 */
@Composable
fun BlockList(
    blocks: List<ContentBlock>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        blocks.forEach { block ->
            BlockRenderer(block = block, textColor = textColor)
        }
    }
}

/**
 * 입체 미디어(SpatialMediaBlock) 읽기 전용 렌더러.
 *
 * 상단 배지 규칙 (사용자 의도):
 *  - 메인 라벨: 항상 "사진" 또는 "영상" 만 표출 (3D 접두사 없음)
 *  - 3D 일 때만 별도 소형 "3D" 칩을 메인 배지 옆에 노출
 *  - 보조 라벨: 3D 일 때만 포맷 상세(MPO/Stereo MP4 등) 표시
 *
 * 본문(미디어 영역) 2D/3D 시각 구분:
 *  - 3D PHOTO: 좌/우 SBS 합성 (paths >= 2 일 때) + 보라 border + purple accent
 *  - 2D PHOTO: 단일 이미지 + 회색 border
 *  - 3D VIDEO: VideoView + 보라 border
 *  - 2D VIDEO: VideoView + 회색 border
 */
@Composable
private fun SpatialMediaBlockView(
    block: ContentBlock.SpatialMediaBlock,
    textColor: Color,
    showImageCaption: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedPaths = remember(block.paths) {
        block.paths.mapNotNull { rel ->
            if (rel.isBlank()) null
            else File(context.filesDir, rel).takeIf { it.exists() }
        }
    }
    val isPhoto = block.mediaType == com.grepiu.aidiary.data.model.SpatialMediaType.PHOTO
    val is3D = block.captureMode.is3D
    // 메인 배지: 항상 "사진" 또는 "영상" (3D 접두사 X)
    val mediaTypeLabel = if (isPhoto) "사진" else "영상"
    val accent = if (is3D) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.outline
    val secondaryLabel = if (is3D) block.captureMode.label else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 3D 배지 + 포맷 라벨 + 3D 입체 보기 버튼
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = if (is3D) 0.18f else 0.10f),
                border = BorderStroke(if (is3D) 1.dp else 0.5.dp, accent.copy(alpha = if (is3D) 0.8f else 0.4f))
            ) {
                Text(
                    text = mediaTypeLabel,
                    fontSize = if (is3D) 12.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (is3D) {
                // 3D 소형 칩 (메인 배지 옆에)
                Spacer(modifier = Modifier.size(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "3D",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.size(6.dp))
            if (secondaryLabel != null) {
                Text(
                    text = secondaryLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (is3D) {
                Spacer(modifier = Modifier.weight(1f))
                val context = LocalContext.current
                androidx.compose.material3.Button(
                    onClick = {
                        launchExternal3DViewer(context, block)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C4DFF)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = Color(0xFF7C4DFF),
                            spotColor = Color(0xFF7C4DFF)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "3D 입체 감상",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "3D 입체 보기",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isPhoto) {
            when {
                resolvedPaths.size >= 2 -> {
                    // SBS 합성: 좌/우 2장 나란히
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        resolvedPaths.take(2).forEachIndexed { idx, file ->
                            AsyncImage(
                                model = file,
                                contentDescription = if (idx == 0) "입체 사진 좌측 시점" else "입체 사진 우측 시점",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
                resolvedPaths.size == 1 -> {
                    // STEREO_EXIF — 단일 이미지 + 3D 배지만
                    AsyncImage(
                        model = resolvedPaths[0],
                        contentDescription = "입체 사진",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    )
                }
                else -> {
                    // 파일 못 찾음
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "입체 사진 파일을 찾을 수 없어요",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // VIDEO — 실제 재생 (sandbox 내부 파일을 VideoView 가 직접 읽음)
            val videoFile = resolvedPaths.firstOrNull()
            if (videoFile != null) {
                // 비디오 재생을 제어하기 위한 Compose 상태들
                var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
                var isPrepared by remember { mutableStateOf(false) }
                var isPlaying by remember { mutableStateOf(false) }
                var currentPos by remember { mutableStateOf(0f) }
                var duration by remember { mutableStateOf(0f) }
                var showControls by remember { mutableStateOf(true) }
                var isDraggingSlider by remember { mutableStateOf(false) }
                var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

                // 비디오 재생 중일 때 주기적으로 현재 재생 시간을 업데이트
                LaunchedEffect(isPlaying, isDraggingSlider) {
                    if (isPlaying && !isDraggingSlider) {
                        while (true) {
                            videoViewRef?.let { vv ->
                                currentPos = vv.currentPosition.toFloat()
                                isPlaying = vv.isPlaying // 실제 동영상 상태와 동기화
                            }
                            kotlinx.coroutines.delay(100)
                        }
                    }
                }

                // 3초 미조작 시 컨트롤러 자동 페이드아웃 처리
                LaunchedEffect(showControls, isPlaying, lastInteractionTime) {
                    if (showControls && isPlaying) {
                        kotlinx.coroutines.delay(3000)
                        if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
                            showControls = false
                        }
                    }
                }

                val formatTime = { ms: Float ->
                    val totalSecs = (ms / 1000).toInt()
                    val minutes = totalSecs / 60
                    val seconds = totalSecs % 60
                    String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(
                            width = if (is3D) 1.5.dp else 0.5.dp,
                            color = accent.copy(alpha = if (is3D) 0.8f else 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            showControls = !showControls
                            if (showControls) {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                ) {
                    // 1. Android VideoView
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoURI(android.net.Uri.fromFile(videoFile))
                                setOnErrorListener { _, what, extra ->
                                    android.util.Log.w("BlockRenderer", "VideoView error: what=$what extra=$extra")
                                    true
                                }
                                setOnPreparedListener { mp ->
                                    isPrepared = true
                                    duration = mp.duration.toFloat()
                                    mp.isLooping = false
                                    mp.setVolume(1f, 1f)
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPos = 0f
                                    seekTo(0)
                                }
                                videoViewRef = this
                            }
                        },
                        update = { vv ->
                            videoViewRef = vv
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    )

                    // 2. 초기 로딩 중 스켈레톤/로딩바 오버레이
                    if (!isPrepared) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = accent,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "동영상을 불러오는 중…",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    // 3. 커스텀 컨트롤러 오버레이 UI (Fade In/Out 효과)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPrepared && showControls,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            // 중앙 재생/일시정지 큰 버튼
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        lastInteractionTime = System.currentTimeMillis()
                                        videoViewRef?.let { vv ->
                                            if (vv.isPlaying) {
                                                vv.pause()
                                                isPlaying = false
                                            } else {
                                                vv.start()
                                                isPlaying = true
                                            }
                                        }
                                    }
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "일시정지" else "재생",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // 하단 컨트롤 바 (재생 시간 슬라이더 + 타이머)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .clickable(enabled = false) {}
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${formatTime(currentPos)} / ${formatTime(duration)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // SeekBar 대체용 슬라이더
                                androidx.compose.material3.Slider(
                                    value = currentPos.coerceIn(0f, duration),
                                    onValueChange = { value ->
                                        isDraggingSlider = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        currentPos = value
                                    },
                                    onValueChangeFinished = {
                                        videoViewRef?.let { vv ->
                                            vv.seekTo(currentPos.toInt())
                                        }
                                        isDraggingSlider = false
                                    },
                                    valueRange = 0f..maxOf(1f, duration),
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "영상 파일을 찾을 수 없어요",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showImageCaption && block.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = block.caption,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// 싱크 유지를 위한 좌/우 플레이어 참조 홀더
private class SbsSyncHolder {
    var leftPlayer: android.widget.VideoView? = null
    var rightPlayer: android.widget.VideoView? = null
    var isPreparedLeft = false
    var isPreparedRight = false
}

@Composable
private fun StereoSbsViewerDialog(
    isPhoto: Boolean,
    resolvedPaths: List<File>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var isPrepared by remember { mutableStateOf(false) }

    // 싱크 홀더
    val syncHolder = remember { SbsSyncHolder() }

    // 비디오 재생 싱크 맞춤용 LaunchedEffect
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val left = syncHolder.leftPlayer
                val right = syncHolder.rightPlayer
                if (left != null && right != null) {
                    currentPos = left.currentPosition
                    // 좌우 간 싱크 오차 250ms 이상 날 시 강제 맞춤
                    val diff = Math.abs(left.currentPosition - right.currentPosition)
                    if (diff > 250) {
                        right.seekTo(left.currentPosition)
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            syncHolder.leftPlayer?.stopPlayback()
            syncHolder.rightPlayer?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. SBS 좌/우 시야 분할
            Row(modifier = Modifier.fillMaxSize()) {
                // 좌안(Left Eye)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPhoto) {
                        val leftFile = resolvedPaths.firstOrNull()
                        if (leftFile != null) {
                            AsyncImage(
                                model = leftFile,
                                contentDescription = "좌안 시점",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        val videoFile = resolvedPaths.firstOrNull()
                        if (videoFile != null) {
                            SbsVideoPlayer(
                                videoFile = videoFile,
                                isLeft = true,
                                syncHolder = syncHolder,
                                isPlayingState = isPlaying,
                                seekPos = currentPos,
                                onPrepared = { d ->
                                    duration = d
                                    if (syncHolder.isPreparedLeft && syncHolder.isPreparedRight) {
                                        isPrepared = true
                                    }
                                }
                            )
                        }
                    }
                }

                // 우안(Right Eye)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPhoto) {
                        val rightFile = if (resolvedPaths.size >= 2) resolvedPaths[1] else resolvedPaths.firstOrNull()
                        if (rightFile != null) {
                            AsyncImage(
                                model = rightFile,
                                contentDescription = "우안 시점",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        val videoFile = resolvedPaths.firstOrNull()
                        if (videoFile != null) {
                            SbsVideoPlayer(
                                videoFile = videoFile,
                                isLeft = false,
                                syncHolder = syncHolder,
                                isPlayingState = isPlaying,
                                seekPos = currentPos,
                                onPrepared = { _ ->
                                    if (syncHolder.isPreparedLeft && syncHolder.isPreparedRight) {
                                        isPrepared = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 2. 중앙 가이드 수직 분할선
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
                    .align(Alignment.Center)
            )

            // 3. 사진용 하단 3D 태그 표출 / 비디오용 플레이어 컨트롤 바 (좌우 대칭으로 듀얼 렌더링하여 양안에 다 보이게 함)
            if (!isPhoto && isPrepared) {
                // 비디오 조작 컨트롤러 (좌안용)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .align(Alignment.BottomStart)
                ) {
                    SbsControllerOverlay(
                        isPlaying = isPlaying,
                        currentPos = currentPos.toFloat(),
                        duration = duration.toFloat(),
                        onPlayToggle = {
                            isPlaying = !isPlaying
                            syncHolder.leftPlayer?.let { lp ->
                                if (isPlaying) lp.start() else lp.pause()
                            }
                            syncHolder.rightPlayer?.let { rp ->
                                if (isPlaying) rp.start() else rp.pause()
                            }
                        },
                        onSeek = { pos ->
                            currentPos = pos.toInt()
                            syncHolder.leftPlayer?.seekTo(currentPos)
                            syncHolder.rightPlayer?.seekTo(currentPos)
                        }
                    )
                }

                // 비디오 조작 컨트롤러 (우안용)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .align(Alignment.BottomEnd)
                ) {
                    SbsControllerOverlay(
                        isPlaying = isPlaying,
                        currentPos = currentPos.toFloat(),
                        duration = duration.toFloat(),
                        onPlayToggle = {
                            isPlaying = !isPlaying
                            syncHolder.leftPlayer?.let { lp ->
                                if (isPlaying) lp.start() else lp.pause()
                            }
                            syncHolder.rightPlayer?.let { rp ->
                                if (isPlaying) rp.start() else rp.pause()
                            }
                        },
                        onSeek = { pos ->
                            currentPos = pos.toInt()
                            syncHolder.leftPlayer?.seekTo(currentPos)
                            syncHolder.rightPlayer?.seekTo(currentPos)
                        }
                    )
                }
            }

            // 4. 대칭형 상단 닫기 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 좌안 닫기
                IconButton(
                    onClick = {
                        syncHolder.leftPlayer?.stopPlayback()
                        syncHolder.rightPlayer?.stopPlayback()
                        onDismiss()
                    },
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.White
                    )
                }

                // 우안 닫기
                IconButton(
                    onClick = {
                        syncHolder.leftPlayer?.stopPlayback()
                        syncHolder.rightPlayer?.stopPlayback()
                        onDismiss()
                    },
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SbsVideoPlayer(
    videoFile: File,
    isLeft: Boolean,
    syncHolder: SbsSyncHolder,
    isPlayingState: Boolean,
    seekPos: Int,
    onPrepared: (Int) -> Unit
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setVideoURI(android.net.Uri.fromFile(videoFile))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    // 에코 방지를 위해 사운드는 한쪽(Left)에서만 출력
                    mp.setVolume(if (isLeft) 1f else 0f, if (isLeft) 1f else 0f)
                    if (isLeft) {
                        syncHolder.leftPlayer = this
                        syncHolder.isPreparedLeft = true
                    } else {
                        syncHolder.rightPlayer = this
                        syncHolder.isPreparedRight = true
                    }
                    onPrepared(mp.duration)
                }
            }
        },
        update = { vv ->
            // 실시간 재생 여부 동기화
            if (syncHolder.isPreparedLeft && syncHolder.isPreparedRight) {
                if (isPlayingState) {
                    if (!vv.isPlaying) vv.start()
                } else {
                    if (vv.isPlaying) vv.pause()
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SbsControllerOverlay(
    isPlaying: Boolean,
    currentPos: Float,
    duration: Float,
    onPlayToggle: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 하단 조작 바
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "일시정지" else "재생",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val formatTime = { ms: Float ->
                    val totalSecs = (ms / 1000).toInt()
                    val minutes = totalSecs / 60
                    val seconds = totalSecs % 60
                    String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                }
                Text(
                    text = "${formatTime(currentPos)} / ${formatTime(duration)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Slider(
                value = currentPos.coerceIn(0f, duration),
                onValueChange = onSeek,
                valueRange = 0f..maxOf(1f, duration),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color(0xFF7C4DFF),
                    activeTrackColor = Color(0xFF7C4DFF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
            )
        }
    }
}

private fun launchExternal3DViewer(context: Context, block: ContentBlock.SpatialMediaBlock) {
    Log.d("External3DViewer", "launchExternal3DViewer 시작 - mediaType: ${block.mediaType}, paths: ${block.paths}")
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        
        // HMD 디바이스 제조사별 공식 3D/VR 입체 뷰어 패키지 목록 (Android XR, Quest, Pico 등)
        val xrPackages = listOf(
            "com.google.android.apps.xr.media.player",
            "com.google.android.apps.xr.media",
            "com.google.android.apps.xr.player",
            "com.oculus.gallery",
            "com.oculus.tv",
            "com.pico.gallery"
        )
        val pm = context.packageManager
        var matchedPackage: String? = null
        for (pkg in xrPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                matchedPackage = pkg
                Log.d("External3DViewer", "감지된 HMD 3D 플레이어 패키지: $pkg")
                break
            } catch (_: Exception) {}
        }
        if (matchedPackage != null) {
            intent.setPackage(matchedPackage)
            Log.d("External3DViewer", "인텐트 패키지 지정 완료: $matchedPackage")
        } else {
            Log.d("External3DViewer", "감지된 HMD 전용 3D 플레이어 패키지가 없어 기본 선택기/플레이어로 전송합니다.")
        }

        if (block.mediaType == SpatialMediaType.VIDEO) {
            if (block.paths.isNotEmpty()) {
                val rawFile = File(context.filesDir, block.paths[0])
                Log.d("External3DViewer", "비디오 원본 파일 경로: ${rawFile.absolutePath}, 존재여부: ${rawFile.exists()}, 크기: ${rawFile.length()} bytes")
                if (rawFile.exists()) {
                    // 기기 플레이어가 파일명 접미사(_3D_SBS)를 보고 3D 모드를 자동 활성화하도록
                    // 캐시 폴더에 '_3D_SBS.mp4' 형식의 파일명으로 임시 복사하여 전달합니다.
                    val baseName = rawFile.nameWithoutExtension
                    val tempVideoFile = File(context.cacheDir, "${baseName}_3D_SBS.mp4")
                    
                    Log.d("External3DViewer", "임시 복사 비디오 경로: ${tempVideoFile.absolutePath}")
                    // 파일 복사 실행
                    rawFile.inputStream().use { input ->
                        tempVideoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("External3DViewer", "비디오 임시 복사 완료 - 크기: ${tempVideoFile.length()} bytes")
                    
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempVideoFile
                    )
                    intent.setDataAndType(uri, "video/*")
                    
                    // 3D 입체 모드 활성화를 위한 다채로운 VR/3D HMD 플레이어 엑스트라 탑재
                    intent.putExtra("3d_mode", "sbs")
                    intent.putExtra("3d", true)
                    intent.putExtra("stereo_mode", "sbs")
                    intent.putExtra("stereo", "sbs")
                    intent.putExtra("vr_mode", true)
                    intent.putExtra("open_as_3d", true)
                    intent.putExtra("render_mode", "stereo_sbs")
                    intent.putExtra("android.intent.extra.vr.enable_stereo", true)
                    
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    // XR 환경에서 안전하게 새 작업 창으로 분리되도록 플래그 설정
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    
                    Log.d("External3DViewer", "비디오 ACTION_VIEW 인텐트 실행 시도")
                    context.startActivity(intent)
                    Log.d("External3DViewer", "비디오 ACTION_VIEW 인텐트 실행 성공")
                } else {
                    Log.e("External3DViewer", "비디오 파일이 존재하지 않아 인텐트를 발송하지 못했습니다.")
                    Toast.makeText(context, "비디오 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("External3DViewer", "비디오 block.paths 가 비어 있습니다.")
            }
        } else {
            Log.d("External3DViewer", "사진 입체 렌더링 모드 준비 - paths size: ${block.paths.size}")
            // PHOTO의 경우, 좌/우 이미지를 임시 SBS(Side-by-Side) 이미지로 합쳐서 캐시 폴더에 저장 후 뷰어로 보냄
            if (block.paths.size == 1) {
                // 단일 파일인 경우: 원본 자체가 이미 SBS 형태로 합쳐진 이미지로 판단
                val rawFile = File(context.filesDir, block.paths[0])
                Log.d("External3DViewer", "단일 3D 사진 원본 파일 경로: ${rawFile.absolutePath}, 존재여부: ${rawFile.exists()}")
                if (rawFile.exists()) {
                    val baseName = rawFile.nameWithoutExtension
                    val tempFile = File(context.cacheDir, "${baseName}_3D_SBS.jpg")
                    
                    // 파일 복사 실행
                    rawFile.inputStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("External3DViewer", "단일 3D 사진 복사 완료 - 크기: ${tempFile.length()} bytes")

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    intent.setDataAndType(uri, "image/*")
                    
                    // 3D 사진 입체 모드를 위한 엑스트라 탑재
                    intent.putExtra("3d_mode", "sbs")
                    intent.putExtra("stereo_mode", "sbs")
                    intent.putExtra("open_as_3d", true)
                    intent.putExtra("vr_mode", true)
                    
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    // XR 환경에서 안전하게 새 작업 창으로 분리되도록 플래그 설정
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    
                    Log.d("External3DViewer", "단일 사진 ACTION_VIEW 인텐트 실행 시도")
                    context.startActivity(intent)
                    Log.d("External3DViewer", "단일 사진 ACTION_VIEW 인텐트 실행 성공")
                } else {
                    Log.e("External3DViewer", "단일 3D 사진 파일이 존재하지 않습니다.")
                    Toast.makeText(context, "사진 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } else if (block.paths.size >= 2) {
                // 분리된 파일인 경우: 좌/우 이미지를 임시 SBS(Side-by-Side) 이미지로 합쳐서 캐시 폴더에 저장 후 뷰어로 보냄
                val fileL = File(context.filesDir, block.paths[0])
                val fileR = File(context.filesDir, block.paths[1])
                Log.d("External3DViewer", "좌안 파일: ${fileL.absolutePath} (존재: ${fileL.exists()}), 우안 파일: ${fileR.absolutePath} (존재: ${fileR.exists()})")
                if (fileL.exists() && fileR.exists()) {
                    val bmpLeft = BitmapFactory.decodeFile(fileL.absolutePath)
                    val bmpRight = BitmapFactory.decodeFile(fileR.absolutePath)
                    if (bmpLeft != null && bmpRight != null) {
                        val combinedWidth = bmpLeft.width + bmpRight.width
                        val combinedHeight = maxOf(bmpLeft.height, bmpRight.height)
                        val sbsBitmap = Bitmap.createBitmap(combinedWidth, combinedHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(sbsBitmap)
                        canvas.drawBitmap(bmpLeft, 0f, 0f, null)
                        canvas.drawBitmap(bmpRight, bmpLeft.width.toFloat(), 0f, null)

                        // 3D/VR 뷰어가 SBS 파일명 패턴을 자동 분석할 수 있도록 접미사를 추가합니다.
                        val tempFile = File(context.cacheDir, "sbs_photo_view_3D_SBS.jpg")
                        FileOutputStream(tempFile).use { out ->
                            sbsBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }

                        bmpLeft.recycle()
                        bmpRight.recycle()
                        sbsBitmap.recycle()

                        Log.d("External3DViewer", "사진 SBS 합성 완료 - 경로: ${tempFile.absolutePath}, 크기: ${tempFile.length()} bytes")

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )
                        intent.setDataAndType(uri, "image/*")
                        
                        // 3D 사진 입체 모드를 위한 엑스트라 탑재
                        intent.putExtra("3d_mode", "sbs")
                        intent.putExtra("stereo_mode", "sbs")
                        intent.putExtra("open_as_3d", true)
                        intent.putExtra("vr_mode", true)
                        
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        // XR 환경에서 안전하게 새 작업 창으로 분리되도록 플래그 설정
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        Log.d("External3DViewer", "사진 ACTION_VIEW 인텐트 실행 시도")
                        context.startActivity(intent)
                        Log.d("External3DViewer", "사진 ACTION_VIEW 인텐트 실행 성공")
                    } else {
                        Log.e("External3DViewer", "비트맵 디코딩 실패 (좌: ${bmpLeft != null}, 우: ${bmpRight != null})")
                        Toast.makeText(context, "사진 데이터 변환에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("External3DViewer", "3D 사진 파일 일부 또는 전체가 존재하지 않습니다.")
                    Toast.makeText(context, "3D 사진 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("External3DViewer", "3D 사진 감상에는 최소 1장 이상의 경로가 필요합니다. (현재: ${block.paths.size})")
            }
        }
    } catch (e: android.content.ActivityNotFoundException) {
        Log.w("External3DViewer", "특정 3D/VR 뷰어 앱 매핑 실패, 순수 플레이어로 폴백 실행 시도", e)
        try {
            // 패키지 및 고유 필터를 완전히 걷어내어 시스템 기본 플레이어 선택창이 뜨도록 복구 처리
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                if (block.mediaType == SpatialMediaType.VIDEO) {
                    val rawFile = File(context.filesDir, block.paths[0])
                    val baseName = rawFile.nameWithoutExtension
                    val tempVideoFile = File(context.cacheDir, "${baseName}_3D_SBS.mp4")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempVideoFile)
                    setDataAndType(uri, "video/*")
                } else {
                    val tempFile = if (block.paths.size == 1) {
                        val rawFile = File(context.filesDir, block.paths[0])
                        File(context.cacheDir, "${rawFile.nameWithoutExtension}_3D_SBS.jpg")
                    } else {
                        File(context.cacheDir, "sbs_photo_view_3D_SBS.jpg")
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    setDataAndType(uri, "image/*")
                }
                // XR 환경에서 안전하게 새 작업 창으로 분리되도록 플래그 설정
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(fallbackIntent)
            Log.d("External3DViewer", "순수 ACTION_VIEW 폴백 실행 성공")
        } catch (fallbackEx: Exception) {
            Log.e("External3DViewer", "폴백 실행마저 실패", fallbackEx)
            Toast.makeText(context, "플레이어 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("External3DViewer", "내장 뷰어 실행 중 예외 발생", e)
        Toast.makeText(context, "내장 뷰어 실행 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

