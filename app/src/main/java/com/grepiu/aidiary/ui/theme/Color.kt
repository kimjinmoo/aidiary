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
// =============================================================================
// ── ATLAS (Default, 로열 블루 + 선버스트 앰버 + 에메랄드 그린 + 슬레이트) ─────────
// Primary — 로열 블루 (Royal Navy Blue): 높은 가시성, 가독성, 신뢰감
// =============================================================================
val AtlasPrimaryLight            = Color(0xFF1D5D9B)
val AtlasOnPrimaryLight          = Color(0xFFFFFFFF)
val AtlasPrimaryContainerLight   = Color(0xFFD8E6F5)
val AtlasOnPrimaryContainerLight = Color(0xFF0C2B4B)

val AtlasPrimaryDark             = Color(0xFF5C9CE6)
val AtlasOnPrimaryDark           = Color(0xFF061E38)
val AtlasPrimaryContainerDark    = Color(0xFF14416D)
val AtlasOnPrimaryContainerDark  = Color(0xFFD8E6F5)

// Secondary — 선버스트 앰버 골드 (Sunburst Amber): 명확한 서브 액센트 및 포인트
val AtlasSecondaryLight            = Color(0xFFD97706)
val AtlasOnSecondaryLight          = Color(0xFFFFFFFF)
val AtlasSecondaryContainerLight   = Color(0xFFFEF3C7)
val AtlasOnSecondaryContainerLight = Color(0xFF78350F)

val AtlasSecondaryDark             = Color(0xFFFBBF24)
val AtlasOnSecondaryDark           = Color(0xFF451A03)
val AtlasSecondaryContainerDark    = Color(0xFF78350F)
val AtlasOnSecondaryContainerDark  = Color(0xFFFEF3C7)

// Tertiary — 에메랄드 그린 (Emerald Teal): 목표 및 진행률 선명한 시각화
val AtlasTertiaryLight            = Color(0xFF059669)
val AtlasOnTertiaryLight          = Color(0xFFFFFFFF)
val AtlasTertiaryContainerLight   = Color(0xFFD1FAE5)
val AtlasOnTertiaryContainerLight = Color(0xFF064E3B)

val AtlasTertiaryDark             = Color(0xFF34D399)
val AtlasOnTertiaryDark           = Color(0xFF022C22)
val AtlasTertiaryContainerDark    = Color(0xFF064E3B)
val AtlasOnTertiaryContainerDark  = Color(0xFFD1FAE5)

// Surface / Background — 슬레이트 계열 높은 대비 (High-Contrast Slate)
val AtlasSurfaceLight          = Color(0xFFFFFFFF)
val AtlasOnSurfaceLight        = Color(0xFF0F172A) // 딥 슬레이트 블랙 (가독성 100%)
val AtlasSurfaceVariantLight   = Color(0xFFF1F5F9) // 서브 카드 백그라운드
val AtlasOnSurfaceVariantLight = Color(0xFF334155) // 슬레이트 딥 차콜 (또렷한 본문/보조 텍스트)
val AtlasOutlineLight          = Color(0xFF94A3B8) // 명확한 경계선
val AtlasOutlineVariantLight   = Color(0xFFE2E8F0)

val AtlasSurfaceDark           = Color(0xFF1E293B) // 딥 슬레이트 카드 표면
val AtlasOnSurfaceDark         = Color(0xFFF8FAFC) // 순백 텍스트
val AtlasSurfaceVariantDark    = Color(0xFF334155)
val AtlasOnSurfaceVariantDark  = Color(0xFFCBD5E1)
val AtlasOutlineDark           = Color(0xFF64748B)
val AtlasOutlineVariantDark    = Color(0xFF334155)

val AtlasBackgroundLight = Color(0xFFF8FAFC)  // 슬레이트 펄 화이트
val AtlasBackgroundDark  = Color(0xFF0F172A)  // 딥 슬레이트 미드나잇

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

// =============================================================================
// ── AURORA_GLASS (북극광처럼 화려한 오로라 글래스 — 바이올렛 + 네온 사안 + 코스믹 핑크) ──
// Primary — 오로라 바이올렛 (Electric Aurora Violet)
// =============================================================================
val AuroraGlassPrimaryLight            = Color(0xFFA855F7)
val AuroraGlassOnPrimaryLight          = Color(0xFFFFFFFF)
val AuroraGlassPrimaryContainerLight   = Color(0xFFF3E8FF)
val AuroraGlassOnPrimaryContainerLight = Color(0xFF581C87)

val AuroraGlassPrimaryDark             = Color(0xFFC084FC)
val AuroraGlassOnPrimaryDark           = Color(0xFF3B0764)
val AuroraGlassPrimaryContainerDark    = Color(0xFF6B21A8)
val AuroraGlassOnPrimaryContainerDark  = Color(0xFFF3E8FF)

// Secondary — 네온 사이버 사안 (Cyber Cyan Neon)
val AuroraGlassSecondaryLight            = Color(0xFF06B6D4)
val AuroraGlassOnSecondaryLight          = Color(0xFFFFFFFF)
val AuroraGlassSecondaryContainerLight   = Color(0xFFCFFAFE)
val AuroraGlassOnSecondaryContainerLight = Color(0xFF155E75)

val AuroraGlassSecondaryDark             = Color(0xFF22D3EE)
val AuroraGlassOnSecondaryDark           = Color(0xFF083344)
val AuroraGlassSecondaryContainerDark    = Color(0xFF0E7490)
val AuroraGlassOnSecondaryContainerDark  = Color(0xFFCFFAFE)

// Tertiary — 코스믹 핑크 글로우 (Radiant Cosmic Pink)
val AuroraGlassTertiaryLight            = Color(0xFFF43F5E)
val AuroraGlassOnTertiaryLight          = Color(0xFFFFFFFF)
val AuroraGlassTertiaryContainerLight   = Color(0xFFFFE4E6)
val AuroraGlassOnTertiaryContainerLight = Color(0xFF881337)

val AuroraGlassTertiaryDark             = Color(0xFFFB7185)
val AuroraGlassOnTertiaryDark           = Color(0xFF4C0519)
val AuroraGlassTertiaryContainerDark    = Color(0xFF9F1239)
val AuroraGlassOnTertiaryContainerDark  = Color(0xFFFFE4E6)

// Surface / Background — 라벤더 오팔 & 딥 코스믹 미드나잇
val AuroraGlassSurfaceLight          = Color(0xFFFFFFFF)
val AuroraGlassOnSurfaceLight        = Color(0xFF1E1535)
val AuroraGlassSurfaceVariantLight   = Color(0xFFF3E8FF)
val AuroraGlassOnSurfaceVariantLight = Color(0xFF4C3B6E)
val AuroraGlassOutlineLight          = Color(0xFFC084FC)
val AuroraGlassOutlineVariantLight   = Color(0xFFE9D5FF)

val AuroraGlassSurfaceDark           = Color(0xFF1A103C)
val AuroraGlassOnSurfaceDark         = Color(0xFFF5F3FF)
val AuroraGlassSurfaceVariantDark    = Color(0xFF2E1F5C)
val AuroraGlassOnSurfaceVariantDark  = Color(0xFFDDD6FE)
val AuroraGlassOutlineDark           = Color(0xFFA855F7)
val AuroraGlassOutlineVariantDark    = Color(0xFF4C1D95)

val AuroraGlassBackgroundLight = Color(0xFFFAF5FF)  // 프레시 프로스티드 오팔 라벤더
val AuroraGlassBackgroundDark  = Color(0xFF0F0728)  // 딥 코스믹 스페이스 미드나잇

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
    AppTheme.ATLAS        -> Color(0xFF1D5D9B) // 로열 네이비 블루
    AppTheme.AURORA_GLASS -> Color(0xFFA855F7) // 오로라 바이올렛
    AppTheme.BLOSSOM      -> Color(0xFFC67A8E) // 더스티 로즈
    AppTheme.ECLIPSE      -> Color(0xFF8B8FF8) // 라이트 인디고 네온
    AppTheme.KIDS         -> Color(0xFFFF6B81) // 캔디 버블검 핑크
}

fun themePlannerAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS        -> Color(0xFFD97706) // 선버스트 앰버 골드
    AppTheme.AURORA_GLASS -> Color(0xFFFB923C) // 오로라 코스믹 오렌지
    AppTheme.BLOSSOM      -> Color(0xFFE8945C) // 웜 피치
    AppTheme.ECLIPSE      -> Color(0xFFFFB74D) // 앰버 글로우
    AppTheme.KIDS         -> Color(0xFFFFA502) // 캔디 바나나 오렌지
}

fun themeGoalsAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS        -> Color(0xFF059669) // 에메랄드 그린
    AppTheme.AURORA_GLASS -> Color(0xFF22D3EE) // 사이버 사안 네온
    AppTheme.BLOSSOM      -> Color(0xFF5FA37E) // 세이지 그린
    AppTheme.ECLIPSE      -> Color(0xFF4DCFB0) // 아쿠아 민트
    AppTheme.KIDS         -> Color(0xFF2ED573) // 캔디 라임 민트
}

fun themeChatAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.ATLAS        -> Color(0xFF4F46E5) // 로열 인디고
    AppTheme.AURORA_GLASS -> Color(0xFFF43F5E) // 코스믹 핑크 글로우
    AppTheme.BLOSSOM      -> Color(0xFF8B87C7) // 모브
    AppTheme.ECLIPSE      -> Color(0xFFBB86FC) // 네온 바이올렛
    AppTheme.KIDS         -> Color(0xFF70A1FF) // 캔디 스카이 블루
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
