package com.grepiu.aidiary.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AIDiary 브랜드 컬러 시스템 v3.0
 *
 * 타겟: 20-30대 여성 60% + 남성 40% (남성 이질감 최소화)
 * 무드: 소프트 페미닌 — 더스티로즈/모브/피치/세이지, 부드럽고 따뜻하며 세련됨
 * 키워드: soft, feminine, warm, sophisticated, layered
 *
 * 핵심 원칙:
 *  - 3단 레이어링: Background(#FBF6F5) < Surface Variant(#F4EAEA) 웜톤, Surface(#FFFFFF)는 밝게 띄워 카드 리프트
 *  - 기능별 액센트 4방향 색상환 분산(로즈/피치/세이지/모브)으로 명도가 아닌 색상(hue)으로 구분
 */

// =============================================================================
// Primary — 더스티 로즈 (Dusty Rose): 소프트 페미닌, 따뜻함, 세련됨
// =============================================================================
val PrimaryLight = Color(0xFFC67A8E)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFF7DCE2)
val OnPrimaryContainerLight = Color(0xFF40121F)

val PrimaryDark = Color(0xFFE7A6B4)
val OnPrimaryDark = Color(0xFF40121F)
val PrimaryContainerDark = Color(0xFF5D3542)
val OnPrimaryContainerDark = Color(0xFFF7DCE2)

// =============================================================================
// Secondary — 모브 (Mauve/Periwinkle): 은은한 퍼플, AI/포인트
// =============================================================================
val SecondaryLight = Color(0xFF8B87C7)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE7E2F5)
val OnSecondaryContainerLight = Color(0xFF231A3E)

val SecondaryDark = Color(0xFFC3BEF0)
val OnSecondaryDark = Color(0xFF231A3E)
val SecondaryContainerDark = Color(0xFF423A5C)
val OnSecondaryContainerDark = Color(0xFFE7E2F5)

// =============================================================================
// Tertiary — 세이지 (Sage): 평온, 자연, 목표
// =============================================================================
val TertiaryLight = Color(0xFF5FA37E)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFCDEEDB)
val OnTertiaryContainerLight = Color(0xFF00210F)

val TertiaryDark = Color(0xFF9AD3B4)
val OnTertiaryDark = Color(0xFF00210F)
val TertiaryContainerDark = Color(0xFF2F5240)
val OnTertiaryContainerDark = Color(0xFFCDEEDB)

// =============================================================================
// Surface / Background — 3단 레이어링 (블러시 웜화이트 + 카드 리프트)
// =============================================================================
val SurfaceLight = Color(0xFFFFFFFF)          // 카드: 배경보다 밝게 → 시각적으로 뜸
val OnSurfaceLight = Color(0xFF241F1E)
val SurfaceVariantLight = Color(0xFFF4EAEA)   // 서브카드·캘린더 래퍼·구분 영역
val OnSurfaceVariantLight = Color(0xFF5B4F4C)
val OutlineLight = Color(0xFF9A8B88)
val OutlineVariantLight = Color(0xFFE4D2D0)

val SurfaceDark = Color(0xFF241E21)           // 카드 리프트
val OnSurfaceDark = Color(0xFFECE0DE)
val SurfaceVariantDark = Color(0xFF3A3134)
val OnSurfaceVariantDark = Color(0xFFD8C6C4)
val OutlineDark = Color(0xFFA08C88)
val OutlineVariantDark = Color(0xFF4E4244)

// =============================================================================
// Tab Accent Colors — 기능별 액센트 (색상환 4방향 분산)
//   기록=로즈핑크 · 플래너=피치 · 목표=세이지 · AI비서=모브
// =============================================================================
val DiaryAccent = Color(0xFFC67A8E)
val PlannerAccent = Color(0xFFE8945C)
val GoalsAccent = Color(0xFF5FA37E)
val ChatAccent = Color(0xFF8B87C7)

// =============================================================================
// Emotion Colors — 감정 시각화 (새 팔레트 조화 재튜닝)
// =============================================================================
val EmotionJoy = Color(0xFFE0A94A)      // 웜 골드
val EmotionSadness = Color(0xFF6B8BAE)  // 스틸 블루
val EmotionAnger = Color(0xFFD96B6B)    // 코랄
val EmotionAnxiety = Color(0xFF9585BE)  // 더스티 퍼플
val EmotionCalm = Color(0xFF6FA98A)     // 세이지

// =============================================================================
// Content Type Colors
// =============================================================================
val DiaryTypeColor = Color(0xFFC67A8E)  // 로즈
val PostTypeColor = Color(0xFF8B87C7)   // 모브
val NoteTypeColor = Color(0xFF5FA37E)   // 세이지

// =============================================================================
// Utility
// =============================================================================
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

val BackgroundLight = Color(0xFFFBF6F5)  // 블러시 웜화이트 (핑크 언더톤 미세)
val BackgroundDark = Color(0xFF1A1618)   // 웜 니어블랙

// 기존 레거시 색상 (외부 참조 호환 유지)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
