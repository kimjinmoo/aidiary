package com.grepiu.aidiary.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 앱 색상 테마 종류.
 *
 * @param label 설정 화면에 표시할 사용자 향 이름
 * @param emoji 설정 화면 아이콘
 * @param description 테마 무드 설명
 */
enum class AppTheme(val label: String, val emoji: String, val description: String) {
    ATLAS(       "아틀라스", "🗺️", "차분하고 중성적인 블루 톤"),
    AURORA_GLASS("오로라 글래스", "🌌", "북극광처럼 화려하고 오묘한 오로라 글래스 테마"),
    BLOSSOM(     "블라섬", "🌸", "소프트 & 따뜻한 로즈 톤"),
    ECLIPSE(     "이클립스", "🌑", "세련된 딥 다크 블랙 테마"),
    KIDS(        "키즈 캔디", "🧸", "8~13세 아이들을 위한 톡톡 튀는 캔디 무지개 파스텔 테마")
}

// =============================================================================
// BLOSSOM ColorScheme (기존 더스티 로즈 팔레트)
// =============================================================================
private val BlossomLightColorScheme = lightColorScheme(
    primary             = BlossomPrimaryLight,
    onPrimary           = BlossomOnPrimaryLight,
    primaryContainer    = BlossomPrimaryContainerLight,
    onPrimaryContainer  = BlossomOnPrimaryContainerLight,
    secondary           = BlossomSecondaryLight,
    onSecondary         = BlossomOnSecondaryLight,
    secondaryContainer  = BlossomSecondaryContainerLight,
    onSecondaryContainer= BlossomOnSecondaryContainerLight,
    tertiary            = BlossomTertiaryLight,
    onTertiary          = BlossomOnTertiaryLight,
    tertiaryContainer   = BlossomTertiaryContainerLight,
    onTertiaryContainer = BlossomOnTertiaryContainerLight,
    error               = ErrorLight,
    onError             = OnErrorLight,
    background          = BlossomBackgroundLight,
    onBackground        = BlossomOnSurfaceLight,
    surface             = BlossomSurfaceLight,
    onSurface           = BlossomOnSurfaceLight,
    surfaceVariant      = BlossomSurfaceVariantLight,
    onSurfaceVariant    = BlossomOnSurfaceVariantLight,
    outline             = BlossomOutlineLight,
    outlineVariant      = BlossomOutlineVariantLight,
)

private val BlossomDarkColorScheme = darkColorScheme(
    primary             = BlossomPrimaryDark,
    onPrimary           = BlossomOnPrimaryDark,
    primaryContainer    = BlossomPrimaryContainerDark,
    onPrimaryContainer  = BlossomOnPrimaryContainerDark,
    secondary           = BlossomSecondaryDark,
    onSecondary         = BlossomOnSecondaryDark,
    secondaryContainer  = BlossomSecondaryContainerDark,
    onSecondaryContainer= BlossomOnSecondaryContainerDark,
    tertiary            = BlossomTertiaryDark,
    onTertiary          = BlossomOnTertiaryDark,
    tertiaryContainer   = BlossomTertiaryContainerDark,
    onTertiaryContainer = BlossomOnTertiaryContainerDark,
    error               = ErrorDark,
    onError             = OnErrorDark,
    background          = BlossomBackgroundDark,
    onBackground        = BlossomOnSurfaceDark,
    surface             = BlossomSurfaceDark,
    onSurface           = BlossomOnSurfaceDark,
    surfaceVariant      = BlossomSurfaceVariantDark,
    onSurfaceVariant    = BlossomOnSurfaceVariantDark,
    outline             = BlossomOutlineDark,
    outlineVariant      = BlossomOutlineVariantDark,
)

// =============================================================================
// ATLAS ColorScheme (남녀 공용, 스틸 블루 + 앰버 + 틸)
// =============================================================================
private val AtlasLightColorScheme = lightColorScheme(
    primary             = AtlasPrimaryLight,
    onPrimary           = AtlasOnPrimaryLight,
    primaryContainer    = AtlasPrimaryContainerLight,
    onPrimaryContainer  = AtlasOnPrimaryContainerLight,
    secondary           = AtlasSecondaryLight,
    onSecondary         = AtlasOnSecondaryLight,
    secondaryContainer  = AtlasSecondaryContainerLight,
    onSecondaryContainer= AtlasOnSecondaryContainerLight,
    tertiary            = AtlasTertiaryLight,
    onTertiary          = AtlasOnTertiaryLight,
    tertiaryContainer   = AtlasTertiaryContainerLight,
    onTertiaryContainer = AtlasOnTertiaryContainerLight,
    error               = ErrorLight,
    onError             = OnErrorLight,
    background          = AtlasBackgroundLight,
    onBackground        = AtlasOnSurfaceLight,
    surface             = AtlasSurfaceLight,
    onSurface           = AtlasOnSurfaceLight,
    surfaceVariant      = AtlasSurfaceVariantLight,
    onSurfaceVariant    = AtlasOnSurfaceVariantLight,
    outline             = AtlasOutlineLight,
    outlineVariant      = AtlasOutlineVariantLight,
)

private val AtlasDarkColorScheme = darkColorScheme(
    primary             = AtlasPrimaryDark,
    onPrimary           = AtlasOnPrimaryDark,
    primaryContainer    = AtlasPrimaryContainerDark,
    onPrimaryContainer  = AtlasOnPrimaryContainerDark,
    secondary           = AtlasSecondaryDark,
    onSecondary         = AtlasOnSecondaryDark,
    secondaryContainer  = AtlasSecondaryContainerDark,
    onSecondaryContainer= AtlasOnSecondaryContainerDark,
    tertiary            = AtlasTertiaryDark,
    onTertiary          = AtlasOnTertiaryDark,
    tertiaryContainer   = AtlasTertiaryContainerDark,
    onTertiaryContainer = AtlasOnTertiaryContainerDark,
    error               = ErrorDark,
    onError             = OnErrorDark,
    background          = AtlasBackgroundDark,
    onBackground        = AtlasOnSurfaceDark,
    surface             = AtlasSurfaceDark,
    onSurface           = AtlasOnSurfaceDark,
    surfaceVariant      = AtlasSurfaceVariantDark,
    onSurfaceVariant    = AtlasOnSurfaceVariantDark,
    outline             = AtlasOutlineDark,
    outlineVariant      = AtlasOutlineVariantDark,
)

// =============================================================================
// ECLIPSE ColorScheme (블랙 테마 — Dark only)
// =============================================================================
private val EclipseColorScheme = darkColorScheme(
    primary             = EclipsePrimary,
    onPrimary           = EclipseOnPrimary,
    primaryContainer    = EclipsePrimaryContainer,
    onPrimaryContainer  = EclipseOnPrimaryContainer,
    secondary           = EclipseSecondary,
    onSecondary         = EclipseOnSecondary,
    secondaryContainer  = EclipseSecondaryContainer,
    onSecondaryContainer= EclipseOnSecondaryContainer,
    tertiary            = EclipseTertiary,
    onTertiary          = EclipseOnTertiary,
    tertiaryContainer   = EclipseTertiaryContainer,
    onTertiaryContainer = EclipseOnTertiaryContainer,
    error               = ErrorDark,
    onError             = OnErrorDark,
    background          = EclipseBackground,
    onBackground        = EclipseOnSurface,
    surface             = EclipseSurface,
    onSurface           = EclipseOnSurface,
    outline             = EclipseOutline,
    outlineVariant      = EclipseOutlineVariant,
)
// =============================================================================
// KIDS ColorScheme (8~13세 아이들 타깃, 캔디 무지개 핑크 + 바나나 옐로우 + 라임)
// =============================================================================
private val KidsLightColorScheme = lightColorScheme(
    primary             = androidx.compose.ui.graphics.Color(0xFFFF6B81),
    onPrimary           = androidx.compose.ui.graphics.Color.White,
    primaryContainer    = androidx.compose.ui.graphics.Color(0xFFFFF0F5),
    onPrimaryContainer  = androidx.compose.ui.graphics.Color(0xFFD63031),
    secondary           = androidx.compose.ui.graphics.Color(0xFFFFA502),
    onSecondary         = androidx.compose.ui.graphics.Color.White,
    secondaryContainer  = androidx.compose.ui.graphics.Color(0xFFFFECC4),
    onSecondaryContainer= androidx.compose.ui.graphics.Color(0xFFE67E22),
    tertiary            = androidx.compose.ui.graphics.Color(0xFF2ED573),
    onTertiary          = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer   = androidx.compose.ui.graphics.Color(0xFFE8FAEB),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF10AC84),
    error               = ErrorLight,
    onError             = OnErrorLight,
    background          = androidx.compose.ui.graphics.Color(0xFFFFFDF9),
    onBackground        = androidx.compose.ui.graphics.Color(0xFF2D3436),
    surface             = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface           = androidx.compose.ui.graphics.Color(0xFF2D3436),
    surfaceVariant      = androidx.compose.ui.graphics.Color(0xFFFFF5EE),
    onSurfaceVariant    = androidx.compose.ui.graphics.Color(0xFF636E72),
    outline             = androidx.compose.ui.graphics.Color(0xFFFFBE76),
    outlineVariant      = androidx.compose.ui.graphics.Color(0xFFFFEAA7),
)

private val KidsDarkColorScheme = darkColorScheme(
    primary             = androidx.compose.ui.graphics.Color(0xFFFF7675),
    onPrimary           = androidx.compose.ui.graphics.Color(0xFF2D3436),
    primaryContainer    = androidx.compose.ui.graphics.Color(0xFF632B30),
    onPrimaryContainer  = androidx.compose.ui.graphics.Color(0xFFFFD8D8),
    secondary           = androidx.compose.ui.graphics.Color(0xFFFFBE76),
    onSecondary         = androidx.compose.ui.graphics.Color(0xFF2D3436),
    secondaryContainer  = androidx.compose.ui.graphics.Color(0xFF5C431A),
    onSecondaryContainer= androidx.compose.ui.graphics.Color(0xFFFFEAA7),
    tertiary            = androidx.compose.ui.graphics.Color(0xFF55E6C1),
    onTertiary          = androidx.compose.ui.graphics.Color(0xFF2D3436),
    tertiaryContainer   = androidx.compose.ui.graphics.Color(0xFF1B4D3E),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFC7FCEB),
    error               = ErrorDark,
    onError             = OnErrorDark,
    background          = androidx.compose.ui.graphics.Color(0xFF1E1E24),
    onBackground        = androidx.compose.ui.graphics.Color(0xFFF5F6FA),
    surface             = androidx.compose.ui.graphics.Color(0xFF25252E),
    onSurface           = androidx.compose.ui.graphics.Color(0xFFF5F6FA),
    surfaceVariant      = androidx.compose.ui.graphics.Color(0xFF2F2F3D),
    onSurfaceVariant    = androidx.compose.ui.graphics.Color(0xFFDFE4EA),
    outline             = androidx.compose.ui.graphics.Color(0xFFFF7675),
    outlineVariant      = androidx.compose.ui.graphics.Color(0xFF57606F),
)

// =============================================================================
// AURORA_GLASS ColorScheme (오로라 글래스 — 바이올렛 + 사이버 사안 + 코스믹 핑크)
// =============================================================================
private val AuroraGlassLightColorScheme = lightColorScheme(
    primary             = AuroraGlassPrimaryLight,
    onPrimary           = AuroraGlassOnPrimaryLight,
    primaryContainer    = AuroraGlassPrimaryContainerLight,
    onPrimaryContainer  = AuroraGlassOnPrimaryContainerLight,
    secondary           = AuroraGlassSecondaryLight,
    onSecondary         = AuroraGlassOnSecondaryLight,
    secondaryContainer  = AuroraGlassSecondaryContainerLight,
    onSecondaryContainer= AuroraGlassOnSecondaryContainerLight,
    tertiary            = AuroraGlassTertiaryLight,
    onTertiary          = AuroraGlassOnTertiaryLight,
    tertiaryContainer   = AuroraGlassTertiaryContainerLight,
    onTertiaryContainer = AuroraGlassOnTertiaryContainerLight,
    error               = ErrorLight,
    onError             = OnErrorLight,
    background          = AuroraGlassBackgroundLight,
    onBackground        = AuroraGlassOnSurfaceLight,
    surface             = AuroraGlassSurfaceLight,
    onSurface           = AuroraGlassOnSurfaceLight,
    surfaceVariant      = AuroraGlassSurfaceVariantLight,
    onSurfaceVariant    = AuroraGlassOnSurfaceVariantLight,
    outline             = AuroraGlassOutlineLight,
    outlineVariant      = AuroraGlassOutlineVariantLight,
)

private val AuroraGlassDarkColorScheme = darkColorScheme(
    primary             = AuroraGlassPrimaryDark,
    onPrimary           = AuroraGlassOnPrimaryDark,
    primaryContainer    = AuroraGlassPrimaryContainerDark,
    onPrimaryContainer  = AuroraGlassOnPrimaryContainerDark,
    secondary           = AuroraGlassSecondaryDark,
    onSecondary         = AuroraGlassOnSecondaryDark,
    secondaryContainer  = AuroraGlassSecondaryContainerDark,
    onSecondaryContainer= AuroraGlassOnSecondaryContainerDark,
    tertiary            = AuroraGlassTertiaryDark,
    onTertiary          = AuroraGlassOnTertiaryDark,
    tertiaryContainer   = AuroraGlassTertiaryContainerDark,
    onTertiaryContainer = AuroraGlassOnTertiaryContainerDark,
    error               = ErrorDark,
    onError             = OnErrorDark,
    background          = AuroraGlassBackgroundDark,
    onBackground        = AuroraGlassOnSurfaceDark,
    surface             = AuroraGlassSurfaceDark,
    onSurface           = AuroraGlassOnSurfaceDark,
    surfaceVariant      = AuroraGlassSurfaceVariantDark,
    onSurfaceVariant    = AuroraGlassOnSurfaceVariantDark,
    outline             = AuroraGlassOutlineDark,
    outlineVariant      = AuroraGlassOutlineVariantDark,
)

/** 현재 앱 테마를 Composable 트리에 전달하는 CompositionLocal */
val LocalAppTheme = compositionLocalOf { AppTheme.ATLAS }

/**
 * AIDiary 앱 테마 컴포저블.
 *
 * @param appTheme 사용자가 설정에서 선택한 [AppTheme]. 기본값 [AppTheme.ATLAS].
 * @param darkTheme 시스템 다크 모드 여부. Eclipse는 항상 다크 적용.
 * @param dynamicColor Android 12+ Dynamic Color 사용 여부 (비활성화 기본).
 */
@Composable
fun AIDiaryTheme(
    appTheme: AppTheme = AppTheme.ATLAS,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic Color는 BLOSSOM일 때만 허용 (다른 테마는 커스텀 팔레트 우선)
        dynamicColor && appTheme == AppTheme.BLOSSOM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        appTheme == AppTheme.ECLIPSE      -> EclipseColorScheme
        appTheme == AppTheme.AURORA_GLASS -> if (darkTheme) AuroraGlassDarkColorScheme else AuroraGlassLightColorScheme
        appTheme == AppTheme.KIDS         -> if (darkTheme) KidsDarkColorScheme else KidsLightColorScheme
        appTheme == AppTheme.ATLAS        -> if (darkTheme) AtlasDarkColorScheme else AtlasLightColorScheme
        else                              -> if (darkTheme) BlossomDarkColorScheme else BlossomLightColorScheme
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DiaryTypography,
            content = content
        )
    }
}
