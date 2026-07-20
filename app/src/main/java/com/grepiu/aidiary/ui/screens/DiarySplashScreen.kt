package com.grepiu.aidiary.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.ui.theme.Pretendard
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 프리미엄 라이트 오로라 파스텔 스플래시 화면.
 * 새 2030 타깃 밝은 App 아이콘과 동일한 톤앤무드(화이트 & 스카이블루 & 피치)로 리디자인.
 *
 * 애니메이션 구성:
 *  1. 배경 - 부드럽게 살아있는 오로라 그라데이션 파형 (라이트 앰비언트 Blob 2개)
 *  2. 아이콘 카드 - 그라스모피즘 카드 + 스케일 스프링 바운스 인트로 등장
 *  3. 다이어리 아이콘 - 새 아이콘과 동일한 스타일(노트북+별빛) Canvas 드로잉
 *  4. 회전 반짝임 링 - 아이콘 주위를 돌며 AI 연결성 시각화
 *  5. 파티클 스파클 - 공간에 떠다니는 소형 AI 별 파티클 7개
 *  6. 타이틀 슬라이드업 - 'AI 다이어리' 타이포그래피 바텀업 스무스 등장
 *  7. 서브타이틀 & AI 뱃지 - 지연 페이드인 + 슬라이드업
 *  8. 하단 로딩 인디케이터 - 부드러운 좌→우 스위핑 밝은 라인
 */
@Composable
fun DiarySplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    // ────────────────────────────────────────────────────────────
    // 1. 아이콘 카드 등장 애니메이션 (Spring Bounce)
    // ────────────────────────────────────────────────────────────
    val cardScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "cardAlpha"
    )

    // ────────────────────────────────────────────────────────────
    // 2. 타이틀 슬라이드업
    // ────────────────────────────────────────────────────────────
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(900, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleSlideY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 48f,
        animationSpec = tween(900, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "titleSlideY"
    )

    // ────────────────────────────────────────────────────────────
    // 3. 서브타이틀 페이드인
    // ────────────────────────────────────────────────────────────
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 900, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )
    val subtitleSlideY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 24f,
        animationSpec = tween(800, delayMillis = 900, easing = FastOutSlowInEasing),
        label = "subtitleSlideY"
    )

    // ────────────────────────────────────────────────────────────
    // 4. AI 뱃지 페이드인
    // ────────────────────────────────────────────────────────────
    val badgeAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 1200, easing = FastOutSlowInEasing),
        label = "badgeAlpha"
    )

    // ────────────────────────────────────────────────────────────
    // 5. 무한 애니메이션 (배경 Blob, 링 회전, 파티클, 로딩바)
    // ────────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "inf")

    // 배경 오로라 Blob 맥동
    val blobPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blobPulse"
    )

    // 아이콘 주위 반짝임 링 회전
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing)
        ),
        label = "ringRotation"
    )
    val ringRotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing)
        ),
        label = "ringRotationReverse"
    )

    // AI 별 파티클 부유 효과
    val particleFloat by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particleFloat"
    )

    // 아이콘 그라스 카드 은은한 맥동
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPulse"
    )

    // 하단 로딩 스위퍼
    val loaderProgress by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing)
        ),
        label = "loaderProgress"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2800)
        onTimeout()
    }

    // ════════════════════════════════════════════════════════════
    // 메인 배경: 라이트 오로라 파스텔 그라데이션
    // ════════════════════════════════════════════════════════════
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),          // 최상단 화이트
                        Color(0xFFF0F7FF),          // 연한 아이스 블루
                        Color(0xFFE8F4FF),          // 하늘빛 화이트
                        Color(0xFFFDF0F5),          // 연한 피치 로즈
                        Color(0xFFFFF8F6),          // 따뜻한 화이트
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        // ──────────────────────────────────────────────────────
        // 레이어 1: 오로라 Blob 배경 (부드럽게 살아있는 느낌)
        // ──────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = blobPulse

            // Blob 1: 좌상단 스카이 블루 오로라
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x55A8D8FF),
                        Color(0x2272B5F0),
                        Color(0x00A8D8FF)
                    ),
                    center = Offset(w * (0.15f + t * 0.08f), h * (0.2f - t * 0.05f)),
                    radius = w * (0.55f + t * 0.1f)
                ),
                radius = w * (0.55f + t * 0.1f),
                center = Offset(w * (0.15f + t * 0.08f), h * (0.2f - t * 0.05f))
            )

            // Blob 2: 우하단 피치 핑크 오로라
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x55FFB8C6),
                        Color(0x22F99CB4),
                        Color(0x00FFB8C6)
                    ),
                    center = Offset(w * (0.88f - t * 0.07f), h * (0.78f + t * 0.05f)),
                    radius = w * (0.5f + t * 0.08f)
                ),
                radius = w * (0.5f + t * 0.08f),
                center = Offset(w * (0.88f - t * 0.07f), h * (0.78f + t * 0.05f))
            )

            // Blob 3: 중앙 민트 오로라 (은은하게)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x3396E8C8),
                        Color(0x1168C9A6),
                        Color(0x0096E8C8)
                    ),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = w * 0.4f
                ),
                radius = w * 0.4f,
                center = Offset(w * 0.5f, h * 0.45f)
            )
        }

        // ──────────────────────────────────────────────────────
        // 레이어 2: 작은 AI 파티클 스파클들 (7개 랜덤 배치)
        // ──────────────────────────────────────────────────────
        val particles = remember {
            listOf(
                Triple(0.12f, 0.18f, 14f),  // (x비율, y비율, 크기)
                Triple(0.85f, 0.12f, 10f),
                Triple(0.92f, 0.42f, 8f),
                Triple(0.08f, 0.62f, 12f),
                Triple(0.78f, 0.78f, 9f),
                Triple(0.20f, 0.85f, 7f),
                Triple(0.60f, 0.10f, 11f),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize().alpha(subtitleAlpha)) {
            val w = size.width
            val h = size.height
            particles.forEachIndexed { i, (rx, ry, sz) ->
                val floatOffset = if (i % 2 == 0) particleFloat else -particleFloat
                val cx = w * rx
                val cy = h * ry + floatOffset * (0.6f + i * 0.1f)
                val sPx = sz.dp.toPx()
                // 4방향 스파클 별
                val starPath = Path().apply {
                    moveTo(cx, cy - sPx)
                    quadraticTo(cx, cy, cx + sPx * 0.4f, cy)
                    quadraticTo(cx, cy, cx, cy + sPx)
                    quadraticTo(cx, cy, cx - sPx * 0.4f, cy)
                    quadraticTo(cx, cy, cx, cy - sPx)
                }
                drawPath(
                    starPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (i % 3 == 0) Color(0xFFFFD6A5) else if (i % 3 == 1) Color(0xFFB8E0FF) else Color(0xFFFFC8D8),
                            Color.White
                        ),
                        start = Offset(cx - sPx, cy - sPx),
                        end = Offset(cx + sPx, cy + sPx)
                    ),
                    alpha = 0.75f
                )
            }
        }

        // ──────────────────────────────────────────────────────
        // 메인 컨텐츠 컬럼
        // ──────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ══════════════════════════════════════════════════
            // 아이콘 영역: 그라스모피즘 카드 + 다이어리 Canvas
            // ══════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(cardScale * iconPulse)
                    .alpha(cardAlpha),
                contentAlignment = Alignment.Center
            ) {
                // 링 1: 외곽 점선 회전 링 (시계방향)
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer(rotationZ = ringRotation)
                ) {
                    val r = size.width / 2f - 4.dp.toPx()
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0x00A8D8FF),
                                Color(0x88A8D8FF),
                                Color(0xFFFFB8C6),
                                Color(0x88FFD6A5),
                                Color(0x00A8D8FF),
                            )
                        ),
                        radius = r,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(18f, 22f), 0f
                            )
                        )
                    )
                }

                // 링 2: 내곽 반시계방향 링 (피치 톤)
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer(rotationZ = ringRotationReverse)
                ) {
                    val r = size.width / 2f - 4.dp.toPx()
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0x00FFB8C6),
                                Color(0x77FFB8C6),
                                Color(0xFFB8E0FF),
                                Color(0x00FFB8C6),
                            )
                        ),
                        radius = r,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 18f), 0f
                            )
                        )
                    )
                }

                // 아이콘 그라스모피즘 카드 배경
                Canvas(modifier = Modifier.size(130.dp)) {
                    val cardR = size.width / 2f
                    // 카드 그림자 (Soft Shadow)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x22A8C8FF), Color(0x00A8C8FF)),
                            center = Offset(cardR + 4.dp.toPx(), cardR + 8.dp.toPx()),
                            radius = cardR * 1.15f
                        ),
                        radius = cardR * 1.15f,
                        center = Offset(cardR + 4.dp.toPx(), cardR + 8.dp.toPx())
                    )
                    // 카드 메인 (밝은 glassmorphism)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFF8FCFF),  // 상단 아이스 화이트
                                Color(0xFFFEF6F8),  // 하단 피치 화이트
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        size = size
                    )
                    // 카드 테두리 (thin gradient stroke)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xCCB8E0FF),
                                Color(0xCCFFB8C6),
                                Color(0xCCFFD6A5),
                            )
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        size = size,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // 다이어리 노트 + AI 스타 Canvas 드로잉 (새 아이콘과 동일 무드)
                Canvas(modifier = Modifier.size(74.dp)) {
                    val w = size.width
                    val h = size.height

                    // ── 다이어리 페이지 그림자
                    val shadowPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = w * 0.22f + 3.dp.toPx(),
                                top = h * 0.12f + 4.dp.toPx(),
                                right = w * 0.22f + w * 0.60f + 3.dp.toPx(),
                                bottom = h * 0.12f + h * 0.72f + 4.dp.toPx(),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                        )
                    }
                    drawPath(shadowPath, Color(0x18779ABF))

                    // ── 다이어리 커버 (스카이 블루 ~ 화이트 그라데이션)
                    val coverPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = w * 0.22f,
                                top = h * 0.12f,
                                right = w * 0.22f + w * 0.60f,
                                bottom = h * 0.12f + h * 0.72f,
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                        )
                    }
                    drawPath(
                        coverPath,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE8F6FF),
                                Color(0xFFFDF8FF),
                                Color(0xFFFFF0F4),
                            ),
                            start = Offset(w * 0.22f, h * 0.12f),
                            end = Offset(w * 0.82f, h * 0.84f)
                        )
                    )
                    // 커버 테두리
                    drawPath(
                        coverPath,
                        Color(0xAABBDDF0),
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    // ── 줄 (노트 필기 라인 3개)
                    val lx1 = w * 0.30f
                    val lx2 = w * 0.76f
                    listOf(0.32f, 0.45f, 0.58f).forEachIndexed { i, yr ->
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0x6688B8D8), Color(0x2288B8D8))
                            ),
                            start = Offset(lx1, h * yr),
                            end = Offset(if (i == 2) lx2 - w * 0.1f else lx2, h * yr),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // ── 좌측 스프링 링 바인딩 (3개)
                    val ringX = w * 0.22f
                    for (i in 0..2) {
                        val ry = h * 0.24f + i * h * 0.18f
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFF72B5E8), Color(0xFFA8D8FF))
                            ),
                            startAngle = 90f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(ringX - 5.dp.toPx(), ry - 4.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // ── 우상단 AI 별 스파클 (메인, 골든 피치)
                    val sCx = w * 0.75f
                    val sCy = h * 0.16f
                    val sR = 9.dp.toPx()
                    val aiStar = Path().apply {
                        moveTo(sCx, sCy - sR)
                        quadraticTo(sCx, sCy, sCx + sR * 0.42f, sCy)
                        quadraticTo(sCx, sCy, sCx, sCy + sR)
                        quadraticTo(sCx, sCy, sCx - sR * 0.42f, sCy)
                        quadraticTo(sCx, sCy, sCx, sCy - sR)
                    }
                    drawPath(
                        aiStar,
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFC878), Color(0xFFFF9EB5)),
                            start = Offset(sCx - sR, sCy - sR),
                            end = Offset(sCx + sR, sCy + sR)
                        )
                    )

                    // ── 소형 서브 스파클 (좌하단, 스카이)
                    val s2Cx = w * 0.25f
                    val s2Cy = h * 0.83f
                    val s2R = 5.dp.toPx()
                    val aiStar2 = Path().apply {
                        moveTo(s2Cx, s2Cy - s2R)
                        quadraticTo(s2Cx, s2Cy, s2Cx + s2R * 0.4f, s2Cy)
                        quadraticTo(s2Cx, s2Cy, s2Cx, s2Cy + s2R)
                        quadraticTo(s2Cx, s2Cy, s2Cx - s2R * 0.4f, s2Cy)
                        quadraticTo(s2Cx, s2Cy, s2Cx, s2Cy - s2R)
                    }
                    drawPath(
                        aiStar2,
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF88D8FF), Color(0xFFB0E8D4)),
                            start = Offset(s2Cx - s2R, s2Cy - s2R),
                            end = Offset(s2Cx + s2R, s2Cy + s2R)
                        )
                    )
                }

                // 링 위의 작은 빛 점 (회전과 함께 돔)
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer(rotationZ = ringRotation)
                ) {
                    val cx = size.width / 2f
                    val cy = 6.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFD6A5), Color(0x00FFD6A5)),
                            center = Offset(cx, cy),
                            radius = 10.dp.toPx()
                        ),
                        radius = 5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer(rotationZ = ringRotationReverse)
                ) {
                    val cx = size.width / 2f
                    val cy = 4.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFB8C6), Color(0x00FFB8C6)),
                            center = Offset(cx, cy),
                            radius = 8.dp.toPx()
                        ),
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ──────────────────────────────────────────────────
            // 타이틀: AI 다이어리
            // ──────────────────────────────────────────────────
            Text(
                text = "AI 다이어리",
                color = Color(0xFF1A2A4A),
                fontSize = 34.sp,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .graphicsLayer(translationY = titleSlideY)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ──────────────────────────────────────────────────
            // 서브타이틀
            // ──────────────────────────────────────────────────
            Text(
                text = "나만의 스마트 AI 기록 플래너",
                color = Color(0xFF7A9AB8),
                fontSize = 15.sp,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                modifier = Modifier
                    .alpha(subtitleAlpha)
                    .graphicsLayer(translationY = subtitleSlideY)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ──────────────────────────────────────────────────
            // AI 뱃지 (온디바이스 · 오프라인)
            // ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(badgeAlpha)
            ) {
                listOf(
                    "🎙️ 음성 인식" to Color(0xFFE6F4FF),
                    "🛡️ 온디바이스" to Color(0xFFF0FFF6),
                    "🤖 AI 감정" to Color(0xFFFFF4F0),
                ).forEach { (label, bgColor) ->
                    Box(
                        modifier = Modifier.background(
                            color = bgColor,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                        ).padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4A7A9B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ──────────────────────────────────────────────────
            // 하단 스위핑 로딩 인디케이터
            // ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(bottom = 52.dp)
                    .width(120.dp)
                    .height(3.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().alpha(subtitleAlpha)) {
                    val trackW = size.width
                    val trackH = size.height
                    // 트랙 배경
                    drawRoundRect(
                        color = Color(0x22A8D8FF),
                        cornerRadius = CornerRadius(trackH / 2)
                    )
                    // 스위핑 글로우
                    val sweepW = trackW * 0.45f
                    val sweepX = trackW * loaderProgress - sweepW / 2
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x00A8D8FF),
                                Color(0xFFFFB8C6),
                                Color(0xFFA8D8FF),
                                Color(0x00A8D8FF),
                            ),
                            startX = sweepX,
                            endX = sweepX + sweepW
                        ),
                        topLeft = Offset(sweepX.coerceIn(0f, trackW), 0f),
                        size = androidx.compose.ui.geometry.Size(
                            sweepW.coerceAtMost(trackW - sweepX.coerceIn(0f, trackW)),
                            trackH
                        ),
                        cornerRadius = CornerRadius(trackH / 2)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiarySplashScreenPreview() {
    DiarySplashScreen(onTimeout = {})
}
