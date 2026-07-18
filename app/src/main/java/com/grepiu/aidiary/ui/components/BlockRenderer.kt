package com.grepiu.aidiary.ui.components

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.grepiu.aidiary.data.model.ContentBlock
import java.io.File

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
        // 3D 배지 + 포맷 라벨
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
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        val videoView = android.widget.VideoView(ctx)
                        videoView.setVideoURI(android.net.Uri.fromFile(videoFile))
                        val controller = android.widget.MediaController(ctx)
                        controller.setAnchorView(videoView)
                        videoView.setMediaController(controller)
                        videoView.setOnPreparedListener { mp ->
                            mp.isLooping = false
                            mp.setVolume(1f, 1f)
                        }
                        videoView.setOnErrorListener { _, what, extra ->
                            android.util.Log.w("BlockRenderer", "VideoView error: what=$what extra=$extra")
                            true
                        }
                        videoView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (is3D) 1.dp else 0.5.dp,
                            color = accent.copy(alpha = if (is3D) 0.5f else 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
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
