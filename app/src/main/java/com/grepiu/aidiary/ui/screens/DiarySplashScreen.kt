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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grepiu.aidiary.ui.theme.Pretendard
import kotlinx.coroutines.delay

/**
 * 앱 실행 시 표시되는 애니메이션 스플래시 화면입니다.
 * 스마트한 느낌의 그라데이션 우주 공간 테마와 궤도를 도는 인공지능 입자선,
 * 바운스 핏 로고 및 한글 폰트 타이포그래피 애니메이션이 가미되어 있습니다.
 */
@Composable
fun DiarySplashScreen(
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    // 메인 북 카드 로고 스케일/투명도 애니메이션 (바운스 효과)
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1.5f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800),
        label = "logoAlpha"
    )

    // 타이틀 텍스트 슬라이드 및 투명도 애니메이션
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 600),
        label = "textAlpha"
    )

    val textTranslationY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 30f,
        animationSpec = tween(1000, delayMillis = 600),
        label = "textTranslation"
    )

    // 서브타이틀 투명도 애니메이션
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 1100),
        label = "subtitleAlpha"
    )

    // 무한 루프 애니메이션 효과들
    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    
    // AI 연산/연결성을 나타내는 두 개의 궤도(오빗) 회전각
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "orbitRotation"
    )

    // 은은하게 깜빡이는 아우라 백그라운드
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // AI 스파클의 가벼운 맥동(Pulse) 효과
    val sparkScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkScale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // 약 2.5초간 진행 후 목록 화면으로 진입
        onTimeout()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C091A), // 스마트한 느낌의 깊은 어두운 밤하늘
                        Color(0xFF150F2B),
                        Color(0xFF281E48)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. 네온 빛 입자(배경 데코레이션) 은은하게 렌더링
        Canvas(modifier = Modifier.fillMaxSize().alpha(glowAlpha)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // 왼쪽 위 오라
            drawCircle(
                color = Color(0xFF6E59C7),
                radius = 180.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(canvasWidth * 0.2f, canvasHeight * 0.3f),
                style = Stroke(width = 0f),
                alpha = 0.15f
            )
            // 오른쪽 아래 오라
            drawCircle(
                color = Color(0xFF9070FF),
                radius = 240.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(canvasWidth * 0.8f, canvasHeight * 0.7f),
                style = Stroke(width = 0f),
                alpha = 0.12f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 2. 다이어리 로고 그래픽 레이아웃
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                // 회전하는 점선 궤도 디자인 (Connectivity 느낌 부여)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = orbitRotation)
                ) {
                    val sizePx = size.width
                    // 외곽 궤도
                    drawCircle(
                        color = Color(0xFF8A76FF),
                        radius = (sizePx / 2) - 15.dp.toPx(),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(20f, 40f),
                                phase = 0f
                            )
                        ),
                        alpha = 0.4f
                    )
                    // 내곽 궤도
                    drawCircle(
                        color = Color(0xFFD0BCFF),
                        radius = (sizePx / 2) - 40.dp.toPx(),
                        style = Stroke(
                            width = 0.8.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(15f, 30f),
                                phase = 15f
                            )
                        ),
                        alpha = 0.3f
                    )
                }

                // 다이어리 노트 드로잉
                Canvas(
                    modifier = Modifier.size(120.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // 다이어리 바디
                    val bookLeft = w * 0.28f
                    val bookTop = h * 0.26f
                    val bookW = w * 0.44f
                    val bookH = h * 0.54f

                    // 그림자 효과
                    val shadowPath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = bookLeft + 5.dp.toPx(),
                                top = bookTop + 5.dp.toPx(),
                                right = bookLeft + bookW + 5.dp.toPx(),
                                bottom = bookTop + bookH + 5.dp.toPx(),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                            )
                        )
                    }
                    drawPath(shadowPath, Color(0x40000000))

                    // 다이어리 커버 둥근 사각형
                    val bookPath = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = bookLeft,
                                top = bookTop,
                                right = bookLeft + bookW,
                                bottom = bookTop + bookH,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                            )
                        )
                    }
                    
                    val bookGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFEBE6FF), Color(0xFFCBBDFD)),
                        start = androidx.compose.ui.geometry.Offset(bookLeft, bookTop),
                        end = androidx.compose.ui.geometry.Offset(bookLeft + bookW, bookTop + bookH)
                    )
                    drawPath(bookPath, bookGradient)

                    // 얇은 테두리선
                    drawPath(bookPath, Color(0xFFE2DCFF), alpha = 0.8f, style = Stroke(width = 1.5.dp.toPx()))

                    // 다이어리 내 필기 느낌선 (언제 어디서든 입력을 연상)
                    val lineStartX = bookLeft + 12.dp.toPx()
                    val lineEndX = bookLeft + bookW - 12.dp.toPx()
                    val lineY1 = bookTop + 18.dp.toPx()
                    val lineY2 = bookTop + 28.dp.toPx()
                    val lineY3 = bookTop + 38.dp.toPx()
                    val lineY4 = bookTop + 48.dp.toPx()

                    drawLine(
                        color = Color(0x66504675),
                        start = androidx.compose.ui.geometry.Offset(lineStartX, lineY1),
                        end = androidx.compose.ui.geometry.Offset(lineEndX, lineY1),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0x66504675),
                        start = androidx.compose.ui.geometry.Offset(lineStartX, lineY2),
                        end = androidx.compose.ui.geometry.Offset(lineEndX, lineY2),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0x66504675),
                        start = androidx.compose.ui.geometry.Offset(lineStartX, lineY3),
                        end = androidx.compose.ui.geometry.Offset(lineEndX - 15.dp.toPx(), lineY3),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0x33504675),
                        start = androidx.compose.ui.geometry.Offset(lineStartX, lineY4),
                        end = androidx.compose.ui.geometry.Offset(lineEndX - 10.dp.toPx(), lineY4),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // 좌측 스프링 제본용 링들
                    val ringX = bookLeft
                    for (i in 0..4) {
                        val ringY = bookTop + 11.dp.toPx() + (i * 12.dp.toPx())
                        drawArc(
                            color = Color(0xFF6C5A96),
                            startAngle = 100f,
                            sweepAngle = 160f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(ringX - 5.dp.toPx(), ringY - 3.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 7.dp.toPx()),
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 북마크 책갈피 끈
                    val bookmarkPath = Path().apply {
                        moveTo(bookLeft + bookW - 16.dp.toPx(), bookTop)
                        lineTo(bookLeft + bookW - 16.dp.toPx(), bookTop + 20.dp.toPx())
                        lineTo(bookLeft + bookW - 11.dp.toPx(), bookTop + 15.dp.toPx())
                        lineTo(bookLeft + bookW - 6.dp.toPx(), bookTop + 20.dp.toPx())
                        lineTo(bookLeft + bookW - 6.dp.toPx(), bookTop)
                        close()
                    }
                    drawPath(bookmarkPath, Color(0xFFEFA2B5))
                }

                // AI 스파클링 스타(언제나 연결되어 지능화 분석하는 AI 상징)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(sparkScale)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // 1. 우상단 금빛/핑크 네온 스타
                        val star1CenterX = w * 0.70f
                        val star1CenterY = h * 0.30f
                        val star1Size = 22.dp.toPx()

                        val starPath1 = Path().apply {
                            moveTo(star1CenterX, star1CenterY - star1Size)
                            quadraticTo(star1CenterX, star1CenterY, star1CenterX + star1Size, star1CenterY)
                            quadraticTo(star1CenterX, star1CenterY, star1CenterX, star1CenterY + star1Size)
                            quadraticTo(star1CenterX, star1CenterY, star1CenterX - star1Size, star1CenterY)
                            quadraticTo(star1CenterX, star1CenterY, star1CenterX, star1CenterY - star1Size)
                        }
                        
                        val star1Gradient = Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF7C0), Color(0xFFFF9EA7)),
                            start = androidx.compose.ui.geometry.Offset(star1CenterX - star1Size, star1CenterY - star1Size),
                            end = androidx.compose.ui.geometry.Offset(star1CenterX + star1Size, star1CenterY + star1Size)
                        )
                        drawPath(starPath1, star1Gradient)
                        drawPath(starPath1, Color.White, alpha = 0.9f, style = Stroke(width = 1.dp.toPx()))

                        // 2. 좌하단 하늘/퍼플 스타
                        val star2CenterX = w * 0.28f
                        val star2CenterY = h * 0.70f
                        val star2Size = 12.dp.toPx()

                        val starPath2 = Path().apply {
                            moveTo(star2CenterX, star2CenterY - star2Size)
                            quadraticTo(star2CenterX, star2CenterY, star2CenterX + star2Size, star2CenterY)
                            quadraticTo(star2CenterX, star2CenterY, star2CenterX, star2CenterY + star2Size)
                            quadraticTo(star2CenterX, star2CenterY, star2CenterX - star2Size, star2CenterY)
                            quadraticTo(star2CenterX, star2CenterY, star2CenterX, star2CenterY - star2Size)
                        }
                        
                        val star2Gradient = Brush.linearGradient(
                            colors = listOf(Color(0xFF7DE6FF), Color(0xFFC29DFF)),
                            start = androidx.compose.ui.geometry.Offset(star2CenterX - star2Size, star2CenterY - star2Size),
                            end = androidx.compose.ui.geometry.Offset(star2CenterX + star2Size, star2CenterY + star2Size)
                        )
                        drawPath(starPath2, star2Gradient)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 타이틀 텍스트
            Text(
                text = "AI 다이어리",
                color = Color.White,
                fontSize = 32.sp,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer(translationY = textTranslationY)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. 서브타이틀 설명 문구
            Text(
                text = "언제 어디서나, 스마트하게 기록하는 나만의 생각",
                color = Color(0xFFAFAEC2),
                fontSize = 14.sp,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )
        }
    }
}

@Preview
@Composable
fun DiarySplashScreenPreview() {
    DiarySplashScreen(onTimeout = {})
}
