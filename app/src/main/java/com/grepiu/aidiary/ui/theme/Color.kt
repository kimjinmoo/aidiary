package com.grepiu.aidiary.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AIDiary 브랜드 컬러 시스템 v4.0
 *
 * 테마 3종:
 *  - BLOSSOM : 소프트 페미닌 (더스티 로즈/모브/세이지) — 기존 v3.0 유지
 *  - ATLAS   : 남녀 공용 Default (스틸 블루/앰버/틸 그린) — 중성적 모던
 *  - ECLIPSE : 블랙 다크 (딥 니어블랙 배경 + 인디고/바이올렛 네온 엑센트)
 */

// =============================================================================
// ── BLOSSOM (기존 v3.0, 소프트 페미닌) ──────────────────────────────────────
// Primary — 더스티 로즈 (Dusty Rose)
// =============================================================================
val BlossomPrimaryLight            = Color(0xFFC67A8E)
val BlossomOnPrimaryLight          = Color(0xFFFFFFFF)
val BlossomPrimaryContainerLight   = Color(0xFFF7DCE2)
val BlossomOnPrimaryContainerLight = Color(0xFF40121F)

val BlossomPrimaryDark             = Color(0xFFE7A6B4)
val BlossomOnPrimaryDark           = Color(0xFF40121F)
val BlossomPrimaryContainerDark    = Color(0xFF5D3542)
val BlossomOnPrimaryContainerDark  = Color(0xFFF7DCE2)

// Secondary — 모브 (Mauve/Periwinkle)
val BlossomSecondaryLight            = Color(0xFF8B87C7)
val BlossomOnSecondaryLight          = Color(0xFFFFFFFF)
val BlossomSecondaryContainerLight   = Color(0xFFE7E2F5)
val BlossomOnSecondaryContainerLight = Color(0xFF231A3E)

val BlossomSecondaryDark             = Color(0xFFC3BEF0)
val BlossomOnSecondaryDark           = Color(0xFF231A3E)
val BlossomSecondaryContainerDark    = Color(0xFF423A5C)
val BlossomOnSecondaryContainerDark  = Color(0xFFE7E2F5)

// Tertiary — 세이지 (Sage)
val BlossomTertiaryLight            = Color(0xFF5FA37E)
val BlossomOnTertiaryLight          = Color(0xFFFFFFFF)
val BlossomTertiaryContainerLight   = Color(0xFFCDEEDB)
val BlossomOnTertiaryContainerLight = Color(0xFF00210F)

val BlossomTertiaryDark             = Color(0xFF9AD3B4)
val BlossomOnTertiaryDark           = Color(0xFF00210F)
val BlossomTertiaryContainerDark    = Color(0xFF2F5240)
val BlossomOnTertiaryContainerDark  = Color(0xFFCDEEDB)

// Surface / Background — 블러시 웜화이트 (기존 유지)
val BlossomSurfaceLight          = Color(0xFFFFFFFF)
val BlossomOnSurfaceLight        = Color(0xFF241F1E)
val BlossomSurfaceVariantLight   = Color(0xFFF4EAEA)
val BlossomOnSurfaceVariantLight = Color(0xFF5B4F4C)
val BlossomOutlineLight          = Color(0xFF9A8B88)
val BlossomOutlineVariantLight   = Color(0xFFE4D2D0)

val BlossomSurfaceDark           = Color(0xFF241E21)
val BlossomOnSurfaceDark         = Color(0xFFECE0DE)
val BlossomSurfaceVariantDark    = Color(0xFF3A3134)
val BlossomOnSurfaceVariantDark  = Color(0xFFD8C6C4)
val BlossomOutlineDark           = Color(0xFFA08C88)
val BlossomOutlineVariantDark    = Color(0xFF4E4244)

val BlossomBackgroundLight = Color(0xFFFBF6F5)  // 블러시 웜화이트
val BlossomBackgroundDark  = Color(0xFF1A1618)  // 웜 니어블랙

// =============================================================================
// ── ATLAS (남녀 공용 Default, 스틸 블루 + 앰버 + 틸 그린) ────────────────────
// Primary — 스틸 블루 (Steel Blue): 신뢰, 안정, 중성적
// =============================================================================
val AtlasPrimaryLight            = Color(0xFF3D7BB5)
val AtlasOnPrimaryLight          = Color(0xFFFFFFFF)
val AtlasPrimaryContainerLight   = Color(0xFFD4E8F7)
val AtlasOnPrimaryContainerLight = Color(0xFF001E33)

val AtlasPrimaryDark             = Color(0xFF7EB8E8)
val AtlasOnPrimaryDark           = Color(0xFF001E33)
val AtlasPrimaryContainerDark    = Color(0xFF1A4F78)
val AtlasOnPrimaryContainerDark  = Color(0xFFD4E8F7)

// Secondary — 앰버 (Warm Amber): 활력, 포인트
val AtlasSecondaryLight            = Color(0xFFB07D2A)
val AtlasOnSecondaryLight          = Color(0xFFFFFFFF)
val AtlasSecondaryContainerLight   = Color(0xFFF5E6C3)
val AtlasOnSecondaryContainerLight = Color(0xFF2C1E00)

val AtlasSecondaryDark             = Color(0xFFDFBA6A)
val AtlasOnSecondaryDark           = Color(0xFF2C1E00)
val AtlasSecondaryContainerDark    = Color(0xFF614800)
val AtlasOnSecondaryContainerDark  = Color(0xFFF5E6C3)

// Tertiary — 틸 그린 (Teal): 균형, 집중, 목표
val AtlasTertiaryLight            = Color(0xFF2A8C7B)
val AtlasOnTertiaryLight          = Color(0xFFFFFFFF)
val AtlasTertiaryContainerLight   = Color(0xFFBEEDE5)
val AtlasOnTertiaryContainerLight = Color(0xFF002920)

val AtlasTertiaryDark             = Color(0xFF72CFC0)
val AtlasOnTertiaryDark           = Color(0xFF002920)
val AtlasTertiaryContainerDark    = Color(0xFF004B3F)
val AtlasOnTertiaryContainerDark  = Color(0xFFBEEDE5)

// Surface / Background — 쿨 뉴트럴 (Cool Neutral)
val AtlasSurfaceLight          = Color(0xFFFFFFFF)
val AtlasOnSurfaceLight        = Color(0xFF1A1C1E)
val AtlasSurfaceVariantLight   = Color(0xFFE8EEF3)
val AtlasOnSurfaceVariantLight = Color(0xFF3F4751)
val AtlasOutlineLight          = Color(0xFF6F7B86)
val AtlasOutlineVariantLight   = Color(0xFFBEC8D2)

val AtlasSurfaceDark           = Color(0xFF181C1F)
val AtlasOnSurfaceDark         = Color(0xFFE2E3E5)
val AtlasSurfaceVariantDark    = Color(0xFF2B333A)
val AtlasOnSurfaceVariantDark  = Color(0xFFBEC8D2)
val AtlasOutlineDark           = Color(0xFF8A9299)
val AtlasOutlineVariantDark    = Color(0xFF3A444C)

val AtlasBackgroundLight = Color(0xFFF4F6F9)  // 쿨 화이트-그레이
val AtlasBackgroundDark  = Color(0xFF111416)  // 딥 차콜

// =============================================================================
// ── ECLIPSE (블랙 다크 테마, 딥 다크 + 인디고/바이올렛 네온) ──────────────────
// Eclipse는 항상 다크(Dark only). 라이트 팔레트 없음.
// Primary — 라이트 인디고 (Light Indigo): 네온 글로우 엑센트
// =============================================================================
val EclipsePrimary            = Color(0xFF8B8FF8)   // 소프트 인디고 네온
val EclipseOnPrimary          = Color(0xFF0A0A1A)
val EclipsePrimaryContainer   = Color(0xFF2B2E7A)
val EclipseOnPrimaryContainer = Color(0xFFCDD0FF)

// Secondary — 바이올렛 (Violet): 미묘한 포인트
val EclipseSecondary            = Color(0xFFBB86FC)   // Material 시그니처 퍼플
val EclipseOnSecondary          = Color(0xFF1A0030)
val EclipseSecondaryContainer   = Color(0xFF3B1F5E)
val EclipseOnSecondaryContainer = Color(0xFFEDDAFF)

// Tertiary — 아쿠아 민트 (Aqua Mint): 목표/성공 액센트
val EclipseTertiary            = Color(0xFF4DCFB0)
val EclipseOnTertiary          = Color(0xFF00201A)
val EclipseTertiaryContainer   = Color(0xFF00443A)
val EclipseOnTertiaryContainer = Color(0xFFB0F0E4)

// Surface / Background — 딥 니어블랙
val EclipseSurface          = Color(0xFF1A1A2E)   // 딥 인디고 블랙
val EclipseOnSurface        = Color(0xFFE8E8F4)
val EclipseSurfaceVariant   = Color(0xFF252540)   // 서브 패널
val EclipseOnSurfaceVariant = Color(0xFFBCBCDC)
val EclipseOutline          = Color(0xFF6666AA)
val EclipseOutlineVariant   = Color(0xFF333360)

val EclipseBackground       = Color(0xFF0F0F1A)   // 거의 블랙

// Error (공통)
val ErrorLight   = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorDark    = Color(0xFFFFB4AB)
val OnErrorDark  = Color(0xFF690005)

// =============================================================================
// Tab Accent & Content Type Colors — 기능별 액센트 (AppTheme 동적 대응)
//   기록(DIARY) · 플래너(PLANNER) · 목표(GOALS) · AI비서(CHAT)
// =============================================================================

fun themeDiaryAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS   -> Color(0xFF3D7BB5) // 스틸 블루
    AppTheme.BLOSSOM -> Color(0xFFC67A8E) // 더스티 로즈
    AppTheme.ECLIPSE -> Color(0xFF8B8FF8) // 라이트 인디고 네온
    AppTheme.KIDS    -> Color(0xFFFF6B81) // 캔디 버블검 핑크
}

fun themePlannerAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS   -> Color(0xFFB07D2A) // 앰버 골드
    AppTheme.BLOSSOM -> Color(0xFFE8945C) // 웜 피치
    AppTheme.ECLIPSE -> Color(0xFFFFB74D) // 앰버 글로우
    AppTheme.KIDS    -> Color(0xFFFFA502) // 캔디 바나나 오렌지
}

fun themeGoalsAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS   -> Color(0xFF2A8C7B) // 틸 그린
    AppTheme.BLOSSOM -> Color(0xFF5FA37E) // 세이지 그린
    AppTheme.ECLIPSE -> Color(0xFF4DCFB0) // 아쿠아 민트
    AppTheme.KIDS    -> Color(0xFF2ED573) // 캔디 라임 민트
}

fun themeChatAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS   -> Color(0xFF5C6BC0) // 스마트 인디고
    AppTheme.BLOSSOM -> Color(0xFF8B87C7) // 모브
    AppTheme.ECLIPSE -> Color(0xFFBB86FC) // 네온 바이올렛
    AppTheme.KIDS    -> Color(0xFF70A1FF) // 캔디 스카이 블루
}

// =============================================================================
// Emotion Colors — 감정 시각화
// =============================================================================
val EmotionJoy     = Color(0xFFE0A94A)  // 웜 골드
val EmotionSadness = Color(0xFF6B8BAE)  // 스틸 블루
val EmotionAnger   = Color(0xFFD96B6B)  // 코랄
val EmotionAnxiety = Color(0xFF9585BE)  // 더스티 퍼플
val EmotionCalm    = Color(0xFF6FA98A)  // 세이지

val DiaryAccent: Color
    @Composable get() = themeDiaryAccent(LocalAppTheme.current)

val PlannerAccent: Color
    @Composable get() = themePlannerAccent(LocalAppTheme.current)

val GoalsAccent: Color
    @Composable get() = themeGoalsAccent(LocalAppTheme.current)

val ChatAccent: Color
    @Composable get() = themeChatAccent(LocalAppTheme.current)

val DiaryTypeColor: Color
    @Composable get() = themeDiaryAccent(LocalAppTheme.current)

val PostTypeColor: Color
    @Composable get() = themeChatAccent(LocalAppTheme.current)

val NoteTypeColor: Color
    @Composable get() = themeGoalsAccent(LocalAppTheme.current)

// =============================================================================
// 기존 레거시 색상 호환 유지 (외부 참조용)
// =============================================================================
/** @deprecated Color.kt v4.0 — 아래 레거시 이름은 하위 호환용. 신규 코드는 Blossom/Atlas/Eclipse 접두사 사용 */
val PrimaryLight            = BlossomPrimaryLight
val OnPrimaryLight          = BlossomOnPrimaryLight
val PrimaryContainerLight   = BlossomPrimaryContainerLight
val OnPrimaryContainerLight = BlossomOnPrimaryContainerLight
val PrimaryDark             = BlossomPrimaryDark
val OnPrimaryDark           = BlossomOnPrimaryDark
val PrimaryContainerDark    = BlossomPrimaryContainerDark
val OnPrimaryContainerDark  = BlossomOnPrimaryContainerDark

val SecondaryLight            = BlossomSecondaryLight
val OnSecondaryLight          = BlossomOnSecondaryLight
val SecondaryContainerLight   = BlossomSecondaryContainerLight
val OnSecondaryContainerLight = BlossomOnSecondaryContainerLight
val SecondaryDark             = BlossomSecondaryDark
val OnSecondaryDark           = BlossomOnSecondaryDark
val SecondaryContainerDark    = BlossomSecondaryContainerDark
val OnSecondaryContainerDark  = BlossomOnSecondaryContainerDark

val TertiaryLight            = BlossomTertiaryLight
val OnTertiaryLight          = BlossomOnTertiaryLight
val TertiaryContainerLight   = BlossomTertiaryContainerLight
val OnTertiaryContainerLight = BlossomOnTertiaryContainerLight
val TertiaryDark             = BlossomTertiaryDark
val OnTertiaryDark           = BlossomOnTertiaryDark
val TertiaryContainerDark    = BlossomTertiaryContainerDark
val OnTertiaryContainerDark  = BlossomOnTertiaryContainerDark

val SurfaceLight          = BlossomSurfaceLight
val OnSurfaceLight        = BlossomOnSurfaceLight
val SurfaceVariantLight   = BlossomSurfaceVariantLight
val OnSurfaceVariantLight = BlossomOnSurfaceVariantLight
val OutlineLight          = BlossomOutlineLight
val OutlineVariantLight   = BlossomOutlineVariantLight

val SurfaceDark           = BlossomSurfaceDark
val OnSurfaceDark         = BlossomOnSurfaceDark
val SurfaceVariantDark    = BlossomSurfaceVariantDark
val OnSurfaceVariantDark  = BlossomOnSurfaceVariantDark
val OutlineDark           = BlossomOutlineDark
val OutlineVariantDark    = BlossomOutlineVariantDark

val BackgroundLight = BlossomBackgroundLight
val BackgroundDark  = BlossomBackgroundDark

val Purple80      = Color(0xFFD0BCFF)
val PurpleGrey80  = Color(0xFFCCC2DC)
val Pink80        = Color(0xFFEFB8C8)
val Purple40      = Color(0xFF6650a4)
val PurpleGrey40  = Color(0xFF625b71)
val Pink40        = Color(0xFF7D5260)
